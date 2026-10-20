package io.github.secnewt.maps.link

enum class Why { NO_LINK, NO_LOCATION, NO_INTERNET, NOTHING_FOUND }

sealed interface Outcome {
    data class Found(val place: LinkResult.Place) : Outcome
    data class NotFound(val why: Why, val link: String?) : Outcome
}

class LinkPipeline(private val resolver: ShortLinkResolver, private val geocoder: Geocoder) {
    fun run(text: String): Outcome = Outcome.NotFound(Why.NO_LINK, null)
}
