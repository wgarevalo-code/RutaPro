package com.rutapro.analyzer

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.rutapro.analyzer.analyzer.AppSettings
import com.rutapro.analyzer.capture.ScreenCaptureService
import java.util.Locale

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
    private lateinit var fuelPerKm: EditText

    private lateinit var toggleHour: TextView
    private lateinit var toggleKm: TextView
    private lateinit var togglePickup: TextView
    private lateinit var toggleTurbo: TextView
    private lateinit var turboState: TextView
    private lateinit var filtersActive: TextView

    private lateinit var overlayState: TextView
    private lateinit var locationState: TextView

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
        fuelPerKm = findViewById(R.id.fuelPerKm)
        toggleHour = findViewById(R.id.toggleHour)
        toggleKm = findViewById(R.id.toggleKm)
        togglePickup = findViewById(R.id.togglePickup)
        toggleTurbo = findViewById(R.id.toggleTurbo)
        turboState = findViewById(R.id.turboState)
        filtersActive = findViewById(R.id.filtersActive)
        overlayState = findViewById(R.id.overlayState)
        locationState = findViewById(R.id.locationState)

        loadValues()
        setupExpanders()
        setupPresets()

        startBtn.setOnClickListener { toggleRunning() }
        findViewById<TextView>(R.id.saveBtn).setOnClickListener {
            save(); Toast.makeText(this, "Cambios guardados", Toast.LENGTH_SHORT).show()
        }
        findViewById<TextView>(R.id.overlayBtn).setOnClickListener { requestOverlay() }
        findViewById<TextView>(R.id.locationBtn).setOnClickListener { requestLocation() }

        toggleHour.setOnClickListener { settings.filterHourOn = !settings.filterHourOn; renderFilters() }
        toggleKm.setOnClickListener { settings.filterKmOn = !settings.filterKmOn; renderFilters() }
        togglePickup.setOnClickListener { settings.filterPickupOn = !settings.filterPickupOn; renderFilters() }
        toggleTurbo.setOnClickListener { settings.turboMode = !settings.turboMode; renderFilters() }

        findViewById<TextView>(R.id.resetStats).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Reiniciar contadores")
                .setMessage("Total, Aceptar y Rechazar vuelven a cero. Tu billetera no se toca.")
                .setPositiveButton("Reiniciar") { _, _ ->
                    settings.resetStats(); renderStats()
                    Toast.makeText(this, "Contadores en cero", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        setupBottomNav()
    }

    override fun onResume() {
        super.onResume()
        renderStats()
        renderStartButton()
        updateStatus()
    }

    // ---------- Iniciar / parar ----------

    private fun toggleRunning() {
        if (settings.running) {
            settings.running = false
            settings.stopOnline()
            stopService(Intent(this, ScreenCaptureService::class.java))
            stopService(Intent(this, OverlayService::class.java))
            renderStartButton()
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Primero permite la ventana flotante (abajo).", Toast.LENGTH_LONG).show()
            scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
            return
        }

        save()
        // Android pide autorizacion para leer la pantalla en cada jornada.
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        try {
            startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE)
        } catch (e: Exception) {
            Toast.makeText(this, "Tu teléfono no permite la captura de pantalla.", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Compatibilidad con la API de captura")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_CAPTURE) return

        if (resultCode != Activity.RESULT_OK || data == null) {
            Toast.makeText(this, "Sin permiso de pantalla no puedo leer las carreras.", Toast.LENGTH_LONG).show()
            return
        }

        settings.running = true
        settings.startOnline()

        startService(Intent(this, OverlayService::class.java))
        startService(
            Intent(this, ScreenCaptureService::class.java)
                .putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                .putExtra(ScreenCaptureService.EXTRA_DATA, data)
        )
        renderStartButton()
        Toast.makeText(this, "Listo. Abre Uber o inDrive.", Toast.LENGTH_SHORT).show()
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

    // ---------- Filtros ----------

    private fun setupExpanders() {
        expander(R.id.hourHeader, R.id.hourPanel, R.id.hourChevron)
        expander(R.id.kmHeader, R.id.kmPanel, R.id.kmChevron)
        expander(R.id.pickHeader, R.id.pickPanel, R.id.pickChevron)
        expander(R.id.turboHeader, R.id.turboPanel, R.id.turboChevron)
    }

    private fun expander(headerId: Int, panelId: Int, chevronId: Int) {
        val header = findViewById<View>(headerId)
        val panel = findViewById<View>(panelId)
        val chevron = findViewById<ImageView>(chevronId)
        header.setOnClickListener {
            val open = panel.visibility == View.VISIBLE
            panel.visibility = if (open) View.GONE else View.VISIBLE
            chevron.animate().rotation(if (open) 0f else 180f).setDuration(180).start()
        }
    }

    private fun setupPresets() {
        preset(R.id.hourP1, minPerHour, 8.0, 2)
        preset(R.id.hourP2, minPerHour, 10.0, 2)
        preset(R.id.hourP3, minPerHour, 15.0, 2)
        preset(R.id.kmP1, minPerKm, 0.35, 2)
        preset(R.id.kmP2, minPerKm, 0.45, 2)
        preset(R.id.kmP3, minPerKm, 0.60, 2)
        preset(R.id.pickP1, maxPickup, 2.0, 1)
        preset(R.id.pickP2, maxPickup, 3.0, 1)
        preset(R.id.pickP3, maxPickup, 5.0, 1)
        preset(R.id.fuelP1, fuelPerKm, 0.03, 2)
        preset(R.id.fuelP2, fuelPerKm, 0.06, 2)
        preset(R.id.fuelP3, fuelPerKm, 0.10, 2)
    }

    private fun preset(id: Int, target: EditText, value: Double, decimals: Int) {
        findViewById<TextView>(id).setOnClickListener {
            target.setText(dec(value, decimals))
            save()
            Toast.makeText(this, "Listo: " + dec(value, decimals), Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadValues() {
        minPerHour.setText(dec(settings.minPerHour, 2))
        minPerKm.setText(dec(settings.minPerKm, 2))
        maxPickup.setText(dec(settings.maxPickupKm, 1))
        fuelPerKm.setText(dec(settings.fuelCostPerKm, 2))
        renderFilters()
    }

    private fun save() {
        settings.minPerHour = num(minPerHour) ?: settings.minPerHour
        settings.minPerKm = num(minPerKm) ?: settings.minPerKm
        settings.maxPickupKm = num(maxPickup) ?: settings.maxPickupKm
        settings.fuelCostPerKm = num(fuelPerKm) ?: settings.fuelCostPerKm
    }

    private fun dec(v: Double, decimals: Int): String =
        String.format(Locale.US, "%.${decimals}f", v)

    private fun num(field: EditText): Double? =
        field.text.toString().trim().replace(',', '.').toDoubleOrNull()

    private fun renderFilters() {
        renderToggle(toggleHour, settings.filterHourOn)
        renderToggle(toggleKm, settings.filterKmOn)
        renderToggle(togglePickup, settings.filterPickupOn)
        renderToggle(toggleTurbo, settings.turboMode)
        turboState.text = if (settings.turboMode) "Activado" else "Desactivado"
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

    // ---------- Permisos ----------

    private fun requestOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } else {
            Toast.makeText(this, "Ya está concedido", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestLocation() {
        if (hasLocation()) {
            Toast.makeText(this, "Ya está concedido", Toast.LENGTH_SHORT).show()
            return
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            REQ_LOCATION
        )
    }

    private fun hasLocation(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateStatus()
    }

    private fun updateStatus() {
        renderPerm(overlayState, Settings.canDrawOverlays(this))
        renderPerm(locationState, hasLocation())
    }

    private fun renderPerm(view: TextView, granted: Boolean) {
        if (granted) {
            view.text = "✓ Concedido"
            view.setTextColor(Color.parseColor("#2BD576"))
        } else {
            view.text = "Sin conceder — tócalo"
            view.setTextColor(Color.parseColor("#FFCC33"))
        }
    }

    // ---------- Navegación ----------

    private fun setupBottomNav() {
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav)
        nav.selectedItemId = R.id.nav_home
        nav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> scroll.post { scroll.fullScroll(ScrollView.FOCUS_UP) }
                R.id.nav_wallet -> startActivity(Intent(this, WalletActivity::class.java))
                R.id.nav_stats -> startActivity(Intent(this, StatsActivity::class.java))
                R.id.nav_config -> scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
                R.id.nav_help -> showHelp()
            }
            true
        }
    }

    private fun showHelp() {
        AlertDialog.Builder(this)
            .setTitle("Cómo funciona RutaPro")
            .setMessage(
                "1. Configura tus filtros y el costo de gasolina.\n\n" +
                    "2. Toca el botón ▶. Android te pedirá autorización para leer la pantalla: " +
                    "acepta y abre Uber o inDrive.\n\n" +
                    "3. Cuando entre una carrera, aparece arriba un cuadro con el veredicto. " +
                    "Si la tomas, toca ✓ Sí y queda en tu billetera.\n\n" +
                    "4. Al terminar la jornada toca ⏹ para que el cálculo de ganancia por hora sea exacto.\n\n" +
                    "Todo se procesa dentro de tu teléfono. Nada se envía a internet."
            )
            .setPositiveButton("Entendido", null)
            .show()
    }

    companion object {
        private const val REQ_CAPTURE = 1001
        private const val REQ_LOCATION = 1002
    }
}
