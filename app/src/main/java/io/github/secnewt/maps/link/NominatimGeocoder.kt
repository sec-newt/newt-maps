package io.github.secnewt.maps.link

import org.json.JSONArray
import org.json.JSONException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One GET returning the body. Throws HttpStatusException on a non-2xx answer, or IOException when offline. */
fun interface Transport { fun get(url: String): String }

/** A non-2xx answer from Nominatim (429 rate-limited, 5xx, 4xx) — not the same as being offline. */
class HttpStatusException(val status: Int) : IOException("Nominatim answered $status")

/**
 * OpenStreetMap's search server. Only the search text is sent, at most once a second
 * (Nominatim's usage policy), with a User-Agent naming the app.
 */
class NominatimGeocoder(
    private val transport: Transport = HttpTransport(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = Thread::sleep,
) : Geocoder {

    private var lastRequestAt: Long? = null

    override fun find(text: String): GeoResult {
        val first = search(text)
        if (first != GeoResult.NotFound) return first
        val retry = addressPart(text) ?: return GeoResult.NotFound
        return search(retry)
    }

    @Synchronized
    private fun search(text: String): GeoResult {
        lastRequestAt?.let { last ->
            val wait = MIN_GAP_MS - (clock() - last)
            if (wait > 0) sleep(wait)
        }
        lastRequestAt = clock()
        val url = "$BASE_URL?q=${URLEncoder.encode(text, Charsets.UTF_8)}&format=jsonv2&limit=1&countrycodes=us"
        val body = try {
            transport.get(url)
        } catch (_: IOException) {
            return GeoResult.NoInternet
        }
        return try {
            val results = JSONArray(body)
            if (results.length() == 0) return GeoResult.NotFound
            val top = results.getJSONObject(0)
            GeoResult.Found(top.getString("lat").toDouble(), top.getString("lon").toDouble())
        } catch (_: JSONException) {
            GeoResult.NotFound
        } catch (_: NumberFormatException) {
            GeoResult.NotFound
        }
    }

    companion object {
        const val BASE_URL = "https://nominatim.openstreetmap.org/search"
        private const val MIN_GAP_MS = 1_000L
    }
}

class HttpTransport : Transport {
    override fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", HttpHopFetcher.USER_AGENT)
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("Nominatim answered $status")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object { private const val TIMEOUT_MS = 10_000 }
}
