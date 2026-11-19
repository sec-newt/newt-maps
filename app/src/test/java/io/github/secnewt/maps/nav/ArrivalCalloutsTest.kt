package io.github.secnewt.maps.nav

import org.junit.Assert.assertEquals
import org.junit.Test
import uniffi.ferrostar.ManeuverModifier

class ArrivalCalloutsTest {

    @Test
    fun `a long last road gets the half-mile call`() {
        assertEquals(ArrivalCallouts.Plan.HALF_MILE, ArrivalCallouts.plan(3000.0, turnBefore = true))
        assertEquals(ArrivalCallouts.Plan.HALF_MILE, ArrivalCallouts.plan(1000.0, turnBefore = false))
    }

    @Test
    fun `a short last road gets it on the last turn instead, never before a turn still to come`() {
        assertEquals(ArrivalCallouts.Plan.ON_LAST_TURN, ArrivalCallouts.plan(103.0, turnBefore = true))
        // Just past half a mile: the half-mile call would land on top of the turn's call.
        assertEquals(ArrivalCallouts.Plan.ON_LAST_TURN, ArrivalCallouts.plan(850.0, turnBefore = true))
        assertEquals(ArrivalCallouts.Plan.NONE, ArrivalCallouts.plan(103.0, turnBefore = false))
    }

    @Test
    fun `the side comes from the arrival's direction`() {
        assertEquals("on the right", ArrivalCallouts.side(ManeuverModifier.RIGHT))
        assertEquals("on the left", ArrivalCallouts.side(ManeuverModifier.SLIGHT_LEFT))
        assertEquals("ahead", ArrivalCallouts.side(null))
        assertEquals("ahead", ArrivalCallouts.side(ManeuverModifier.STRAIGHT))
    }

    @Test
    fun `the calls read naturally`() {
        assertEquals("In half a mile, your destination will be on the right.", ArrivalCallouts.halfMileCall("on the right"))
        assertEquals(
            "Turn right onto Main Street. Your destination will be on the left.",
            ArrivalCallouts.lastTurnCall("Turn right onto Main Street.", "on the left"),
        )
        assertEquals(
            "Turn right onto Main Street. Your destination will be on the left.",
            ArrivalCallouts.lastTurnCall("Turn right onto Main Street", "on the left"),
        )
    }
}
