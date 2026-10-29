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
            val options = LocationComponentOptions.builder(context)
                .foregroundTintColor(color)
                .bearingTintColor(color)
                .accuracyColor(color)
                .accuracyAlpha(0.15f)
                .build()
            location.activateLocationComponent(
                LocationComponentActivationOptions.builder(context, style)
                    .locationComponentOptions(options)
                    .useDefaultLocationEngine(true)
                    .build(),
            )
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
    onTap: (MapCard.Street?) -> Unit,
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
            map.setStyle(Style.Builder().fromJson(MapStyle.json(dark, textScale))) { style -> controller.style = style }
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}
