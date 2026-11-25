package io.github.secnewt.maps.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoordinatesTest {

    private fun assertAt(lat: Double, lng: Double, text: String) {
        val found = Coordinates.parse(text) ?: throw AssertionError("no coordinates in \"$text\"")
        assertEquals("lat of \"$text\"", lat, found.first, 1e-4)
        assertEquals("lng of \"$text\"", lng, found.second, 1e-4)
    }

    @Test fun googleCopyCoordinates() = assertAt(32.9126, -96.6389, "32.9126, -96.6389")
    @Test fun noSpace() = assertAt(32.9126, -96.6389, "32.9126,-96.6389")
    @Test fun spaceOnly() = assertAt(32.9126, -96.6389, "32.9126 -96.6389")
    @Test fun paddedAndInParens() = assertAt(32.9126, -96.6389, "  (32.9126, -96.6389) ")
    @Test fun hemispheres() = assertAt(32.9126, -96.6389, "32.9126° N, 96.6389° W")
    @Test fun hemispheresTight() = assertAt(32.9126, -96.6389, "32.9126N 96.6389W")
    @Test fun southEast() = assertAt(-33.8688, 151.2093, "33.8688 S, 151.2093 E")
    @Test fun wholeDegreesWithHemispheres() = assertAt(32.0, -96.0, "32N 96W")
    @Test fun degreesMinutesSeconds() = assertAt(32.912611, -96.638889, "32°54'45.4\"N 96°38'20.0\"W")
    @Test fun degreesMinutesOnly() = assertAt(32.9, -96.5, "32°54'N 96°30'W")

    @Test fun addressesAreNotCoordinates() {
        assertNull(Coordinates.parse("1500 Marilla St"))
        assertNull(Coordinates.parse("75 40"))
        assertNull(Coordinates.parse("200, 300"))
        assertNull(Coordinates.parse("El Fenix"))
    }

    @Test fun outOfRange() {
        assertNull(Coordinates.parse("95.1, -96.6"))
        assertNull(Coordinates.parse("32.9, -196.6"))
        assertNull(Coordinates.parse("32°61'N 96°30'W"))
    }

    @Test fun signAndHemisphereTogetherIsNotCoordinates() = assertNull(Coordinates.parse("-32.9 N, 96.6 W"))
}
