package io.github.secnewt.maps.nav

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixAgeTest {

    private val second = 1_000_000_000L
    private val now = 3_600 * second

    @Test fun recentFixIsFresh() = assertTrue(FixAge.isFresh(now - 5 * second, now))

    @Test fun oldFixIsNotFresh() = assertFalse(FixAge.isFresh(now - 20 * 60 * second, now))

    @Test fun fixFromTheFutureIsNotFresh() = assertFalse(FixAge.isFresh(now + second, now))
}
