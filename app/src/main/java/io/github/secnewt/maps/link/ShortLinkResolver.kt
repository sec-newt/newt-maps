package io.github.secnewt.maps.link

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class Hop(val status: Int, val location: String?)

/** One request to a short link, without following the redirect. Throws IOException when offline. */
fun interface HopFetcher { fun fetch(url: String): Hop }

enum class Reason { NO_INTERNET, LEFT_COMPANY, TOO_MANY_HOPS, NOT_HTTPS, NO_REDIRECT }

sealed interface Resolved {
    data class To(val url: String) : Resolved
    data class Failed(val reason: Reason) : Resolved
}

/**
 * Follows a short link's redirects by hand until the address is no longer a short link.
 * Every hop must be https and stay with the link's company; the final page is never fetched.
 */
class ShortLinkResolver(private val fetcher: HopFetcher = HttpHopFetcher()) {

    fun resolve(url: String): Resolved {
        val startUrl = httpsEntry(url) ?: return Resolved.Failed(Reason.NOT_HTTPS)
        val company = companyOf(startUrl) ?: return Resolved.Failed(Reason.LEFT_COMPANY)
        var current = startUrl
        repeat(MAX_HOPS) {
            val hop = try {
                fetcher.fetch(current)
            } catch (_: IOException) {
                return Resolved.Failed(Reason.NO_INTERNET)
            }
            val location = hop.location
            if (hop.status !in 300..399 || location == null) return Resolved.Failed(Reason.NO_REDIRECT)
            val next = try {
                URI(current).resolve(location).toString()
            } catch (_: Exception) {
                return Resolved.Failed(Reason.NO_REDIRECT)
            }
            val nextUri = URI(next)
            if (nextUri.scheme?.lowercase() != "https") return Resolved.Failed(Reason.NOT_HTTPS)
            if (nextUri.host?.lowercase() !in company) return Resolved.Failed(Reason.LEFT_COMPANY)
            if (!LinkReader.isShortLink(next)) return Resolved.To(next)
            current = next
        }
        return Resolved.Failed(Reason.TOO_MANY_HOPS)
    }

    /**
     * Accepts an https entry URL as-is; upgrades a plain http entry URL to https (same
     * host/path/query — plain HTTP is never sent). Any other scheme, or no scheme, is rejected
     * before any fetch happens.
     */
    private fun httpsEntry(url: String): String? {
        val uri = try { URI(url) } catch (_: Exception) { return null }
        return when (uri.scheme?.lowercase()) {
            "https" -> url
            "http" -> try {
                URI("https", uri.userInfo, uri.host, uri.port, uri.path, uri.query, uri.fragment).toString()
            } catch (_: Exception) {
                null
            }
            else -> null
        }
    }

    private fun companyOf(url: String): Set<String>? {
        val host = try { URI(url).host?.lowercase() } catch (_: Exception) { null } ?: return null
        return listOf(GOOGLE, APPLE).firstOrNull { host in it }
    }

    companion object {
        const val MAX_HOPS = 5
        val GOOGLE = setOf("maps.app.goo.gl", "goo.gl", "google.com", "www.google.com", "maps.google.com")
        val APPLE = setOf("maps.apple", "maps.apple.com")
    }
}

/** The real network: one GET, redirects off, body never read. */
class HttpHopFetcher : HopFetcher {
    override fun fetch(url: String): Hop {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            return Hop(connection.responseCode, connection.getHeaderField("Location"))
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TIMEOUT_MS = 10_000
        const val USER_AGENT = "NewtMaps (https://github.com/sec-newt/newt-maps)"
    }
}
