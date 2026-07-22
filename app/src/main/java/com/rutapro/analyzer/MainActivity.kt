package com.rutapro.analyzer

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.rutapro.analyzer.analyzer.AppSettings

class MainActivity : AppCompatActivity() {

    private lateinit var settings: AppSettings

    private lateinit var scroll: ScrollView
    private lateinit var startBtn: ImageButton
    private lateinit var statusPill: TextView
    private lateinit var startHint: TextView

    private lateinit var statTotal: TextView
    private lateinit var statAccept: TextView
    private lateinit var statReject: TextView

    private lateinit var minPerHour: EditText
    private lateinit var minPerKm: EditText
    private lateinit var maxPickup: EditText
    private lateinit var fuelPrice: EditText
    private lateinit var kmPerGallon: EditText

    private lateinit var toggleHour: TextView
    private lateinit var toggleKm: TextView
    private lateinit var togglePickup: TextView
    private lateinit var filtersActive: TextView

    private lateinit var turboSwitch: SwitchCompat
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        settings = AppSettings(this)

        scroll = findViewById(R.id.scroll)
        startBtn = findViewById(R.id.startBtn)
        statusPill = findViewById(R.id.statusPill)
        startHint = findViewById(R.id.startHint)
        statTotal = findViewById(R.id.statTotal)
        statAccept = findViewById(R.id.statAccept)
        statReject = findViewById(R.id.statReject)
        minPerHour = findViewById(R.id.minPerHour)
        minPerKm = findViewById(R.id.minPerKm)
        maxPickup = findViewById(R.id.maxPickup)
        fuelPrice = findViewById(R.id.fuelPrice)
        kmPerGallon = findViewById(R.id.kmPerGallon)
        toggleHour = findViewById(R.id.toggleHour)
        toggleKm = findViewById(R.id.toggleKm)
        togglePickup = findViewById(R.id.togglePickup)
        filtersActive = findViewById(R.id.filtersActive)
        turboSwitch = findViewById(R.id.turboSwitch)
        status = findViewById(R.id.status)

        loadValues()

        startBtn.setOnClickListener { toggleRunning() }
        findViewById<Button>(R.id.saveBtn).setOnClickListener { save(); Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show() }
        findViewById<Button>(R.id.overlayBtn).setOnClickListener { requestOverlay() }
        findViewById<Button>(R.id.accessBtn).setOnClickListener { openAccessibilitySettings() }

        toggleHour.setOnClickListener { settings.filterHourOn = !settings.filterHourOn; renderFilters() }
        toggleKm.setOnClickListener { settings.filterKmOn = !settings.filterKmOn; renderFilters() }
        togglePickup.setOnClickListener { settings.filterPickupOn = !settings.filterPickupOn; renderFilters() }

        turboSwitch.setOnCheckedChangeListener { _, c -> settings.turboMode = c }

        statTotal.setOnLongClickListener { settings.resetStats(); renderStats(); true }

        setupBottomNav()
    }

    override fun onResume() {
        super.onResume()
        renderStats()
        renderStartButton()
        updateStatus()
    }

    private fun loadValues() {
        minPerHour.setText(settings.minPerHour.toInt().toString())
        minPerKm.setText(settings.minPerKm.toInt().toString())
        maxPickup.setText(settings.maxPickupKm.toString())
        fuelPrice.setText(settings.fuelPricePerGallon.toInt().toString())
        kmPerGallon.setText(settings.kmPerGallon.toInt().toString())
        turboSwitch.isChecked = settings.turboMode
        renderFilters()
    }

    private fun save() {
        settings.minPerHour = minPerHour.text.toString().toDoubleOrNull() ?: settings.minPerHour
        settings.minPerKm = minPerKm.text.toString().toDoubleOrNull() ?: settings.minPerKm
        settings.maxPickupKm = maxPickup.text.toString().toDoubleOrNull() ?: settings.maxPickupKm
        settings.fuelPricePerGallon = fuelPrice.text.toString().toDoubleOrNull() ?: settings.fuelPricePerGallon
        settings.kmPerGallon = kmPerGallon.text.toString().toDoubleOrNull() ?: settings.kmPerGallon
    }

    private fun renderFilters() {
        renderToggle(toggleHour, settings.filterHourOn)
        renderToggle(toggleKm, settings.filterKmOn)
        renderToggle(togglePickup, settings.filterPickupOn)
        filtersActive.text = "${settings.activeFilterCount} activos"
    }

    private fun renderToggle(view: TextView, on: Boolean) {
        if (on) {
            view.text = "ON"
            view.setBackgroundResource(R.drawable.toggle_on)
            view.setTextColor(Color.parseColor("#2BD576"))
        } else {
            view.text = "OFF"
            view.setBackgroundResource(R.drawable.toggle_off)
            view.setTextColor(Color.parseColor("#8A96B5"))
        }
    }

    private fun renderStats() {
        statTotal.text = settings.statTotal.toString()
        statAccept.text = settings.statAccept.toString()
        statReject.text = settings.statReject.toString()
    }

    private fun toggleRunning() {
        if (!settings.running) {
            if (!Settings.canDrawOverlays(this) || !isAccessibilityEnabled()) {
                Toast.makeText(this, "Primero activa la burbuja y la accesibilidad (más abajo).", Toast.LENGTH_LONG).show()
                scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
                return
            }
            save()
            settings.running = true
            startService(Intent(this, OverlayService::class.java))
        } else {
            settings.running = false
            stopService(Intent(this, OverlayService::class.java))
        }
        renderStartButton()
    }

    private fun renderStartButton() {
        if (settings.running) {
            startBtn.setImageResource(R.drawable.ic_stop)
            startBtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#FF4D5E"))
            statusPill.text = "Analizando…"
            startHint.text = "Toca para detener el análisis"
        } else {
            startBtn.setImageResource(R.drawable.ic_play)
            startBtn.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#22C7E0"))
            statusPill.text = "Listo para iniciar"
            startHint.text = "Toca para iniciar el análisis"
        }
    }

    private fun requestOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } else {
            Toast.makeText(this, "Burbuja ya permitida", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun setupBottomNav() {
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.selectedItemId = R.id.nav_home
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> scroll.post { scroll.fullScroll(ScrollView.FOCUS_UP) }
                R.id.nav_config -> scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
                else -> Toast.makeText(this, "Próximamente", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    private fun updateStatus() {
        val overlayOk = Settings.canDrawOverlays(this)
        val a11yOk = isAccessibilityEnabled()
        status.text = buildString {
            append("Burbuja: ").append(if (overlayOk) "OK" else "falta").append("   ")
            append("Accesibilidad: ").append(if (a11yOk) "OK" else "falta")
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        if (!am.isEnabled) return false
        val expected = "$packageName/${RideAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }
}
