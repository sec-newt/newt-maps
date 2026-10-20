package io.github.secnewt.maps.link

import io.github.secnewt.maps.link.LinkResult.Place
import io.github.secnewt.maps.link.LinkResult.Search
import io.github.secnewt.maps.link.LinkResult.Short
import io.github.secnewt.maps.link.LinkResult.Unreadable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkReaderTest {

    // --- Jerry's real links (PLAN.md, 2026-10-19) ---

    @Test fun realGoogleSearchWithBusinessName() = assertEquals(
        Search("The Catch 5949 Broadway Blvd Garland TX"),
        LinkReader.read("https://www.google.com/maps/search/?api=1&query=The%20Catch%205949%20Broadway%20Blvd%20Garland%20TX&query_place_id=ChIJ8TWioYqnToYR03QYbJ8D4QE"),
    )

    @Test fun realGoogleSearchWithPlusSigns() = assertEquals(
        Search("Dickies Arena 1911 Montgomery St Fort Worth TX 76107"),
        LinkReader.read("https://www.google.com/maps/search/?api=1&query=Dickies+Arena+1911+Montgomery+St+Fort+Worth+TX+76107"),
    )

    @Test fun realOldGoogleLink() = assertEquals(
        Search("Clements University Hospital Parking Garage B, 6201 Harry Hines Blvd, Dallas, TX 75390"),
        LinkReader.read("https://maps.google.com/?q=Clements+University+Hospital+Parking+Garage+B,+6201+Harry+Hines+Blvd,+Dallas,+TX+75390"),
    )

    @Test fun realOldGoogleLinkAddressOnly() =
        assertEquals(Search("4402 Broadway Blvd"), LinkReader.read("https://maps.google.com/?q=4402+Broadway+Blvd"))

    @Test fun realAppleShortLink() =
        assertEquals(Short("https://maps.apple/p/w~VGC-HHBheq~7"), LinkReader.read("https://maps.apple/p/w~VGC-HHBheq~7"))

    @Test fun realAppleShortLinkTarget() = assertEquals(
        Place(32.749302, -96.681877, name = "Gonzalez Restaurants", address = "8121 Bruton Rd, Dallas, TX  75217, United States"),
        LinkReader.read("https://maps.apple.com/place?address=8121%20Bruton%20Rd,%20Dallas,%20TX%20%2075217,%20United%20States&coordinate=32.749302,-96.681877&name=Gonzalez%20Restaurants&place-id=ICA1E0D07F4B555E7&map=explore"),
    )

    // --- Google, with coordinates ---

    @Test fun googlePinWinsOverMapCentre() = assertEquals(
        Place(32.9101, -96.6302, name = "The Catch"),
        LinkReader.read("https://www.google.com/maps/place/The+Catch/@32.9,-96.6,17z/data=!3m1!4b1!4m6!3m5!1s0x0:0x0!8m2!3d32.9101!4d-96.6302"),
    )

    @Test fun googleMapCentreWhenNoPin() =
        assertEquals(Place(32.91, -96.63), LinkReader.read("https://www.google.com/maps/@32.91,-96.63,15z"))

    @Test fun googleQueryThatIsCoordinates() =
        assertEquals(Place(32.91, -96.63), LinkReader.read("https://www.google.com/maps/search/?api=1&query=32.91,-96.63"))

    @Test fun googlePlaceNameWithoutCoordinates() =
        assertEquals(Search("Whataburger", name = "Whataburger"), LinkReader.read("https://www.google.com/maps/place/Whataburger/"))

    @Test fun googleShortLinks() {
        assertEquals(Short("https://maps.app.goo.gl/AbCd123"), LinkReader.read("https://maps.app.goo.gl/AbCd123"))
        assertEquals(Short("https://goo.gl/maps/XyZ"), LinkReader.read("https://goo.gl/maps/XyZ"))
    }

    // --- Apple, older form ---

    @Test fun appleLlAndQ() =
        assertEquals(Place(32.91, -96.63, name = "Home Depot"), LinkReader.read("https://maps.apple.com/?ll=32.91,-96.63&q=Home%20Depot"))

    @Test fun appleQueryOnly() =
        assertEquals(Search("Firewheel Town Center"), LinkReader.read("https://maps.apple.com/?q=Firewheel+Town+Center"))

    // --- geo: ---

    @Test fun geoWithLabel() =
        assertEquals(Place(32.91, -96.63, name = "Church"), LinkReader.read("geo:32.91,-96.63?q=32.91,-96.63(Church)"))

    @Test fun geoPlain() = assertEquals(Place(32.91, -96.63), LinkReader.read("geo:32.91,-96.63"))

    @Test fun geoZeroWithTextQuery() =
        assertEquals(Search("4402 Broadway Blvd"), LinkReader.read("geo:0,0?q=4402%20Broadway%20Blvd"))

    // --- messy input ---

    @Test fun linkInsideSharedText() = assertEquals(
        Search("4402 Broadway Blvd"),
        LinkReader.read("Meet me here: https://maps.google.com/?q=4402+Broadway+Blvd. See you!"),
    )

    @Test fun outOfRangeCoordinates() = assertEquals(Unreadable, LinkReader.read("geo:95.0,-96.63"))

    @Test fun notAMapsLink() = assertEquals(Unreadable, LinkReader.read("https://www.youtube.com/watch?v=YUYxR-md8HM"))

    @Test fun noLinkAtAll() = assertEquals(Unreadable, LinkReader.read("see you at six"))

    @Test fun googleWithNothingUseful() = assertEquals(Unreadable, LinkReader.read("https://www.google.com/maps"))

    @Test fun firstLinkTrimsTrailingPunctuation() {
        assertEquals("https://maps.google.com/?q=4402+Broadway+Blvd", LinkReader.firstLink("(https://maps.google.com/?q=4402+Broadway+Blvd)."))
        assertNull(LinkReader.firstLink("no link here"))
    }

    @Test fun isShortLink() {
        assertTrue(LinkReader.isShortLink("https://maps.app.goo.gl/AbCd"))
        assertTrue(LinkReader.isShortLink("https://goo.gl/maps/AbCd"))
        assertTrue(LinkReader.isShortLink("https://maps.apple/p/w~VGC"))
        assertFalse(LinkReader.isShortLink("https://goo.gl/other"))
        assertFalse(LinkReader.isShortLink("https://www.google.com/maps/place/X/@1,2,3z"))
    }
}
