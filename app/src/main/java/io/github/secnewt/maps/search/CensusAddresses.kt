package io.github.secnewt.maps.search

import io.github.secnewt.maps.link.HttpTransport
import io.github.secnewt.maps.link.Transport
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * House-number addresses from the US Census Bureau's geocoder (free, no key). It knows the
 * address ranges of every US street, so it finds addresses OpenStreetMap doesn't have — in
 * Garland, OSM has only about 1 in 8. It sees only the address text and a city/state.
 *
 * It won't look anything up without a city, state or ZIP, so when [street] has none it's
 * tried with the city I'm in, then with just my state (which finds "1500 Marilla"
 * from Garland, though it's in Dallas).
 */
class CensusAddresses(
    private val transport: Transport = HttpTransport(),
    private val baseUrl: String = BASE_URL,
) {
    /** [area] is asked for only when needed: my (city, state). Any failure is just no results. */
    fun find(street: String, area: () -> Pair<String?, String?>?): List<SearchHit> {
        ask(street).takeIf { it.isNotEmpty() }?.let { return it }
        val (city, state) = area() ?: return emptyList()
        if (state == null) return emptyList()
        if (city != null) ask("$street, $city, $state").takeIf { it.isNotEmpty() }?.let { return it }
        return ask("$street, $state")
    }

    private fun ask(address: String): List<SearchHit> {
        val url = "$baseUrl?address=${URLEncoder.encode(address, Charsets.UTF_8)}&benchmark=Public_AR_Current&format=json"
        return try {
            val matches = JSONObject(transport.get(url)).getJSONObject("result").getJSONArray("addressMatches")
            (0 until matches.length()).mapNotNull { i ->
                val match = matches.getJSONObject(i)
                val at = match.getJSONObject("coordinates")
                hit(match.getString("matchedAddress"), at.getDouble("y"), at.getDouble("x"))
            }
        } catch (e: IOException) {
            android.util.Log.w("CensusAddresses", "lookup failed", e)
            emptyList()
        } catch (_: JSONException) {
            emptyList()
        }
    }

    companion object {
        const val BASE_URL = "https://geocoding.geo.census.gov/geocoder/locations/onelineaddress"

        /** "1500 MARILLA ST, DALLAS, TX, 75201" → "1500 Marilla St" / "Dallas, TX 75201". */
        internal fun hit(matched: String, lat: Double, lng: Double): SearchHit? {
            val parts = matched.split(",").map(String::trim)
            val street = parts.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return null
            val city = parts.getOrNull(1)?.let(::words)
            val stateZip = parts.drop(2).joinToString(" ").ifEmpty { null }
            return SearchHit(words(street), listOfNotNull(city, stateZip).joinToString(", "), lat, lng, null)
        }

        private fun words(text: String) =
            text.lowercase().split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
    }
}
