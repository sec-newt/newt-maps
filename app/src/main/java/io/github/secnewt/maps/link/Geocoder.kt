package io.github.secnewt.maps.link

sealed interface GeoResult {
    data class Found(val lat: Double, val lng: Double) : GeoResult
    data object NotFound : GeoResult
    data object NoInternet : GeoResult
}

/** Looks up text that has no coordinates. Nominatim today; search on zeus later. */
interface Geocoder { fun find(text: String): GeoResult }

private val HOUSE_NUMBER = Regex("""(?<![\w-])\d+[A-Za-z]?\s+\S""")

/**
 * "The Catch 5949 Broadway Blvd" → "5949 Broadway Blvd": the text from the first house
 * number on, so a search that failed on the business name can be retried on the address.
 * Null when the text already starts with the number or has none.
 */
fun addressPart(text: String): String? {
    val match = HOUSE_NUMBER.find(text) ?: return null
    if (match.range.first == 0) return null
    return text.substring(match.range.first).trim()
}
