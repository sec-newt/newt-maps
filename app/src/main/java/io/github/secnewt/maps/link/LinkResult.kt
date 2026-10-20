package io.github.secnewt.maps.link

/** What a maps link turned out to contain. */
sealed interface LinkResult {
    /** A location. [name] and [address] are shown in the picker when the link has them. */
    data class Place(
        val lat: Double,
        val lng: Double,
        val name: String? = null,
        val address: String? = null,
    ) : LinkResult

    /** No coordinates, only text to look up ("4402 Broadway Blvd"). */
    data class Search(val text: String, val name: String? = null) : LinkResult

    /** A short link (maps.app.goo.gl, maps.apple/p/…) that must be followed first. */
    data class Short(val url: String) : LinkResult

    data object Unreadable : LinkResult
}
