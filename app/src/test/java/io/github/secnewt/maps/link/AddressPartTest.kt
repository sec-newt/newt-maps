package io.github.secnewt.maps.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddressPartTest {
    @Test fun dropsBusinessName() =
        assertEquals("5949 Broadway Blvd Garland TX", addressPart("The Catch 5949 Broadway Blvd Garland TX"))

    @Test fun dropsNameBeforeComma() = assertEquals(
        "6201 Harry Hines Blvd, Dallas, TX 75390",
        addressPart("Clements University Hospital Parking Garage B, 6201 Harry Hines Blvd, Dallas, TX 75390"),
    )

    @Test fun numberInsideNameIsSkipped() = assertEquals("123 Main St", addressPart("7-Eleven 123 Main St"))

    @Test fun alreadyStartsWithNumber() = assertNull(addressPart("4402 Broadway Blvd"))

    @Test fun noNumber() = assertNull(addressPart("Firewheel Town Center"))
}
