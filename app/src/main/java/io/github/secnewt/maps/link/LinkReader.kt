package io.github.secnewt.maps.link

import io.github.secnewt.maps.link.LinkResult.Place
import io.github.secnewt.maps.link.LinkResult.Search
import io.github.secnewt.maps.link.LinkResult.Short
import io.github.secnewt.maps.link.LinkResult.Unreadable
import java.net.URI
import java.net.URLDecoder

/** Reads Google Maps, Apple Maps and geo: links. No network: short links come back as [Short]. */
object LinkReader {

    private val LINK = Regex("""(https?://\S+|geo:\S+)""", RegexOption.IGNORE_CASE)
    private val GOOGLE_PIN = Regex("""!3d(-?\d+(?:\.\d+)?)!4d(-?\d+(?:\.\d+)?)""")
    private val GOOGLE_CENTRE = Regex("""@(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""")
    private val GOOGLE_PLACE_NAME = Regex("""/maps/place/([^/@?]+)""")
    private val COORDS = Regex("""^\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*$""")
    private val COORDS_WITH_LABEL = Regex("""^\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*(?:\((.*)\))?\s*$""")

    private val GOOGLE_HOSTS = setOf("google.com", "www.google.com", "maps.google.com")

    fun firstLink(text: String): String? =
        LINK.find(text)?.value?.trimEnd('.', ',', ')', ']', '!', '?', ';', ':', '"', '\'')

    fun isShortLink(url: String): Boolean {
        val uri = parse(url) ?: return false
        val path = uri.rawPath ?: ""
        return when (uri.host?.lowercase()) {
            "maps.app.goo.gl" -> true
            "goo.gl" -> path.startsWith("/maps")
            "maps.apple" -> path.startsWith("/p/")
            else -> false
        }
    }

    fun read(text: String): LinkResult {
        val link = firstLink(text) ?: return Unreadable
        if (link.startsWith("geo:", ignoreCase = true)) return readGeo(link)
        if (isShortLink(link)) return Short(link)
        val uri = parse(link) ?: return Unreadable
        if (uri.scheme?.lowercase() != "https" && uri.scheme?.lowercase() != "http") return Unreadable
        val host = uri.host?.lowercase() ?: return Unreadable
        return when {
            host in GOOGLE_HOSTS -> readGoogle(link, uri)
            host == "maps.apple.com" -> readApple(uri)
            else -> Unreadable
        }
    }

    private fun readGoogle(link: String, uri: URI): LinkResult {
        val decoded = decode(link)
        val name = GOOGLE_PLACE_NAME.find(uri.rawPath ?: "")?.groupValues?.get(1)?.let(::decode)?.ifBlank { null }
        GOOGLE_PIN.find(decoded)?.let { return place(it, name) }
        GOOGLE_CENTRE.find(decoded)?.let { return place(it, name) }
        val params = params(uri)
        val query = params["query"] ?: params["q"]
        if (query != null) {
            COORDS.find(query)?.let { return place(it, name) }
            if (query.isNotBlank()) return Search(query.trim(), name)
        }
        if (name != null) return Search(name, name)
        return Unreadable
    }

    private fun readApple(uri: URI): LinkResult {
        val params = params(uri)
        val name = (params["name"] ?: params["q"])?.ifBlank { null }
        val address = params["address"]?.ifBlank { null }
        val coords = params["coordinate"] ?: params["ll"]
        if (coords != null) COORDS.find(coords)?.let { return place(it, name, address) }
        val text = params["q"] ?: address
        return if (!text.isNullOrBlank()) Search(text.trim()) else Unreadable
    }

    private fun readGeo(link: String): LinkResult {
        val body = link.substringAfter(':')
        val coordsPart = body.substringBefore('?').substringBefore(';')
        val query = if ('?' in body) params(body.substringAfter('?'))["q"] else null
        val queryCoords = query?.let { COORDS_WITH_LABEL.find(it) }
        if (queryCoords != null) {
            return place(queryCoords, queryCoords.groupValues[3].ifBlank { null })
        }
        val coords = COORDS.find(coordsPart) ?: return Unreadable
        val lat = coords.groupValues[1].toDouble()
        val lng = coords.groupValues[2].toDouble()
        if (lat == 0.0 && lng == 0.0) {
            return if (!query.isNullOrBlank()) Search(query.trim()) else Unreadable
        }
        return place(coords, null)
    }

    private fun place(match: MatchResult, name: String?, address: String? = null): LinkResult {
        val lat = match.groupValues[1].toDouble()
        val lng = match.groupValues[2].toDouble()
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return Unreadable
        return Place(lat, lng, name, address)
    }

    private fun params(uri: URI): Map<String, String> = params(uri.rawQuery ?: "")

    private fun params(rawQuery: String): Map<String, String> =
        rawQuery.split('&').filter { it.isNotEmpty() }.associate { pair ->
            decode(pair.substringBefore('=')) to decode(pair.substringAfter('=', ""))
        }

    private fun decode(s: String): String = try {
        URLDecoder.decode(s, Charsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        s
    }

    private fun parse(url: String): URI? = try {
        URI(url)
    } catch (_: Exception) {
        null
    }
}
