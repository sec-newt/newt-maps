package io.github.secnewt.maps.link

fun interface Transport { fun get(url: String): String }

class NominatimGeocoder(
    private val transport: Transport,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = Thread::sleep,
) : Geocoder {
    override fun find(text: String): GeoResult = GeoResult.NotFound
}
