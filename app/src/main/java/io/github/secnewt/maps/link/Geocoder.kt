package io.github.secnewt.maps.link

sealed interface GeoResult {
    data class Found(val lat: Double, val lng: Double) : GeoResult
    data object NotFound : GeoResult
    data object NoInternet : GeoResult
}

/** Looks up text that has no coordinates. Nominatim today; search on zeus later. */
interface Geocoder { fun find(text: String): GeoResult }

fun addressPart(text: String): String? = null
