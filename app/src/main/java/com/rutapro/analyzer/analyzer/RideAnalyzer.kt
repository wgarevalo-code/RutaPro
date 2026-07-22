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
    val perHour: Double,    // neto proyectado por hora
    val verdict: Verdict,
    val headline: String,   // texto corto: CONVIENE / REGULAR / NO CONVIENE
    val reason: String      // explicacion corta del porque
)

/**
 * Fórmula completa: combina filtros por hora, por km y por minuto, penaliza recogidas
 * lejanas y descuenta el costo real de gasolina. El modo TURBO sube todas las exigencias.
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
        val perHour = perMin * 60.0

        // Umbrales ajustados por el modo turbo.
        val f = settings.demandFactor
        val reqKm = settings.minPerKm * f
        val reqMin = settings.minPerMin * f
        val reqHour = settings.minPerHour * f

        val meetsKm = perKm >= reqKm
        val meetsMin = perMin >= reqMin
        val meetsHour = perHour >= reqHour
        val pickupTooFar = offer.pickupKm > settings.maxPickupKm
        val pickupHeavy = offer.tripKm > 0 && offer.pickupKm > offer.tripKm * 0.6

        val passed = listOf(meetsKm, meetsMin, meetsHour).count { it }

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
            passed == 0 -> {
                verdict = Verdict.BAD
                reason = "No pasa ningun filtro."
            }
            passed == 3 && !pickupHeavy -> {
                verdict = Verdict.GOOD
                reason = if (settings.turboMode) "Excelente (modo turbo)." else "Buen pago por km, minuto y hora."
            }
            else -> {
                verdict = Verdict.FAIR
                reason = when {
                    pickupHeavy -> "Recogida larga para el viaje."
                    !meetsHour -> "Floja por hora."
                    !meetsKm -> "Justa por km."
                    else -> "Justa por minuto."
                }
            }
        }

        val headline = when (verdict) {
            Verdict.GOOD -> if (settings.turboMode) "CONVIENE ⚡" else "CONVIENE"
            Verdict.FAIR -> "REGULAR"
            Verdict.BAD -> "NO CONVIENE"
        }

        return Analysis(fuelCost, net, perKm, perMin, perHour, verdict, headline, reason)
    }

    private fun fmt(v: Double): String =
        if (v >= 10) v.roundToInt().toString() else String.format("%.1f", v)
}
