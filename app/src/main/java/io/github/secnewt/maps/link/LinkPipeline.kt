package io.github.secnewt.maps.link

enum class Why { NO_LINK, NO_LOCATION, NO_INTERNET, NOTHING_FOUND }

sealed interface Outcome {
    data class Found(val place: LinkResult.Place) : Outcome
    data class NotFound(val why: Why, val link: String?) : Outcome
}

/** The four steps from PLAN.md: read → follow short link → look up text → give up with the link. */
class LinkPipeline(
    private val resolver: ShortLinkResolver = ShortLinkResolver(),
    private val geocoder: Geocoder = NominatimGeocoder.shared,
) {
    fun run(text: String): Outcome {
        val link = LinkReader.firstLink(text) ?: return Outcome.NotFound(Why.NO_LINK, null)
        var result = LinkReader.read(link)
        if (result is LinkResult.Short) {
            result = when (val resolved = resolver.resolve(result.url)) {
                is Resolved.To -> LinkReader.read(resolved.url)
                is Resolved.Failed -> return Outcome.NotFound(
                    if (resolved.reason == Reason.NO_INTERNET) Why.NO_INTERNET else Why.NO_LOCATION,
                    link,
                )
            }
        }
        return when (result) {
            is LinkResult.Place -> Outcome.Found(result)
            is LinkResult.Search -> when (val found = geocoder.find(result.text)) {
                is GeoResult.Found -> Outcome.Found(LinkResult.Place(found.lat, found.lng, name = result.name ?: result.text))
                GeoResult.NotFound -> Outcome.NotFound(Why.NOTHING_FOUND, link)
                GeoResult.NoInternet -> Outcome.NotFound(Why.NO_INTERNET, link)
            }
            is LinkResult.Short, LinkResult.Unreadable -> Outcome.NotFound(Why.NO_LOCATION, link)
        }
    }
}
