package io.github.secnewt.maps.map

import org.json.JSONArray
import org.json.JSONObject

/**
 * The map's look, built for low vision: bright, thick roads on near-black (or dark roads on
 * light), street names big and bold with a heavy outline so they read over anything, and
 * little else. No shop icons, no building clutter until fully zoomed in.
 *
 * Map data comes from OpenFreeMap (free, no account, OpenStreetMap data in the OpenMapTiles
 * layout). Later the same layers come from my own server; only [TILES] changes.
 */
object MapStyle {

    const val TILES = "https://tiles.openfreemap.org/planet"
    const val GLYPHS = "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf"

    /** Layers that can be tapped to say which street it is. */
    const val STREET_HIT_LAYER = "street-hit"

    /** The place a search or link found, drawn as a big dot. */
    const val PLACE_SOURCE = "place"

    /** The route being previewed or driven, drawn as a thick green line. */
    const val ROUTE_SOURCE = "route"

    private class Colors(
        val background: String, val water: String, val park: String, val building: String,
        val highway: String, val main: String, val street: String, val small: String,
        val label: String, val halo: String, val placeLabel: String, val place: String, val placeRing: String,
        val route: String,
    )

    private val Dark = Colors(
        background = "#050608", water = "#16304F", park = "#0F2216", building = "#1C1F2B",
        highway = "#FF9E64", main = "#E0AF68", street = "#C0CAF5", small = "#6B7299",
        label = "#FFFFFF", halo = "#000000", placeLabel = "#7DCFFF", place = "#FF7A93", placeRing = "#FFFFFF",
        route = "#9ECE6A",
    )

    private val Light = Colors(
        background = "#F2F3F7", water = "#9CC3EE", park = "#CFE6C4", building = "#DADCE5",
        highway = "#B15C00", main = "#8C6C3E", street = "#343B58", small = "#8990B3",
        label = "#0B0C10", halo = "#FFFFFF", placeLabel = "#0F4B6E", place = "#D20F39", placeRing = "#FFFFFF",
        route = "#1E7A34",
    )

    /**
     * The style as MapLibre JSON. [textScale] is the phone's font size setting, so street
     * names grow with the rest of the app.
     */
    fun json(dark: Boolean, textScale: Float = 1f): String {
        val c = if (dark) Dark else Light
        val s = textScale.coerceIn(1f, 2f)
        val layers = JSONArray()
            .put(layer("background", "background", paint = obj("background-color" to c.background)))
            .put(fill("water", "water", c.water))
            .put(fill("park", "park", c.park))
            .put(fill("building", "building", c.building, minZoom = 16.0))
            // Small roads first, so bigger ones draw over them where they meet.
            .put(road("road-small", listOf("service", "track"), c.small, widths = listOf(14 to 1, 18 to 6)))
            .put(road("road-street", listOf("minor"), c.street, widths = listOf(12 to 1, 14 to 3, 18 to 14)))
            .put(road("road-main", listOf("primary", "secondary", "tertiary"), c.main, widths = listOf(10 to 1.5, 14 to 5, 18 to 20)))
            .put(road("road-highway", listOf("motorway", "trunk"), c.highway, widths = listOf(6 to 1, 12 to 4, 18 to 24)))
            // The route: green (no road is green) over a dark edge, under the street names.
            .put(
                layer(
                    "route-edge", "line", source = ROUTE_SOURCE,
                    layout = obj("line-cap" to "round", "line-join" to "round"),
                    paint = obj("line-color" to c.halo, "line-width" to zoomed(10 to 8, 18 to 30)),
                ),
            )
            .put(
                layer(
                    "route-line", "line", source = ROUTE_SOURCE,
                    layout = obj("line-cap" to "round", "line-join" to "round"),
                    paint = obj("line-color" to c.route, "line-width" to zoomed(10 to 5, 18 to 22)),
                ),
            )
            // Invisible, wide lines along every named road, so a tap near a street finds it.
            .put(
                layer(
                    STREET_HIT_LAYER, "line", source = "openmaptiles", sourceLayer = "transportation_name", minZoom = 12.0,
                    paint = obj("line-color" to c.background, "line-opacity" to 0.01, "line-width" to 28),
                ),
            )
            .put(
                layer(
                    "street-names", "symbol", source = "openmaptiles", sourceLayer = "transportation_name", minZoom = 13.0,
                    layout = obj(
                        "symbol-placement" to "line",
                        "text-field" to arr("coalesce", arr("get", "name:en"), arr("get", "name")),
                        "text-font" to arr("Noto Sans Bold"),
                        "text-size" to zoomed(13 to 15 * s, 16 to 19 * s, 18 to 24 * s),
                        "text-max-angle" to 30,
                        "text-padding" to 4,
                        "symbol-spacing" to 300,
                    ),
                    paint = obj("text-color" to c.label, "text-halo-color" to c.halo, "text-halo-width" to 3, "text-halo-blur" to 0),
                ),
            )
            .put(
                layer(
                    "house-numbers", "symbol", source = "openmaptiles", sourceLayer = "housenumber", minZoom = 17.5,
                    layout = obj("text-field" to arr("get", "housenumber"), "text-font" to arr("Noto Sans Regular"), "text-size" to 14 * s),
                    paint = obj("text-color" to c.label, "text-halo-color" to c.halo, "text-halo-width" to 2),
                ),
            )
            .put(
                layer(
                    "places", "symbol", source = "openmaptiles", sourceLayer = "place", maxZoom = 15.0,
                    filter = arr("match", arr("get", "class"), arr("city", "town", "village", "suburb"), true, false),
                    layout = obj(
                        "text-field" to arr("coalesce", arr("get", "name:en"), arr("get", "name")),
                        "text-font" to arr("Noto Sans Bold"),
                        "text-size" to zoomed(8 to 16 * s, 13 to 22 * s),
                    ),
                    paint = obj("text-color" to c.placeLabel, "text-halo-color" to c.halo, "text-halo-width" to 3),
                ),
            )
            .put(
                layer(
                    "place-dot", "circle", source = PLACE_SOURCE,
                    paint = obj("circle-radius" to 12, "circle-color" to c.place, "circle-stroke-color" to c.placeRing, "circle-stroke-width" to 4),
                ),
            )
        return obj(
            "version" to 8,
            "name" to if (dark) "Newt dark" else "Newt light",
            "glyphs" to GLYPHS,
            "sources" to obj(
                "openmaptiles" to obj("type" to "vector", "url" to TILES),
                PLACE_SOURCE to obj("type" to "geojson", "data" to obj("type" to "FeatureCollection", "features" to JSONArray())),
                ROUTE_SOURCE to obj("type" to "geojson", "data" to obj("type" to "FeatureCollection", "features" to JSONArray())),
            ),
            "layers" to layers,
        ).toString()
    }

