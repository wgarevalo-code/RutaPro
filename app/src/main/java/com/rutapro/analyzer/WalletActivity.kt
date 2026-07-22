package com.rutapro.analyzer

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.rutapro.analyzer.analyzer.AppSettings
import com.rutapro.analyzer.data.EntryType
import com.rutapro.analyzer.data.Ledger
import com.rutapro.analyzer.data.LedgerEntry
import java.util.Locale

/**
 * Contabilidad personal del conductor: ingresos por carrera, gastos de gasolina y
 * otros, con el rendimiento real de la jornada.
 */
class WalletActivity : AppCompatActivity() {

    private lateinit var ledger: Ledger
    private lateinit var settings: AppSettings

    private lateinit var netAmount: TextView
    private lateinit var incomeAmount: TextView
    private lateinit var expenseAmount: TextView
    private lateinit var ridesCount: TextView
    private lateinit var hoursOnline: TextView
    private lateinit var realPerHour: TextView
    private lateinit var avgRide: TextView
    private lateinit var entryList: LinearLayout
    private lateinit var emptyLabel: TextView
    private lateinit var periodLabel: TextView
    private lateinit var tabToday: TextView
    private lateinit var tabWeek: TextView
    private lateinit var tabMonth: TextView

    /** 1 = hoy, 7 = ultima semana, 30 = ultimo mes. */
    private var period = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet)
        ledger = Ledger(this)
        settings = AppSettings(this)

        netAmount = findViewById(R.id.netAmount)
        incomeAmount = findViewById(R.id.incomeAmount)
        expenseAmount = findViewById(R.id.expenseAmount)
        ridesCount = findViewById(R.id.ridesCount)
        hoursOnline = findViewById(R.id.hoursOnline)
        realPerHour = findViewById(R.id.realPerHour)
        avgRide = findViewById(R.id.avgRide)
        entryList = findViewById(R.id.entryList)
        emptyLabel = findViewById(R.id.emptyLabel)
        periodLabel = findViewById(R.id.periodLabel)
        tabToday = findViewById(R.id.tabToday)
        tabWeek = findViewById(R.id.tabWeek)
        tabMonth = findViewById(R.id.tabMonth)

        tabToday.setOnClickListener { period = 1; render() }
        tabWeek.setOnClickListener { period = 7; render() }
        tabMonth.setOnClickListener { period = 30; render() }

        findViewById<TextView>(R.id.addFuel).setOnClickListener {
            askAmount("Cargar gasolina", "Monto del tanqueo") { v ->
                ledger.add(EntryType.FUEL, v, "Gasolina"); render()
            }
        }
        findViewById<TextView>(R.id.addExpense).setOnClickListener {
            askAmount("Otro gasto", "Lavado, mantenimiento, peaje…") { v ->
                ledger.add(EntryType.EXPENSE, v, "Gasto"); render()
            }
        }
        findViewById<TextView>(R.id.addRide).setOnClickListener {
            askAmount("Carrera manual", "Lo que te pagaron") { v ->
                ledger.add(EntryType.RIDE, v, "Carrera manual"); render()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val entries = if (period == 1) ledger.today() else ledger.lastDays(period)
        val s = ledger.summarize(entries)

        netAmount.text = money(s.net)
        netAmount.setTextColor(if (s.net >= 0) Color.parseColor("#2BD576") else Color.parseColor("#FF4D5E"))
        incomeAmount.text = money(s.income)
        expenseAmount.text = money(s.expense)
        ridesCount.text = s.rides.toString()
        avgRide.text = money(if (s.rides > 0) s.income / s.rides else 0.0)

        // Las horas en linea solo se miden por dia.
        val secs = settings.onlineSecondsToday()
        val hours = secs / 3600.0
        hoursOnline.text = formatDuration(secs)
        realPerHour.text = if (period == 1 && hours > 0.02) money(s.net / hours) else "—"

        periodLabel.text = when (period) {
            1 -> "Resumen de hoy"
            7 -> "Resumen de los últimos 7 días"
            else -> "Resumen de los últimos 30 días"
        }
        renderTabs()
        renderList(entries)
    }

    private fun renderTabs() {
        for ((tab, p) in listOf(tabToday to 1, tabWeek to 7, tabMonth to 30)) {
            if (p == period) {
                tab.setBackgroundResource(R.drawable.toggle_on)
                tab.setTextColor(Color.parseColor("#2BD576"))
            } else {
                tab.setBackgroundResource(R.drawable.toggle_off)
                tab.setTextColor(Color.parseColor("#8A96B5"))
            }
        }
    }

    private fun renderList(entries: List<LedgerEntry>) {
        entryList.removeAllViews()
        emptyLabel.visibility = if (entries.isEmpty()) TextView.VISIBLE else TextView.GONE

        for (e in entries) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.inner_bg)
                setPadding(dp(14), dp(12), dp(14), dp(12))
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
            row.layoutParams = lp

            val left = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            left.addView(TextView(this).apply {
                text = labelFor(e)
                setTextColor(Color.parseColor("#EAF0FF"))
                textSize = 15f
            })
            left.addView(TextView(this).apply {
                text = Ledger.timeLabel(e.ts)
                setTextColor(Color.parseColor("#8A96B5"))
                textSize = 12f
            })
            row.addView(left)

            row.addView(TextView(this).apply {
                text = (if (e.isIncome) "+" else "−") + money(e.amount)
                setTextColor(
                    if (e.isIncome) Color.parseColor("#2BD576") else Color.parseColor("#FF4D5E")
                )
                textSize = 17f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })

            row.setOnLongClickListener {
                AlertDialog.Builder(this)
                    .setTitle("¿Borrar este movimiento?")
                    .setMessage(labelFor(e) + "  " + money(e.amount))
                    .setPositiveButton("Borrar") { _, _ -> ledger.delete(e.id); render() }
                    .setNegativeButton("Cancelar", null)
                    .show()
                true
            }

            entryList.addView(row)
        }
    }

    private fun labelFor(e: LedgerEntry): String = when (e.type) {
        EntryType.RIDE -> if (e.note.isNotBlank()) e.note else "Carrera"
        EntryType.FUEL -> "Gasolina"
        else -> if (e.note.isNotBlank()) e.note else "Gasto"
    }

    private fun askAmount(title: String, hint: String, onOk: (Double) -> Unit) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            this.hint = hint
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val v = input.text.toString().trim().replace(',', '.').toDoubleOrNull()
                if (v != null && v > 0) onOk(v)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun money(v: Double): String = String.format(Locale.US, "$%.2f", v)

    private fun formatDuration(secs: Long): String {
        val h = secs / 3600
        val m = (secs % 3600) / 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
