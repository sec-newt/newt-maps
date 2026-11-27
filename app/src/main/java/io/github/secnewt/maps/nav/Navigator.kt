package io.github.secnewt.maps.nav

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import com.stadiamaps.ferrostar.core.AlternativeRouteProcessor
import com.stadiamaps.ferrostar.core.AndroidTtsObserver
import com.stadiamaps.ferrostar.core.AndroidTtsStatusListener
import com.stadiamaps.ferrostar.core.FerrostarCore
import com.stadiamaps.ferrostar.core.NavigationState
import com.stadiamaps.ferrostar.core.http.OkHttpClientProvider.Companion.toOkHttpClientProvider
import com.stadiamaps.ferrostar.core.location.AndroidLocationProvider
import com.stadiamaps.ferrostar.core.location.NavigationLocationProvider
import com.stadiamaps.ferrostar.core.location.SimulatedLocationProvider
import com.stadiamaps.ferrostar.core.location.toUserLocation
import com.stadiamaps.ferrostar.core.withJsonOptions
import java.time.Duration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
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

/**
 * Why a route couldn't be found, in words for the card. [roads]: the marked roads may be why,
 * so the card offers the list to turn some off. [fastest]: Shortest's hard "no tolls / no
 * highways" may be why, so the card offers the fastest route (which only prefers to avoid them).
 */
