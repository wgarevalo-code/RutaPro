package com.rutapro.analyzer.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.rutapro.analyzer.data.Ledger

/**
 * Sigue la ubicacion mientras la jornada esta activa para dos cosas:
 *   - sumar los kilometros realmente recorridos en el dia
 *   - saber en que zona apareció cada carrera
 *
 * Nada se envia a internet: todo queda guardado en el telefono.
 */
class LocationTracker(private val context: Context) {

    private val client = LocationServices.getFusedLocationProviderClient(context)
    private val store = TripStore(context)
    private var lastLocation: Location? = null
    private var running = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            onNewLocation(loc)
        }
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun start() {
        if (running || !hasPermission()) return
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 15_000L)
            .setMinUpdateDistanceMeters(40f)
            .build()
        try {
            client.requestLocationUpdates(request, callback, context.mainLooper)
            running = true
        } catch (_: SecurityException) {
        }
    }

    fun stop() {
        if (!running) return
        try { client.removeLocationUpdates(callback) } catch (_: Exception) {}
        running = false
        lastLocation = null
    }

    private fun onNewLocation(loc: Location) {
        store.rollDayIfNeeded()
        val prev = lastLocation
        if (prev != null) {
            val meters = prev.distanceTo(loc)
            // Ignora saltos de GPS absurdos (mas de 2 km entre dos lecturas).
            if (meters in 5f..2000f) {
                store.addMeters(meters.toDouble())
            }
        }
        lastLocation = loc
        store.saveLastPosition(loc.latitude, loc.longitude)
    }

    companion object {
        /** Ultima posicion conocida, para etiquetar la carrera que se acepta. */
        fun lastPosition(context: Context): Pair<Double, Double>? =
            TripStore(context).lastPosition()

        fun kmToday(context: Context): Double = TripStore(context).kmToday()
    }
}

/** Guarda los km del dia y la ultima posicion. */
class TripStore(context: Context) {

    private val prefs = context.getSharedPreferences("ruta_pro_trip", Context.MODE_PRIVATE)

    fun rollDayIfNeeded() {
        val today = Ledger.dayKey()
        if (prefs.getString(KEY_DAY, "") != today) {
            prefs.edit().putString(KEY_DAY, today).putFloat(KEY_METERS, 0f).apply()
        }
    }

    fun addMeters(m: Double) {
        prefs.edit().putFloat(KEY_METERS, (prefs.getFloat(KEY_METERS, 0f) + m.toFloat())).apply()
    }

    fun kmToday(): Double {
        rollDayIfNeeded()
        return prefs.getFloat(KEY_METERS, 0f) / 1000.0
    }

    fun saveLastPosition(lat: Double, lng: Double) {
        prefs.edit()
            .putFloat(KEY_LAT, lat.toFloat())
            .putFloat(KEY_LNG, lng.toFloat())
            .apply()
    }

    fun lastPosition(): Pair<Double, Double>? {
        val lat = prefs.getFloat(KEY_LAT, 0f)
        val lng = prefs.getFloat(KEY_LNG, 0f)
        return if (lat == 0f && lng == 0f) null else Pair(lat.toDouble(), lng.toDouble())
    }

    companion object {
        private const val KEY_DAY = "day"
        private const val KEY_METERS = "meters"
        private const val KEY_LAT = "lat"
        private const val KEY_LNG = "lng"
    }
}
