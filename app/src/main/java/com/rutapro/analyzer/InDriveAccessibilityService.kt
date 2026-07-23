package com.rutapro.analyzer

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.rutapro.analyzer.analyzer.AppSettings
import com.rutapro.analyzer.analyzer.RideAnalyzer
import com.rutapro.analyzer.analyzer.Verdict
import com.rutapro.analyzer.parser.RideParser

/**
 * Lectura de inDrive por accesibilidad. Existe SOLO para inDrive, porque inDrive activa
 * FLAG_SECURE y la captura de pantalla (OCR) le devuelve una imagen en negro.
 * La accesibilidad no la bloquea FLAG_SECURE, asi que es la unica via posible.
 *
 * Para Uber se sigue usando el OCR (ScreenCaptureService); este servicio ni se activa
 * a menos que el usuario lo encienda a proposito para inDrive.
 */
class InDriveAccessibilityService : AccessibilityService() {

    private lateinit var settings: AppSettings
    private lateinit var analyzer: RideAnalyzer

    private var lastText = ""
    private var lastProcessMs = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        settings = AppSettings(this)
        analyzer = RideAnalyzer(settings)
        Log.i(TAG, "Lectura de inDrive lista")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (!settings.running) return
        val pkg = event.packageName?.toString() ?: return
        if (!pkg.startsWith("sinet.startup.inDriver")) return

        val now = System.currentTimeMillis()
        if (now - lastProcessMs < 500) return

        val root = rootInActiveWindow ?: return
        val sb = StringBuilder()
        collectText(root, sb)
        val text = sb.toString()
        if (text.isBlank() || text == lastText) return

        lastText = text
        lastProcessMs = now

        val offer = RideParser.parse(text) ?: return
        if (!offer.isValid) return

        // Cuenta y muestra solo la primera vez que aparece esta oferta.
        if (!com.rutapro.analyzer.analyzer.OfferGate.isNew(offer)) return

        // Asegura que el overlay este arriba para mostrar el resultado.
        if (OverlayService.instance == null) {
            startService(Intent(this, OverlayService::class.java))
        }

        val analysis = analyzer.analyze(offer)
        if (analysis.verdict == Verdict.BAD) settings.recordReject() else settings.recordAccept()
        OverlayService.instance?.showAnalysis(offer, analysis, "inDrive")
    }

    override fun onInterrupt() {}

    private fun collectText(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return
        node.text?.let { if (it.isNotBlank()) sb.append(it).append('\n') }
        node.contentDescription?.let { if (it.isNotBlank()) sb.append(it).append('\n') }
        for (i in 0 until node.childCount) collectText(node.getChild(i), sb)
    }

    companion object {
        private const val TAG = "InDriveA11y"
    }
}
