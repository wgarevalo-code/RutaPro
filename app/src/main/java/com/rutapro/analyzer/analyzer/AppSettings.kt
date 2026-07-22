package com.rutapro.analyzer.analyzer

import android.content.Context

/**
 * Parametros del conductor, guardados en SharedPreferences.
 * Todos los valores por defecto estan pensados para Colombia (pesos, km, galones).
 * Ajustalos en la pantalla principal de la app.
 */
class AppSettings(context: Context) {

    private val prefs = context.getSharedPreferences("ruta_pro", Context.MODE_PRIVATE)

    /** Tarifa minima aceptable por kilometro de viaje. */
    var minPerKm: Double
        get() = prefs.getFloat(KEY_MIN_PER_KM, 1500f).toDouble()
        set(v) = prefs.edit().putFloat(KEY_MIN_PER_KM, v.toFloat()).apply()

    /** Tarifa minima aceptable por minuto de trabajo (recogida + viaje). */
    var minPerMin: Double
        get() = prefs.getFloat(KEY_MIN_PER_MIN, 200f).toDouble()
        set(v) = prefs.edit().putFloat(KEY_MIN_PER_MIN, v.toFloat()).apply()

    /** Precio de la gasolina por galon. */
    var fuelPricePerGallon: Double
        get() = prefs.getFloat(KEY_FUEL_PRICE, 16000f).toDouble()
        set(v) = prefs.edit().putFloat(KEY_FUEL_PRICE, v.toFloat()).apply()

    /** Rendimiento del vehiculo en km por galon. */
    var kmPerGallon: Double
        get() = prefs.getFloat(KEY_KM_PER_GALLON, 40f).toDouble()
        set(v) = prefs.edit().putFloat(KEY_KM_PER_GALLON, v.toFloat()).apply()

    /** Distancia maxima de recogida aceptable (km). Mas alla de esto se penaliza. */
    var maxPickupKm: Double
        get() = prefs.getFloat(KEY_MAX_PICKUP_KM, 3f).toDouble()
        set(v) = prefs.edit().putFloat(KEY_MAX_PICKUP_KM, v.toFloat()).apply()

    /** Si se muestran las unidades de distancia en millas en vez de km. */
    var useMiles: Boolean
        get() = prefs.getBoolean(KEY_USE_MILES, false)
        set(v) = prefs.edit().putBoolean(KEY_USE_MILES, v).apply()

    companion object {
        private const val KEY_MIN_PER_KM = "min_per_km"
        private const val KEY_MIN_PER_MIN = "min_per_min"
        private const val KEY_FUEL_PRICE = "fuel_price"
        private const val KEY_KM_PER_GALLON = "km_per_gallon"
        private const val KEY_MAX_PICKUP_KM = "max_pickup_km"
        private const val KEY_USE_MILES = "use_miles"
    }
}
