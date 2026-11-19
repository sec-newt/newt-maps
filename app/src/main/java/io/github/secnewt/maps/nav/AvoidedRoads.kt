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

/** Everything a route is asked to avoid. A new set means a new route. */
data class RouteOptions(
    val avoidTolls: Boolean,
    val avoidHighways: Boolean = false,
    val avoid: List<Spot> = emptyList(),
)

/**
 * The roads I avoid, in one small JSON file in the app's private storage (like saved places,
 * and written the same careful way). Nothing here is sent anywhere except the route server.
 */
class AvoidedRoads(private val file: File) {

    var roads: List<AvoidedRoad> = emptyList()
        private set

    init { load() }

    /** Avoid [road] too. Spots on a road already in the list join it (and turn it on). */
    fun add(road: AvoidedRoad) {
        val same = roads.firstOrNull { it.name == road.name }
        roads = if (same == null) {
            roads + road
        } else {
            roads.map { if (it === same) it.copy(spots = (it.spots + road.spots).distinct(), on = true) else it }
        }
        write()
    }

    fun setOn(name: String, on: Boolean) {
        roads = roads.map { if (it.name == name) it.copy(on = on) else it }
        write()
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
     * same road. "Same" means a name starting with the nearest one's first name: pressing
     * I 635 also catches "I 635 TEXPR" (the express lanes next to it) and the other direction,
     * but not "Lyndon B. Johnson Service Road", which is the way around. Null if no named
     * road is there.
     */
    fun roadAt(answer: String): AvoidedRoad? {
        val edges = try {
            JSONArray(answer).optJSONObject(0)?.optJSONArray("edges") ?: return null
        } catch (_: JSONException) {
            return null
        }
        val all = (0 until edges.length()).mapNotNull { edges.optJSONObject(it) }
            .sortedBy { it.optDouble("distance", Double.MAX_VALUE) }
        val nearest = all.firstOrNull { names(it).isNotEmpty() } ?: return null
        val key = names(nearest).first()
        val spots = all.filter { edge -> names(edge).any { it.startsWith(key) } }
            .map { Spot(round(it.getDouble("correlated_lat")), round(it.getDouble("correlated_lon"))) }
            .distinct()
        return AvoidedRoad(names(nearest).take(2).joinToString(" · "), spots)
    }

    private fun names(edge: JSONObject): List<String> {
        val list = edge.optJSONObject("edge_info")?.optJSONArray("names") ?: return emptyList()
        return (0 until list.length()).map { list.optString(it) }.filter { it.isNotBlank() }
    }

    private fun round(value: Double) = Math.round(value * 1e6) / 1e6
}
