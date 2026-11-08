package io.github.secnewt.maps.saved

import io.github.secnewt.maps.search.PhotonSearch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** A place I saved, under the name I gave it. */
data class SavedPlace(val name: String, val lat: Double, val lng: Double, val address: String? = null)

/**
 * Home and my saved places, kept in one small JSON file in the app's private storage.
 * Nothing here is ever sent anywhere. Every change is written straight away (to a temp file,
 * then renamed, so a crash mid-write can't lose the list).
 */
class SavedPlaces(private val file: File) {

    var home: SavedPlace? = null
        private set
    var places: List<SavedPlace> = emptyList()
        private set

    init { load() }

    /** The saved place at this spot (within about 30 m), if any. */
    fun at(lat: Double, lng: Double): SavedPlace? = places.firstOrNull { near(it, lat, lng) }

    fun isHome(lat: Double, lng: Double): Boolean = home?.let { near(it, lat, lng) } ?: false

    fun save(place: SavedPlace) {
        places = places.filterNot { near(it, place.lat, place.lng) } + place
        write()
    }

    fun rename(place: SavedPlace, name: String) {
        val clean = name.trim().ifEmpty { return }
        places = places.map { if (it == place) it.copy(name = clean) else it }
        write()
    }

    fun remove(place: SavedPlace) {
        places = places - place
        write()
    }

    fun setHome(place: SavedPlace) {
        home = place
        write()
    }

    fun clearHome() {
        home = null
        write()
    }

    /**
     * Saved places whose name or address has every word I typed, ignoring case and
     * apostrophes ("moms" finds "Mom's"). "home" finds home.
     */
    fun matching(text: String): List<SavedPlace> {
        val words = squash(text).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val everything = listOfNotNull(home?.copy(name = "Home")) + places
        return everything.filter { place ->
            val haystack = squash("${place.name} ${place.address.orEmpty()}")
            words.all { it in haystack }
        }
    }

    /** Home first, then the rest in the order they were saved. */
    fun all(): List<SavedPlace> = listOfNotNull(home?.copy(name = "Home")) + places

    private fun squash(text: String) = text.lowercase().replace(Regex("['’]"), "").replace(Regex("\\s+"), " ").trim()

    private fun near(place: SavedPlace, lat: Double, lng: Double) = PhotonSearch.miles(place.lat, place.lng, lat, lng) < SAME_PLACE_MILES

    private fun load() {
        val text = try { if (file.exists()) file.readText() else return } catch (_: IOException) { return }
        try {
            val json = JSONObject(text)
            home = json.optJSONObject("home")?.let(::place)
            val list = json.optJSONArray("places") ?: JSONArray()
            places = (0 until list.length()).mapNotNull { list.optJSONObject(it)?.let(::place) }
        } catch (_: JSONException) {
            // A damaged file: start empty, but keep the old one for a look later.
            file.copyTo(File(file.path + ".damaged"), overwrite = true)
        }
    }

    private fun place(json: JSONObject): SavedPlace? = try {
        SavedPlace(json.getString("name"), json.getDouble("lat"), json.getDouble("lng"), json.optString("address").ifEmpty { null })
    } catch (_: JSONException) {
        null
    }

    private fun json(place: SavedPlace) = JSONObject()
        .put("name", place.name).put("lat", place.lat).put("lng", place.lng)
        .apply { place.address?.let { put("address", it) } }

    private fun write() {
        val json = JSONObject().put("version", 1).put("places", JSONArray(places.map(::json)))
        home?.let { json.put("home", json(it)) }
        val temp = File(file.path + ".tmp")
        temp.writeText(json.toString(2))
        if (!temp.renameTo(file)) { file.writeText(json.toString(2)); temp.delete() }
    }

    companion object {
        private const val SAME_PLACE_MILES = 0.02 // about 30 m
    }
}
