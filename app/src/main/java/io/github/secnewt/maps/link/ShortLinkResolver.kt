package io.github.secnewt.maps.link

data class Hop(val status: Int, val location: String?)

fun interface HopFetcher { fun fetch(url: String): Hop }

enum class Reason { NO_INTERNET, LEFT_COMPANY, TOO_MANY_HOPS, NOT_HTTPS, NO_REDIRECT }

sealed interface Resolved {
    data class To(val url: String) : Resolved
    data class Failed(val reason: Reason) : Resolved
}

class ShortLinkResolver(private val fetcher: HopFetcher) {
    fun resolve(url: String): Resolved = Resolved.Failed(Reason.NO_REDIRECT)
}
