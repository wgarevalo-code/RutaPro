package com.rutapro.analyzer.analyzer

/**
 * Evita contar y mostrar la MISMA oferta muchas veces.
 *
 * La pantalla se lee varias veces por segundo, y una oferta se queda ahi 20-30 s.
 * Sin este candado, una sola carrera se contaria decenas de veces (por eso el
 * contador se inflaba). Aqui se reconoce la oferta por su tarifa + distancia y no
 * se vuelve a procesar hasta que pasa un tiempo o cambia la oferta.
 */
object OfferGate {

    private var lastKey = ""
    private var lastTimeMs = 0L
    private const val WINDOW_MS = 90_000L  // misma oferta = ignorar por 90 s

    /** true solo la primera vez que aparece una oferta distinta. */
    @Synchronized
    fun isNew(offer: RideOffer): Boolean {
        val key = signature(offer)
        val now = System.currentTimeMillis()
        if (key == lastKey && now - lastTimeMs < WINDOW_MS) {
            lastTimeMs = now   // sigue en pantalla: refresca el tiempo
            return false
        }
        lastKey = key
        lastTimeMs = now
        return true
    }

    private fun signature(o: RideOffer): String {
        val fare = Math.round(o.fare * 100)      // centavos
        val km = Math.round(o.tripKm * 10)       // decimas de km
        return "$fare|$km"
    }
}
