package com.rutapro.analyzer.parser

import com.rutapro.analyzer.analyzer.RideOffer

/**
 * Extrae una oferta de carrera del texto capturado (Uber / inDrive) en Ecuador.
 *
 * Ajustado con capturas reales:
 *   - Uber:    "$2.52" ... "A 4 min (0.4 km)" (recogida) ... "Viaje: 6 min (1.4 km)"
 *   - inDrive: "~7,5 km" ... "$2.50" ... "Aceptar por $2.50" ... mapa "7 min 2,2 km"
 *
 * Lo mas importante sigue siendo saber CUANDO NO leer (promos, el propio panel de
 * RutaPro, menus) para no inventar carreras.
 */
object RideParser {

    // Dinero con simbolo: $2.50, $16.12  (en Ecuador el punto es decimal)
    private val MONEY = Regex("""\$\s?(\d{1,4}(?:[.,]\d{1,2})?)""")

    // "Aceptar por $2.50"  -> la tarifa mas confiable en inDrive
    private val ACCEPT_FOR = Regex("""aceptar por\s*\$?\s?(\d{1,4}(?:[.,]\d{1,2})?)""", RegexOption.IGNORE_CASE)

    // "6 min (1.4 km)" o "18 min 5,1 km" -> tiempo + distancia juntos
    private val TIME_DIST = Regex(
        """(\d{1,3})\s*min\s*[(\s]*\s*(\d{1,3}(?:[.,]\d)?)\s*(km|mi)""",
        RegexOption.IGNORE_CASE
    )
    // distancia con "~" (inDrive marca asi la distancia del viaje): "~7,5 km"
    private val TILDE_DIST = Regex("""~\s*(\d{1,3}(?:[.,]\d)?)\s*(km|mi)""", RegexOption.IGNORE_CASE)

    // Firmas de cada app para etiquetar bien la carrera.
    private val INDRIVE_SIG = Regex("""(ofrece tu tarifa|solicitud de viaje|aceptar por)""", RegexOption.IGNORE_CASE)

    // Palabra "viaje" = tramo pagado; el otro tramo es la recogida.
    private val TRIP_HINT = Regex("""viaje""", RegexOption.IGNORE_CASE)

    /** Pantallas que NO son ofertas: promos, el propio RutaPro, menus, etc. */
    private val NOT_AN_OFFER = Regex(
        "(oportunidad|promoci|adicionales|al completar|solicitudes de|premio|racha|" +
            "cuponera|ganancias|historial|semana pasada|resumen|billetera|saldo|retirar|" +
            "calificaci|incentivo|desaf|" +
            // el propio RutaPro / su popup:
            "rutapro|panel de control|filtros inteligentes|reiniciar contadores|" +
            "analizando|la tomaste|no conviene|conviene|mínimo que te conviene)",
        RegexOption.IGNORE_CASE
    )

    private const val MIN_FARE = 0.30
    private const val MAX_FARE = 500.0
    private const val MAX_PICKUP_KM = 25.0
    private const val MAX_TRIP_KM = 300.0

    data class Parsed(val offer: RideOffer, val app: String)

    fun parseWithApp(raw: String): Parsed? {
        val offer = parse(raw) ?: return null
        val app = if (INDRIVE_SIG.containsMatchIn(raw)) "inDrive" else "Uber"
        return Parsed(offer, app)
    }

    fun parse(raw: String): RideOffer? {
        if (raw.isBlank()) return null
        if (NOT_AN_OFFER.containsMatchIn(raw)) return null

        val fare = extractFare(raw) ?: return null

        // Todos los pares (tiempo, distancia) con su posicion en el texto.
        val segs = TIME_DIST.findAll(raw).map {
            Seg(
                min = it.groupValues[1].toDoubleOrNull() ?: 0.0,
                km = toKm(it.groupValues[2], it.groupValues[3]),
                pos = it.range.first
            )
        }.toMutableList()

        if (segs.isEmpty()) return null

        // El viaje: el que esta justo despues de la palabra "viaje", o el marcado con "~".
        val tildeKm = TILDE_DIST.find(raw)?.let { toKm(it.groupValues[1], it.groupValues[2]) }
        val tripPos = TRIP_HINT.findAll(raw).map { it.range.first }.toList()

        var trip: Seg? = null
        var pickup: Seg? = null

        if (tripPos.isNotEmpty()) {
            // El segmento cuya distancia aparece justo despues de "Viaje"
            trip = segs.filter { s -> tripPos.any { it < s.pos } }.minByOrNull { s ->
                tripPos.filter { it < s.pos }.minOf { s.pos - it }
            }
        }
        if (trip == null && tildeKm != null) {
            trip = segs.maxByOrNull { it.km } ?: segs.first()
        }
        if (trip == null) {
            // Sin pistas: el viaje suele ser el de mayor distancia.
            trip = segs.maxByOrNull { it.km }
        }
        pickup = segs.filter { it != trip }.minByOrNull { it.pos }

        var tripKm = trip?.km ?: 0.0
        // Si inDrive dio la distancia del viaje con "~", esa manda.
        if (tildeKm != null && tildeKm > 0) tripKm = tildeKm
        val tripMin = trip?.min ?: 0.0
        val pickupKm = pickup?.km ?: 0.0
        val pickupMin = pickup?.min ?: 0.0

        val offer = RideOffer(fare, tripKm, tripMin, pickupKm, pickupMin, raw)
        return if (isSane(offer)) offer else null
    }

    private data class Seg(val min: Double, val km: Double, val pos: Int)

    private fun isSane(o: RideOffer): Boolean {
        if (o.fare < MIN_FARE || o.fare > MAX_FARE) return false
        if (o.tripKm <= 0.0 || o.tripKm > MAX_TRIP_KM) return false
        if (o.pickupKm < 0.0 || o.pickupKm > MAX_PICKUP_KM) return false
        if (o.tripMin <= 0.0 && o.pickupMin <= 0.0) return false
        // Filtro anti-basura: pagos por km disparatados suelen ser lecturas malas.
        val perKmBruto = o.fare / o.tripKm
        if (perKmBruto > 8.0) return false
        return true
    }

    private fun extractFare(raw: String): Double? {
        // 1) inDrive: "Aceptar por $X" es la tarifa exacta.
        ACCEPT_FOR.find(raw)?.let { return normalize(it.groupValues[1]) }
        // 2) Uber y demas: el primer monto con decimales ($2.52), que es la tarifa.
        //    Se prefiere uno con decimales para no agarrar "$3" de un boton de oferta.
        val withDec = Regex("""\$\s?(\d{1,4}[.,]\d{2})""").find(raw)?.groupValues?.get(1)
        if (withDec != null) return normalize(withDec)
        return MONEY.find(raw)?.groupValues?.get(1)?.let { normalize(it) }
    }

    private fun normalize(s: String): Double =
        s.replace(",", ".").toDoubleOrNull() ?: 0.0

    private fun toKm(value: String, unit: String): Double {
        val v = value.replace(",", ".").toDoubleOrNull() ?: 0.0
        return if (unit.lowercase().startsWith("mi")) v * 1.60934 else v
    }
}
