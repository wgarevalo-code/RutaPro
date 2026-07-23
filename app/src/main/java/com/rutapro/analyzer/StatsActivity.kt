package com.rutapro.analyzer

import android.graphics.Color
import android.location.Geocoder
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.rutapro.analyzer.data.Ledger
import com.rutapro.analyzer.data.ZoneStat
import com.rutapro.analyzer.location.LocationTracker
import java.util.Locale

/**
 * Muestra donde y cuando gana mas el conductor, usando lo que ya se registro:
 * kilometros del dia, franjas horarias mas rentables y zonas con mejores carreras.
 */
class StatsActivity : AppCompatActivity() {

    private lateinit var ledger: Ledger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)
        ledger = Ledger(this)
    }

    override fun onResume() {
        super.onResume()
        renderToday()
        renderHours()
        renderZones()
    }

    private fun renderToday() {
        val km = LocationTracker.kmToday(this)
        val today = ledger.today().filter { it.isIncome }
        val income = today.sumOf { it.amount }

        findViewById<TextView>(R.id.kmToday).text = String.format(Locale.US, "%.1f", km)
        findViewById<TextView>(R.id.paidKm).text = today.size.toString()
        findViewById<TextView>(R.id.perKmToday).text =
            if (km > 0.5) String.format(Locale.US, "$%.2f", income / km) else "—"

        val warn = findViewById<TextView>(R.id.locationWarn)
        if (!LocationTracker(this).hasPermission()) {
            warn.visibility = TextView.VISIBLE
            warn.text = "⚠ Falta el permiso de ubicación. Sin él no puedo contar los km ni las zonas."
        } else {
            warn.visibility = TextView.GONE
        }
    }

    private fun renderHours() {
        val list = findViewById<LinearLayout>(R.id.hoursList)
        list.removeAllViews()
        val hours = ledger.bestHours().take(6)
        if (hours.isEmpty()) {
            list.addView(emptyRow("Aún no hay suficientes carreras. Registra varias y aquí verás tus mejores horas."))
            return
        }
        val max = hours.first().total
        for (h in hours) {
            list.addView(
                barRow(
                    h.label,
                    "${h.rides} carreras · promedio ${money(h.avg)}",
                    money(h.total),
                    if (max > 0) (h.total / max).toFloat() else 0f,
                    Color.parseColor("#22C7E0")
                )
            )
        }
    }

    private fun renderZones() {
        val list = findViewById<LinearLayout>(R.id.zonesList)
        list.removeAllViews()
        val zones = ledger.bestZones().take(6)
        if (zones.isEmpty()) {
            list.addView(emptyRow("Cuando aceptes carreras con el GPS activo, aquí aparecerán tus mejores zonas."))
            return
        }
        val max = zones.first().total
        for (z in zones) {
            list.addView(
                barRow(
                    zoneName(z),
                    "${z.rides} carreras · promedio ${money(z.avg)}",
                    money(z.total),
                    if (max > 0) (z.total / max).toFloat() else 0f,
                    Color.parseColor("#2BD576")
                )
            )
        }
    }

    /** Intenta traducir las coordenadas a un nombre de barrio o calle. */
    private fun zoneName(z: ZoneStat): String {
        return try {
            @Suppress("DEPRECATION")
            val res = Geocoder(this, Locale.getDefault()).getFromLocation(z.lat, z.lng, 1)
            val a = res?.firstOrNull()
            a?.subLocality ?: a?.locality ?: a?.thoroughfare
                ?: String.format(Locale.US, "%.4f, %.4f", z.lat, z.lng)
        } catch (e: Exception) {
            String.format(Locale.US, "%.4f, %.4f", z.lat, z.lng)
        }
    }

    /** Fila con barra de progreso proporcional al total ganado. */
    private fun barRow(title: String, subtitle: String, amount: String, ratio: Float, color: Int): LinearLayout {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.inner_bg)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#EAF0FF"))
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        top.addView(TextView(this).apply {
            text = amount
            setTextColor(color)
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(top)

        root.addView(TextView(this).apply {
            text = subtitle
            setTextColor(Color.parseColor("#8A96B5"))
            textSize = 12f
        })

        // Barra proporcional
        val track = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(6)
            ).apply { topMargin = dp(8) }
            setBackgroundColor(Color.parseColor("#1B2338"))
        }
        track.addView(android.view.View(this).apply {
            setBackgroundColor(color)
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, ratio.coerceIn(0.03f, 1f)
            )
        })
        track.addView(android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, (1f - ratio).coerceIn(0f, 0.97f)
            )
        })
        root.addView(track)

        return root
    }

    private fun emptyRow(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Color.parseColor("#8A96B5"))
        textSize = 13f
        setPadding(dp(4), dp(10), dp(4), dp(10))
    }

    private fun money(v: Double): String = String.format(Locale.US, "$%.2f", v)
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
