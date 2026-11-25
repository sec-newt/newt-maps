package io.github.secnewt.maps.search

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.secnewt.maps.link.Transport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.URLDecoder

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PlaceSearchTest {

    private val garland = 32.91 to -96.64

    /** Answers by server: Photon search, Photon reverse, Census. */
    private class Servers(
        val photon: (String) -> String = { EMPTY },
        val reverse: () -> String = { feature(null, 32.91, -96.64, city = "Garland", state = "Texas") .let { collection(it) } },
        val census: (String) -> String = { censusAnswer() },
    ) : Transport {
        val asked = mutableListOf<String>()
        override fun get(url: String): String {
            asked += url
            return when {
                url.startsWith(CensusAddresses.BASE_URL) -> census(URLDecoder.decode(url.substringAfter("address=").substringBefore('&'), Charsets.UTF_8))
                url.startsWith(PhotonSearch.REVERSE_URL) -> reverse()
                else -> photon(url)
            }
        }
        fun censusAsks() = asked.filter { it.startsWith(CensusAddresses.BASE_URL) }
            .map { URLDecoder.decode(it.substringAfter("address=").substringBefore('&'), Charsets.UTF_8) }
    }

    private fun search(servers: Servers) = PlaceSearch(PhotonSearch(servers), CensusAddresses(servers))

    @Test fun coordinatesAreAPinWithoutAskingAnyone() {
        val servers = Servers()
        val found = search(servers).search("32.9126, -96.6389", garland) as SearchOutcome.Results
        val pin = found.hits.single()
        assertEquals(PlaceSearch.DROPPED_PIN, pin.name)
        assertEquals("32.9126, -96.6389", pin.address)
        assertEquals(32.9126, pin.lat, 1e-9)
        assertEquals(-96.6389, pin.lng, 1e-9)
        assertTrue(servers.asked.isEmpty())
    }

    @Test fun addressOsmLacksComesFromTheCensus() {
        // Photon has only the road; the Census has the address.
        val road = feature("Marilla Street", 32.7770, -96.7965, city = "Dallas", key = "highway")
        val servers = Servers(
            photon = { collection(road) },
            census = { address -> if (address == "1500 Marilla, Texas") censusAnswer(cityHall) else censusAnswer() },
        )
        val found = search(servers).search("1500 Marilla", garland) as SearchOutcome.Results
        assertEquals(listOf("1500 Marilla St"), found.hits.map { it.name })
        assertEquals("Dallas, TX 75201", found.hits[0].address)
        assertEquals(32.7767, found.hits[0].lat, 1e-4)
        assertEquals(listOf("1500 Marilla", "1500 Marilla, Garland, Texas", "1500 Marilla, Texas"), servers.censusAsks())
    }

    @Test fun businessNameBeforeTheAddressIsDroppedForTheCensus() {
        val servers = Servers(census = { address -> if (address.startsWith("1500 marilla")) censusAnswer(cityHall) else censusAnswer() })
        val found = search(servers).search("city hall 1500 marilla", garland) as SearchOutcome.Results
        assertEquals("1500 Marilla St", found.hits.first().name)
        assertEquals("1500 marilla", servers.censusAsks().first())
    }

    @Test fun cityTypedNeedsNoLookupOfMine() {
        val servers = Servers(census = { censusAnswer(cityHall) })
        search(servers).search("1500 Marilla St, Dallas", garland)
        assertTrue(servers.asked.none { it.startsWith(PhotonSearch.REVERSE_URL) })
    }

    @Test fun nearestTownFirstAndFarOnesHidden() {
        val servers = Servers(census = { censusAnswer(quinlan, garlandFifth, abilene) })
        val found = search(servers).search("200 N 5th St", garland) as SearchOutcome.Results
        assertEquals(listOf("Garland, TX 75040", "Quinlan, TX 75474"), found.hits.map { it.address })
    }

    @Test fun photonsSameAddressInAnotherTownComesAfterTheNearOne() {
        // From Garland, Photon knows 200 N Fifth St only in Wylie.
        val wylie = feature(null, 33.015, -96.533, "200", "North Fifth Street", "Wylie")
        val servers = Servers(photon = { collection(wylie) }, census = { censusAnswer(garlandFifth) })
        val found = search(servers).search("200 N Fifth St", garland) as SearchOutcome.Results
        assertEquals(listOf("Garland, TX 75040", "Wylie"), found.hits.map { it.address })
    }

    @Test fun theSameSpotFromBothShowsOnce() {
        val osm = feature(null, 32.9131, -96.6362, "200", "North 5th Street", "Garland")
        val servers = Servers(photon = { collection(osm) }, census = { censusAnswer(garlandFifth) })
        val found = search(servers).search("200 N 5th St", garland) as SearchOutcome.Results
        assertEquals(listOf("200 N 5th St"), found.hits.map { it.name })
    }

    @Test fun placesStayButRoadPiecesGo() {
        val road = feature("Marilla Street", 32.7770, -96.7965, city = "Dallas", key = "highway")
        val library = feature("Dallas Public Library", 32.7795, -96.7970, "1515", "Marilla Street", "Dallas")
        val servers = Servers(photon = { collection(road, library) }, census = { censusAnswer(cityHall) })
        val found = search(servers).search("1500 Marilla", garland) as SearchOutcome.Results
        assertEquals(listOf("1500 Marilla St", "Dallas Public Library"), found.hits.map { it.name })
    }

    @Test fun censusDownLeavesPhotonsAnswer() {
        val road = feature("Marilla Street", 32.7770, -96.7965, city = "Dallas", key = "highway")
        val servers = Servers(photon = { collection(road) }, census = { throw IOException("down") })
        val found = search(servers).search("1500 Marilla", garland) as SearchOutcome.Results
        assertEquals(listOf("Marilla Street"), found.hits.map { it.name })
    }

    @Test fun nothingAnywhereIsNotFound() {
        assertEquals(SearchOutcome.NotFound, search(Servers()).search("99999 Nowhere Rd", garland))
    }

    @Test fun noHouseNumberNeverAsksTheCensus() {
        val servers = Servers()
        search(servers).search("El Fenix", garland)
        assertTrue(servers.censusAsks().isEmpty())
    }

    @Test fun streetAddressPart() {
        assertEquals("1500 Marilla St", PlaceSearch.streetAddress(" 1500 Marilla St "))
        assertEquals("1500 marilla", PlaceSearch.streetAddress("city hall 1500 marilla"))
        assertEquals(null, PlaceSearch.streetAddress("El Fenix"))
    }

    @Test fun censusAddressIsTidied() {
        val hit = CensusAddresses.hit("200 N 5TH ST, GARLAND, TX, 75040", 32.9, -96.6)!!
        assertEquals("200 N 5th St", hit.name)
        assertEquals("Garland, TX 75040", hit.address)
    }

    companion object {
        const val EMPTY = """{"type":"FeatureCollection","features":[]}"""
        val cityHall = Triple("1500 MARILLA ST, DALLAS, TX, 75201", 32.7767, -96.7958)
        val garlandFifth = Triple("200 N 5TH ST, GARLAND, TX, 75040", 32.913, -96.636)
        val quinlan = Triple("200 N 5TH ST, QUINLAN, TX, 75474", 32.912, -96.138)
        val abilene = Triple("200 N 5TH ST, ABILENE, TX, 79601", 32.453, -99.724)

        fun censusAnswer(vararg matches: Triple<String, Double, Double>) =
            """{"result":{"addressMatches":[""" + matches.joinToString(",") { (address, lat, lng) ->
                """{"matchedAddress":"$address","coordinates":{"x":$lng,"y":$lat}}"""
            } + "]}}"

        fun feature(name: String?, lat: Double, lng: Double, number: String? = null, street: String? = null, city: String? = null, state: String? = null, key: String? = null) =
            """{"type":"Feature","geometry":{"type":"Point","coordinates":[$lng,$lat]},"properties":{""" +
                listOfNotNull(
                    name?.let { "\"name\":\"$it\"" },
                    number?.let { "\"housenumber\":\"$it\"" },
                    street?.let { "\"street\":\"$it\"" },
                    city?.let { "\"city\":\"$it\"" },
                    state?.let { "\"state\":\"$it\"" },
                    key?.let { "\"osm_key\":\"$it\"" },
                    "\"countrycode\":\"US\"",
                ).joinToString(",") + "}}"

        fun collection(vararg features: String) = """{"type":"FeatureCollection","features":[${features.joinToString(",")}]}"""
    }
}
