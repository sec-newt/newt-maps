package io.github.secnewt.maps.link

import io.github.secnewt.maps.link.Resolved.Failed
import io.github.secnewt.maps.link.Resolved.To
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

class ShortLinkResolverTest {

    /** A fake web: each URL answers with a fixed hop; records what was asked. */
    private class FakeWeb(private val answers: Map<String, Hop>) : HopFetcher {
        val asked = mutableListOf<String>()
        override fun fetch(url: String): Hop {
            asked += url
            return answers[url] ?: throw IOException("offline")
        }
    }

    private val appleTarget = "https://maps.apple.com/place?coordinate=32.749302,-96.681877&name=Gonzalez%20Restaurants"

    @Test fun appleShortLinkOneHop() {
        val web = FakeWeb(mapOf("https://maps.apple/p/w~VGC" to Hop(301, appleTarget)))
        assertEquals(To(appleTarget), ShortLinkResolver(web).resolve("https://maps.apple/p/w~VGC"))
        assertEquals(listOf("https://maps.apple/p/w~VGC"), web.asked) // never fetched the target page
    }

    @Test fun googleChainStopsBeforeFullPage() {
        val full = "https://www.google.com/maps/place/X/@32.9,-96.6,17z"
        val web = FakeWeb(mapOf(
            "https://goo.gl/maps/a" to Hop(302, "https://maps.app.goo.gl/b"),
            "https://maps.app.goo.gl/b" to Hop(302, full),
        ))
        assertEquals(To(full), ShortLinkResolver(web).resolve("https://goo.gl/maps/a"))
        assertEquals(2, web.asked.size)
    }

    @Test fun relativeLocationIsResolved() {
        // A relative Location resolves against the current host (goo.gl/x is no longer a short link).
        val web = FakeWeb(mapOf("https://goo.gl/maps/a" to Hop(302, "/x?ll=1.5,2.5")))
        assertEquals(To("https://goo.gl/x?ll=1.5,2.5"), ShortLinkResolver(web).resolve("https://goo.gl/maps/a"))
    }

    @Test fun hopToAnotherCompanyStops() {
        val web = FakeWeb(mapOf("https://maps.app.goo.gl/a" to Hop(302, "https://evil.example/track")))
        assertEquals(Failed(Reason.LEFT_COMPANY), ShortLinkResolver(web).resolve("https://maps.app.goo.gl/a"))
    }

    @Test fun appleLinkMayNotHopToGoogle() {
        val web = FakeWeb(mapOf("https://maps.apple/p/a" to Hop(301, "https://www.google.com/maps/@1,2,3z")))
        assertEquals(Failed(Reason.LEFT_COMPANY), ShortLinkResolver(web).resolve("https://maps.apple/p/a"))
    }

    @Test fun httpHopStops() {
        val web = FakeWeb(mapOf("https://maps.app.goo.gl/a" to Hop(302, "http://www.google.com/maps/@1,2,3z")))
        assertEquals(Failed(Reason.NOT_HTTPS), ShortLinkResolver(web).resolve("https://maps.app.goo.gl/a"))
    }

    @Test fun tooManyHops() {
        val web = FakeWeb((0..9).associate { "https://maps.app.goo.gl/$it" to Hop(302, "https://maps.app.goo.gl/${it + 1}") })
        assertEquals(Failed(Reason.TOO_MANY_HOPS), ShortLinkResolver(web).resolve("https://maps.app.goo.gl/0"))
        assertEquals(5, web.asked.size)
    }

    @Test fun offline() =
        assertEquals(Failed(Reason.NO_INTERNET), ShortLinkResolver(FakeWeb(emptyMap())).resolve("https://maps.app.goo.gl/a"))

    @Test fun noRedirect() {
        val web = FakeWeb(mapOf("https://maps.app.goo.gl/a" to Hop(200, null)))
        assertEquals(Failed(Reason.NO_REDIRECT), ShortLinkResolver(web).resolve("https://maps.app.goo.gl/a"))
    }
}
