package com.rutapro.analyzer.analyzer

import kotlin.math.roundToInt

enum class Verdict { GOOD, FAIR, BAD }

/**
 * Resultado del analisis de una oferta.
 */
data class Analysis(
    val fuelCost: Double,   // costo estimado de gasolina para toda la operacion
    val net: Double,        // ganancia neta despues de gasolina
    val perKm: Double,      // neto por km de viaje
    val perMin: Double,     // neto por minuto trabajado (recogida + viaje)
    val verdict: Verdict,
    val headline: String,   // texto corto: CONVIENE / REGULAR / NO CONVIENE
    val reason: String      // explicacion corta del porque
)

/**
 * Fórmula "completa": combina ganancia por km, por minuto y penaliza recogidas lejanas,
 * descontando el costo real de gasolina.
 */
class RideAnalyzer(private val settings: AppSettings) {

    fun analyze(offer: RideOffer): Analysis {
        val totalKm = offer.tripKm + offer.pickupKm
        val totalMin = offer.tripMin + offer.pickupMin

        val fuelCost = if (settings.kmPerGallon > 0) {
            (totalKm / settings.kmPerGallon) * settings.fuelPricePerGallon
        } else 0.0

        val net = offer.fare - fuelCost
        val perKm = if (offer.tripKm > 0) net / offer.tripKm else 0.0
        val perMin = if (totalMin > 0) net / totalMin else 0.0

        val meetsPerKm = perKm >= settings.minPerKm
        val meetsPerMin = perMin >= settings.minPerMin
        val pickupTooFar = offer.pickupKm > settings.maxPickupKm
        // Recogida "cara": recorrer mas para llegar que lo que dura el viaje mismo.
        val pickupHeavy = offer.tripKm > 0 && offer.pickupKm > offer.tripKm * 0.6

        val verdict: Verdict
        val reason: String
        when {
            net <= 0 -> {
                verdict = Verdict.BAD
                reason = "La gasolina se come la tarifa."
            }
            pickupTooFar -> {
                verdict = Verdict.BAD
                reason = "Recogida muy lejos (${fmt(offer.pickupKm)} km)."
            }
            !meetsPerKm && !meetsPerMin -> {
                verdict = Verdict.BAD
                reason = "Paga poco por km y por minuto."
            }
            !meetsPerKm || !meetsPerMin || pickupHeavy -> {
                verdict = Verdict.FAIR
                reason = when {
                    pickupHeavy -> "Recogida larga para el viaje."
                    !meetsPerKm -> "Justo en el limite por km."
                    else -> "Justo en el limite por minuto."
                }
            }
            else -> {
                verdict = Verdict.GOOD
                reason = "Buen pago por km y por tiempo."
            }
        }

        val headline = when (verdict) {
            Verdict.GOOD -> "CONVIENE"
            Verdict.FAIR -> "REGULAR"
            Verdict.BAD -> "NO CONVIENE"
        }

        return Analysis(fuelCost, net, perKm, perMin, verdict, headline, reason)
    }

    private fun fmt(v: Double): String =
        if (v >= 10) v.roundToInt().toString() else String.format("%.1f", v)
}
