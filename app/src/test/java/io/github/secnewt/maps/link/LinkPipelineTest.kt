package io.github.secnewt.maps.link

import io.github.secnewt.maps.link.LinkResult.Place
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class LinkPipelineTest {

    private val noNetwork = ShortLinkResolver { throw IOException("offline") }

    private class FakeGeocoder(private val answer: GeoResult) : Geocoder {
        val asked = mutableListOf<String>()
        override fun find(text: String): GeoResult { asked += text; return answer }
    }

    @Test fun coordinatesNeedNoNetwork() {
        val geo = FakeGeocoder(GeoResult.NotFound)
        assertEquals(Outcome.Found(Place(32.91, -96.63, "Church")), LinkPipeline(noNetwork, geo).run("geo:32.91,-96.63?q=32.91,-96.63(Church)"))
        assertEquals(emptyList<String>(), geo.asked)
    }

    @Test fun textOnlyIsLookedUpAndNamed() {
        val geo = FakeGeocoder(GeoResult.Found(32.84, -96.59))
        val outcome = LinkPipeline(noNetwork, geo).run("https://maps.google.com/?q=4402+Broadway+Blvd")
        assertEquals(Outcome.Found(Place(32.84, -96.59, name = "4402 Broadway Blvd")), outcome)
        assertEquals(listOf("4402 Broadway Blvd"), geo.asked)
    }

    @Test fun shortLinkResolvedThenRead() {
        val resolver = ShortLinkResolver { Hop(301, "https://maps.apple.com/place?coordinate=32.749302,-96.681877&name=Gonzalez%20Restaurants") }
        assertEquals(
            Outcome.Found(Place(32.749302, -96.681877, name = "Gonzalez Restaurants")),
            LinkPipeline(resolver, FakeGeocoder(GeoResult.NotFound)).run("https://maps.apple/p/w~VGC-HHBheq~7"),
        )
    }

    @Test fun shortLinkToTextOnlyGoesToGeocoder() {
        val resolver = ShortLinkResolver { Hop(302, "https://www.google.com/maps/search/?api=1&query=4402+Broadway+Blvd") }
        val geo = FakeGeocoder(GeoResult.Found(1.0, 2.0))
        assertEquals(Outcome.Found(Place(1.0, 2.0, name = "4402 Broadway Blvd")), LinkPipeline(resolver, geo).run("https://maps.app.goo.gl/x"))
    }

    @Test fun shortLinkOffline() = assertEquals(
        Outcome.NotFound(Why.NO_INTERNET, "https://maps.app.goo.gl/x"),
        LinkPipeline(noNetwork, FakeGeocoder(GeoResult.NotFound)).run("https://maps.app.goo.gl/x"),
    )

    @Test fun shortLinkThatGoesNowhere() = assertEquals(
        Outcome.NotFound(Why.NO_LOCATION, "https://maps.app.goo.gl/x"),
        LinkPipeline(ShortLinkResolver { Hop(200, null) }, FakeGeocoder(GeoResult.NotFound)).run("https://maps.app.goo.gl/x"),
    )

    @Test fun nothingFoundKeepsTheLink() = assertEquals(
        Outcome.NotFound(Why.NOTHING_FOUND, "https://maps.google.com/?q=nowhere+special"),
        LinkPipeline(noNetwork, FakeGeocoder(GeoResult.NotFound)).run("look: https://maps.google.com/?q=nowhere+special"),
    )

    @Test fun geocoderOffline() = assertEquals(
        Outcome.NotFound(Why.NO_INTERNET, "https://maps.google.com/?q=4402+Broadway+Blvd"),
        LinkPipeline(noNetwork, FakeGeocoder(GeoResult.NoInternet)).run("https://maps.google.com/?q=4402+Broadway+Blvd"),
    )

    @Test fun unreadableMapsLink() = assertEquals(
        Outcome.NotFound(Why.NO_LOCATION, "https://www.google.com/maps"),
        LinkPipeline(noNetwork, FakeGeocoder(GeoResult.NotFound)).run("https://www.google.com/maps"),
    )

    @Test fun noLink() = assertEquals(
        Outcome.NotFound(Why.NO_LINK, null),
        LinkPipeline(noNetwork, FakeGeocoder(GeoResult.NotFound)).run("see you at six"),
    )
}
