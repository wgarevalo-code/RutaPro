package com.rutapro.analyzer.parser

import com.rutapro.analyzer.analyzer.RideOffer

/**
 * Extrae una oferta de carrera del texto capturado en pantalla (Uber / inDrive).
 *
 * Lo mas importante de este parser NO es leer, es SABER CUANDO NO LEER: las apps de
 * conductor tienen muchas pantallas con precios (promociones, bonos, ganancias,
 * historial) que no son ofertas de viaje. Si no se filtran, la burbuja muestra
 * numeros absurdos. Por eso hay tres capas de defensa:
 *   1. Lista negra de palabras de pantallas que NO son ofertas.
 *   2. Exigir la estructura tipica de una oferta (tarifa + distancia + tiempo).
 *   3. Rangos de cordura (una recogida de 33 km no existe).
 */
object RideParser {

    // Numero de dinero con simbolo de moneda delante.
    private val MONEY_SYMBOL = Regex("""\$\s?(\d{1,3}(?:[.,]\d{3})*(?:[.,]\d{1,2})?)""")

    // "12 min" -> minutos
    private val MIN = Regex("""(\d+(?:[.,]\d+)?)\s*min""", RegexOption.IGNORE_CASE)

    // "3,5 km" o "2.1 mi" -> distancia con unidad
    private val DIST = Regex("""(\d+(?:[.,]\d+)?)\s*(km|kms|mi\b|millas?)""", RegexOption.IGNORE_CASE)

    private val PICKUP_HINT = Regex("""(recog|distancia|llegar|away|pickup|hacia|de ti|minutos de)""", RegexOption.IGNORE_CASE)
    private val TRIP_HINT = Regex("""(viaje|trip|destino|drop|hasta el destino|de viaje)""", RegexOption.IGNORE_CASE)

    /**
     * Palabras que delatan una pantalla que NO es una oferta de carrera.
     * Si aparece cualquiera, se descarta todo el texto.
     */
    private val NOT_AN_OFFER = Regex(
        """(oportunidad|promoci|adicionales?\s|al completar|solicitudes|premio|racha|
           |bono\b|cuponera|ganancias|historial|semana pasada|resumen|billetera|
           |saldo|retirar|pagos|calificaci|puntuaci|incentivo|meta\s|desaf|
           |configuraci|ajustes|ayuda|soporte)""".trimMargin().replace("\n", ""),
        setOf(RegexOption.IGNORE_CASE, RegexOption.COMMENTS)
    )

    // Rangos de cordura. Fuera de esto, no es una oferta valida.
    private const val MIN_FARE = 0.30
    private const val MAX_FARE = 500.0
    private const val MAX_PICKUP_KM = 25.0
    private const val MAX_TRIP_KM = 300.0

    fun parse(raw: String): RideOffer? {
        if (raw.isBlank()) return null

        // Capa 1: pantallas que no son ofertas.
        if (NOT_AN_OFFER.containsMatchIn(raw)) return null

        // Capa 2: estructura de una oferta real.
        val fare = extractFare(raw) ?: return null
        val segments = extractSegments(raw)
        if (segments.isEmpty()) return null
        // Una oferta siempre trae tiempo, no solo distancia.
        if (segments.none { it.min > 0 }) return null

        var pickup = segments.firstOrNull { it.kind == Kind.PICKUP }
        var trip = segments.firstOrNull { it.kind == Kind.TRIP }

        if (pickup == null && trip == null) {
            pickup = segments.getOrNull(0)
            trip = segments.getOrNull(1)
        } else if (trip == null) {
            trip = segments.lastOrNull { it != pickup }
        } else if (pickup == null) {
            pickup = segments.firstOrNull { it != trip }
        }

        // Si solo hay un segmento, es el viaje (sin datos de recogida).
        if (trip == null) { trip = pickup; pickup = null }

        val tripKm = trip?.km ?: 0.0
        val tripMin = trip?.min ?: 0.0
        val pickupKm = pickup?.km ?: 0.0
        val pickupMin = pickup?.min ?: 0.0

        val offer = RideOffer(fare, tripKm, tripMin, pickupKm, pickupMin, raw)

        // Capa 3: rangos de cordura.
        if (!isSane(offer)) return null
        return offer
    }

    private fun isSane(o: RideOffer): Boolean {
        if (o.fare < MIN_FARE || o.fare > MAX_FARE) return false
        if (o.tripKm <= 0.0 || o.tripKm > MAX_TRIP_KM) return false
        if (o.pickupKm < 0.0 || o.pickupKm > MAX_PICKUP_KM) return false
        if (o.tripMin <= 0.0 && o.pickupMin <= 0.0) return false
        return true
    }

    private fun extractFare(raw: String): Double? {
        // Solo se acepta un monto que venga con simbolo de moneda.
        val m = MONEY_SYMBOL.find(raw) ?: return null
        return normalizeMoney(m.groupValues[1]).takeIf { it > 0 }
    }

    private enum class Kind { PICKUP, TRIP, UNKNOWN }
    private data class Segment(val min: Double, val km: Double, val kind: Kind)

    private fun extractSegments(raw: String): List<Segment> {
        val result = mutableListOf<Segment>()
        for (m in DIST.findAll(raw)) {
            val km = toKm(m.groupValues[1], m.groupValues[2])
            val start = (m.range.first - 40).coerceAtLeast(0)
            val end = (m.range.last + 40).coerceAtMost(raw.length)
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

    private fun normalizeMoney(s: String): Double {
        val cleaned = s.trim()
        val thousands = Regex(""".*[.,]\d{3}$""")
        return if (thousands.matches(cleaned)) {
            cleaned.replace(".", "").replace(",", "").toDoubleOrNull() ?: 0.0
        } else {
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
