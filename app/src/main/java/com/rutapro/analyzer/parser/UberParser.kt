package com.rutapro.analyzer.parser

import com.rutapro.analyzer.analyzer.RideOffer

/**
 * Extrae una oferta de carrera del texto capturado en la pantalla del conductor de Uber.
 *
 * IMPORTANTE: el formato exacto del texto de Uber cambia segun pais, idioma y version de la app.
 * Este parser usa heuristicas sobre patrones tipicos como:
 *   "$8.500"                          -> tarifa
 *   "12 min (3,5 km) de distancia"    -> recogida
 *   "25 min (8,2 km) de viaje"        -> viaje
 * Si en tu ciudad el texto es distinto, activa el modo debug en la app: te mostrara el texto
 * crudo capturado para poder afinar estas expresiones.
 */
object UberParser {

    // Numero de dinero: $ opcional, con separadores de miles (. o ,) y decimales opcionales.
    private val MONEY = Regex("""\$?\s?(\d{1,3}(?:[.,]\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?)""")

    // "12 min" -> minutos
    private val MIN = Regex("""(\d+(?:[.,]\d+)?)\s*min""", RegexOption.IGNORE_CASE)

    // "3,5 km" o "2.1 mi" -> distancia con unidad
    private val DIST = Regex("""(\d+(?:[.,]\d+)?)\s*(km|kms|mi|mile|milla)""", RegexOption.IGNORE_CASE)

    // Palabras que marcan la seccion de recogida vs. viaje (ES e EN).
    private val PICKUP_HINT = Regex("""(recog|distancia|llegar|away|pickup|hacia el)""", RegexOption.IGNORE_CASE)
    private val TRIP_HINT = Regex("""(viaje|trip|destino|drop|total del viaje)""", RegexOption.IGNORE_CASE)

    fun parse(raw: String): RideOffer? {
        if (raw.isBlank()) return null

        val fare = extractFare(raw) ?: return null

        // Recolecta pares (min, km) en el orden en que aparecen, con su contexto.
        val segments = extractSegments(raw)
        if (segments.isEmpty()) return null

        // Clasifica cada segmento como recogida o viaje segun las palabras cercanas;
        // si no hay pista, se asume: primero recogida, luego viaje.
        var pickup: Segment? = segments.firstOrNull { it.kind == Kind.PICKUP }
        var trip: Segment? = segments.firstOrNull { it.kind == Kind.TRIP }

        if (pickup == null && trip == null) {
            pickup = segments.getOrNull(0)
            trip = segments.getOrNull(1) ?: segments.getOrNull(0)
        } else if (trip == null) {
            trip = segments.lastOrNull { it != pickup } ?: pickup
        } else if (pickup == null) {
            pickup = segments.firstOrNull { it != trip }
        }

        val tripKm = trip?.km ?: 0.0
        val tripMin = trip?.min ?: 0.0
        val pickupKm = pickup?.km ?: 0.0
        val pickupMin = pickup?.min ?: 0.0

        return RideOffer(
            fare = fare,
            tripKm = tripKm,
            tripMin = tripMin,
            pickupKm = pickupKm,
            pickupMin = pickupMin,
            rawText = raw
        )
    }

    private fun extractFare(raw: String): Double? {
        // Prefiere el numero que venga junto a un simbolo de moneda.
        val withSymbol = Regex("""\$\s?(\d{1,3}(?:[.,]\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?)""")
            .find(raw)?.groupValues?.get(1)
        val candidate = withSymbol ?: MONEY.find(raw)?.groupValues?.get(1)
        return candidate?.let { normalizeMoney(it) }?.takeIf { it > 0 }
    }

    private enum class Kind { PICKUP, TRIP, UNKNOWN }
    private data class Segment(val min: Double, val km: Double, val kind: Kind)

    private fun extractSegments(raw: String): List<Segment> {
        val result = mutableListOf<Segment>()
        // Busca cada distancia y, cerca de ella, el tiempo y las palabras de contexto.
        for (m in DIST.findAll(raw)) {
            val km = toKm(m.groupValues[1], m.groupValues[2])
            val start = (m.range.first - 30).coerceAtLeast(0)
            val end = (m.range.last + 30).coerceAtMost(raw.length)
            val around = raw.substring(start, end)
            val min = MIN.find(around)?.groupValues?.get(1)?.let { normalizeDecimal(it) } ?: 0.0
            val kind = when {
                PICKUP_HINT.containsMatchIn(around) -> Kind.PICKUP
                TRIP_HINT.containsMatchIn(around) -> Kind.TRIP
                else -> Kind.UNKNOWN
            }
            result.add(Segment(min, km, kind))
        }
        return result
    }

    /** Convierte "8.500" (COP) o "4.50" (USD) a Double, tratando . o , como separador de miles. */
    private fun normalizeMoney(s: String): Double {
        val cleaned = s.trim()
        // Si hay separador seguido de exactamente 3 digitos al final -> es de miles.
        val thousands = Regex(""".*[.,]\d{3}$""")
        return if (thousands.matches(cleaned)) {
            cleaned.replace(".", "").replace(",", "").toDoubleOrNull() ?: 0.0
        } else {
            // Trata el ultimo separador como decimal.
            cleaned.replace(",", ".").let { withDot ->
                val idx = withDot.lastIndexOf('.')
                if (idx >= 0) {
                    val intPart = withDot.substring(0, idx).replace(".", "")
                    val decPart = withDot.substring(idx + 1)
                    "$intPart.$decPart".toDoubleOrNull() ?: 0.0
                } else withDot.toDoubleOrNull() ?: 0.0
            }
        }
    }

    private fun normalizeDecimal(s: String): Double =
        s.replace(",", ".").toDoubleOrNull() ?: 0.0

    private fun toKm(value: String, unit: String): Double {
        val v = normalizeDecimal(value)
        return if (unit.lowercase().startsWith("mi")) v * 1.60934 else v
    }
}
