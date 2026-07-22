package com.rutapro.analyzer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.rutapro.analyzer.analyzer.AppSettings

class MainActivity : AppCompatActivity() {

    private lateinit var settings: AppSettings

    private lateinit var minPerKm: EditText
    private lateinit var minPerMin: EditText
    private lateinit var fuelPrice: EditText
    private lateinit var kmPerGallon: EditText
    private lateinit var maxPickup: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = AppSettings(this)

        minPerKm = findViewById(R.id.minPerKm)
        minPerMin = findViewById(R.id.minPerMin)
        fuelPrice = findViewById(R.id.fuelPrice)
        kmPerGallon = findViewById(R.id.kmPerGallon)
        maxPickup = findViewById(R.id.maxPickup)
        status = findViewById(R.id.status)

        loadValues()

        findViewById<Button>(R.id.saveBtn).setOnClickListener { save() }
        findViewById<Button>(R.id.overlayBtn).setOnClickListener { requestOverlay() }
        findViewById<Button>(R.id.accessBtn).setOnClickListener { openAccessibilitySettings() }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun loadValues() {
        minPerKm.setText(settings.minPerKm.toInt().toString())
        minPerMin.setText(settings.minPerMin.toInt().toString())
        fuelPrice.setText(settings.fuelPricePerGallon.toInt().toString())
        kmPerGallon.setText(settings.kmPerGallon.toInt().toString())
        maxPickup.setText(settings.maxPickupKm.toString())
    }

    private fun save() {
        settings.minPerKm = minPerKm.text.toString().toDoubleOrNull() ?: settings.minPerKm
        settings.minPerMin = minPerMin.text.toString().toDoubleOrNull() ?: settings.minPerMin
        settings.fuelPricePerGallon = fuelPrice.text.toString().toDoubleOrNull() ?: settings.fuelPricePerGallon
        settings.kmPerGallon = kmPerGallon.text.toString().toDoubleOrNull() ?: settings.kmPerGallon
        settings.maxPickupKm = maxPickup.text.toString().toDoubleOrNull() ?: settings.maxPickupKm
        Toast.makeText(this, "Parametros guardados", Toast.LENGTH_SHORT).show()
    }

    private fun requestOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } else {
            startService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, "Permiso de overlay ya concedido", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun updateStatus() {
        val overlayOk = Settings.canDrawOverlays(this)
        val a11yOk = isAccessibilityEnabled()
        status.text = buildString {
            append("Overlay: ").append(if (overlayOk) "OK" else "falta").append('\n')
            append("Accesibilidad: ").append(if (a11yOk) "OK" else "falta")
            if (overlayOk && a11yOk) {
                append("\n\nTodo listo. Abre Uber y las carreras se analizaran solas.")
                startService(Intent(this, OverlayService::class.java))
            }
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        if (!am.isEnabled) return false
        val expected = "$packageName/${RideAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }
}
