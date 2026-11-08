package io.github.secnewt.maps

import io.github.secnewt.maps.handoff.MapApps
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
import io.github.secnewt.maps.link.LinkResult
import io.github.secnewt.maps.saved.SavedPlace
import io.github.secnewt.maps.saved.SavedPlaces
import io.github.secnewt.maps.search.PhotonSearch
import java.io.File
import io.github.secnewt.maps.search.SearchHit
import io.github.secnewt.maps.search.SearchOutcome
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
    private val search by lazy { PhotonSearch() }
    private val saved by lazy { SavedPlaces(File(filesDir, "saved.json")) }
    // Bumped on every change to saved places, so the screen redraws.
    private val savedVersion = mutableStateOf(0)
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
                        mapApp = remember { MapApps(this@MainActivity).installed().firstOrNull()?.label },
                        home = savedVersion.value.let { saved.home?.let { listOfNotNull(it.name, it.address).joinToString(", ") } },
                        onClearHome = { saved.clearHome(); savedChanged() },
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
        // The last search's list, so closing a place picked from it goes back to the list.
        var results by remember { mutableStateOf<MapCard.Results?>(null) }
        val showResults = { list: MapCard.Results ->
            card = list
            controller.showMany(list.hits.map { it.lat to it.lng })
        }
        val closeCard = {
            val list = results
            if (card is MapCard.Place && list != null) {
                showResults(list)
            } else {
                results = null
                card = null
                controller.clearPlace()
            }
        }
        val version = savedVersion.value
        LaunchedEffect(version) { controller.showSaved(saved.all().map { Triple(it.name, it.lat, it.lng) }) }
        val placeSaved = (card as? MapCard.Place)?.let { saved.at(it.lat, it.lng) != null } ?: false
        val placeIsHome = (card as? MapCard.Place)?.let { saved.isHome(it.lat, it.lng) } ?: false
        val savedHits = { places: List<SavedPlace>, near: Pair<Double, Double>? ->
            places.map { SearchHit(it.name, it.address.orEmpty(), it.lat, it.lng, near?.let { n -> PhotonSearch.miles(n.first, n.second, it.lat, it.lng) }, saved = true) }
        }
        val pick = { hit: SearchHit ->
            card = MapCard.Place(hit.name, hit.lat, hit.lng, hit.address.ifEmpty { null })
            controller.show(hit.lat, hit.lng)
        }
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
        if (card != null) BackHandler { closeCard() }
        MapScreen(
            map = { NewtMap(controller, dark, startPosition(), onTap = { tapped ->
                    card = if (tapped is MapCard.Place) {
                        // A gold dot: fill in the address I saved with it.
                        val mine = if (saved.isHome(tapped.lat, tapped.lng) && tapped.name == "Home") saved.home else saved.at(tapped.lat, tapped.lng)
                        tapped.copy(address = mine?.address)
                    } else {
                        tapped
                    }
                }, Modifier.fillMaxSize()) },
            card = card,
            searching = searching,
            onSearch = { text ->
                searching = true
                val near = controller.near()
                scope.launch {
                    val outcome = withContext(Dispatchers.IO) { search.search(text, near) }
                    searching = false
                    results = null
                    // My saved places that match come first, then the search's, minus repeats.
                    val mine = savedHits(saved.matching(text), near)
                    val theirs = (outcome as? SearchOutcome.Results)?.hits.orEmpty()
                        .filterNot { hit -> mine.any { PhotonSearch.miles(it.lat, it.lng, hit.lat, hit.lng) < 0.05 } }
                    val shortened = (outcome as? SearchOutcome.Results)?.shortenedTo?.takeIf { mine.isEmpty() }
                    val hits = mine + theirs
                    when {
                        hits.size == 1 && shortened == null -> pick(hits.single())
                        hits.isNotEmpty() -> MapCard.Results(text, hits, shortened).let { results = it; showResults(it) }
                        outcome == SearchOutcome.NoInternet -> card = MapCard.Note("No internet, so search can't run.")
                        outcome == SearchOutcome.ServerProblem -> card = MapCard.Note("The search server isn't answering. Try again in a minute.")
                        else -> { card = MapCard.Note("Couldn't find \"$text\"."); controller.clearPlace() }
                    }
                }
            },
            onPick = pick,
            placeSaved = placeSaved,
            placeIsHome = placeIsHome,
            onSave = { place, name ->
                saved.save(SavedPlace(name, place.lat, place.lng, place.address ?: place.name.takeIf { it != name }))
                savedChanged()
                card = place.copy(name = name)
            },
            onRename = { place, name ->
                saved.at(place.lat, place.lng)?.let { saved.rename(it, name) }
                savedChanged()
                card = place.copy(name = name)
            },
            onRemove = { place ->
                saved.at(place.lat, place.lng)?.let(saved::remove)
                savedChanged()
            },
            onSetHome = { place ->
                saved.setHome(SavedPlace(place.name, place.lat, place.lng, place.address))
                savedChanged()
            },
            onHome = {
                val home = saved.home
                if (home == null) {
                    card = MapCard.Note("No home set yet. Search for your address, then tap Set as home on its card.")
                } else {
                    val place = MapCard.Place("Home", home.lat, home.lng, listOfNotNull(home.name.takeIf { it != "Home" }, home.address).joinToString(", ").ifEmpty { null })
                    card = place
                    findRoute(place)
                }
            },
            onBrowseSaved = {
                val all = saved.all()
                if (all.isNotEmpty() && card !is MapCard.Results) {
                    val list = MapCard.Results("", savedHits(all, controller.near()), title = "Saved places")
                    results = list
                    showResults(list)
                }
            },
            onZoomIn = { controller.zoomBy(1.0) },
            onZoomOut = { controller.zoomBy(-1.0) },
            onMyLocation = { withLocation(follow) },
            onSettings = onSettings,
            onCloseCard = closeCard,
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

    private fun savedChanged() { savedVersion.value++ }

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