class RouteProblem(message: String, val roads: Boolean = false, val fastest: Boolean = false) : Exception(message)

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
    private val plainHttp = OkHttpClient.Builder().callTimeout(Duration.ofSeconds(20)).build()
    private val http = plainHttp.toOkHttpClientProvider()
    // The speech engine starts only when asked to; without start() every turn is silent.
    // Started now, so it's ready by the first spoken turn (it takes a moment to wake up).
    // Once it's up, turns are spoken as navigation, not music: Ferrostar asks Android to quiet
    // other audio while it speaks, and speech played as music got quieted along with it.
    private val voice = AndroidTtsObserver(app).also { observer ->
        observer.statusObserver = object : AndroidTtsStatusListener {
            override fun onTtsInitialized(tts: TextToSpeech?, status: Int) {
                tts?.setAudioAttributes(VoiceAudio.attributes())
            }
            override fun onTtsSpeakError(utteranceId: String, status: Int) {}
            override fun onTtsShutdownAndRelease() {}
        }
        observer.start()
    }

    private var options: RouteOptions? = null
    private var core: FerrostarCore? = null

    // A new core when the route options change (tolls, highways, roads to avoid), since
    // they're fixed per core. Going off route asks with the same options, so a new route
    // still avoids the same roads.
    private fun core(options: RouteOptions): FerrostarCore {
        core?.takeIf { this.options == options }?.let { return it }
        core?.stopNavigation()
        val provider = WellKnownRouteProvider.Valhalla(RouteServer.URL, "auto").withJsonOptions(NavText.valhallaOptions(options))
        return FerrostarCore(provider, http, location, CONFIG).also {
            it.spokenInstructionObserver = voice
            // A new route after going off course gets the early "destination on the right" too.
            it.alternativeRouteProcessor = object : AlternativeRouteProcessor {
                override fun loadedAlternativeRoutes(core: FerrostarCore, routes: List<Route>) {
                    routes.firstOrNull()?.let { route -> core.replaceRoute(ArrivalCallouts.addTo(route)) }
                }
            }
            core = it
            this.options = options
        }
    }

    /** The current trip, for the screen. Empty until a route is started. */
    fun state(options: RouteOptions): StateFlow<NavigationState> = core(options).state

    /**
     * A route from where I am to [lat],[lng]. Throws [RouteProblem] with words for the card
     * when there's no location or the server can't be reached.
     */
    suspend fun route(lat: Double, lng: Double, options: RouteOptions): Route {
        // Android's last known location can be hours old (from wherever GPS last ran);
        // routing from there draws a line that starts somewhere else. Wait for a new fix.
        val here = location.lastLocation()?.takeIf { FixAge.isFresh(it.elapsedRealtimeNanos, android.os.SystemClock.elapsedRealtimeNanos()) }
            // The updates start by repeating that same last known fix, so skip old ones there too.
            ?: withTimeoutOrNull(15_000) {
                FixAge.firstFresh(location.locationUpdates(1000), { it.elapsedRealtimeNanos }, android.os.SystemClock::elapsedRealtimeNanos)
            }
            ?: throw RouteProblem("Can't tell where you are yet. Step outside or wait a moment, then try again.")
        val routes = try {
            // Off the main thread: Ferrostar reads the server's answer on whatever thread
            // calls it, and Android refuses network reads on the main one.
            val core = core(options)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                core.getRoutes(here.toUserLocation(), listOf(Waypoint(coordinate = GeographicCoordinate(lat, lng), kind = WaypointKind.BREAK)))
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // A newer request replaced this one: not a route problem, so nothing to show.
            throw e
        } catch (e: java.io.IOException) {
            android.util.Log.w("Navigator", "route request failed", e)
            throw RouteProblem("Couldn't reach the route server on persephone. Is it on, and is Tailscale connected?")
        } catch (e: Exception) {
            // The server answered, but the answer couldn't be used: say so, with the reason,
            // instead of blaming the network.
            android.util.Log.w("Navigator", "route answer unusable", e)
            // Valhalla refuses outright (an error status) when what's forbidden leaves no way
            // through: the marked spots, or Shortest's hard no tolls / no highways. Other
            // failures keep their own reason.
            val hardExcludes = options.shortest && (options.avoidTolls || options.avoidHighways)
            if ((options.avoid.isNotEmpty() || hardExcludes) && e is com.stadiamaps.ferrostar.core.InvalidStatusCodeException) {
                val what = listOfNotNull(
                    "your marked roads".takeIf { options.avoid.isNotEmpty() },
                    "tolls".takeIf { hardExcludes && options.avoidTolls },
                    "highways".takeIf { hardExcludes && options.avoidHighways },
                ).joinToString(" and ")
                throw RouteProblem("No route found that avoids $what. Turn some off, then try again.", roads = options.avoid.isNotEmpty(), fastest = hardExcludes)
            }
            throw RouteProblem("The route server answered, but its route couldn't be read. (${e.javaClass.simpleName}: ${e.message?.take(160)})")
        }
        return routes.firstOrNull() ?: throw RouteProblem("No route found to there.")
    }

    /**
     * The road at [lat],[lng] and its other lanes, to avoid. Null when there's no named road
     * there. Throws [RouteProblem] when the route server can't be reached.
     */
    suspend fun roadAt(lat: Double, lng: Double): AvoidedRoad? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val request = okhttp3.Request.Builder().url(RouteServer.LOCATE_URL)
            .post(RoadLocator.request(lat, lng).toRequestBody("application/json".toMediaType())).build()
        try {
            plainHttp.newCall(request).execute().use { answer ->
                if (!answer.isSuccessful) throw RouteProblem("The route server couldn't look up that road (error ${answer.code}).")
                RoadLocator.roadAt(answer.body.string())
            }
        } catch (e: java.io.IOException) {
            android.util.Log.w("Navigator", "locate failed", e)
            throw RouteProblem("Couldn't reach the route server on persephone. Is it on, and is Tailscale connected?")
        }
    }

    /** Start driving [route]; with [practice], the location moves along it by itself. */
    fun start(route: Route, options: RouteOptions, practice: Boolean) {
        val called = ArrivalCallouts.addTo(route)
        if (practice) location.enableSimulationOn(called) else location.disableSimulation()
        core(options).startNavigation(called)
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

/** Kept apart from [Navigator] so it can be tested without Ferrostar's native code. */
object FixAge {
    private const val MAX_NANOS = 30_000_000_000L

    /** A location fix is fresh enough to route from when it's under 30 seconds old. */
    fun isFresh(fixNanos: Long, nowNanos: Long): Boolean = nowNanos - fixNanos in 0..MAX_NANOS

    /** The first fix from [fixes] that's fresh when it arrives; old ones are skipped. */
    suspend fun <T> firstFresh(fixes: Flow<T>, nanosOf: (T) -> Long, now: () -> Long): T =
        fixes.first { isFresh(nanosOf(it), now()) }
}

/** How spoken turns play: as navigation speech, which Android doesn't turn down for others. */
object VoiceAudio {
    fun attributes(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
}

/**
 * Where routes come from: Valhalla on persephone, reached over Tailscale. Plain HTTP is
 * allowed to this one address only (res/xml/network_security_config.xml); the move to the
 * Proxmox server changes it here and there.
 */
object RouteServer {
    const val HOST = "100.113.230.33"
    const val URL = "http://$HOST:8002/route"
    const val LOCATE_URL = "http://$HOST:8002/locate"
}
