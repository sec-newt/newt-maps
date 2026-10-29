package io.github.secnewt.maps

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import io.github.secnewt.maps.handoff.geoUri
import io.github.secnewt.maps.link.GeoResult
import io.github.secnewt.maps.link.LinkResult
import io.github.secnewt.maps.link.NominatimGeocoder
import io.github.secnewt.maps.map.MapController
import io.github.secnewt.maps.map.NewtMap
import io.github.secnewt.maps.nav.NavGuidance
import io.github.secnewt.maps.nav.Navigator
import io.github.secnewt.maps.nav.RouteProblem
import com.stadiamaps.ferrostar.core.location.toAndroidLocation
import uniffi.ferrostar.Route
import io.github.secnewt.maps.ui.HomeScreen
import io.github.secnewt.maps.ui.MapCard
import io.github.secnewt.maps.ui.MapScreen
import io.github.secnewt.maps.ui.linkHostsAllOn
import io.github.secnewt.ui.NewtTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng

class MainActivity : ComponentActivity() {
    private val needsTurningOn = mutableStateOf(true)
    private val controller = MapController()
    private val geocoder by lazy { NominatimGeocoder() }
    private val navigator by lazy { Navigator(this) }
    private val navPrefs by lazy { getSharedPreferences("navigation", MODE_PRIVATE) }
    private val practice = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        practice.value = navPrefs.getBoolean("practice", false)
        setContent {
            NewtTheme {
                var showSettings by rememberSaveable { mutableStateOf(false) }
                if (showSettings) {
                    BackHandler { showSettings = false }
                    HomeScreen(
                        linksNeedTurningOn = needsTurningOn.value,
                        onTurnOn = { startActivity(Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:$packageName"))) },
                        onBack = { showSettings = false },
                        practice = practice.value,
                        onPractice = { on -> practice.value = on; navPrefs.edit().putBoolean("practice", on).apply() },
                    )
                } else {
                    Map(onSettings = { showSettings = true })
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Map(onSettings: () -> Unit) {
        var card by remember { mutableStateOf<MapCard?>(null) }
        var searching by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val dark = isSystemInDarkTheme()
        val puck = MaterialTheme.colorScheme.primary.toArgb()
        val follow = { if (!controller.followMe(this, puck)) card = MapCard.Note("The map is still loading. Try again in a moment.") }
        // What to do once location is allowed: "where am I", or finding a route.
        var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
        val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            val next = afterPermission
            afterPermission = null
            if (granted.values.any { it }) next?.invoke() else card = MapCard.Note("Location is off for Newt Maps. Turn it on in Settings to see where you are.")
        }
        val withLocation = { action: () -> Unit ->
            val fine = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (fine || coarse) {
                action()
            } else {
                afterPermission = action
                askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }

        // Directions: the place being routed to, the route found, and the trip once started.
        var destination by remember { mutableStateOf<MapCard.Place?>(null) }
        var route by remember { mutableStateOf<Route?>(null) }
        var avoidTolls by remember { mutableStateOf(navPrefs.getBoolean("avoid_tolls", true)) }
        var driving by remember { mutableStateOf(false) }
        var muted by remember { mutableStateOf(false) }
        val navState by remember(avoidTolls) { navigator.state(avoidTolls) }.collectAsState()
        val guidance = if (driving) NavGuidance.from(navState, muted, practice.value) else null
        val findRoute = { place: MapCard.Place ->
            withLocation {
                destination = place
                card = MapCard.Note("Finding a route…")
                scope.launch {
                    try {
                        val found = navigator.route(place.lat, place.lng, avoidTolls)
                        route = found
                        card = null
                        controller.showRoute(found.geometry.map { it.lat to it.lng })
                    } catch (problem: RouteProblem) {
                        card = MapCard.Note(problem.message.orEmpty())
                    }
                }
            }
        }
        val stopDriving = {
            navigator.stop()
            driving = false
            route = null
            controller.stopDriving(this)
            controller.clearRoute()
        }
        // While driving: move the arrow, keep the screen on, and finish on arrival.
        LaunchedEffect(navState, driving) {
            if (!driving) return@LaunchedEffect
            (navState.tripState as? uniffi.ferrostar.TripState.Navigating)?.let { controller.drivingAt(it.snappedUserLocation.toAndroidLocation()) }
            if (NavGuidance.arrived(navState)) {
                val name = destination?.name
                stopDriving()
                card = MapCard.Note(if (name != null) "You've arrived at $name." else "You've arrived.")
            }
        }
        DisposableEffect(driving) {
            if (driving) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
        if (driving) BackHandler { stopDriving() }
        if (route != null && !driving) BackHandler { route = null; controller.clearRoute() }
        if (card != null) BackHandler { card = null; controller.clearPlace() }
        MapScreen(
            map = { NewtMap(controller, dark, startPosition(), onTap = { street -> card = street }, Modifier.fillMaxSize()) },
            card = card,
            searching = searching,
            onSearch = { text ->
                searching = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { geocoder.find(text) }
                    searching = false
                    card = when (result) {
                        is GeoResult.Found -> MapCard.Place(text, result.lat, result.lng).also { controller.show(it.lat, it.lng) }
                        GeoResult.NotFound -> MapCard.Note("Couldn't find \"$text\".")
                        GeoResult.NoInternet -> MapCard.Note("No internet, so search can't run.")
                    }
                }
            },
            onZoomIn = { controller.zoomBy(1.0) },
            onZoomOut = { controller.zoomBy(-1.0) },
            onMyLocation = { withLocation(follow) },
            onSettings = onSettings,
            onCloseCard = { card = null; controller.clearPlace() },
            onShare = { place ->
                val text = "${place.name}\nhttps://www.openstreetmap.org/?mlat=${place.lat}&mlon=${place.lng}#map=17/${place.lat}/${place.lng}"
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
            },
            onCopy = { place ->
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Coordinates", "%.6f, %.6f".format(place.lat, place.lng)))
            },
            onOpenElsewhere = { place ->
                val uri = geoUri(LinkResult.Place(place.lat, place.lng, name = place.name))
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).setClass(this, PickActivity::class.java))
            },
            onDirections = findRoute,
            preview = route?.takeIf { !driving }?.let { NavGuidance.preview(it, destination?.name.orEmpty(), avoidTolls, practice.value) },
            onStart = {
                route?.let { found ->
                    navigator.setMuted(muted)
                    navigator.start(found, avoidTolls, practice.value)
                    controller.clearPlace()
                    controller.startDriving(this, puck)
                    driving = true
                }
            },
            onToggleTolls = {
                avoidTolls = !avoidTolls
                navPrefs.edit().putBoolean("avoid_tolls", avoidTolls).apply()
                route = null
                controller.clearRoute()
                destination?.let(findRoute)
            },
            onClosePreview = { route = null; controller.clearRoute() },
            guidance = guidance,
            onMute = { muted = !muted; navigator.setMuted(muted) },
            onStop = stopDriving,
        )
    }

    /**
     * Where the map opens: the coordinates in the launch intent if given (the emulator test
     * uses this), otherwise Garland, TX.
     */
    private fun startPosition(): CameraPosition {
        val lat = intent.getDoubleExtra(EXTRA_LAT, Double.NaN)
        val lng = intent.getDoubleExtra(EXTRA_LNG, Double.NaN)
        val zoom = intent.getDoubleExtra(EXTRA_ZOOM, 13.0)
        val at = if (lat.isNaN() || lng.isNaN()) LatLng(32.9126, -96.6389) else LatLng(lat, lng)
        return CameraPosition.Builder().target(at).zoom(zoom).build()
    }

    override fun onDestroy() {
        if (isFinishing) navigator.shutdown()
        super.onDestroy()
    }

    // Checked on every return, since the user comes back here from Settings.
    override fun onResume() {
        super.onResume()
        needsTurningOn.value = !linkHostsAllOn(this)
    }

    companion object {
        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"
        const val EXTRA_ZOOM = "zoom"
    }
}
