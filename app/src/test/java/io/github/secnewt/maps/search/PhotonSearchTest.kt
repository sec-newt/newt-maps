package io.github.secnewt.maps.search

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.secnewt.maps.link.HttpStatusException
import io.github.secnewt.maps.link.Transport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.URLDecoder

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PhotonSearchTest {

    private val garland = 32.91 to -96.64

    private class FakeTransport(private val answer: (String) -> String) : Transport {
        val asked = mutableListOf<String>()
        override fun get(url: String): String { asked += url; return answer(url) }
    }

    private fun query(url: String) = URLDecoder.decode(url.substringAfter("q=").substringBefore('&'), Charsets.UTF_8)

    private fun feature(name: String?, lat: Double, lng: Double, number: String? = null, street: String? = null, city: String? = null, country: String = "US") =
        """{"type":"Feature","geometry":{"type":"Point","coordinates":[$lng,$lat]},"properties":{""" +
            listOfNotNull(
                name?.let { "\"name\":\"$it\"" },
                number?.let { "\"housenumber\":\"$it\"" },
                street?.let { "\"street\":\"$it\"" },
                city?.let { "\"city\":\"$it\"" },
                "\"countrycode\":\"$country\"",
            ).joinToString(",") + "}}"

    private fun answer(vararg features: String) = """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""

    private val casaLinda = feature("El Fenix", 32.8330, -96.7007, "255", "Casa Linda Plaza", "Dallas")
    private val plano = feature("El Fenix", 33.0198, -96.7000, null, "Central Expressway", "Plano")

    @Test fun manyResultsNearMe() {
        val t = FakeTransport { answer(casaLinda, plano) }
        val found = PhotonSearch(t).search("El Fenix", garland) as SearchOutcome.Results
        assertEquals(listOf("El Fenix", "El Fenix"), found.hits.map { it.name })
        assertEquals("255 Casa Linda Plaza, Dallas", found.hits[0].address)
        assertEquals("Central Expressway, Plano", found.hits[1].address)
        assertEquals(32.8330, found.hits[0].lat, 1e-9)
        assertEquals(-96.7007, found.hits[0].lng, 1e-9)
        assertNull(found.shortenedTo)
        val url = t.asked.single()
        assertTrue(url.startsWith("https://photon.komoot.io/api/?"))
        assertTrue("lat=32.91" in url && "lon=-96.64" in url && "limit=" in url)
        assertEquals("El Fenix", query(url))
    }

    @Test fun distanceInMiles() {
        val found = PhotonSearch(FakeTransport { answer(casaLinda) }).search("El Fenix", garland) as SearchOutcome.Results
        // Garland to Casa Linda: 5.3 mi south and 3.5 mi west, so 6.4 mi in a straight line.
        assertEquals(6.38, found.hits[0].miles!!, 0.02)
    }

    @Test fun withoutALocationThereIsNoDistance() {
        val t = FakeTransport { answer(casaLinda) }
        val found = PhotonSearch(t).search("El Fenix", null) as SearchOutcome.Results
        assertNull(found.hits[0].miles)
        assertTrue("lat=" !in t.asked.single())
    }

    @Test fun farAwayResultsHideWhenNearOnesExist() {
        val collegeStation = feature("Frankie's Food Mart", 30.62, -96.33, city = "College Station")
        val garlandOne = feature("Frankie's Place", 32.93, -96.65, "1914", "North Plano Road", "Garland")
        val found = PhotonSearch(FakeTransport { answer(collegeStation, garlandOne) }).search("Frankie's", garland) as SearchOutcome.Results
        assertEquals(listOf("Frankie's Place"), found.hits.map { it.name })
    }

    @Test fun farAwayResultsStayWhenThatsAllThereIs() {
        val bronx = feature("Frankie's", 40.85, -73.88, city = "New York")
        val found = PhotonSearch(FakeTransport { answer(bronx) }).search("Frankie's", garland) as SearchOutcome.Results
        assertEquals(1, found.hits.size)
    }

    @Test fun otherCountriesAreLeftOut() {
        val found = PhotonSearch(FakeTransport { answer(feature("El Fenix", 19.4, -99.1, country = "MX"), casaLinda) }).search("El Fenix", garland) as SearchOutcome.Results
        assertEquals(listOf("Dallas"), found.hits.map { it.address.substringAfterLast(", ") })
    }

    @Test fun anAddressWithNoNameIsNamedByItsAddress() {
        val found = PhotonSearch(FakeTransport { answer(feature(null, 32.86, -96.63, "5949", "Broadway Boulevard", "Garland")) }).search("5949 Broadway", garland) as SearchOutcome.Results
        assertEquals("5949 Broadway Boulevard", found.hits[0].name)
        assertEquals("Garland", found.hits[0].address)
    }

    @Test fun theSamePlaceTwiceShowsOnce() {
        val found = PhotonSearch(FakeTransport { answer(casaLinda, feature("El Fenix", 32.8331, -96.7006, "255", "Casa Linda Plaza", "Dallas")) }).search("El Fenix", garland) as SearchOutcome.Results
        assertEquals(1, found.hits.size)
    }

    @Test fun nothingFoundDropsWordsFromTheEndUntilSomethingNearby() {
        val alamo = feature("Alamo Drafthouse Cinema", 32.8675, -96.7416, "6770", "Abrams Road", "Dallas")
        val woodbury = feature("Alamo Drafthouse", 44.92, -92.93, city = "Woodbury")
        val t = FakeTransport { url ->
            when (query(url)) {
                "Alamo Drafthouse Lake Highlands" -> answer()
                "Alamo Drafthouse Lake" -> answer(woodbury) // far away, so not good enough
                "Alamo Drafthouse" -> answer(alamo)
                else -> error("asked for ${query(url)}")
            }
        }
        val found = PhotonSearch(t).search("Alamo Drafthouse Lake Highlands", garland) as SearchOutcome.Results
        assertEquals("Alamo Drafthouse", found.shortenedTo)
        assertEquals(listOf("Alamo Drafthouse Cinema"), found.hits.map { it.name })
    }

    @Test fun neverDropsDownToASingleWord() {
        val t = FakeTransport { answer() }
        assertEquals(SearchOutcome.NotFound, PhotonSearch(t).search("Alamo lake highlands", garland))
        assertEquals(listOf("Alamo lake highlands", "Alamo lake"), t.asked.map(::query))
    }

    @Test fun oneWordMissIsNotFound() {
        val t = FakeTransport { answer() }
        assertEquals(SearchOutcome.NotFound, PhotonSearch(t).search("zzzz", garland))
        assertEquals(1, t.asked.size)
    }

    @Test fun offline() =
        assertEquals(SearchOutcome.NoInternet, PhotonSearch(FakeTransport { throw IOException("offline") }).search("El Fenix", garland))

    @Test fun serverErrorIsNotOffline() =
        assertEquals(SearchOutcome.ServerProblem, PhotonSearch(FakeTransport { throw HttpStatusException(503) }).search("El Fenix", garland))

    @Test fun garbageAnswerIsNotFound() =
        assertEquals(SearchOutcome.NotFound, PhotonSearch(FakeTransport { "<html>" }).search("El Fenix", garland))
}
