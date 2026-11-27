package io.github.secnewt.maps.nav

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixAgeTest {

    private val second = 1_000_000_000L
    private val now = 3_600 * second

    @Test fun recentFixIsFresh() = assertTrue(FixAge.isFresh(now - 5 * second, now))

    @Test fun oldFixIsNotFresh() = assertFalse(FixAge.isFresh(now - 20 * 60 * second, now))

    @Test fun fixStampedJustAheadOfTheClockIsFresh() = assertTrue(FixAge.isFresh(now + second / 100, now))

    @Test fun fixFromWellIntoTheFutureIsNotFresh() = assertFalse(FixAge.isFresh(now + 60 * second, now))

    // Android's location updates start by repeating the last known fix, which can be from
    // wherever GPS last ran (the start of the trip). Routing must skip it and wait for a new one.
    @Test fun skipsTheOldFixThatLocationUpdatesStartWith() = runBlocking {
        val oldFromStartOfTrip = now - 40 * 60 * second
        val newFix = now - second
        assertEquals(newFix, FixAge.firstFresh(flowOf(oldFromStartOfTrip, newFix), { it }, { now }))
    }
}
