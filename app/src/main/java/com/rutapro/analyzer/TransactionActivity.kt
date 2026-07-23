package com.rutapro.analyzer

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.rutapro.analyzer.analyzer.AppSettings
import com.rutapro.analyzer.data.EntryType
import com.rutapro.analyzer.data.Ledger
import com.rutapro.analyzer.data.PayMethod
import java.util.Locale

/**
 * Alta de un movimiento: ingreso o gasto, con categoria y metodo de pago.
 * Pensado para un conductor: las categorias son las que de verdad usa a diario.
 */
class TransactionActivity : AppCompatActivity() {

    private lateinit var ledger: Ledger

    private lateinit var typeIncome: TextView
    private lateinit var typeExpense: TextView
    private lateinit var payCash: TextView
    private lateinit var payCard: TextView
    private lateinit var amountInput: EditText
    private lateinit var noteInput: EditText
    private lateinit var categoryGrid: LinearLayout
    private lateinit var fuelExtra: LinearLayout
    private lateinit var pricePerGallon: EditText
    private lateinit var odometer: EditText
    private lateinit var fuelHint: TextView

    private var isIncome = true
    private var method = PayMethod.CASH
    private var category = INCOME_CATEGORIES.first()
    private val categoryViews = mutableListOf<TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transaction)
        ledger = Ledger(this)

        typeIncome = findViewById(R.id.typeIncome)
        typeExpense = findViewById(R.id.typeExpense)
        payCash = findViewById(R.id.payCash)
        payCard = findViewById(R.id.payCard)
        amountInput = findViewById(R.id.amountInput)
        noteInput = findViewById(R.id.noteInput)
        categoryGrid = findViewById(R.id.categoryGrid)
        fuelExtra = findViewById(R.id.fuelExtra)
        pricePerGallon = findViewById(R.id.pricePerGallon)
        odometer = findViewById(R.id.odometer)
        fuelHint = findViewById(R.id.fuelHint)

        // La Billetera puede abrir esta pantalla ya posicionada.
        when (intent.getStringExtra(EXTRA_MODE)) {
            MODE_FUEL -> { isIncome = false; category = "Combustible" }
            MODE_EXPENSE -> { isIncome = false; category = EXPENSE_CATEGORIES.first() }
            MODE_INCOME -> { isIncome = true; category = INCOME_CATEGORIES.first() }
        }

        typeIncome.setOnClickListener { isIncome = true; category = INCOME_CATEGORIES.first(); render() }
        typeExpense.setOnClickListener { isIncome = false; category = EXPENSE_CATEGORIES.first(); render() }
        payCash.setOnClickListener { method = PayMethod.CASH; render() }
        payCard.setOnClickListener { method = PayMethod.CARD; render() }

        findViewById<TextView>(R.id.saveTx).setOnClickListener { saveTx() }

        render()
    }

    private fun render() {
        pick(typeIncome, isIncome, "#2BD576")
        pick(typeExpense, !isIncome, "#FF4D5E")
        pick(payCash, method == PayMethod.CASH, "#2BD576")
        pick(payCard, method == PayMethod.CARD, "#22C7E0")
        buildCategories()
        renderFuelExtra()
    }

    /** Los campos de tanqueo solo tienen sentido en el gasto de Combustible. */
    private fun renderFuelExtra() {
        val isFuel = !isIncome && category == "Combustible"
        fuelExtra.visibility = if (isFuel) View.VISIBLE else View.GONE
        if (!isFuel) return

        val last = ledger.lastFuelFill()
        fuelHint.text = if (last != null) {
            "Último odómetro registrado: ${last.odometer.toInt()} km"
        } else {
            "Este es tu primer tanqueo. En el siguiente ya podré calcular tu rendimiento real."
        }
    }

    private fun pick(view: TextView, selected: Boolean, colorOn: String) {
        if (selected) {
            view.setBackgroundResource(R.drawable.toggle_on)
            view.setTextColor(Color.parseColor(colorOn))
        } else {
            view.setBackgroundResource(R.drawable.toggle_off)
            view.setTextColor(Color.parseColor("#8A96B5"))
        }
    }

    /** Rejilla de categorias de 2 columnas, generada segun el tipo. */
    private fun buildCategories() {
        categoryGrid.removeAllViews()
        categoryViews.clear()
        val list = if (isIncome) INCOME_CATEGORIES else EXPENSE_CATEGORIES
        if (list.none { it == category }) category = list.first()

        var row: LinearLayout? = null
        list.forEachIndexed { i, name ->
            if (i % 2 == 0) {
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(8) }
                }
                categoryGrid.addView(row)
            }
            val chip = TextView(this).apply {
                text = name
                gravity = Gravity.CENTER
                textSize = 14f
                setPadding(dp(10), dp(13), dp(10), dp(13))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .apply { if (i % 2 == 0) marginEnd = dp(8) }
                setOnClickListener { category = name; buildCategories() }
            }
            pick(chip, name == category, if (isIncome) "#2BD576" else "#FF4D5E")
            categoryViews.add(chip)
            row?.addView(chip)
        }
        // Si la lista es impar, rellena la ultima celda para que no se estire.
        if (list.size % 2 == 1) {
            row?.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
            })
        }
    }

    private fun saveTx() {
        val amount = amountInput.text.toString().trim().replace(',', '.').toDoubleOrNull()
        if (amount == null || amount <= 0) {
            Toast.makeText(this, "Escribe un valor mayor que cero", Toast.LENGTH_SHORT).show()
            return
        }
        val note = noteInput.text.toString().trim().ifBlank { category }
        val isFuel = !isIncome && category == "Combustible"
        val type = if (isIncome) EntryType.RIDE
        else if (isFuel) EntryType.FUEL
        else EntryType.EXPENSE

        val ppg = if (isFuel) num(pricePerGallon) ?: 0.0 else 0.0
        val odo = if (isFuel) num(odometer) ?: 0.0 else 0.0

        ledger.add(type, amount, note, category, method, odo, ppg)

        // Si ya hay dos tanqueos con odometro, actualizamos el costo real por km.
        if (isFuel) {
            val eff = ledger.fuelEfficiency()
            if (eff != null) {
                AppSettings(this).fuelCostPerKm = eff.costPerKm
                Toast.makeText(
                    this,
                    String.format(
                        Locale.US,
                        "Tu carro rinde %.1f km/gal → costo real $%.3f/km. Ya lo actualicé.",
                        eff.kmPerGallon, eff.costPerKm
                    ),
                    Toast.LENGTH_LONG
                ).show()
                finish()
                return
            }
        }

        Toast.makeText(this, "Guardado", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun num(field: EditText): Double? =
        field.text.toString().trim().replace(',', '.').toDoubleOrNull()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_MODE = "mode"
        const val MODE_FUEL = "fuel"
        const val MODE_EXPENSE = "expense"
        const val MODE_INCOME = "income"

        private val INCOME_CATEGORIES = listOf(
            "Uber", "inDrive", "DiDi", "Cabify",
            "Particular", "Propina", "Bono", "Otros"
        )
        private val EXPENSE_CATEGORIES = listOf(
            "Combustible", "Mantenimiento", "Lavado", "Peaje",
            "Alimentación", "Seguro", "Parqueadero", "Multa",
            "Celular/Plan", "Otros"
        )
    }
}
