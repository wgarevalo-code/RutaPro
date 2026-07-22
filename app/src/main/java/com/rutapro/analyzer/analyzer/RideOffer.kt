package com.rutapro.analyzer.analyzer

/**
 * Datos de una oferta de carrera extraidos de la pantalla del conductor.
 * Las distancias estan en kilometros y los tiempos en minutos.
 */
data class RideOffer(
    val fare: Double,        // tarifa ofrecida (en la moneda local)
    val tripKm: Double,      // distancia del viaje (recogida -> destino)
    val tripMin: Double,     // duracion estimada del viaje
    val pickupKm: Double,    // distancia hasta el pasajero
    val pickupMin: Double,   // tiempo hasta el pasajero
    val rawText: String      // texto crudo capturado (para depuracion/ajuste)
) {
    /** Una oferta es valida para analizar si al menos hay tarifa y distancia de viaje. */
    val isValid: Boolean
        get() = fare > 0.0 && tripKm > 0.0
}
