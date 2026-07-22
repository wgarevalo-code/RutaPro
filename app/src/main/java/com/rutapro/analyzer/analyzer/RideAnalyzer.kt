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

        // Solo cuentan los filtros que el usuario tiene encendidos.
        val enabledChecks = mutableListOf<Boolean>()
        if (settings.filterHourOn) enabledChecks.add(perHour >= reqHour)
        if (settings.filterKmOn) enabledChecks.add(perKm >= reqKm)
        if (settings.filterMinOn) enabledChecks.add(perMin >= reqMin)

        val meetsKm = !settings.filterKmOn || perKm >= reqKm
        val meetsMin = !settings.filterMinOn || perMin >= reqMin
        val meetsHour = !settings.filterHourOn || perHour >= reqHour
        val pickupTooFar = settings.filterPickupOn && offer.pickupKm > settings.maxPickupKm
        val pickupHeavy = offer.tripKm > 0 && offer.pickupKm > offer.tripKm * 0.6

        val passed = enabledChecks.count { it }
        val totalChecks = enabledChecks.size

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
            totalChecks > 0 && passed == 0 -> {
                verdict = Verdict.BAD
                reason = "No pasa ningun filtro."
            }
            passed == totalChecks && !pickupHeavy -> {
                verdict = Verdict.GOOD
                reason = if (settings.turboMode) "Excelente (modo turbo)." else "Buen pago segun tus filtros."
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
