package com.rutapro.analyzer.analyzer

import kotlin.math.ceil

enum class Verdict { GOOD, FAIR, BAD }

/**
 * Resultado del analisis de una oferta.
 */
data class Analysis(
    val fuelCost: Double,     // costo estimado de gasolina para toda la operacion
    val net: Double,          // ganancia neta despues de gasolina
    val perKm: Double,        // neto por km de viaje
    val perMin: Double,       // neto por minuto trabajado (recogida + viaje)
    val perHour: Double,      // neto proyectado por hora
    val verdict: Verdict,
    val headline: String,     // texto corto: CONVIENE / REGULAR / NO CONVIENE
    val reason: String,       // explicacion corta del porque
    val fairFare: Double,     // precio minimo para que ESTA carrera cumpla tus filtros
    val goodFare: Double      // precio recomendado para pedir (con margen para negociar)
)

/**
 * Fórmula completa: combina filtros por hora, por km y por minuto, penaliza recogidas
 * lejanas y descuenta el costo real de gasolina. El modo TURBO sube todas las exigencias.
 */
class RideAnalyzer(private val settings: AppSettings) {

    fun analyze(offer: RideOffer): Analysis {
        val totalKm = offer.tripKm + offer.pickupKm
        val totalMin = offer.tripMin + offer.pickupMin

        val fuelCost = totalKm * settings.fuelCostPerKm

        val net = offer.fare - fuelCost
        val perKm = if (offer.tripKm > 0) net / offer.tripKm else 0.0
        val perMin = if (totalMin > 0) net / totalMin else 0.0
        val perHour = perMin * 60.0

        // Umbrales ajustados por el modo turbo.
        val f = settings.demandFactor
        val reqKm = settings.minPerKm * f
        val reqHour = settings.minPerHour * f

        // Solo cuentan los filtros que el usuario tiene encendidos.
        val enabledChecks = mutableListOf<Boolean>()
        if (settings.filterHourOn) enabledChecks.add(perHour >= reqHour)
        if (settings.filterKmOn) enabledChecks.add(perKm >= reqKm)

        val meetsKm = !settings.filterKmOn || perKm >= reqKm
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
                    else -> "Justa, en el limite."
                }
            }
        }

        val headline = when (verdict) {
            Verdict.GOOD -> if (settings.turboMode) "CONVIENE ⚡" else "CONVIENE"
            Verdict.FAIR -> "REGULAR"
            Verdict.BAD -> "NO CONVIENE"
        }

        // Precio minimo para que esta carrera cumpla TODOS tus filtros encendidos.
        val fairFare = suggestFare(offer, fuelCost, totalMin, reqKm, reqHour)
        // Precio recomendado para pedir: un 12% arriba, para dejar margen de regateo.
        val goodFare = roundUp(fairFare * 1.12)

        return Analysis(
            fuelCost, net, perKm, perMin, perHour, verdict, headline, reason,
            fairFare, goodFare
        )
    }

    /**
     * Calcula el precio minimo al que la carrera te conviene: es el mayor de lo que
     * exigen tus filtros por km y por hora, mas la gasolina. Es el numero clave para
     * negociar en inDrive.
     */
    private fun suggestFare(
        offer: RideOffer, fuelCost: Double, totalMin: Double,
        reqKm: Double, reqHour: Double
    ): Double {
        var needed = fuelCost  // como minimo, cubrir la gasolina
        if (settings.filterKmOn && offer.tripKm > 0) {
            needed = maxOf(needed, reqKm * offer.tripKm + fuelCost)
        }
        if (settings.filterHourOn && totalMin > 0) {
            needed = maxOf(needed, reqHour * (totalMin / 60.0) + fuelCost)
        }
        return roundUp(needed)
    }

    /** Redondea hacia arriba al siguiente multiplo de 0.25 (queda "bonito" para pedir). */
    private fun roundUp(v: Double): Double = ceil(v * 4.0) / 4.0

    private fun fmt(v: Double): String =
        String.format(java.util.Locale.US, "%.1f", v)
}
