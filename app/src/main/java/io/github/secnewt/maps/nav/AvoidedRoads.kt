package io.github.secnewt.maps.nav

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** A spot on the map to route around: Valhalla's exclude_locations takes points, not names. */
data class Spot(val lat: Double, val lng: Double)

/**
 * A road I don't want to drive on, as the spots I marked on it. Each long-press adds a few
 * spots (one per lane group: main lanes, express lanes, each direction), so a long stretch
 * is covered by marking it in a few places. [on] is whether trips use it right now.
 */
data class AvoidedRoad(val name: String, val spots: List<Spot>, val on: Boolean = true)

/**
 * Everything a route is asked to avoid, and whether it should be the shortest (fewest
 * miles) instead of the fastest. A new set means a new route.
 */
data class RouteOptions(
    val avoidTolls: Boolean,
    val avoidHighways: Boolean = false,
    val avoid: List<Spot> = emptyList(),
    val shortest: Boolean = false,
)

/**
 * The roads I avoid, in one small JSON file in the app's private storage (like saved places,
 * and written the same careful way). Nothing here is sent anywhere except the route server.
 */
class AvoidedRoads(private val file: File) {

    companion object {
        /** Valhalla's default max_exclude_locations; more and it refuses every route. */
        const val MAX_SPOTS = 50
    }

    var roads: List<AvoidedRoad> = emptyList()
        private set

    init { load() }

    /**
     * Avoid [road] too. Spots on a road already in the list join it (and turn it on).
     * False, and nothing changes, when that would ask Valhalla to avoid more than [MAX_SPOTS].
     */
    fun add(road: AvoidedRoad): Boolean {
        val same = roads.firstOrNull { it.name == road.name }
        val changed = if (same == null) {
            roads + road
        } else {
            roads.map { if (it === same) it.copy(spots = (it.spots + road.spots).distinct(), on = true) else it }
        }
        if (changed.filter { it.on }.sumOf { it.spots.size } > MAX_SPOTS) return false
        roads = changed
        write()
        return true
    }

    /** Turn a road on or off for trips. False when turning it on would pass [MAX_SPOTS]. */
    fun setOn(name: String, on: Boolean): Boolean {
        val changed = roads.map { if (it.name == name) it.copy(on = on) else it }
        if (on && changed.filter { it.on }.sumOf { it.spots.size } > MAX_SPOTS) return false
        roads = changed
        write()
        return true
    }

    fun remove(name: String) {
        roads = roads.filterNot { it.name == name }
        write()
    }

    /** The spots trips avoid right now. */
    fun activeSpots(): List<Spot> = roads.filter { it.on }.flatMap { it.spots }

    private fun load() {
        val text = try { if (file.exists()) file.readText() else return } catch (_: IOException) { return }
        try {
            val list = JSONObject(text).optJSONArray("roads") ?: JSONArray()
            roads = (0 until list.length()).mapNotNull { i -> list.optJSONObject(i)?.let(::road) }
        } catch (_: JSONException) {
            file.copyTo(File(file.path + ".damaged"), overwrite = true)
        }
    }

    private fun road(json: JSONObject): AvoidedRoad? = try {
        val spots = json.getJSONArray("spots")
        AvoidedRoad(
            json.getString("name"),
            (0 until spots.length()).map { spots.getJSONObject(it).let { s -> Spot(s.getDouble("lat"), s.getDouble("lng")) } },
            json.optBoolean("on", true),
        )
    } catch (_: JSONException) {
        null
    }

    private fun write() {
        val list = JSONArray(roads.map { road ->
            JSONObject().put("name", road.name).put("on", road.on)
                .put("spots", JSONArray(road.spots.map { JSONObject().put("lat", it.lat).put("lng", it.lng) }))
        })
        val text = JSONObject().put("version", 1).put("roads", list).toString(2)
        val temp = File(file.path + ".tmp")
        temp.writeText(text)
        if (!temp.renameTo(file)) { file.writeText(text); temp.delete() }
    }
}

/**
 * Which road is at a spot, from Valhalla's /locate. Kept free of the network so it can be
 * tested: [roadAt] reads the server's answer.
 */
object RoadLocator {

    /** How far around the press to look for the road's other lanes, in meters. */
    const val REACH = 50

    fun request(lat: Double, lng: Double): String = JSONObject()
        .put("locations", JSONArray().put(JSONObject().put("lat", lat).put("lon", lng).put("radius", REACH)))
        .put("costing", "auto")
        .put("verbose", true)
        .toString()

    /**
     * The nearest named road in a /locate answer, with a spot on each nearby piece of the
     * same road. "Same" means the same name (the other direction), or the name plus more
     * words on a road of the same class: pressing I 635 also catches "I 635 TEXPR" (the
     * express lanes beside it, also a motorway), but not "Lyndon B. Johnson Service Road"
     * or an "I 635 Frontage Road" (smaller roads: the way around), nor "I 635E". Null if no
     * named road is there, or the answer can't be read.
     */
    fun roadAt(answer: String): AvoidedRoad? = try {
        val edges = JSONArray(answer).optJSONObject(0)?.optJSONArray("edges")
        val all = (0 until (edges?.length() ?: 0)).mapNotNull { edges!!.optJSONObject(it) }
            .filter { it.has("correlated_lat") && it.has("correlated_lon") }
            .sortedBy { it.optDouble("distance", Double.MAX_VALUE) }
        all.firstOrNull { names(it).isNotEmpty() }?.let { nearest ->
            val key = names(nearest).first()
            val kind = kind(nearest)
            val spots = all.filter { edge -> names(edge).any { it == key || (it.startsWith("$key ") && kind(edge) == kind) } }
                .map { Spot(round(it.getDouble("correlated_lat")), round(it.getDouble("correlated_lon"))) }
                .distinct()
            AvoidedRoad(names(nearest).take(2).joinToString(" · "), spots)
        }
    } catch (_: JSONException) {
        null
    }

    private fun kind(edge: JSONObject): String? = edge.optJSONObject("edge")?.optJSONObject("classification")?.optString("classification")

    private fun names(edge: JSONObject): List<String> {
        val list = edge.optJSONObject("edge_info")?.optJSONArray("names") ?: return emptyList()
        return (0 until list.length()).map { list.optString(it) }.filter { it.isNotBlank() }
    }

    private fun round(value: Double) = Math.round(value * 1e6) / 1e6
}
