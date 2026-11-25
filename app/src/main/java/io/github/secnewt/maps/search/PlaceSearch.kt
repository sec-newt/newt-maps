package io.github.secnewt.maps.search

import io.github.secnewt.maps.handoff.formatCoord
import io.github.secnewt.maps.link.addressPart

/**
 * What the search box does with what I typed:
 * 1. Coordinates → a pin right there, nothing sent anywhere.
 * 2. Otherwise Photon (places, and the addresses OpenStreetMap has).
 * 3. If there's a house number, the Census Bureau's address ranges too, listed first.
 *    Even when Photon has that number: OSM has "200 N Fifth St" in Wylie but not the one
 *    in Garland.
 */
class PlaceSearch(
    private val photon: PhotonSearch = PhotonSearch(),
    private val census: CensusAddresses = CensusAddresses(),
) {
    fun search(text: String, near: Pair<Double, Double>?): SearchOutcome {
        Coordinates.parse(text)?.let { (lat, lng) ->
            val miles = near?.let { PhotonSearch.miles(it.first, it.second, lat, lng) }
            return SearchOutcome.Results(listOf(SearchHit(DROPPED_PIN, "${formatCoord(lat)}, ${formatCoord(lng)}", lat, lng, miles)))
        }
        val found = photon.search(text, near)
        val street = streetAddress(text) ?: return found
        val photonHits = (found as? SearchOutcome.Results)?.hits.orEmpty()
        val addresses = closest(census.find(street) { near?.let(photon::area) }, near)
        if (addresses.isEmpty()) return found
        // The address is the answer; bits of the road it's on are just noise next to it.
        val others = photonHits.filterNot { hit -> hit.road || addresses.any { PhotonSearch.miles(it.lat, it.lng, hit.lat, hit.lng) < 0.05 } }
        return SearchOutcome.Results(addresses + others)
    }

    /** Nearest first, and only the nearby ones when there are any (the same "200 N 5th St" is in 50 Texas towns). */
    private fun closest(hits: List<SearchHit>, near: Pair<Double, Double>?): List<SearchHit> {
        if (near == null) return hits.take(MAX_ADDRESSES)
        val measured = hits.map { it.copy(miles = PhotonSearch.miles(near.first, near.second, it.lat, it.lng)) }.sortedBy { it.miles }
        return measured.filter { it.miles!! <= PhotonSearch.NEARBY_MILES }.ifEmpty { measured }.take(MAX_ADDRESSES)
    }

    companion object {
        const val DROPPED_PIN = "Dropped pin"
        private const val MAX_ADDRESSES = 5

        /**
         * The street address in the search, if it has one: "1500 Marilla St" as is, or
         * "1500 marilla" out of "city hall 1500 marilla".
         */
        internal fun streetAddress(text: String): String? {
            val trimmed = text.trim()
            if (Regex("""^\d+[A-Za-z]?\s+\S""").containsMatchIn(trimmed)) return trimmed
            return addressPart(trimmed)
        }
    }
}
