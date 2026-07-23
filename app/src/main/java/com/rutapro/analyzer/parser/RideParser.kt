package com.rutapro.analyzer.parser

import com.rutapro.analyzer.analyzer.RideOffer
import kotlin.math.abs

/**
 * Extrae una oferta de carrera del texto capturado (Uber / inDrive) en Ecuador.
 *
 * Semantica REAL de cada app (confirmada con capturas):
 *
 *  inDrive:
 *    "~4,2 km"            -> distancia de RECOGIDA (de mi ubicacion al pasajero)
 *    "13 min 4,2 km" (A azul) -> recogida (mismo 4,2)
 *    "17 min 8,2 km" (B verde) -> VIAJE (del pasajero al destino)
 *    "Aceptar por $2.80" -> tarifa exacta
 *
 *  Uber:
 *    "A 4 min (0.4 km)"      -> recogida
 *    "Viaje: 6 min (1.4 km)" -> viaje
 *    "$2.52"                 -> tarifa
 */
object RideParser {

    private val MONEY_DEC = Regex("""\$\s?(\d{1,4}[.,]\d{2})""")
    private val MONEY_ANY = Regex("""\$\s?(\d{1,4}(?:[.,]\d{1,2})?)""")
    private val ACCEPT_FOR = Regex("""aceptar por\s*\$?\s?(\d{1,4}(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE)

    // "6 min (1.4 km)" o "13 min 4,2 km"
    private val TIME_DIST = Regex(
        """(\d{1,3})\s*min\s*[(\s]*\s*(\d{1,3}(?:[.,]\d)?)\s*(km|mi)""",
        RegexOption.IGNORE_CASE
    )
    private val TILDE_DIST = Regex("""~\s*(\d{1,3}(?:[.,]\d)?)\s*(km|mi)""", RegexOption.IGNORE_CASE)

    private val INDRIVE_SIG = Regex("""(ofrece tu tarifa|solicitud de viaje|aceptar por)""", RegexOption.IGNORE_CASE)
    private val TRIP_HINT = Regex("""viaje""", RegexOption.IGNORE_CASE)

    private val NOT_AN_OFFER = Regex(
        "(oportunidad|promoci|adicionales|al completar|solicitudes de|premio|racha|" +
            "cuponera|ganancias|historial|semana pasada|resumen|billetera|saldo|retirar|" +
            "calificaci|incentivo|desaf|" +
            "rutapro|panel de control|filtros inteligentes|reiniciar contadores|" +
            "analizando|la tomaste|no conviene|conviene|mínimo que te conviene)",
        RegexOption.IGNORE_CASE
    )

    private const val MIN_FARE = 0.30
    private const val MAX_FARE = 500.0
    private const val MAX_PICKUP_KM = 25.0
    private const val MAX_TRIP_KM = 300.0

    data class Parsed(val offer: RideOffer, val app: String)
    private data class Seg(val min: Double, val km: Double, val pos: Int)

    fun parseWithApp(raw: String): Parsed? {
        if (raw.isBlank() || NOT_AN_OFFER.containsMatchIn(raw)) return null
        val isInDrive = INDRIVE_SIG.containsMatchIn(raw)
        val offer = (if (isInDrive) parseInDrive(raw) else parseUber(raw)) ?: return null
        if (!isSane(offer)) return null
        return Parsed(offer, if (isInDrive) "inDrive" else "Uber")
    }

    fun parse(raw: String): RideOffer? = parseWithApp(raw)?.offer

    // ---------- inDrive ----------
    private fun parseInDrive(raw: String): RideOffer? {
        val fare = ACCEPT_FOR.find(raw)?.let { normalize(it.groupValues[1]) }
            ?: firstDecimalMoney(raw) ?: return null

        val segs = segments(raw)
        val pickupHint = TILDE_DIST.find(raw)?.let { toKm(it.groupValues[1], it.groupValues[2]) }

        var pickupKm = 0.0; var pickupMin = 0.0
        var tripKm = 0.0; var tripMin = 0.0

        if (pickupHint != null && segs.isNotEmpty()) {
            // La recogida es el segmento cuya distancia coincide con el "~X km".
            val pSeg = segs.minByOrNull { abs(it.km - pickupHint) }
            pickupKm = pickupHint
            pickupMin = if (pSeg != null && abs(pSeg.km - pickupHint) < 0.6) pSeg.min else 0.0
            // El viaje es el OTRO segmento (el verde). No se asume que sea el mayor.
            val tSeg = segs.filter { it !== pSeg }.maxByOrNull { it.pos }
                ?: segs.firstOrNull { abs(it.km - pickupHint) >= 0.6 }
            if (tSeg != null) { tripKm = tSeg.km; tripMin = tSeg.min }
        } else if (segs.size >= 2) {
            // Sin "~": por orden, primero recogida (A) y luego viaje (B).
            val sorted = segs.sortedBy { it.pos }
            pickupKm = sorted[0].km; pickupMin = sorted[0].min
            tripKm = sorted[1].km; tripMin = sorted[1].min
        } else if (segs.size == 1) {
            tripKm = segs[0].km; tripMin = segs[0].min
        }

        if (tripKm <= 0.0) return null
        return RideOffer(fare, tripKm, tripMin, pickupKm, pickupMin, raw)
    }

    // ---------- Uber ----------
    private fun parseUber(raw: String): RideOffer? {
        val fare = firstDecimalMoney(raw) ?: MONEY_ANY.find(raw)?.let { normalize(it.groupValues[1]) }
        ?: return null

        val segs = segments(raw)
        if (segs.isEmpty()) return null

        val tripPositions = TRIP_HINT.findAll(raw).map { it.range.first }.toList()
        var trip: Seg? = null
        if (tripPositions.isNotEmpty()) {
            // El viaje: el segmento que aparece justo despues de la palabra "Viaje".
            trip = segs.filter { s -> tripPositions.any { it < s.pos } }
                .minByOrNull { s -> tripPositions.filter { it < s.pos }.minOf { s.pos - it } }
        }
        if (trip == null) trip = segs.maxByOrNull { it.km }
        val pickup = segs.filter { it !== trip }.minByOrNull { it.pos }

        val tripKm = trip?.km ?: 0.0
        if (tripKm <= 0.0) return null
        return RideOffer(fare, tripKm, trip?.min ?: 0.0, pickup?.km ?: 0.0, pickup?.min ?: 0.0, raw)
    }

    // ---------- comun ----------
    private fun segments(raw: String): List<Seg> =
        TIME_DIST.findAll(raw).map {
            Seg(
                min = it.groupValues[1].toDoubleOrNull() ?: 0.0,
                km = toKm(it.groupValues[2], it.groupValues[3]),
                pos = it.range.first
            )
        }.toList()

    private fun firstDecimalMoney(raw: String): Double? =
        MONEY_DEC.find(raw)?.groupValues?.get(1)?.let { normalize(it) }

    private fun isSane(o: RideOffer): Boolean {
        if (o.fare < MIN_FARE || o.fare > MAX_FARE) return false
        if (o.tripKm <= 0.0 || o.tripKm > MAX_TRIP_KM) return false
        if (o.pickupKm < 0.0 || o.pickupKm > MAX_PICKUP_KM) return false
        if (o.tripMin <= 0.0 && o.pickupMin <= 0.0) return false
        if (o.fare / o.tripKm > 8.0) return false  // pago por km disparatado = lectura mala
        return true
    }

    private fun normalize(s: String): Double = s.replace(",", ".").toDoubleOrNull() ?: 0.0

    private fun toKm(value: String, unit: String): Double {
        val v = value.replace(",", ".").toDoubleOrNull() ?: 0.0
        return if (unit.lowercase().startsWith("mi")) v * 1.60934 else v
    }
}
