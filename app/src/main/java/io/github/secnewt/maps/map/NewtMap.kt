package io.github.secnewt.maps.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RectF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.secnewt.maps.ui.MapCard
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point

/** Moves the map from outside it: zoom buttons, "where am I", a found place. */
class MapController {
    internal var map: MapLibreMap? = null
    internal var style: Style? = null

    fun zoomBy(amount: Double) {
        map?.animateCamera(CameraUpdateFactory.zoomBy(amount), 300)
    }

    /** Center on a place and mark it with the big dot. */
    fun show(lat: Double, lng: Double) {
        style?.getSourceAs<GeoJsonSource>(MapStyle.PLACE_SOURCE)?.setGeoJson(Point.fromLngLat(lng, lat))
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 16.0), 600)
    }

    /** Draw home and my saved places (name, lat, lng). Kept so they come back if the style reloads. */
    fun showSaved(places: List<Triple<String, Double, Double>>) {
        saved = places
        val features = places.map { (name, lat, lng) ->
            org.maplibre.geojson.Feature.fromGeometry(Point.fromLngLat(lng, lat)).apply { addStringProperty("name", name) }
        }
        style?.getSourceAs<GeoJsonSource>(MapStyle.SAVED_SOURCE)?.setGeoJson(org.maplibre.geojson.FeatureCollection.fromFeatures(features))
    }

    internal var saved: List<Triple<String, Double, Double>> = emptyList()

    /** Mark several places (search results) and fit them all on screen above the results card. */
    fun showMany(points: List<Pair<Double, Double>>) {
        if (points.isEmpty()) return
        if (points.size == 1) return show(points[0].first, points[0].second)
        val dots = org.maplibre.geojson.FeatureCollection.fromFeatures(points.map { (lat, lng) -> org.maplibre.geojson.Feature.fromGeometry(Point.fromLngLat(lng, lat)) })
        style?.getSourceAs<GeoJsonSource>(MapStyle.PLACE_SOURCE)?.setGeoJson(dots)
        val bounds = org.maplibre.android.geometry.LatLngBounds.Builder().includes(points.map { (lat, lng) -> LatLng(lat, lng) }).build()
        val height = map?.height?.toInt() ?: 0
        // The results card covers about the bottom half; keep the dots in the top part.
        map?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100, 300, 100, maxOf(300, height / 2 + 100)), 600)
    }

    /**
     * Where search should look near: my last known location when the blue dot is on,
     * otherwise the middle of the map. Null before the map is ready.
     */
    @SuppressLint("MissingPermission")
    fun near(): Pair<Double, Double>? {
        val map = map ?: return null
        val location = map.locationComponent.takeIf { it.isLocationComponentActivated && it.isLocationComponentEnabled }?.lastKnownLocation
        if (location != null) return location.latitude to location.longitude
        val target = map.cameraPosition.target ?: return null
        return target.latitude to target.longitude
    }

    /** Draw a route ([points] as lat, lng) and fit it on screen. */
    fun showRoute(points: List<Pair<Double, Double>>) {
        if (points.size < 2) return
        val line = org.maplibre.geojson.LineString.fromLngLats(points.map { (lat, lng) -> Point.fromLngLat(lng, lat) })
        style?.getSourceAs<GeoJsonSource>(MapStyle.ROUTE_SOURCE)?.setGeoJson(line)
        val bounds = org.maplibre.android.geometry.LatLngBounds.Builder().includes(points.map { (lat, lng) -> LatLng(lat, lng) }).build()
        // Room at the top for the search box and at the bottom for the route card.
        map?.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80, 260, 80, 700), 600)
    }

    fun clearRoute() {
        style?.getSourceAs<GeoJsonSource>(MapStyle.ROUTE_SOURCE)?.setGeoJson(org.maplibre.geojson.FeatureCollection.fromFeatures(emptyList()))
    }

    /**
     * Driving: the arrow shows where navigation says I am (snapped to the road), and the map
     * follows it, turning so the road ahead points up.
     */
    @SuppressLint("MissingPermission")
    fun startDriving(context: Context, color: Int) {
        val map = map ?: return
        val style = style ?: return
        val location = map.locationComponent
        if (!location.isLocationComponentActivated) {
            location.activateLocationComponent(
                LocationComponentActivationOptions.builder(context, style)
                    .locationComponentOptions(puck(context, color))
                    .useDefaultLocationEngine(false)
                    .build(),
            )
        }
        location.locationEngine = null
        location.isLocationComponentEnabled = true
        location.renderMode = org.maplibre.android.location.modes.RenderMode.GPS
        location.setCameraMode(CameraMode.TRACKING_GPS, 600L, 17.0, null, null, null)
    }

    /** Move the arrow while driving. */
    fun drivingAt(where: android.location.Location) {
        map?.locationComponent?.takeIf { it.isLocationComponentActivated }?.forceLocationUpdate(where)
    }

    /** Back to a north-up map that doesn't follow. */
    fun stopDriving(context: Context) {
        val location = map?.locationComponent?.takeIf { it.isLocationComponentActivated } ?: return
        location.setCameraMode(CameraMode.NONE)
        location.renderMode = org.maplibre.android.location.modes.RenderMode.NORMAL
        location.locationEngine = org.maplibre.android.location.engine.LocationEngineDefault.getDefaultLocationEngine(context)
        map?.animateCamera(CameraUpdateFactory.bearingTo(0.0), 400)
    }

    private fun puck(context: Context, color: Int) = LocationComponentOptions.builder(context)
        .foregroundTintColor(color)
        .bearingTintColor(color)
        .accuracyColor(color)
        .accuracyAlpha(0.15f)
        .build()

    fun clearPlace() {
        style?.getSourceAs<GeoJsonSource>(MapStyle.PLACE_SOURCE)?.setGeoJson(org.maplibre.geojson.FeatureCollection.fromFeatures(emptyList()))
    }

    /**
     * Show where I am and follow me. Call only once location permission is granted; false
     * when the map isn't ready yet.
     */
    @SuppressLint("MissingPermission")
    fun followMe(context: Context, color: Int): Boolean {
        val map = map ?: return false
        val style = style ?: return false
        val location = map.locationComponent
        if (!location.isLocationComponentActivated) {
            location.activateLocationComponent(
                LocationComponentActivationOptions.builder(context, style)
                    .locationComponentOptions(puck(context, color))
                    .useDefaultLocationEngine(true)
                    .build(),
            )
        }
        // After a drive the arrow was fed by navigation; give it the phone's location again.
        if (location.locationEngine == null) {
            location.locationEngine = org.maplibre.android.location.engine.LocationEngineDefault.getDefaultLocationEngine(context)
        }
        location.isLocationComponentEnabled = true
        location.setCameraMode(CameraMode.TRACKING, 600L, 16.0, null, null, null)
        return true
    }
}

