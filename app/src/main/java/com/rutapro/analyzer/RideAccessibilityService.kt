package com.rutapro.analyzer

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.rutapro.analyzer.analyzer.AppSettings
import com.rutapro.analyzer.analyzer.RideAnalyzer
import com.rutapro.analyzer.parser.UberParser

/**
 * Lee el texto que aparece en la pantalla del conductor de Uber, lo pasa al parser + analizador,
 * y le envia el resultado al overlay flotante.
 */
class RideAccessibilityService : AccessibilityService() {

    private lateinit var settings: AppSettings
    private lateinit var analyzer: RideAnalyzer

    private var lastText: String = ""
    private var lastProcessMs: Long = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        settings = AppSettings(this)
        analyzer = RideAnalyzer(settings)
        // Asegura que el overlay este corriendo.
        startService(Intent(this, OverlayService::class.java))
        Log.i(TAG, "Servicio de accesibilidad conectado")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        // Respeta el boton iniciar/parar de la app.
        if (!settings.running) return
        val pkg = event.packageName?.toString() ?: return
        if (!TARGET_PACKAGES.any { pkg.startsWith(it) }) return

        // Evita procesar mas de una vez cada 400 ms.
        val now = System.currentTimeMillis()
        if (now - lastProcessMs < 400) return

        val root = rootInActiveWindow ?: return
        val sb = StringBuilder()
        collectText(root, sb)
        val text = sb.toString()
        if (text.isBlank() || text == lastText) return

        lastText = text
        lastProcessMs = now

        val offer = UberParser.parse(text)
        if (offer != null && offer.isValid) {
            val analysis = analyzer.analyze(offer)
            if (analysis.verdict == com.rutapro.analyzer.analyzer.Verdict.BAD) {
                settings.recordReject()
            } else {
                settings.recordAccept()
            }
            OverlayService.instance?.showAnalysis(offer, analysis)
        }
    }

    override fun onInterrupt() {}

    private fun collectText(node: AccessibilityNodeInfo?, sb: StringBuilder) {
        if (node == null) return
        node.text?.let { if (it.isNotBlank()) sb.append(it).append('\n') }
        node.contentDescription?.let { if (it.isNotBlank()) sb.append(it).append('\n') }
        for (i in 0 until node.childCount) {
            collectText(node.getChild(i), sb)
        }
    }

    companion object {
        private const val TAG = "RideA11y"
        // Uber Driver y Uber pasajero. Agrega otros paquetes si hace falta.
        private val TARGET_PACKAGES = listOf("com.ubercab.driver", "com.ubercab")
    }
}
