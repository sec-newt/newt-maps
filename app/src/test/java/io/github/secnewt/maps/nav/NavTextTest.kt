package io.github.secnewt.maps.nav

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test
import uniffi.ferrostar.ManeuverModifier
import uniffi.ferrostar.ManeuverType

class NavTextTest {

    @Test
    fun `distances read like road signs`() {
        assertEquals("50 ft", NavText.distance(3.0))
        assertEquals("300 ft", NavText.distance(91.0))
        assertEquals("0.4 mi", NavText.distance(650.0))
        assertEquals("4.3 mi", NavText.distance(6920.0))
        assertEquals("12 mi", NavText.distance(19_312.0))
    }

    @Test
    fun `times are minutes, then hours and minutes, never zero`() {
        assertEquals("1 min", NavText.duration(10.0))
        assertEquals("12 min", NavText.duration(720.0))
        assertEquals("1 hr 12 min", NavText.duration(4320.0))
    }

    @Test
    fun `arrival is a clock time`() {
        assertEquals("3:45 PM", NavText.arrival(900.0, LocalTime.of(15, 30)))
        assertEquals("12:10 AM", NavText.arrival(1200.0, LocalTime.of(23, 50)))
    }

    @Test
    fun `each turn gets its own arrow`() {
        assertEquals(Turn.RIGHT, NavText.turn(ManeuverType.TURN, ManeuverModifier.RIGHT))
        assertEquals(Turn.SLIGHT_LEFT, NavText.turn(ManeuverType.TURN, ManeuverModifier.SLIGHT_LEFT))
        assertEquals(Turn.U_TURN, NavText.turn(ManeuverType.CONTINUE, ManeuverModifier.U_TURN))
        assertEquals(Turn.RAMP_RIGHT, NavText.turn(ManeuverType.OFF_RAMP, ManeuverModifier.SLIGHT_RIGHT))
        assertEquals(Turn.ROUNDABOUT_LEFT, NavText.turn(ManeuverType.ROUNDABOUT, ManeuverModifier.LEFT))
        assertEquals(Turn.FORK_LEFT, NavText.turn(ManeuverType.FORK, ManeuverModifier.SLIGHT_LEFT))
        assertEquals(Turn.ARRIVE, NavText.turn(ManeuverType.ARRIVE, ManeuverModifier.RIGHT))
        assertEquals(Turn.STRAIGHT, NavText.turn(ManeuverType.DEPART, null))
    }

    @Suppress("UNCHECKED_CAST")
    private fun auto(options: Map<String, Any>) = (options["costing_options"] as Map<String, Any>)["auto"] as Map<String, Any>

    @Test
    fun `avoiding tolls tells Valhalla to stay off them, in miles`() {
        assertEquals(0.0, auto(NavText.valhallaOptions(RouteOptions(avoidTolls = true)))["use_tolls"])
        assertEquals(0.5, auto(NavText.valhallaOptions(RouteOptions(avoidTolls = false)))["use_tolls"])
        assertEquals("miles", NavText.valhallaOptions(RouteOptions(true))["units"])
    }

    @Test
    fun `avoiding highways turns them down all the way`() {
        assertEquals(0.0, auto(NavText.valhallaOptions(RouteOptions(true, avoidHighways = true)))["use_highways"])
        assertEquals(1.0, auto(NavText.valhallaOptions(RouteOptions(true, avoidHighways = false)))["use_highways"])
    }

    @Test
    fun `marked spots go to Valhalla as places to exclude, and only when there are some`() {
        val options = NavText.valhallaOptions(RouteOptions(true, avoid = listOf(Spot(32.85, -96.64))))
        assertEquals(listOf(mapOf("lat" to 32.85, "lon" to -96.64)), options["exclude_locations"])
        assertEquals(null, NavText.valhallaOptions(RouteOptions(true))["exclude_locations"])
    }
}
