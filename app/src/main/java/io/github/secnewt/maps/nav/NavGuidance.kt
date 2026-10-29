package io.github.secnewt.maps.nav

import com.stadiamaps.ferrostar.core.NavigationState
import io.github.secnewt.maps.ui.Guidance
import io.github.secnewt.maps.ui.RoutePreview
import java.time.LocalTime
import uniffi.ferrostar.Route
import uniffi.ferrostar.TripState

/** Ferrostar's trip state, turned into what the screen shows. */
object NavGuidance {

    /** Null unless a trip is under way. */
    fun from(state: NavigationState, muted: Boolean, practice: Boolean, now: LocalTime = LocalTime.now()): Guidance? {
        val trip = state.tripState as? TripState.Navigating ?: return null
        val content = trip.visualInstruction?.primaryContent
        val step = trip.remainingSteps.firstOrNull()
        val next = trip.remainingSteps.getOrNull(1)
        val road = content?.text?.takeIf { it.isNotBlank() } ?: step?.roadName ?: step?.instruction.orEmpty()
        return Guidance(
            turn = NavText.turn(content?.maneuverType, content?.maneuverModifier),
            distance = NavText.distance(trip.progress.distanceToNextManeuver),
            road = road,
            spoken = step?.instruction ?: road,
            // Worth showing only when the next turn comes right after this one.
            then = next?.takeIf { (step?.distance ?: Double.MAX_VALUE) < 400 }?.let { n ->
                n.visualInstructions.firstOrNull()?.primaryContent?.text?.let { "Then $it" }
            },
            timeLeft = NavText.duration(trip.progress.durationRemaining),
            distanceLeft = NavText.distance(trip.progress.distanceRemaining),
            arrival = NavText.arrival(trip.progress.durationRemaining, now),
            muted = muted,
            rerouting = state.isCalculatingNewRoute,
            practice = practice,
        )
    }

    /** Whether the trip just finished. */
    fun arrived(state: NavigationState): Boolean = state.tripState is TripState.Complete

    fun preview(route: Route, placeName: String, avoidTolls: Boolean, practice: Boolean, now: LocalTime = LocalTime.now()): RoutePreview {
        val seconds = route.steps.sumOf { it.duration }
        return RoutePreview(
            placeName = placeName,
            duration = NavText.duration(seconds),
            distance = NavText.distance(route.distance),
            arrival = NavText.arrival(seconds, now),
            avoidTolls = avoidTolls,
            practice = practice,
        )
    }
}
