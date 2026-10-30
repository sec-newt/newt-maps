package io.github.secnewt.maps.nav

import android.content.Context
import com.stadiamaps.ferrostar.core.AndroidTtsObserver
import com.stadiamaps.ferrostar.core.FerrostarCore
import com.stadiamaps.ferrostar.core.NavigationState
import com.stadiamaps.ferrostar.core.http.OkHttpClientProvider.Companion.toOkHttpClientProvider
import com.stadiamaps.ferrostar.core.location.AndroidLocationProvider
import com.stadiamaps.ferrostar.core.location.NavigationLocationProvider
import com.stadiamaps.ferrostar.core.location.SimulatedLocationProvider
import com.stadiamaps.ferrostar.core.location.toUserLocation
import com.stadiamaps.ferrostar.core.withJsonOptions
import java.time.Duration
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import uniffi.ferrostar.CourseFiltering
import uniffi.ferrostar.GeographicCoordinate
import uniffi.ferrostar.NavigationControllerConfig
import uniffi.ferrostar.Route
import uniffi.ferrostar.RouteDeviationTracking
import uniffi.ferrostar.Waypoint
import uniffi.ferrostar.WaypointAdvanceMode
import uniffi.ferrostar.WaypointKind
import uniffi.ferrostar.WellKnownRouteProvider
import uniffi.ferrostar.stepAdvanceDistanceEntryAndExit
import uniffi.ferrostar.stepAdvanceDistanceToEndOfStep

/** Why a route couldn't be found, in words for the card. */
class RouteProblem(message: String) : Exception(message)

/**
 * Turn-by-turn driving, on Ferrostar. Routes come from my own Valhalla server ([RouteServer]),
 * turns are spoken with street names, and going off route asks for a new route by itself.
 *
 * Practice mode drives the route without moving, for trying it out at a desk.
 */
class Navigator(context: Context) {

    private val app = context.applicationContext
    private val simulated = SimulatedLocationProvider(warpFactor = 2u)
    private val location = NavigationLocationProvider(AndroidLocationProvider(app), simulated)
    private val http = OkHttpClient.Builder().callTimeout(Duration.ofSeconds(20)).build().toOkHttpClientProvider()
    private val voice = AndroidTtsObserver(app)

    private var avoidTolls: Boolean? = null
    private var core: FerrostarCore? = null

    // A new core when the toll choice changes, since route options are fixed per core.
    private fun core(avoidTolls: Boolean): FerrostarCore {
        core?.takeIf { this.avoidTolls == avoidTolls }?.let { return it }
        core?.stopNavigation()
        val provider = WellKnownRouteProvider.Valhalla(RouteServer.URL, "auto").withJsonOptions(NavText.valhallaOptions(avoidTolls))
        return FerrostarCore(provider, http, location, CONFIG).also {
            it.spokenInstructionObserver = voice
            core = it
            this.avoidTolls = avoidTolls
        }
    }

    /** The current trip, for the screen. Empty until a route is started. */
    fun state(avoidTolls: Boolean): StateFlow<NavigationState> = core(avoidTolls).state

    /**
     * A route from where I am to [lat],[lng]. Throws [RouteProblem] with words for the card
     * when there's no location or the server can't be reached.
     */
    suspend fun route(lat: Double, lng: Double, avoidTolls: Boolean): Route {
        val here = location.lastLocation()
            ?: withTimeoutOrNull(15_000) { location.locationUpdates(1000).first() }
            ?: throw RouteProblem("Can't tell where you are yet. Step outside or wait a moment, then try again.")
        val routes = try {
            core(avoidTolls).getRoutes(here.toUserLocation(), listOf(Waypoint(coordinate = GeographicCoordinate(lat, lng), kind = WaypointKind.BREAK)))
        } catch (e: Exception) {
            throw RouteProblem("Couldn't reach the route server on persephone. Is it on, and is Tailscale connected?")
        }
        return routes.firstOrNull() ?: throw RouteProblem("No route found to there.")
    }

    /** Start driving [route]; with [practice], the location moves along it by itself. */
    fun start(route: Route, avoidTolls: Boolean, practice: Boolean) {
        if (practice) location.enableSimulationOn(route) else location.disableSimulation()
        core(avoidTolls).startNavigation(route)
    }

    fun stop() {
        core?.stopNavigation()
        location.disableSimulation()
    }

    fun setMuted(muted: Boolean) = voice.setMuted(muted)

    /** Free the voice when the app closes. */
    fun shutdown() {
        stop()
        voice.shutdown()
    }

    private companion object {
        // Ferrostar's suggested settings: advance a step 30 m into the turn, re-route after
        // 15 s more than 50 m off the route, and snap the arrow onto the road.
        val CONFIG = NavigationControllerConfig(
            WaypointAdvanceMode.WaypointWithinRange(100.0),
            stepAdvanceDistanceEntryAndExit(30u, 5u, 32u),
            stepAdvanceDistanceToEndOfStep(10u, 32u),
            RouteDeviationTracking.StaticThreshold(15U, 50.0),
            CourseFiltering.SNAP_TO_ROUTE,
        )
    }
}

/**
 * Where routes come from: Valhalla on persephone, reached over Tailscale. Plain HTTP is
 * allowed to this one address only (res/xml/network_security_config.xml); the move to the
 * Proxmox server changes it here and there.
 */
object RouteServer {
    const val HOST = "100.113.230.33"
    const val URL = "http://$HOST:8002/route"
}