    private fun road(id: String, classes: List<String>, color: String, widths: List<Pair<Int, Number>>) = layer(
        id, "line", source = "openmaptiles", sourceLayer = "transportation",
        filter = arr("match", arr("get", "class"), JSONArray(classes), true, false),
        layout = obj("line-cap" to "round", "line-join" to "round"),
        paint = obj("line-color" to color, "line-width" to zoomed(*widths.toTypedArray())),
    )

    private fun fill(id: String, sourceLayer: String, color: String, minZoom: Double? = null) =
        layer(id, "fill", source = "openmaptiles", sourceLayer = sourceLayer, minZoom = minZoom, paint = obj("fill-color" to color))

    private fun layer(
        id: String, type: String, source: String? = null, sourceLayer: String? = null,
        minZoom: Double? = null, maxZoom: Double? = null, filter: JSONArray? = null,
        layout: JSONObject? = null, paint: JSONObject? = null,
    ) = JSONObject().apply {
        put("id", id); put("type", type)
        source?.let { put("source", it) }
        sourceLayer?.let { put("source-layer", it) }
        minZoom?.let { put("minzoom", it) }
        maxZoom?.let { put("maxzoom", it) }
        filter?.let { put("filter", it) }
        layout?.let { put("layout", it) }
        paint?.let { put("paint", it) }
    }

    /** A value that changes smoothly with zoom: (zoom to value) stops. */
    private fun zoomed(vararg stops: Pair<Int, Number>): JSONArray {
        val expr = arr("interpolate", arr("linear"), arr("zoom"))
        stops.forEach { (zoom, value) -> expr.put(zoom).put(value) }
        return expr
    }

    private fun obj(vararg pairs: Pair<String, Any>) = JSONObject().apply { pairs.forEach { (k, v) -> put(k, v) } }
    private fun arr(vararg items: Any) = JSONArray().apply { items.forEach { put(it) } }
}

/** Plain words for what kind of road it is, from the map data's road class. */
fun roadKind(roadClass: String?): String? = when (roadClass) {
    "motorway" -> "Highway"
    "trunk" -> "Major highway"
    "primary", "secondary" -> "Main road"
    "tertiary" -> "Through street"
    "minor" -> "Street"
    "service" -> "Service road"
    "track" -> "Track"
    "path" -> "Path"
    else -> null
}
