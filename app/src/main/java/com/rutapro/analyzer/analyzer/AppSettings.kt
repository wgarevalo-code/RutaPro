package com.rutapro.analyzer.analyzer

import android.content.Context

/**
 * Parametros del conductor, guardados en SharedPreferences.
 * Todos los valores por defecto estan pensados para Colombia (pesos, km, galones).
 * Ajustalos en la pantalla principal de la app.
 */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("ruta_pro", Context.MODE_PRIVATE)

    /** Filtro por KM: tarifa minima aceptable por kilometro de viaje. */
    var minPerKm: Double
        get() = prefs.getFloat(KEY_MIN_PER_KM, 1500f).toDouble()
        set(v) = prefs.edit().putFloat(KEY_MIN_PER_KM, v.toFloat()).apply()

    /** Filtro por minuto: tarifa minima aceptable por minuto trabajado (recogida + viaje). */
    var minPerMin: Double
        get() = prefs.getFloat(KEY_MIN_PER_MIN, 200f).toDouble()
        set(v) = prefs.edit().putFloat(KEY_MIN_PER_MIN, v.toFloat()).apply()

    /** Filtro por hora: ganancia minima aceptable por hora de trabajo. */
    var minPerHour: Double
        get() = prefs.getFloat(KEY_MIN_PER_HOUR, 12000f).toDouble()
        set(v) = prefs.edit().putFloat(KEY_MIN_PER_HOUR, v.toFloat()).apply()

    /** Pick Up max: distancia maxima de recogida aceptable (km). Mas alla se rechaza. */
    var maxPickupKm: Double
        get() = prefs.getFloat(KEY_MAX_PICKUP_KM, 3f).toDouble()
        set(v) = prefs.edit().putFloat(KEY_MAX_PICKUP_KM, v.toFloat()).apply()

    /** Precio de la gasolina por galon. */
    var fuelPricePerGallon: Double
        get() = prefs.getFloat(KEY_FUEL_PRICE, 16000f).toDouble()
        set(v) = prefs.edit().putFloat(KEY_FUEL_PRICE, v.toFloat()).apply()

    /** Rendimiento del vehiculo en km por galon. */
    var kmPerGallon: Double
        get() = prefs.getFloat(KEY_KM_PER_GALLON, 40f).toDouble()
        set(v) = prefs.edit().putFloat(KEY_KM_PER_GALLON, v.toFloat()).apply()

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

    /** Multiplicador de exigencia segun el modo turbo. */
    val demandFactor: Double
        get() = if (turboMode) 1.4 else 1.0

    companion object {
        private const val KEY_MIN_PER_KM = "min_per_km"
        private const val KEY_MIN_PER_MIN = "min_per_min"
        private const val KEY_MIN_PER_HOUR = "min_per_hour"
        private const val KEY_MAX_PICKUP_KM = "max_pickup_km"
        private const val KEY_FUEL_PRICE = "fuel_price"
        private const val KEY_KM_PER_GALLON = "km_per_gallon"
        private const val KEY_TURBO = "turbo"
        private const val KEY_RUNNING = "running"
    }
}
