package com.rutapro.analyzer.analyzer

import android.content.Context

/**
 * Parametros del conductor, guardados en SharedPreferences.
 * Todo se maneja en DOLARES con decimales (ej: 10.00 por hora, 0.45 por km),
 * igual que las apps de conductor de la region.
 */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("ruta_pro", Context.MODE_PRIVATE)

    init {
        // Migracion: las primeras versiones guardaban valores en miles (pesos).
        // Si venimos de ahi, reiniciamos a los valores por defecto en dolares.
        if (prefs.getInt(KEY_VERSION, 1) < PREFS_VERSION) {
            prefs.edit()
                .putFloat(KEY_MIN_PER_HOUR, DEF_HOUR)
                .putFloat(KEY_MIN_PER_KM, DEF_KM)
                .putFloat(KEY_MAX_PICKUP_KM, DEF_PICKUP)
                .putFloat(KEY_FUEL_PER_KM, DEF_FUEL_KM)
                .putInt(KEY_VERSION, PREFS_VERSION)
                .apply()
        }
    }

    /** Filtro Por hora: ganancia neta minima por hora de trabajo. Ej: 10.00 */
    var minPerHour: Double
        get() = prefs.getFloat(KEY_MIN_PER_HOUR, DEF_HOUR).toDouble()
        set(v) = prefs.edit().putFloat(KEY_MIN_PER_HOUR, v.toFloat()).apply()

    /** Filtro Por km: ganancia neta minima por km de viaje. Ej: 0.45 */
    var minPerKm: Double
        get() = prefs.getFloat(KEY_MIN_PER_KM, DEF_KM).toDouble()
        set(v) = prefs.edit().putFloat(KEY_MIN_PER_KM, v.toFloat()).apply()

    /** Filtro Pick Up max: distancia maxima de recogida (km). Ej: 3.0 */
    var maxPickupKm: Double
        get() = prefs.getFloat(KEY_MAX_PICKUP_KM, DEF_PICKUP).toDouble()
        set(v) = prefs.edit().putFloat(KEY_MAX_PICKUP_KM, v.toFloat()).apply()

    /**
     * Costo de gasolina por kilometro. Un solo numero, sin tener que calcular
     * rendimiento ni precio del galon. Ej: 0.06 (carro pequeno).
     */
    var fuelCostPerKm: Double
        get() = prefs.getFloat(KEY_FUEL_PER_KM, DEF_FUEL_KM).toDouble()
        set(v) = prefs.edit().putFloat(KEY_FUEL_PER_KM, v.toFloat()).apply()

    /**
     * Modo TURBO: sube las exigencias (x1.4) para que solo marque en verde
     * las carreras realmente buenas. Ideal en horas pico.
     */
    var turboMode: Boolean
        get() = prefs.getBoolean(KEY_TURBO, false)
        set(v) = prefs.edit().putBoolean(KEY_TURBO, v).apply()

    /** Estado global: si el analisis esta encendido (boton iniciar/parar). */
    var running: Boolean
        get() = prefs.getBoolean(KEY_RUNNING, false)
        set(v) = prefs.edit().putBoolean(KEY_RUNNING, v).apply()

    // --- Interruptores ON/OFF de cada filtro ---
    var filterHourOn: Boolean
        get() = prefs.getBoolean(KEY_F_HOUR, true)
        set(v) = prefs.edit().putBoolean(KEY_F_HOUR, v).apply()
    var filterKmOn: Boolean
        get() = prefs.getBoolean(KEY_F_KM, true)
        set(v) = prefs.edit().putBoolean(KEY_F_KM, v).apply()
    var filterPickupOn: Boolean
        get() = prefs.getBoolean(KEY_F_PICKUP, true)
        set(v) = prefs.edit().putBoolean(KEY_F_PICKUP, v).apply()

    /** Cuantos filtros estan activos (para el badge "N activos"). */
    val activeFilterCount: Int
        get() = listOf(filterHourOn, filterKmOn, filterPickupOn).count { it }

    // --- Contadores de estadisticas ---
    var statTotal: Int
        get() = prefs.getInt(KEY_S_TOTAL, 0)
        set(v) = prefs.edit().putInt(KEY_S_TOTAL, v).apply()
    var statAccept: Int
        get() = prefs.getInt(KEY_S_ACCEPT, 0)
        set(v) = prefs.edit().putInt(KEY_S_ACCEPT, v).apply()
    var statReject: Int
        get() = prefs.getInt(KEY_S_REJECT, 0)
        set(v) = prefs.edit().putInt(KEY_S_REJECT, v).apply()

    fun recordAccept() { statTotal += 1; statAccept += 1 }
    fun recordReject() { statTotal += 1; statReject += 1 }
    fun resetStats() { statTotal = 0; statAccept = 0; statReject = 0 }

    // --- Tiempo en linea (para calcular la ganancia por hora REAL) ---

    /** Arranca el cronometro de la jornada. */
    fun startOnline() {
        rollDayIfNeeded()
        prefs.edit().putLong(KEY_ONLINE_START, System.currentTimeMillis()).apply()
    }

    /** Detiene el cronometro y acumula el tiempo trabajado. */
    fun stopOnline() {
        rollDayIfNeeded()
        val start = prefs.getLong(KEY_ONLINE_START, 0L)
        if (start > 0) {
            val delta = (System.currentTimeMillis() - start) / 1000
            prefs.edit()
                .putLong(KEY_ONLINE_ACCUM, prefs.getLong(KEY_ONLINE_ACCUM, 0L) + delta)
                .putLong(KEY_ONLINE_START, 0L)
                .apply()
        }
    }

    /** Segundos trabajados hoy, incluyendo la sesion en curso. */
    fun onlineSecondsToday(): Long {
        rollDayIfNeeded()
        val accum = prefs.getLong(KEY_ONLINE_ACCUM, 0L)
        val start = prefs.getLong(KEY_ONLINE_START, 0L)
        val live = if (start > 0) (System.currentTimeMillis() - start) / 1000 else 0
        return accum + live
    }

    /** Al cambiar de dia, el contador de horas arranca de cero. */
    private fun rollDayIfNeeded() {
        val today = com.rutapro.analyzer.data.Ledger.dayKey()
        if (prefs.getString(KEY_ONLINE_DAY, "") != today) {
            prefs.edit()
                .putString(KEY_ONLINE_DAY, today)
                .putLong(KEY_ONLINE_ACCUM, 0L)
                .putLong(KEY_ONLINE_START, 0L)
                .apply()
        }
    }

    /** Multiplicador de exigencia segun el modo turbo. */
    val demandFactor: Double
        get() = if (turboMode) 1.4 else 1.0

    companion object {
        private const val PREFS_VERSION = 3

        // Valores por defecto en dolares.
        private const val DEF_HOUR = 10.0f
        private const val DEF_KM = 0.45f
        private const val DEF_PICKUP = 3.0f
        private const val DEF_FUEL_KM = 0.06f

        private const val KEY_VERSION = "prefs_version"
        private const val KEY_MIN_PER_KM = "min_per_km"
        private const val KEY_MIN_PER_HOUR = "min_per_hour"
        private const val KEY_MAX_PICKUP_KM = "max_pickup_km"
        private const val KEY_FUEL_PER_KM = "fuel_per_km"
        private const val KEY_TURBO = "turbo"
        private const val KEY_RUNNING = "running"
        private const val KEY_F_HOUR = "f_hour"
        private const val KEY_F_KM = "f_km"
        private const val KEY_F_PICKUP = "f_pickup"
        private const val KEY_S_TOTAL = "s_total"
        private const val KEY_S_ACCEPT = "s_accept"
        private const val KEY_S_REJECT = "s_reject"
        private const val KEY_ONLINE_START = "online_start"
        private const val KEY_ONLINE_ACCUM = "online_accum"
        private const val KEY_ONLINE_DAY = "online_day"
    }
}
