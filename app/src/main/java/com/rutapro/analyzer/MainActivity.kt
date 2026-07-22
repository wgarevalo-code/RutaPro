package com.rutapro.analyzer

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.rutapro.analyzer.analyzer.AppSettings

class MainActivity : AppCompatActivity() {

    private lateinit var settings: AppSettings

    private lateinit var minPerHour: EditText
    private lateinit var minPerKm: EditText
    private lateinit var minPerMin: EditText
    private lateinit var maxPickup: EditText
    private lateinit var fuelPrice: EditText
    private lateinit var kmPerGallon: EditText
    private lateinit var turboSwitch: Switch
    private lateinit var startStopBtn: Button
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = AppSettings(this)

        minPerHour = findViewById(R.id.minPerHour)
        minPerKm = findViewById(R.id.minPerKm)
        minPerMin = findViewById(R.id.minPerMin)
        maxPickup = findViewById(R.id.maxPickup)
        fuelPrice = findViewById(R.id.fuelPrice)
        kmPerGallon = findViewById(R.id.kmPerGallon)
        turboSwitch = findViewById(R.id.turboSwitch)
        startStopBtn = findViewById(R.id.startStopBtn)
        status = findViewById(R.id.status)

        loadValues()

        turboSwitch.setOnCheckedChangeListener { _, checked ->
            settings.turboMode = checked
        }
        findViewById<Button>(R.id.saveBtn).setOnClickListener { save() }
        findViewById<Button>(R.id.overlayBtn).setOnClickListener { requestOverlay() }
        findViewById<Button>(R.id.accessBtn).setOnClickListener { openAccessibilitySettings() }
        startStopBtn.setOnClickListener { toggleRunning() }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        renderStartStop()
    }

    private fun loadValues() {
        minPerHour.setText(settings.minPerHour.toInt().toString())
        minPerKm.setText(settings.minPerKm.toInt().toString())
        minPerMin.setText(settings.minPerMin.toInt().toString())
        maxPickup.setText(settings.maxPickupKm.toString())
        fuelPrice.setText(settings.fuelPricePerGallon.toInt().toString())
        kmPerGallon.setText(settings.kmPerGallon.toInt().toString())
        turboSwitch.isChecked = settings.turboMode
    }

    private fun save() {
        settings.minPerHour = minPerHour.text.toString().toDoubleOrNull() ?: settings.minPerHour
        settings.minPerKm = minPerKm.text.toString().toDoubleOrNull() ?: settings.minPerKm
        settings.minPerMin = minPerMin.text.toString().toDoubleOrNull() ?: settings.minPerMin
        settings.maxPickupKm = maxPickup.text.toString().toDoubleOrNull() ?: settings.maxPickupKm
        settings.fuelPricePerGallon = fuelPrice.text.toString().toDoubleOrNull() ?: settings.fuelPricePerGallon
        settings.kmPerGallon = kmPerGallon.text.toString().toDoubleOrNull() ?: settings.kmPerGallon
        Toast.makeText(this, "Filtros guardados", Toast.LENGTH_SHORT).show()
    }

    private fun toggleRunning() {
        // Antes de iniciar, exige los dos permisos.
        if (!settings.running) {
            if (!Settings.canDrawOverlays(this) || !isAccessibilityEnabled()) {
                Toast.makeText(
                    this,
                    "Primero concede los permisos de burbuja y accesibilidad.",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            save()
            settings.running = true
            startService(Intent(this, OverlayService::class.java))
        } else {
            settings.running = false
            stopService(Intent(this, OverlayService::class.java))
        }
        renderStartStop()
    }

    private fun renderStartStop() {
        if (settings.running) {
            startStopBtn.text = "⏹ PARAR"
            startStopBtn.setBackgroundColor(Color.parseColor("#B00020"))
        } else {
            startStopBtn.text = "▶ INICIAR"
            startStopBtn.setBackgroundColor(Color.parseColor("#1B8A3A"))
        }
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
            Toast.makeText(this, "Permiso de burbuja ya concedido", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun updateStatus() {
        val overlayOk = Settings.canDrawOverlays(this)
        val a11yOk = isAccessibilityEnabled()
        status.text = buildString {
            append("Burbuja: ").append(if (overlayOk) "OK" else "falta").append('\n')
            append("Accesibilidad: ").append(if (a11yOk) "OK" else "falta")
            if (overlayOk && a11yOk) {
                append("\n\nListo. Pulsa INICIAR y abre Uber.")
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
