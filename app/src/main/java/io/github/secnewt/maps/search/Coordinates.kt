package io.github.secnewt.maps.search

/**
 * Reads coordinates typed or pasted into the search box, so they become a pin instead of
 * a search Photon can't answer:
 * - `32.9126, -96.6389` (Google's "copy coordinates"), `32.9126 -96.6389`, `(32.9126, -96.6389)`
 * - `32.9126° N, 96.6389° W` and `32.9126N 96.6389W`
 * - `32°54'45.4"N 96°38'20.0"W` (degrees, minutes, seconds)
 *
 * At least one number needs a decimal point (or a N/S/E/W), so "75 40" stays a search.
 */
object Coordinates {
    private const val NUMBER = """([+-]?\d{1,3}(?:\.\d+)?)"""
    private val DECIMAL = Regex(
        """^\(?\s*$NUMBER\s*°?\s*([NSns])?\s*(?:,\s*|\s+)$NUMBER\s*°?\s*([EWew])?\s*\)?$""",
    )
    private const val DMS_PART = """(\d{1,3})\s*°\s*(\d{1,2})\s*['′]\s*(?:(\d{1,2}(?:\.\d+)?)\s*(?:["″]|'')\s*)?"""
    private val DMS = Regex("""^$DMS_PART([NSns])\s*,?\s*$DMS_PART([EWew])$""")

    fun parse(text: String): Pair<Double, Double>? {
        val clean = text.trim()
        return decimal(clean) ?: dms(clean)
    }

    private fun decimal(text: String): Pair<Double, Double>? {
        val m = DECIMAL.matchEntire(text) ?: return null
        val (latText, ns, lngText, ew) = m.destructured
        if ('.' !in latText && '.' !in lngText && ns.isEmpty() && ew.isEmpty()) return null
        // With a hemisphere letter the number itself shouldn't carry a sign.
        if ((ns.isNotEmpty() && latText.first() in "+-") || (ew.isNotEmpty() && lngText.first() in "+-")) return null
        val lat = signed(latText.toDouble(), ns, 'S')
        val lng = signed(lngText.toDouble(), ew, 'W')
        return inRange(lat, lng)
    }

    private fun dms(text: String): Pair<Double, Double>? {
        val g = DMS.matchEntire(text)?.groupValues ?: return null
        val lat = signed(degrees(g[1], g[2], g[3]) ?: return null, g[4], 'S')
        val lng = signed(degrees(g[5], g[6], g[7]) ?: return null, g[8], 'W')
        return inRange(lat, lng)
    }

    private fun degrees(d: String, m: String, s: String): Double? {
        val minutes = m.toDouble()
        val seconds = s.ifEmpty { "0" }.toDouble()
        if (minutes >= 60 || seconds >= 60) return null
        return d.toDouble() + minutes / 60 + seconds / 3600
    }

    private fun signed(value: Double, hemisphere: String, negative: Char) =
        if (hemisphere.equals(negative.toString(), ignoreCase = true)) -value else value

    private fun inRange(lat: Double, lng: Double) =
        (lat to lng).takeIf { lat in -90.0..90.0 && lng in -180.0..180.0 }
}
