package com.rutapro.analyzer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import com.rutapro.analyzer.analyzer.Analysis
import com.rutapro.analyzer.analyzer.RideOffer
import com.rutapro.analyzer.analyzer.Verdict
import kotlin.math.roundToInt

/**
 * Servicio en primer plano que dibuja UNA sola burbuja flotante: el logo de RutaPro.
 * Al llegar una carrera, el logo se pinta del color del veredicto y se despliega una
 * tarjeta con los numeros. Se puede arrastrar y tocar para ocultar/mostrar el detalle.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var root: View
    private lateinit var logo: ImageView
    private lateinit var result: TextView
    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())
    private val hideDetail = Runnable { result.visibility = View.GONE }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForegroundNotification()
        addBubble()
    }

    private fun addBubble() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        root = LayoutInflater.from(this).inflate(R.layout.overlay_bubble, null)
        logo = root.findViewById(R.id.overlayLogo)
        result = root.findViewById(R.id.overlayResult)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 260
        }

        enableDragAndTap()
        windowManager.addView(root, params)
    }

    private var downX = 0
    private var downY = 0
    private var touchX = 0f
    private var touchY = 0f
    private var moved = false

    private fun enableDragAndTap() {
        logo.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = params.x; downY = params.y
                    touchX = e.rawX; touchY = e.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - touchX).roundToInt()
                    val dy = (e.rawY - touchY).roundToInt()
                    if (kotlin.math.abs(dx) > 8 || kotlin.math.abs(dy) > 8) moved = true
                    params.x = downX + dx
                    params.y = downY + dy
                    windowManager.updateViewLayout(root, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        // Toque: alterna el detalle si hay algo que mostrar.
                        if (result.text.isNotBlank()) {
                            result.visibility =
                                if (result.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    /** Llamado desde el servicio de accesibilidad cuando hay una oferta analizada. */
    fun showAnalysis(offer: RideOffer, analysis: Analysis) {
        handler.post {
            val color = when (analysis.verdict) {
                Verdict.GOOD -> Color.parseColor("#2BD576")
                Verdict.FAIR -> Color.parseColor("#FFCC33")
                Verdict.BAD -> Color.parseColor("#FF4D5E")
            }
            logo.background?.mutate()?.setTint(color)
            (result.background?.mutate() as? android.graphics.drawable.GradientDrawable)
                ?.setStroke(dp(2), color)
            result.setTextColor(color)
            result.text = buildString {
                append(analysis.headline).append('\n')
                append("Por hora: ").append(money(analysis.perHour)).append('\n')
                append("Por km: ").append(money(analysis.perKm)).append('\n')
                append("Recogida: ").append(fmtKm(offer.pickupKm)).append(" km\n")
                append(analysis.reason)
            }
            result.visibility = View.VISIBLE
            // Vuelve al logo solo despues de unos segundos.
            handler.removeCallbacks(hideDetail)
            handler.postDelayed(hideDetail, 15000)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()
    private fun money(v: Double): String = v.roundToInt().toString()
    private fun fmtKm(v: Double): String =
        if (v >= 10) v.roundToInt().toString() else String.format("%.1f", v)

    private fun startForegroundNotification() {
        val channelId = "ruta_pro_overlay"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "RutaPro", NotificationManager.IMPORTANCE_MIN
            )
            nm.createNotificationChannel(channel)
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
        handler.removeCallbacks(hideDetail)
        if (::root.isInitialized) {
            try { windowManager.removeView(root) } catch (_: Exception) {}
        }
        instance = null
    }

    companion object {
        @Volatile
        var instance: OverlayService? = null
            private set
    }
}
