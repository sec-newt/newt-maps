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

    @Test
    fun `avoiding tolls tells Valhalla to stay off them, in miles`() {
        @Suppress("UNCHECKED_CAST")
        fun tolls(options: Map<String, Any>) = ((options["costing_options"] as Map<String, Any>)["auto"] as Map<String, Any>)["use_tolls"]
        assertEquals(0.0, tolls(NavText.valhallaOptions(avoidTolls = true)))
        assertEquals(0.5, tolls(NavText.valhallaOptions(avoidTolls = false)))
        assertEquals("miles", NavText.valhallaOptions(true)["units"])
    }
}
