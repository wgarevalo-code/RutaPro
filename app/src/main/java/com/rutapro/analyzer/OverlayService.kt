package com.rutapro.analyzer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.rutapro.analyzer.analyzer.Analysis
import com.rutapro.analyzer.analyzer.RideOffer
import com.rutapro.analyzer.analyzer.Verdict
import com.rutapro.analyzer.data.EntryType
import com.rutapro.analyzer.data.Ledger
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Servicio en primer plano que muestra un POPUP temporal cuando llega una carrera.
 * No deja ninguna burbuja permanente en pantalla: aparece, informas, y se va solo.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var ledger: Ledger
    private val handler = Handler(Looper.getMainLooper())

    private var popup: View? = null
    private val autoHide = Runnable { removePopup() }

    private var currentFare: Double = 0.0
    private var currentApp: String = "Uber"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        ledger = Ledger(this)
        startForegroundNotification()
    }

    /** Llamado desde el servicio de accesibilidad cuando hay una oferta analizada. */
    fun showAnalysis(offer: RideOffer, analysis: Analysis, sourceApp: String = "Uber") {
        handler.post {
            currentFare = offer.fare
            currentApp = sourceApp
            removePopup()

            val view = LayoutInflater.from(this).inflate(R.layout.overlay_popup, null)
            val color = when (analysis.verdict) {
                Verdict.GOOD -> Color.parseColor("#2BD576")
                Verdict.FAIR -> Color.parseColor("#FFCC33")
                Verdict.BAD -> Color.parseColor("#FF4D5E")
            }

            (view.background?.mutate() as? GradientDrawable)?.setStroke(dp(2), color)

            val headline = view.findViewById<TextView>(R.id.popupHeadline)
            headline.text = analysis.headline
            headline.setTextColor(color)

            view.findViewById<TextView>(R.id.popupBody).text = buildString {
                append("Por hora  ").append(money(analysis.perHour)).append('\n')
                append("Por km    ").append(money(analysis.perKm)).append('\n')
                append("Recogida  ").append(km(offer.pickupKm)).append(" km\n")
                append("Ofrecen   ").append(money(offer.fare)).append("  ·  ").append(sourceApp)
            }

            // inDrive se negocia: mostramos cuanto pedir en grande.
            val negoView = view.findViewById<TextView>(R.id.popupNegotiate)
            if (sourceApp == "inDrive") {
                negoView.visibility = View.VISIBLE
                negoView.text = buildString {
                    append("💬 Pide  ").append(money(analysis.goodFare)).append('\n')
                    append("mínimo que te conviene: ").append(money(analysis.fairFare))
                }
                negoView.setTextColor(
                    if (offer.fare >= analysis.fairFare) Color.parseColor("#2BD576")
                    else Color.parseColor("#FFCC33")
                )
            } else {
                negoView.visibility = View.GONE
            }

            view.findViewById<TextView>(R.id.btnTook).setOnClickListener {
                if (currentFare > 0) {
                    // Guarda tambien donde apareció, para el mapa de mejores zonas.
                    val pos = com.rutapro.analyzer.location.LocationTracker.lastPosition(this)
                    ledger.add(
                        EntryType.RIDE, currentFare, "Carrera $currentApp", currentApp, "",
                        0.0, 0.0, pos?.first ?: 0.0, pos?.second ?: 0.0
                    )
                    toast("Carrera registrada: " + money(currentFare))
                }
                removePopup()
            }
            view.findViewById<TextView>(R.id.btnSkip).setOnClickListener { removePopup() }

            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = dp(90)
            }

            try {
                windowManager.addView(view, params)
                popup = view
                handler.removeCallbacks(autoHide)
                handler.postDelayed(autoHide, 30000)
            } catch (_: Exception) {
            }
        }
    }

    private fun removePopup() {
        handler.removeCallbacks(autoHide)
        popup?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        popup = null
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()
    private fun money(v: Double): String = String.format(Locale.US, "$%.2f", v)
    private fun km(v: Double): String = String.format(Locale.US, "%.1f", v)

    private fun toast(msg: String) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun startForegroundNotification() {
        val channelId = "ruta_pro_overlay"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "RutaPro", NotificationManager.IMPORTANCE_MIN)
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, channelId)
        else
            @Suppress("DEPRECATION") Notification.Builder(this)
        val notification = builder
            .setContentTitle("RutaPro activo")
            .setContentText("Analizando carreras")
            .setSmallIcon(R.drawable.ic_arrow_white)
            .build()
        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        removePopup()
        instance = null
    }

    companion object {
        @Volatile
        var instance: OverlayService? = null
            private set
    }
}
