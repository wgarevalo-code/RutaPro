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
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.rutapro.analyzer.analyzer.Analysis
import com.rutapro.analyzer.analyzer.RideOffer
import com.rutapro.analyzer.analyzer.Verdict
import kotlin.math.roundToInt

/**
 * Servicio en primer plano que dibuja una burbuja flotante encima de Uber con el resultado.
 * Es arrastrable y se puede tocar para ocultar/mostrar el detalle.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var bubble: TextView
    private lateinit var params: WindowManager.LayoutParams
    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        startForegroundNotification()
        addBubble()
    }

    private fun addBubble() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        bubble = TextView(this).apply {
            text = "RutaPro\nlisto"
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(28, 18, 28, 18)
            setBackgroundResource(R.drawable.overlay_background)
        }

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
            y = 240
        }

        enableDrag()
        windowManager.addView(bubble, params)
    }

    private var downX = 0
    private var downY = 0
    private var touchX = 0f
    private var touchY = 0f

    private fun enableDrag() {
        bubble.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = params.x
                    downY = params.y
                    touchX = e.rawX
                    touchY = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = downX + (e.rawX - touchX).roundToInt()
                    params.y = downY + (e.rawY - touchY).roundToInt()
                    windowManager.updateViewLayout(bubble, params)
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
                Verdict.GOOD -> Color.parseColor("#1B8A3A")
                Verdict.FAIR -> Color.parseColor("#B8860B")
                Verdict.BAD -> Color.parseColor("#B00020")
            }
            bubble.setBackgroundResource(R.drawable.overlay_background)
            bubble.background?.setTint(color)

            bubble.text = buildString {
                append(analysis.headline).append('\n')
                append("$/km: ").append(money(analysis.perKm)).append('\n')
                append("$/min: ").append(money(analysis.perMin)).append('\n')
                append("Recogida: ").append(fmtKm(offer.pickupKm)).append(" km\n")
                append(analysis.reason)
            }
        }
    }

    private fun money(v: Double): String = v.roundToInt().toString()
    private fun fmtKm(v: Double): String =
        if (v >= 10) v.roundToInt().toString() else String.format("%.1f", v)

    private fun startForegroundNotification() {
        val channelId = "ruta_pro_overlay"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "RutaPro", NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(channel)
        }
        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("RutaPro activo")
            .setContentText("Analizando carreras en pantalla")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        startForeground(1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::bubble.isInitialized) {
            try { windowManager.removeView(bubble) } catch (_: Exception) {}
        }
        instance = null
    }

    companion object {
        @Volatile
        var instance: OverlayService? = null
            private set
    }
}
