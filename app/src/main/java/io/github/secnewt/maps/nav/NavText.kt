package io.github.secnewt.maps.nav

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import uniffi.ferrostar.ManeuverModifier
import uniffi.ferrostar.ManeuverType

/** Distances, times and turns in plain US words. Kept free of Android so it can be tested. */
object NavText {

    private const val METERS_PER_MILE = 1609.344
    private const val FEET_PER_METER = 3.28084

    /** "300 ft" up close, "0.4 mi" further, "12 mi" far. Feet round to 50, like road signs. */
    fun distance(meters: Double): String {
        val miles = meters / METERS_PER_MILE
        return when {
            miles < 0.1 -> "${(((meters * FEET_PER_METER) / 50).roundToInt() * 50).coerceAtLeast(50)} ft"
            miles < 10 -> String.format(Locale.US, "%.1f mi", miles)
            else -> "${miles.roundToInt()} mi"
        }
    }

    /** "4 min", "1 hr 12 min". Never "0 min". */
    fun duration(seconds: Double): String {
        val minutes = (seconds / 60).roundToInt().coerceAtLeast(1)
        val hours = minutes / 60
        return if (hours == 0) "$minutes min" else "$hours hr ${minutes % 60} min"
    }

    /** When I'll get there: "3:45 PM". */
    fun arrival(secondsFromNow: Double, now: LocalTime = LocalTime.now()): String =
        now.plusSeconds(secondsFromNow.toLong()).format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))

    /** Which arrow to show for a maneuver. */
    fun turn(type: ManeuverType?, modifier: ManeuverModifier?): Turn = when (type) {
        ManeuverType.ARRIVE -> Turn.ARRIVE
        ManeuverType.MERGE -> Turn.MERGE
        ManeuverType.ROUNDABOUT, ManeuverType.ROTARY, ManeuverType.ROUNDABOUT_TURN,
        ManeuverType.EXIT_ROUNDABOUT, ManeuverType.EXIT_ROTARY ->
            if (modifier.isLeft()) Turn.ROUNDABOUT_LEFT else Turn.ROUNDABOUT_RIGHT
        ManeuverType.ON_RAMP, ManeuverType.OFF_RAMP ->
            if (modifier.isLeft()) Turn.RAMP_LEFT else if (modifier.isRight()) Turn.RAMP_RIGHT else byModifier(modifier)
        ManeuverType.FORK -> if (modifier.isLeft()) Turn.FORK_LEFT else Turn.FORK_RIGHT
        else -> byModifier(modifier)
    }

    private fun byModifier(modifier: ManeuverModifier?): Turn = when (modifier) {
        ManeuverModifier.U_TURN -> Turn.U_TURN
        ManeuverModifier.SHARP_RIGHT -> Turn.SHARP_RIGHT
        ManeuverModifier.RIGHT -> Turn.RIGHT
        ManeuverModifier.SLIGHT_RIGHT -> Turn.SLIGHT_RIGHT
        ManeuverModifier.SLIGHT_LEFT -> Turn.SLIGHT_LEFT
        ManeuverModifier.LEFT -> Turn.LEFT
        ManeuverModifier.SHARP_LEFT -> Turn.SHARP_LEFT
        ManeuverModifier.STRAIGHT, null -> Turn.STRAIGHT
    }

    private fun ManeuverModifier?.isLeft() = this == ManeuverModifier.LEFT || this == ManeuverModifier.SLIGHT_LEFT || this == ManeuverModifier.SHARP_LEFT
    private fun ManeuverModifier?.isRight() = this == ManeuverModifier.RIGHT || this == ManeuverModifier.SLIGHT_RIGHT || this == ManeuverModifier.SHARP_RIGHT

    /**
     * Route options sent to Valhalla: miles, and tolls avoided (strongly, not forbidden, so a
     * place only reachable by toll road still gets a route) when [avoidTolls].
     */
    fun valhallaOptions(avoidTolls: Boolean): Map<String, Any> = mapOf(
        "units" to "miles",
        "costing_options" to mapOf("auto" to mapOf("use_tolls" to if (avoidTolls) 0.0 else 0.5)),
    )
}

/** The arrow for the next turn. */
enum class Turn {
    STRAIGHT, SLIGHT_LEFT, LEFT, SHARP_LEFT, SLIGHT_RIGHT, RIGHT, SHARP_RIGHT, U_TURN,
    MERGE, FORK_LEFT, FORK_RIGHT, RAMP_LEFT, RAMP_RIGHT, ROUNDABOUT_LEFT, ROUNDABOUT_RIGHT, ARRIVE,
}
