package com.rutapro.analyzer.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Tipos de movimiento. */
object EntryType {
    const val RIDE = "RIDE"       // ingreso por carrera
    const val FUEL = "FUEL"       // gasto de gasolina
    const val EXPENSE = "EXPENSE" // otro gasto (lavado, mantenimiento, etc.)
}

/** Como se pago / se cobro. */
object PayMethod {
    const val CASH = "Efectivo"
    const val CARD = "Tarjeta"
    const val NONE = ""
}

data class LedgerEntry(
    val id: Long,
    val ts: Long,
    val type: String,
    val amount: Double,
    val note: String,
    val category: String = "",
    val method: String = "",
    /** Solo para tanqueos: lectura del odometro en km. */
    val odometer: Double = 0.0,
    /** Solo para tanqueos: precio por galon del dia. */
    val pricePerGallon: Double = 0.0
) {
    val isIncome: Boolean get() = type == EntryType.RIDE

    /** Galones cargados, deducidos del monto y el precio. */
    val gallons: Double
        get() = if (pricePerGallon > 0) amount / pricePerGallon else 0.0
}

/** Resultado del calculo de rendimiento real entre dos tanqueos. */
data class FuelEfficiency(
    val kmDriven: Double,
    val gallons: Double,
    val kmPerGallon: Double,
    val costPerKm: Double
)

data class DaySummary(
    val income: Double,
    val expense: Double,
    val rides: Int
) {
    val net: Double get() = income - expense
}

/**
 * Libro de movimientos del conductor, guardado como JSON en SharedPreferences.
 * Simple y sin dependencias: suficiente para llevar la contabilidad diaria.
 */
class Ledger(context: Context) {

    private val prefs = context.getSharedPreferences("ruta_pro_ledger", Context.MODE_PRIVATE)

    fun add(
        type: String,
        amount: Double,
        note: String,
        category: String = "",
        method: String = "",
        odometer: Double = 0.0,
        pricePerGallon: Double = 0.0
    ) {
        if (amount <= 0) return
        val arr = readArray()
        val o = JSONObject()
            .put("id", System.currentTimeMillis())
            .put("ts", System.currentTimeMillis())
            .put("type", type)
            .put("amount", amount)
            .put("note", note)
            .put("category", category)
            .put("method", method)
            .put("odometer", odometer)
            .put("ppg", pricePerGallon)
        arr.put(o)
        // Conserva los ultimos 2000 movimientos.
        val trimmed = if (arr.length() > 2000) {
            val n = JSONArray()
            for (i in (arr.length() - 2000) until arr.length()) n.put(arr.get(i))
            n
        } else arr
        prefs.edit().putString(KEY, trimmed.toString()).apply()
    }

    fun delete(id: Long) {
        val arr = readArray()
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            if (o.optLong("id") != id) out.put(o)
        }
        prefs.edit().putString(KEY, out.toString()).apply()
    }

    fun all(): List<LedgerEntry> {
        val arr = readArray()
        val list = ArrayList<LedgerEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                LedgerEntry(
                    id = o.optLong("id"),
                    ts = o.optLong("ts"),
                    type = o.optString("type"),
                    amount = o.optDouble("amount", 0.0),
                    note = o.optString("note"),
                    category = o.optString("category"),
                    method = o.optString("method"),
                    odometer = o.optDouble("odometer", 0.0),
                    pricePerGallon = o.optDouble("ppg", 0.0)
                )
            )
        }
        return list.sortedByDescending { it.ts }
    }

    /** Movimientos de hoy, del mas reciente al mas viejo. */
    fun today(): List<LedgerEntry> {
        val start = startOfToday()
        return all().filter { it.ts >= start }
    }

    /** Movimientos de los ultimos [days] dias (incluye hoy). */
    fun lastDays(days: Int): List<LedgerEntry> {
        val c = Calendar.getInstance()
        c.timeInMillis = startOfToday()
        c.add(Calendar.DAY_OF_YEAR, -(days - 1))
        val from = c.timeInMillis
        return all().filter { it.ts >= from }
    }

    fun summarize(entries: List<LedgerEntry>): DaySummary {
        var income = 0.0
        var expense = 0.0
        var rides = 0
        for (e in entries) {
            if (e.isIncome) { income += e.amount; rides++ } else expense += e.amount
        }
        return DaySummary(income, expense, rides)
    }

    fun clearAll() = prefs.edit().remove(KEY).apply()

    /**
     * Calcula el rendimiento real del vehiculo entre los dos ultimos tanqueos
     * que traen lectura de odometro. Devuelve null si aun no hay suficientes datos.
     *
     * El metodo es el estandar: los galones del ULTIMO tanqueo son los que
     * recorrieron la distancia desde el tanqueo anterior hasta este.
     */
    fun fuelEfficiency(): FuelEfficiency? {
        val fills = all()
            .filter { it.type == EntryType.FUEL && it.odometer > 0 && it.pricePerGallon > 0 }
            .sortedByDescending { it.odometer }
        if (fills.size < 2) return null

        val last = fills[0]
        val prev = fills[1]
        val kmDriven = last.odometer - prev.odometer
        val gallons = last.gallons
        if (kmDriven <= 0 || gallons <= 0) return null

        val kmPerGallon = kmDriven / gallons
        val costPerKm = last.pricePerGallon / kmPerGallon
        return FuelEfficiency(kmDriven, gallons, kmPerGallon, costPerKm)
    }

    /** El ultimo tanqueo registrado (para mostrar el odometro anterior). */
    fun lastFuelFill(): LedgerEntry? =
        all().filter { it.type == EntryType.FUEL && it.odometer > 0 }
            .maxByOrNull { it.odometer }

    private fun readArray(): JSONArray =
        try { JSONArray(prefs.getString(KEY, "[]") ?: "[]") } catch (e: Exception) { JSONArray() }

    companion object {
        private const val KEY = "entries"

        fun startOfToday(): Long {
            val c = Calendar.getInstance()
            c.set(Calendar.HOUR_OF_DAY, 0)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }

        fun timeLabel(ts: Long): String =
            SimpleDateFormat("HH:mm", Locale.US).format(Date(ts))

        fun dayKey(ts: Long = System.currentTimeMillis()): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ts))
    }
}
