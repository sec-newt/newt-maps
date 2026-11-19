package io.github.secnewt.maps.nav

import java.util.UUID
import uniffi.ferrostar.ManeuverModifier
import uniffi.ferrostar.ManeuverType
import uniffi.ferrostar.Route
import uniffi.ferrostar.RouteStep
import uniffi.ferrostar.SpokenInstruction

/**
 * Says early which side the destination is on. Valhalla only says "Your destination is on
 * the left" about 60 m out, too late to get into the right lane. Before a drive starts, this adds:
 *
 *  - "In half a mile, your destination will be on the right." when the last road is long
 *    enough for that to come after the last turn, or
 *  - "Your destination will be on the right." added to the last turn's call, when the last
 *    road is shorter than that (so it's never said before a turn that's still to come).
 *
 * Valhalla's own close-up call stays.
 */
object ArrivalCallouts {

    const val HALF_MILE = 804.672

    /** Room past the half-mile call, so it doesn't land on top of the last turn's call. */
    private const val MARGIN = 150.0

    enum class Plan { HALF_MILE, ON_LAST_TURN, NONE }

    /** Where the early call goes, from the length of the last road and whether a turn leads onto it. */
    fun plan(lastRoadMeters: Double, turnBefore: Boolean): Plan = when {
        lastRoadMeters >= HALF_MILE + MARGIN -> Plan.HALF_MILE
        turnBefore -> Plan.ON_LAST_TURN
        else -> Plan.NONE
    }

    /** "on the right", "on the left", or "ahead" when Valhalla doesn't know the side. */
    fun side(modifier: ManeuverModifier?): String = when (modifier) {
        ManeuverModifier.RIGHT, ManeuverModifier.SLIGHT_RIGHT, ManeuverModifier.SHARP_RIGHT -> "on the right"
        ManeuverModifier.LEFT, ManeuverModifier.SLIGHT_LEFT, ManeuverModifier.SHARP_LEFT -> "on the left"
        else -> "ahead"
    }

    fun halfMileCall(side: String) = "In half a mile, your destination will be $side."

    fun lastTurnCall(turn: String, side: String) = "${turn.trimEnd().let { if (it.endsWith('.')) it else "$it." }} Your destination will be $side."

    /** [route] with the early calls added. Unchanged if it has no arrival in it. */
    fun addTo(route: Route): Route {
        val steps = route.steps
        // Each step's instructions describe the maneuver at its end, so the last road is the
        // step whose banner says "arrive".
        val last = steps.indexOfLast { step -> step.visualInstructions.any { it.primaryContent.maneuverType == ManeuverType.ARRIVE } }
        if (last < 0) return route
        val arrive = steps[last].visualInstructions.last { it.primaryContent.maneuverType == ManeuverType.ARRIVE }
        val side = side(arrive.primaryContent.maneuverModifier)
        val before = steps.getOrNull(last - 1)?.takeIf { it.spokenInstructions.isNotEmpty() }
        val changed = steps.toMutableList()
        when (plan(steps[last].distance, before != null)) {
            Plan.HALF_MILE -> changed[last] = steps[last].withSpoken(
                steps[last].spokenInstructions + SpokenInstruction(halfMileCall(side), null, HALF_MILE, UUID.randomUUID()),
            )
            Plan.ON_LAST_TURN -> {
                // The call made at the turn itself is the one closest to it.
                val atTurn = before!!.spokenInstructions.minBy { it.triggerDistanceBeforeManeuver }
                changed[last - 1] = before.withSpoken(
                    before.spokenInstructions.map { if (it === atTurn) it.copy(text = lastTurnCall(it.text, side), ssml = null) else it },
                )
            }
            Plan.NONE -> return route
        }
        return route.copy(steps = changed)
    }

    // Furthest first, the order Valhalla sends them in.
    private fun RouteStep.withSpoken(spoken: List<SpokenInstruction>) =
        copy(spokenInstructions = spoken.sortedByDescending { it.triggerDistanceBeforeManeuver })
}
