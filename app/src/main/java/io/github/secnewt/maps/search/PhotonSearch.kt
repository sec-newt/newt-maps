package io.github.secnewt.maps.search

import io.github.secnewt.maps.link.HttpStatusException
import io.github.secnewt.maps.link.HttpTransport
import io.github.secnewt.maps.link.Transport
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** One place a search found. [miles] is how far it is from where I am (or the map), when known. */
data class SearchHit(val name: String, val address: String, val lat: Double, val lng: Double, val miles: Double?)

sealed interface SearchOutcome {
    /** [shortenedTo] is set when nothing matched the whole search and these are for fewer words. */
    data class Results(val hits: List<SearchHit>, val shortenedTo: String? = null) : SearchOutcome
    data object NotFound : SearchOutcome
    data object NoInternet : SearchOutcome
    data object ServerProblem : SearchOutcome
}

/**
 * The search box: Photon (OpenStreetMap data, forgiving about spelling and apostrophes),
 * asked to prefer places near [near]. Only the search text and that point are sent.
 *
 * Photon's public server is komoot's; the same program can run at home later, which is
 * just a different [baseUrl].
 */
class PhotonSearch(
    private val transport: Transport = HttpTransport(),
    private val baseUrl: String = BASE_URL,
) {
    fun search(text: String, near: Pair<Double, Double>?): SearchOutcome {
        val first = ask(text, near)
        if (first !is SearchOutcome.Results || first.hits.isNotEmpty()) return first.orNotFound()
        // Nothing at all: drop words from the end ("Alamo Drafthouse Lake Highlands" →
        // "Alamo Drafthouse"), keeping at least two, and take the first try with something nearby.
        var words = text.trim().split(Regex("\\s+"))
        repeat(MAX_DROPS) {
            if (words.size <= 2) return SearchOutcome.NotFound
            words = words.dropLast(1)
            val shorter = words.joinToString(" ")
            when (val next = ask(shorter, near)) {
                is SearchOutcome.Results -> if (next.hits.any { near == null || it.miles!! <= NEARBY_MILES }) return next.copy(shortenedTo = shorter)
                else -> return next
            }
        }
        return SearchOutcome.NotFound
    }

    private fun SearchOutcome.orNotFound() = if (this is SearchOutcome.Results && hits.isEmpty()) SearchOutcome.NotFound else this

    private fun ask(text: String, near: Pair<Double, Double>?): SearchOutcome {
        val url = buildString {
            append(baseUrl).append("?q=").append(URLEncoder.encode(text, Charsets.UTF_8))
            append("&limit=").append(LIMIT).append("&lang=en")
            if (near != null) append("&lat=").append(near.first).append("&lon=").append(near.second)
        }
        val body = try {
            transport.get(url)
        } catch (_: HttpStatusException) {
            return SearchOutcome.ServerProblem
        } catch (_: IOException) {
            return SearchOutcome.NoInternet
        }
        return try {
            SearchOutcome.Results(tidy(parse(body, near), near))
        } catch (_: JSONException) {
            SearchOutcome.NotFound
        }
    }

    private fun parse(body: String, near: Pair<Double, Double>?): List<SearchHit> {
        val features = JSONObject(body).getJSONArray("features")
        return (0 until features.length()).mapNotNull { i ->
            val feature = features.getJSONObject(i)
            val p = feature.getJSONObject("properties")
            if (!p.optString("countrycode").equals("US", ignoreCase = true)) return@mapNotNull null
            val coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates")
            val lng = coordinates.getDouble(0)
            val lat = coordinates.getDouble(1)
            val street = listOfNotNull(p.text("housenumber"), p.text("street")).joinToString(" ").ifEmpty { null }
            val city = p.text("city") ?: p.text("county")
            val name = p.text("name")
            val hit = if (name != null) {
                SearchHit(name, listOfNotNull(street, city).joinToString(", "), lat, lng, null)
            } else {
                SearchHit(street ?: city ?: return@mapNotNull null, if (street != null) city.orEmpty() else "", lat, lng, null)
            }
            hit.copy(miles = near?.let { miles(it.first, it.second, lat, lng) })
        }
    }

    /** Drop repeats of the same place, and far-away results when there are nearby ones. */
    private fun tidy(hits: List<SearchHit>, near: Pair<Double, Double>?): List<SearchHit> {
        val unique = hits.fold(listOf<SearchHit>()) { kept, hit ->
            if (kept.any { it.name == hit.name && miles(it.lat, it.lng, hit.lat, hit.lng) < 0.1 }) kept else kept + hit
        }
        if (near == null) return unique
        val nearby = unique.filter { it.miles!! <= NEARBY_MILES }
        return nearby.ifEmpty { unique }
    }

    private fun JSONObject.text(key: String): String? = optString(key).trim().takeIf { it.isNotEmpty() }

    companion object {
        const val BASE_URL = "https://photon.komoot.io/api/"
        private const val LIMIT = 10
        private const val MAX_DROPS = 3
        private const val NEARBY_MILES = 60.0

        /** Straight-line miles between two points. */
        fun miles(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val r = Math.toRadians(1.0)
            val a = sin((lat2 - lat1) * r / 2).pow(2) + cos(lat1 * r) * cos(lat2 * r) * sin((lng2 - lng1) * r / 2).pow(2)
            return 2 * 3958.8 * asin(sqrt(a))
        }
    }
}