/**
 * The MapLibre map. North stays up (no twisting it by accident); a tap on or near a street
 * reports its name through [onTap], or null when nothing named is there.
 */
@Composable
fun NewtMap(
    controller: MapController,
    dark: Boolean,
    start: CameraPosition,
    onTap: (MapCard?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val textScale = LocalDensity.current.fontScale
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }
    LaunchedEffect(Unit) {
        mapView.getMapAsync { map ->
            controller.map = map
            map.cameraPosition = start
            map.uiSettings.apply {
                isRotateGesturesEnabled = false
                isTiltGesturesEnabled = false
                isCompassEnabled = false
                // The "i" credit button stays: OpenStreetMap's license asks for the credit.
                isLogoEnabled = false
            }
            // For the emulator test's log: how many street names are on screen once drawing settles.
            mapView.addOnDidBecomeIdleListener {
                val all = RectF(0f, 0f, mapView.width.toFloat(), mapView.height.toFloat())
                android.util.Log.i("NewtMap", "idle: ${map.queryRenderedFeatures(all, "street-names").size} street labels, " +
                    "${map.queryRenderedFeatures(all, MapStyle.STREET_HIT_LAYER).size} named roads, layers ${map.style?.layers?.size}")
            }
            mapView.addOnDidFailLoadingMapListener { error -> android.util.Log.w("NewtMap", "map failed to load: $error") }
            map.addOnMapClickListener { point ->
                val screen = map.projection.toScreenLocation(point)
                val reach = 24 * context.resources.displayMetrics.density
                // A saved place wins over the street under it.
                val mine = map.queryRenderedFeatures(
                    RectF(screen.x - reach, screen.y - reach, screen.x + reach, screen.y + reach),
                    "saved-dot", "saved-names",
                ).firstNotNullOfOrNull { feature ->
                    val at = feature.geometry() as? Point ?: return@firstNotNullOfOrNull null
                    feature.getStringProperty("name")?.let { MapCard.Place(it, at.latitude(), at.longitude()) }
                }
                if (mine != null) {
                    onTap(mine)
                    return@addOnMapClickListener true
                }
                val hits = map.queryRenderedFeatures(
                    RectF(screen.x - reach, screen.y - reach, screen.x + reach, screen.y + reach),
                    MapStyle.STREET_HIT_LAYER,
                )
                val street = hits.firstNotNullOfOrNull { feature ->
                    val name = feature.getStringProperty("name:en") ?: feature.getStringProperty("name")
                    name?.takeIf { it.isNotBlank() }?.let {
                        MapCard.Street(it, roadKind(feature.getStringProperty("class")), feature.getStringProperty("ref")?.takeIf { r -> r != it })
                    }
                }
                onTap(street)
                true
            }
        }
    }
    // A new look when the theme or the phone's text size changes.
    LaunchedEffect(dark, textScale) {
        mapView.getMapAsync { map ->
            android.util.Log.i("NewtMap", "setting style dark=$dark scale=$textScale")
            map.setStyle(Style.Builder().fromJson(MapStyle.json(dark, textScale))) { style -> controller.style = style; controller.showSaved(controller.saved) }
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}
