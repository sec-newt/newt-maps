package io.github.secnewt.maps.link

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.URLDecoder

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NominatimGeocoderTest {

    private val hit = """[{"lat":"32.8432023","lon":"-96.5956845","type":"house"}]"""

    private class FakeTransport(private val answer: (String) -> String) : Transport {
        val asked = mutableListOf<String>()
        override fun get(url: String): String { asked += url; return answer(url) }
    }

    private fun query(url: String) = URLDecoder.decode(url.substringAfter("q=").substringBefore('&'), Charsets.UTF_8)

    @Test fun hitFirstTime() {
        val t = FakeTransport { hit }
        assertEquals(GeoResult.Found(32.8432023, -96.5956845), NominatimGeocoder(t, clock = { 0 }, sleep = {}).find("5949 Broadway Blvd Garland TX"))
        val url = t.asked.single()
        assertTrue(url.startsWith("https://nominatim.openstreetmap.org/search?"))
        assertTrue("format=jsonv2" in url && "limit=1" in url && "countrycodes=us" in url)
        assertEquals("5949 Broadway Blvd Garland TX", query(url))
    }

    @Test fun missThenRetryWithoutBusinessName() {
        val t = FakeTransport { url -> if (query(url).startsWith("The Catch")) "[]" else hit }
        val slept = mutableListOf<Long>()
        val result = NominatimGeocoder(t, clock = { 1_000 }, sleep = { slept += it }).find("The Catch 5949 Broadway Blvd Garland TX")
        assertEquals(GeoResult.Found(32.8432023, -96.5956845), result)
        assertEquals(listOf("The Catch 5949 Broadway Blvd Garland TX", "5949 Broadway Blvd Garland TX"), t.asked.map(::query))
        assertEquals(listOf(1_000L), slept) // waited a full second before the second request
    }

    @Test fun missTwice() =
        assertEquals(GeoResult.NotFound, NominatimGeocoder(FakeTransport { "[]" }, clock = { 0 }, sleep = {}).find("The Catch 5949 Broadway Blvd"))

    @Test fun missWithNothingToRetry() {
        val t = FakeTransport { "[]" }
        assertEquals(GeoResult.NotFound, NominatimGeocoder(t, clock = { 0 }, sleep = {}).find("Firewheel Town Center"))
        assertEquals(1, t.asked.size)
    }

    @Test fun offline() {
        val t = FakeTransport { throw IOException("offline") }
        assertEquals(GeoResult.NoInternet, NominatimGeocoder(t, clock = { 0 }, sleep = {}).find("4402 Broadway Blvd"))
        assertEquals(1, t.asked.size)
    }

    @Test fun garbageAnswerIsNotFound() =
        assertEquals(GeoResult.NotFound, NominatimGeocoder(FakeTransport { "<html>" }, clock = { 0 }, sleep = {}).find("4402 Broadway Blvd"))

    @Test fun serverErrorIsNotFoundNotOffline() = assertEquals(
        GeoResult.NotFound,
        NominatimGeocoder(FakeTransport { throw HttpStatusException(503) }, clock = { 0 }, sleep = {}).find("4402 Broadway Blvd"),
    )
}
