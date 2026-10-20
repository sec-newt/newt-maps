# Newt Maps Stage 1 (Link Opener) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Newt app that takes Google/Apple Maps links (and `geo:` links and shared text), works out the place, and hands it to a map app picked from big buttons.

**Architecture:** Plain-Kotlin pieces with injected network seams (`LinkReader` → `ShortLinkResolver` → `Geocoder`, joined by `LinkPipeline`), an Android-facing `MapApps`, an optional WebView-based `GoogleLookup`, and Compose screens (`PickerScreen`, `HomeScreen`) hosted by two activities.

**Tech Stack:** Kotlin, Jetpack Compose + Material 3, `io.github.secnewt:newt-ui` (theme, components), JUnit 4, Robolectric, Roborazzi via `newtScreenshot()` from `newt-test`, `HttpURLConnection`, `org.json`. No new libraries.

**Spec:** `PLAN.md` at the repo root. Read it before starting any task.

## Global Constraints

- Package `io.github.secnewt.maps`; app name "Newt Maps"; `minSdk = 33`, `targetSdk = 35`, `compileSdk = 37` (already set by the template; don't change).
- **No new dependencies.** HTTP is `java.net.HttpURLConnection` (as in Newt Weather's `NoaaClient`); JSON is `org.json`; coroutines come in through Compose.
- **Permissions: `INTERNET` and nothing else.** Every permission must also be listed in `config/allowed-permissions.txt` with a plain-words comment; CI fails otherwise.
- **HTTPS only.** Android already blocks cleartext for this target SDK; never add `usesCleartextTraffic` or a network security config that allows it.
- Short links only contact the link's own company: Google `maps.app.goo.gl`, `goo.gl`, `google.com`, `www.google.com`, `maps.google.com`; Apple `maps.apple`, `maps.apple.com`. Max 5 hops, response bodies never read, 10-second timeout.
- Nominatim: `https://nominatim.openstreetmap.org/search`, params `q`, `format=jsonv2`, `limit=1`, `countrycodes=us`, User-Agent `NewtMaps (https://github.com/sec-newt/newt-maps)`, at most one request per second.
- Nothing goes to Google unless the user taps **Ask Google**.
- House style: follow `NEWT_STYLE.md` (big type, 7:1 contrast, works at 200% text, headings marked with `semantics { heading() }`). Every screen gets `newtScreenshot()` tests in dark, light and `fontScale = 2f`.
- **There is no local JDK or Android SDK on this machine.** All builds and tests run in CI (`.github/workflows/build.yml` → shared `newt-app.yml`). The test loop is: push to the task's branch → `gh pr checks --watch` → read failures with `gh run view --log-failed`. `main` is protected (5 required checks, squash merge only), so work happens on one branch `stage1-link-opener` with one PR, which Jerry merges.
- Commit messages end with:
  ```
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_015peYJuoga8nGbz6VrP1kBs
  ```

## File Structure

```
app/src/main/java/io/github/secnewt/maps/
  link/LinkResult.kt          result types shared by every piece
  link/LinkReader.kt          text/URL → LinkResult (pure Kotlin)
  link/ShortLinkResolver.kt   follows short-link redirects (HopFetcher seam)
  link/Geocoder.kt            Geocoder interface, GeoResult, addressPart()
  link/NominatimGeocoder.kt   Nominatim implementation (Transport seam)
  link/LinkPipeline.kt        runs the four steps → Outcome
  handoff/MapApps.kt          installed geo: apps + handoff intent
  google/GoogleLookup.kt      hidden-WebView "Ask Google"
  ui/PickerScreen.kt          picker / working / not-found / no-apps states
  ui/HomeScreen.kt            launcher screen with "Turn on link opening" card
  PickActivity.kt             receives links, runs the pipeline, shows PickerScreen
  MainActivity.kt             launcher activity (template), shows HomeScreen
app/src/debug/java/io/github/secnewt/maps/
  GoogleProbeActivity.kt      debug-only probe used to prove GoogleLookup in CI
scripts/emulator-google-probe.sh
app/src/test/java/io/github/secnewt/maps/
  link/LinkReaderTest.kt, link/ShortLinkResolverTest.kt, link/AddressPartTest.kt,
  link/NominatimGeocoderTest.kt, link/LinkPipelineTest.kt
  handoff/MapAppsTest.kt
  ui/PickerScreenTest.kt, ui/HomeScreenTest.kt   (WelcomeScreen + its test are deleted)
```

---

### Task 0: Branch

- [ ] **Step 1: Create the working branch**

```bash
cd ~/Git/newt-maps && git checkout -b stage1-link-opener && git push -u origin stage1-link-opener
```

---

### Task 1: LinkReader

**Files:**
- Create: `app/src/main/java/io/github/secnewt/maps/link/LinkResult.kt`
- Create: `app/src/main/java/io/github/secnewt/maps/link/LinkReader.kt`
- Test: `app/src/test/java/io/github/secnewt/maps/link/LinkReaderTest.kt`

**Interfaces:**
- Produces:
  - `sealed interface LinkResult` with `data class Place(val lat: Double, val lng: Double, val name: String? = null, val address: String? = null)`, `data class Search(val text: String, val name: String? = null)`, `data class Short(val url: String)`, `data object Unreadable`
  - `object LinkReader { fun firstLink(text: String): String?; fun isShortLink(url: String): Boolean; fun read(text: String): LinkResult }`

- [ ] **Step 1: Write the result types** (no logic, so no test of their own)

```kotlin
package io.github.secnewt.maps.link

/** What a maps link turned out to contain. */
sealed interface LinkResult {
    /** A location. [name] and [address] are shown in the picker when the link has them. */
    data class Place(
        val lat: Double,
        val lng: Double,
        val name: String? = null,
        val address: String? = null,
    ) : LinkResult

    /** No coordinates, only text to look up ("4402 Broadway Blvd"). */
    data class Search(val text: String, val name: String? = null) : LinkResult

    /** A short link (maps.app.goo.gl, maps.apple/p/…) that must be followed first. */
    data class Short(val url: String) : LinkResult

    data object Unreadable : LinkResult
}
```

- [ ] **Step 2: Write the failing tests** (the real links from `PLAN.md` come first)

```kotlin
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
```

- [ ] **Step 3: Add a stub so the tests compile and fail** (not pass):

```kotlin
package io.github.secnewt.maps.link

object LinkReader {
    fun firstLink(text: String): String? = null
    fun isShortLink(url: String): Boolean = false
    fun read(text: String): LinkResult = LinkResult.Unreadable
}
```

- [ ] **Step 4: Push and watch it fail**

```bash
git add app/src/main/java/io/github/secnewt/maps/link app/src/test/java/io/github/secnewt/maps/link
git commit -m "LinkReader: tests from real links (failing)"   # + trailer lines from Global Constraints
git push
gh pr create --draft --title "Stage 1: link opener" --body "Implements PLAN.md Stage 1. Plan: docs/superpowers/plans/2026-10-19-link-opener.md" # first push only
gh pr checks --watch; gh run view --log-failed | grep -E "LinkReaderTest > .* FAILED" | head -30
```
Expected: the unit-test check FAILS, listing `LinkReaderTest` failures (the `Unreadable`-expecting tests pass).

- [ ] **Step 5: Implement `LinkReader`**

```kotlin
package io.github.secnewt.maps.link

import io.github.secnewt.maps.link.LinkResult.Place
import io.github.secnewt.maps.link.LinkResult.Search
import io.github.secnewt.maps.link.LinkResult.Short
import io.github.secnewt.maps.link.LinkResult.Unreadable
import java.net.URI
import java.net.URLDecoder

/** Reads Google Maps, Apple Maps and geo: links. No network: short links come back as [Short]. */
object LinkReader {

    private val LINK = Regex("""(https?://\S+|geo:\S+)""", RegexOption.IGNORE_CASE)
    private val GOOGLE_PIN = Regex("""!3d(-?\d+(?:\.\d+)?)!4d(-?\d+(?:\.\d+)?)""")
    private val GOOGLE_CENTRE = Regex("""@(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""")
    private val GOOGLE_PLACE_NAME = Regex("""/maps/place/([^/@?]+)""")
    private val COORDS = Regex("""^\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*$""")
    private val COORDS_WITH_LABEL = Regex("""^\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*(?:\((.*)\))?\s*$""")

    private val GOOGLE_HOSTS = setOf("google.com", "www.google.com", "maps.google.com")

    fun firstLink(text: String): String? =
        LINK.find(text)?.value?.trimEnd('.', ',', ')', ']', '!', '?', ';', ':', '"', '\'')

    fun isShortLink(url: String): Boolean {
        val uri = parse(url) ?: return false
        val path = uri.rawPath ?: ""
        return when (uri.host?.lowercase()) {
            "maps.app.goo.gl" -> true
            "goo.gl" -> path.startsWith("/maps")
            "maps.apple" -> path.startsWith("/p/")
            else -> false
        }
    }

    fun read(text: String): LinkResult {
        val link = firstLink(text) ?: return Unreadable
        if (link.startsWith("geo:", ignoreCase = true)) return readGeo(link)
        if (isShortLink(link)) return Short(link)
        val uri = parse(link) ?: return Unreadable
        if (uri.scheme?.lowercase() != "https" && uri.scheme?.lowercase() != "http") return Unreadable
        val host = uri.host?.lowercase() ?: return Unreadable
        return when {
            host in GOOGLE_HOSTS -> readGoogle(link, uri)
            host == "maps.apple.com" -> readApple(uri)
            else -> Unreadable
        }
    }

    private fun readGoogle(link: String, uri: URI): LinkResult {
        val decoded = decode(link)
        val name = GOOGLE_PLACE_NAME.find(uri.rawPath ?: "")?.groupValues?.get(1)?.let(::decode)?.ifBlank { null }
        GOOGLE_PIN.find(decoded)?.let { return place(it, name) }
        GOOGLE_CENTRE.find(decoded)?.let { return place(it, name) }
        val params = params(uri)
        val query = params["query"] ?: params["q"]
        if (query != null) {
            COORDS.find(query)?.let { return place(it, name) }
            if (query.isNotBlank()) return Search(query.trim(), name)
        }
        if (name != null) return Search(name, name)
        return Unreadable
    }

    private fun readApple(uri: URI): LinkResult {
        val params = params(uri)
        val name = (params["name"] ?: params["q"])?.ifBlank { null }
        val address = params["address"]?.ifBlank { null }
        val coords = params["coordinate"] ?: params["ll"]
        if (coords != null) COORDS.find(coords)?.let { return place(it, name, address) }
        val text = params["q"] ?: address
        return if (!text.isNullOrBlank()) Search(text.trim()) else Unreadable
    }

    private fun readGeo(link: String): LinkResult {
        val body = link.substringAfter(':')
        val coordsPart = body.substringBefore('?').substringBefore(';')
        val query = if ('?' in body) params(body.substringAfter('?'))["q"] else null
        val queryCoords = query?.let { COORDS_WITH_LABEL.find(it) }
        if (queryCoords != null) {
            return place(queryCoords, queryCoords.groupValues[3].ifBlank { null })
        }
        val coords = COORDS.find(coordsPart) ?: return Unreadable
        val lat = coords.groupValues[1].toDouble()
        val lng = coords.groupValues[2].toDouble()
        if (lat == 0.0 && lng == 0.0) {
            return if (!query.isNullOrBlank()) Search(query.trim()) else Unreadable
        }
        return place(coords, null)
    }

    private fun place(match: MatchResult, name: String?, address: String? = null): LinkResult {
        val lat = match.groupValues[1].toDouble()
        val lng = match.groupValues[2].toDouble()
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return Unreadable
        return Place(lat, lng, name, address)
    }

    private fun params(uri: URI): Map<String, String> = params(uri.rawQuery ?: "")

    private fun params(rawQuery: String): Map<String, String> =
        rawQuery.split('&').filter { it.isNotEmpty() }.associate { pair ->
            decode(pair.substringBefore('=')) to decode(pair.substringAfter('=', ""))
        }

    private fun decode(s: String): String = try {
        URLDecoder.decode(s, Charsets.UTF_8)
    } catch (_: IllegalArgumentException) {
        s
    }

    private fun parse(url: String): URI? = try {
        URI(url)
    } catch (_: Exception) {
        null
    }
}
```

Note: in `realAppleShortLinkTarget`, `%20%2075217` decodes to two spaces — the expected string keeps both on purpose.

- [ ] **Step 6: Push and watch it pass**

```bash
git add -u && git commit -m "LinkReader: read Google, Apple and geo: links"   # + trailer
git push && gh pr checks --watch
```
Expected: unit tests PASS. If a test fails, fix the code (not the expectation) unless the expectation contradicts `PLAN.md`.

---

### Task 2: ShortLinkResolver

**Files:**
- Create: `app/src/main/java/io/github/secnewt/maps/link/ShortLinkResolver.kt`
- Test: `app/src/test/java/io/github/secnewt/maps/link/ShortLinkResolverTest.kt`

**Interfaces:**
- Consumes: `LinkReader.isShortLink(url: String): Boolean`
- Produces:
  - `data class Hop(val status: Int, val location: String?)`
  - `fun interface HopFetcher { fun fetch(url: String): Hop }` (throws `java.io.IOException` on network failure)
  - `class ShortLinkResolver(fetcher: HopFetcher = HttpHopFetcher())` with `fun resolve(url: String): Resolved` (blocking; call from `Dispatchers.IO`)
  - `sealed interface Resolved { data class To(val url: String); data class Failed(val reason: Reason) }`, `enum class Reason { NO_INTERNET, LEFT_COMPANY, TOO_MANY_HOPS, NOT_HTTPS, NO_REDIRECT }`
  - `class HttpHopFetcher : HopFetcher`

- [ ] **Step 1: Write the failing tests**

```kotlin
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
```

- [ ] **Step 2: Stub, push, watch it fail**

```kotlin
package io.github.secnewt.maps.link

data class Hop(val status: Int, val location: String?)

fun interface HopFetcher { fun fetch(url: String): Hop }

enum class Reason { NO_INTERNET, LEFT_COMPANY, TOO_MANY_HOPS, NOT_HTTPS, NO_REDIRECT }

sealed interface Resolved {
    data class To(val url: String) : Resolved
    data class Failed(val reason: Reason) : Resolved
}

class ShortLinkResolver(private val fetcher: HopFetcher) {
    fun resolve(url: String): Resolved = Resolved.Failed(Reason.NO_REDIRECT)
}
```

```bash
git add app/src && git commit -m "ShortLinkResolver: tests (failing)" && git push && gh pr checks --watch   # + trailer
```
Expected: `ShortLinkResolverTest` failures (only `noRedirect` passes).

- [ ] **Step 3: Implement**

Replace the stub's class with the full file:

```kotlin
package io.github.secnewt.maps.link

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class Hop(val status: Int, val location: String?)

/** One request to a short link, without following the redirect. Throws IOException when offline. */
fun interface HopFetcher { fun fetch(url: String): Hop }

enum class Reason { NO_INTERNET, LEFT_COMPANY, TOO_MANY_HOPS, NOT_HTTPS, NO_REDIRECT }

sealed interface Resolved {
    data class To(val url: String) : Resolved
    data class Failed(val reason: Reason) : Resolved
}

/**
 * Follows a short link's redirects by hand until the address is no longer a short link.
 * Every hop must be https and stay with the link's company; the final page is never fetched.
 */
class ShortLinkResolver(private val fetcher: HopFetcher = HttpHopFetcher()) {

    fun resolve(url: String): Resolved {
        val company = companyOf(url) ?: return Resolved.Failed(Reason.LEFT_COMPANY)
        var current = url
        repeat(MAX_HOPS) {
            val hop = try {
                fetcher.fetch(current)
            } catch (_: IOException) {
                return Resolved.Failed(Reason.NO_INTERNET)
            }
            val location = hop.location
            if (hop.status !in 300..399 || location == null) return Resolved.Failed(Reason.NO_REDIRECT)
            val next = try {
                URI(current).resolve(location).toString()
            } catch (_: Exception) {
                return Resolved.Failed(Reason.NO_REDIRECT)
            }
            val nextUri = URI(next)
            if (nextUri.scheme?.lowercase() != "https") return Resolved.Failed(Reason.NOT_HTTPS)
            if (nextUri.host?.lowercase() !in company) return Resolved.Failed(Reason.LEFT_COMPANY)
            if (!LinkReader.isShortLink(next)) return Resolved.To(next)
            current = next
        }
        return Resolved.Failed(Reason.TOO_MANY_HOPS)
    }

    private fun companyOf(url: String): Set<String>? {
        val host = try { URI(url).host?.lowercase() } catch (_: Exception) { null } ?: return null
        return listOf(GOOGLE, APPLE).firstOrNull { host in it }
    }

    companion object {
        const val MAX_HOPS = 5
        val GOOGLE = setOf("maps.app.goo.gl", "goo.gl", "google.com", "www.google.com", "maps.google.com")
        val APPLE = setOf("maps.apple", "maps.apple.com")
    }
}

/** The real network: one GET, redirects off, body never read. */
class HttpHopFetcher : HopFetcher {
    override fun fetch(url: String): Hop {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            return Hop(connection.responseCode, connection.getHeaderField("Location"))
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val TIMEOUT_MS = 10_000
        const val USER_AGENT = "NewtMaps (https://github.com/sec-newt/newt-maps)"
    }
}
```

- [ ] **Step 4: Push and watch it pass**

```bash
git add -u && git commit -m "ShortLinkResolver: follow short links, Google/Apple hosts only" && git push && gh pr checks --watch   # + trailer
```
Expected: PASS.

---

### Task 3: Geocoder (Nominatim) with the business-name retry

**Files:**
- Create: `app/src/main/java/io/github/secnewt/maps/link/Geocoder.kt`
- Create: `app/src/main/java/io/github/secnewt/maps/link/NominatimGeocoder.kt`
- Test: `app/src/test/java/io/github/secnewt/maps/link/AddressPartTest.kt`
- Test: `app/src/test/java/io/github/secnewt/maps/link/NominatimGeocoderTest.kt`

**Interfaces:**
- Produces:
  - `sealed interface GeoResult { data class Found(val lat: Double, val lng: Double); data object NotFound; data object NoInternet }`
  - `interface Geocoder { fun find(text: String): GeoResult }` (blocking)
  - `fun addressPart(text: String): String?` — text from the first house number on, or `null` if the text already starts with one or has none
  - `fun interface Transport { fun get(url: String): String }` (throws `IOException`)
  - `class NominatimGeocoder(transport: Transport = HttpTransport(), clock: () -> Long = System::currentTimeMillis, sleep: (Long) -> Unit = Thread::sleep) : Geocoder`

- [ ] **Step 1: Write the failing tests**

`AddressPartTest.kt` (pure JVM):

```kotlin
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
```

`NominatimGeocoderTest.kt` (Robolectric, because `org.json` is only real under Robolectric):

```kotlin
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

    @Test fun offline() = assertEquals(
        GeoResult.NoInternet,
        NominatimGeocoder(FakeTransport { throw IOException("offline") }, clock = { 0 }, sleep = {}).find("4402 Broadway Blvd"),
    )

    @Test fun garbageAnswerIsNotFound() =
        assertEquals(GeoResult.NotFound, NominatimGeocoder(FakeTransport { "<html>" }, clock = { 0 }, sleep = {}).find("4402 Broadway Blvd"))
}
```

- [ ] **Step 2: Stub, push, watch it fail**

`Geocoder.kt`:

```kotlin
package io.github.secnewt.maps.link

sealed interface GeoResult {
    data class Found(val lat: Double, val lng: Double) : GeoResult
    data object NotFound : GeoResult
    data object NoInternet : GeoResult
}

/** Looks up text that has no coordinates. Nominatim today; search on zeus later. */
interface Geocoder { fun find(text: String): GeoResult }

fun addressPart(text: String): String? = null
```

`NominatimGeocoder.kt`:

```kotlin
package io.github.secnewt.maps.link

fun interface Transport { fun get(url: String): String }

class NominatimGeocoder(
    private val transport: Transport,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = Thread::sleep,
) : Geocoder {
    override fun find(text: String): GeoResult = GeoResult.NotFound
}
```

```bash
git add app/src && git commit -m "Geocoder: tests (failing)" && git push && gh pr checks --watch   # + trailer
```
Expected: failures in `AddressPartTest` (the two `assertNull` tests pass) and `NominatimGeocoderTest`.

- [ ] **Step 3: Implement `addressPart`** (replace the stub in `Geocoder.kt`)

```kotlin
private val HOUSE_NUMBER = Regex("""(?<![\w-])\d+[A-Za-z]?\s+\S""")

/**
 * "The Catch 5949 Broadway Blvd" → "5949 Broadway Blvd": the text from the first house
 * number on, so a search that failed on the business name can be retried on the address.
 * Null when the text already starts with the number or has none.
 */
fun addressPart(text: String): String? {
    val match = HOUSE_NUMBER.find(text) ?: return null
    if (match.range.first == 0) return null
    return text.substring(match.range.first).trim()
}
```

(`(?<![\w-])` keeps "7" in "7-Eleven" from counting: it needs whitespace after the digits, and the lookbehind stops matches in the middle of a word.)

- [ ] **Step 4: Implement `NominatimGeocoder`** (replace the whole file)

```kotlin
package io.github.secnewt.maps.link

import org.json.JSONArray
import org.json.JSONException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One GET returning the body. Throws IOException when offline or on a non-2xx answer. */
fun interface Transport { fun get(url: String): String }

/**
 * OpenStreetMap's search server. Only the search text is sent, at most once a second
 * (Nominatim's usage policy), with a User-Agent naming the app.
 */
class NominatimGeocoder(
    private val transport: Transport = HttpTransport(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val sleep: (Long) -> Unit = Thread::sleep,
) : Geocoder {

    private var lastRequestAt: Long? = null

    override fun find(text: String): GeoResult {
        val first = search(text)
        if (first != GeoResult.NotFound) return first
        val retry = addressPart(text) ?: return GeoResult.NotFound
        return search(retry)
    }

    @Synchronized
    private fun search(text: String): GeoResult {
        lastRequestAt?.let { last ->
            val wait = MIN_GAP_MS - (clock() - last)
            if (wait > 0) sleep(wait)
        }
        lastRequestAt = clock()
        val url = "$BASE_URL?q=${URLEncoder.encode(text, Charsets.UTF_8)}&format=jsonv2&limit=1&countrycodes=us"
        val body = try {
            transport.get(url)
        } catch (_: IOException) {
            return GeoResult.NoInternet
        }
        return try {
            val results = JSONArray(body)
            if (results.length() == 0) return GeoResult.NotFound
            val top = results.getJSONObject(0)
            GeoResult.Found(top.getString("lat").toDouble(), top.getString("lon").toDouble())
        } catch (_: JSONException) {
            GeoResult.NotFound
        } catch (_: NumberFormatException) {
            GeoResult.NotFound
        }
    }

    companion object {
        const val BASE_URL = "https://nominatim.openstreetmap.org/search"
        private const val MIN_GAP_MS = 1_000L
    }
}

class HttpTransport : Transport {
    override fun get(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", HttpHopFetcher.USER_AGENT)
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("Nominatim answered $status")
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    companion object { private const val TIMEOUT_MS = 10_000 }
}
```

- [ ] **Step 5: Push and watch it pass**

```bash
git add -u && git commit -m "Geocoder: Nominatim lookup with retry on the address alone" && git push && gh pr checks --watch   # + trailer
```
Expected: PASS.

---

### Task 4: LinkPipeline

**Files:**
- Create: `app/src/main/java/io/github/secnewt/maps/link/LinkPipeline.kt`
- Test: `app/src/test/java/io/github/secnewt/maps/link/LinkPipelineTest.kt`

**Interfaces:**
- Consumes: `LinkReader.read/firstLink`, `ShortLinkResolver.resolve` → `Resolved`, `Geocoder.find` → `GeoResult`
- Produces:
  - `sealed interface Outcome { data class Found(val place: LinkResult.Place); data class NotFound(val why: Why, val link: String?) }`
  - `enum class Why { NO_LINK, NO_LOCATION, NO_INTERNET, NOTHING_FOUND }`
  - `class LinkPipeline(resolver: ShortLinkResolver, geocoder: Geocoder)` with `fun run(text: String): Outcome` (blocking)
  - `NotFound.link` is the original link (for **Ask Google** and **Open in browser**); `null` only for `NO_LINK`.

- [ ] **Step 1: Write the failing tests**

```kotlin
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
```

- [ ] **Step 2: Stub, push, watch it fail**

```kotlin
package io.github.secnewt.maps.link

enum class Why { NO_LINK, NO_LOCATION, NO_INTERNET, NOTHING_FOUND }

sealed interface Outcome {
    data class Found(val place: LinkResult.Place) : Outcome
    data class NotFound(val why: Why, val link: String?) : Outcome
}

class LinkPipeline(private val resolver: ShortLinkResolver, private val geocoder: Geocoder) {
    fun run(text: String): Outcome = Outcome.NotFound(Why.NO_LINK, null)
}
```

```bash
git add app/src && git commit -m "LinkPipeline: tests (failing)" && git push && gh pr checks --watch   # + trailer
```
Expected: failures except `noLink`.

- [ ] **Step 3: Implement** (replace the class)

```kotlin
/** The four steps from PLAN.md: read → follow short link → look up text → give up with the link. */
class LinkPipeline(
    private val resolver: ShortLinkResolver = ShortLinkResolver(),
    private val geocoder: Geocoder = NominatimGeocoder(),
) {
    fun run(text: String): Outcome {
        val link = LinkReader.firstLink(text) ?: return Outcome.NotFound(Why.NO_LINK, null)
        var result = LinkReader.read(link)
        if (result is LinkResult.Short) {
            result = when (val resolved = resolver.resolve(result.url)) {
                is Resolved.To -> LinkReader.read(resolved.url)
                is Resolved.Failed -> return Outcome.NotFound(
                    if (resolved.reason == Reason.NO_INTERNET) Why.NO_INTERNET else Why.NO_LOCATION,
                    link,
                )
            }
        }
        return when (result) {
            is LinkResult.Place -> Outcome.Found(result)
            is LinkResult.Search -> when (val found = geocoder.find(result.text)) {
                is GeoResult.Found -> Outcome.Found(LinkResult.Place(found.lat, found.lng, name = result.name ?: result.text))
                GeoResult.NotFound -> Outcome.NotFound(Why.NOTHING_FOUND, link)
                GeoResult.NoInternet -> Outcome.NotFound(Why.NO_INTERNET, link)
            }
            is LinkResult.Short, LinkResult.Unreadable -> Outcome.NotFound(Why.NO_LOCATION, link)
        }
    }
}
```

- [ ] **Step 4: Push and watch it pass**

```bash
git add -u && git commit -m "LinkPipeline: read, follow, look up, or give up with the link" && git push && gh pr checks --watch   # + trailer
```
Expected: PASS.

---

### Task 5: MapApps (who opens geo: links, and the handoff)

**Files:**
- Create: `app/src/main/java/io/github/secnewt/maps/handoff/MapApps.kt`
- Modify: `app/src/main/AndroidManifest.xml` (add `<queries>`)
- Test: `app/src/test/java/io/github/secnewt/maps/handoff/MapAppsTest.kt`

**Interfaces:**
- Consumes: `LinkResult.Place`
- Produces:
  - `data class MapApp(val packageName: String, val label: String)`
  - `class MapApps(context: Context)` with `fun installed(): List<MapApp>` and `fun handoff(place: LinkResult.Place, app: MapApp): Intent`
  - `fun geoUri(place: LinkResult.Place): String` (top-level, pure)

- [ ] **Step 1: Write the failing tests**

```kotlin
package io.github.secnewt.maps.handoff

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.secnewt.maps.link.LinkResult.Place
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MapAppsTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun install(pkg: String, label: String) {
        val info = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = pkg
                name = "$pkg.Main"
                applicationInfo = ApplicationInfo().apply { packageName = pkg; nonLocalizedLabel = label }
            }
            nonLocalizedLabel = label
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0")), info)
    }

    @Test fun knownAppsFirstThenOthersAndNotItself() {
        install("org.example.othermap", "Other Map")
        install("com.generalmagic.magicearth", "Magic Earth")
        install(context.packageName, "Newt Maps")
        install("app.organicmaps", "Organic Maps")
        assertEquals(
            listOf("app.organicmaps", "com.generalmagic.magicearth", "org.example.othermap"),
            MapApps(context).installed().map { it.packageName },
        )
    }

    @Test fun noneInstalled() = assertEquals(emptyList<MapApp>(), MapApps(context).installed())

    @Test fun geoUriWithName() =
        assertEquals("geo:32.91,-96.63?q=32.91,-96.63(The%20Catch)", geoUri(Place(32.91, -96.63, "The Catch")))

    @Test fun geoUriWithoutName() = assertEquals("geo:32.91,-96.63?q=32.91,-96.63", geoUri(Place(32.91, -96.63)))

    @Test fun handoffTargetsTheChosenApp() {
        val intent = MapApps(context).handoff(Place(32.91, -96.63, "X"), MapApp("app.organicmaps", "Organic Maps"))
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("app.organicmaps", intent.`package`)
        assertEquals("geo:32.91,-96.63?q=32.91,-96.63(X)", intent.dataString)
    }
}
```

- [ ] **Step 2: Stub, push, watch it fail**

```kotlin
package io.github.secnewt.maps.handoff

import android.content.Context
import android.content.Intent
import io.github.secnewt.maps.link.LinkResult

data class MapApp(val packageName: String, val label: String)

fun geoUri(place: LinkResult.Place): String = ""

class MapApps(private val context: Context) {
    fun installed(): List<MapApp> = emptyList()
    fun handoff(place: LinkResult.Place, app: MapApp): Intent = Intent()
}
```

```bash
git add app/src && git commit -m "MapApps: tests (failing)" && git push && gh pr checks --watch   # + trailer
```

- [ ] **Step 3: Implement**

```kotlin
package io.github.secnewt.maps.handoff

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import io.github.secnewt.maps.link.LinkResult

data class MapApp(val packageName: String, val label: String)

/** geo:lat,lng?q=lat,lng(name) — the form Organic Maps, CoMaps, Magic Earth and OsmAnd all read. */
fun geoUri(place: LinkResult.Place): String {
    val at = "${place.lat},${place.lng}"
    val label = place.name?.let { "(${Uri.encode(it)})" } ?: ""
    return "geo:$at?q=$at$label"
}

/** The installed apps that open geo: links, known map apps first. Newt Maps leaves itself out. */
class MapApps(private val context: Context) {

    fun installed(): List<MapApp> {
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0"))
        val pm = context.packageManager
        return pm.queryIntentActivities(probe, PackageManager.ResolveInfoFlags.of(0))
            .map { MapApp(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedWith(compareBy({ KNOWN.indexOf(it.packageName).let { i -> if (i < 0) KNOWN.size else i } }, { it.label }))
    }

    fun handoff(place: LinkResult.Place, app: MapApp): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(geoUri(place))).setPackage(app.packageName)

    companion object {
        val KNOWN = listOf(
            "app.organicmaps",
            "app.comaps",
            "app.comaps.fdroid",
            "com.generalmagic.magicearth",
            "net.osmand.plus",
            "net.osmand",
        )
    }
}
```

Note: the debug build's package is `io.github.secnewt.maps.debug`; `context.packageName` covers both builds.

- [ ] **Step 4: Add `<queries>` to the manifest** (inside `<manifest>`, before `<application>`), so Android 11+ shows the app other geo: apps:

```xml
    <!-- Lets the app see which installed apps open map (geo:) links, for the picker. -->
    <queries>
        <intent>
            <action android:name="android.intent.action.VIEW" />
            <data android:scheme="geo" />
        </intent>
    </queries>
```

- [ ] **Step 5: Push and watch it pass**

```bash
git add -u && git commit -m "MapApps: list geo: apps, hand off to the chosen one" && git push && gh pr checks --watch   # + trailer
```
Expected: PASS. If `addResolveInfoForIntent` doesn't match `queryIntentActivities(…, ResolveInfoFlags)` in Robolectric 4.17, switch the test to `shadowOf(pm).addIntentFilterForActivity(ComponentName(pkg, "$pkg.Main"), IntentFilter(Intent.ACTION_VIEW).apply { addDataScheme("geo") })` after `shadowOf(pm).installPackage(PackageInfo …)`; keep the assertions unchanged.

---

### Task 6: Prove "Ask Google" (GoogleLookup) on a CI emulator

This is a gate. If the proof fails, skip the rest of this task, write the result into `PLAN.md` (§ How it works, item 4), and Task 7's `canAskGoogle` stays `false`.

**Files:**
- Create: `app/src/main/java/io/github/secnewt/maps/google/GoogleLookup.kt`
- Create: `app/src/debug/java/io/github/secnewt/maps/GoogleProbeActivity.kt`
- Create: `app/src/debug/AndroidManifest.xml`
- Create: `scripts/emulator-google-probe.sh`
- Modify: `.github/workflows/build.yml` (add an emulator job, modeled on Newt Phone's)

**Interfaces:**
- Consumes: `LinkReader.read(url)` → `LinkResult.Place`
- Produces: `class GoogleLookup(context: Context)` with `fun start(link: String, onDone: (LinkResult.Place?) -> Unit)` and `fun cancel()`. Must be called on the main thread; `onDone` is called once, on the main thread, with `null` on timeout (15 s).

- [ ] **Step 1: Write `GoogleLookup`**

```kotlin
package io.github.secnewt.maps.google

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.secnewt.maps.link.LinkResult
import io.github.secnewt.maps.link.LinkReader

/**
 * "Ask Google": loads the link in an off-screen WebView so Google's own page runs and
 * rewrites its address to the …/@lat,lng form, then reads the coordinates from that address.
 * No cookies are kept. Only used when the user taps Ask Google.
 */
class GoogleLookup(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var done: ((LinkResult.Place?) -> Unit)? = null

    @SuppressLint("SetJavaScriptEnabled") // Google's page only gives up the location by running its script.
    fun start(link: String, onDone: (LinkResult.Place?) -> Unit) {
        cancel()
        done = onDone
        CookieManager.getInstance().setAcceptCookie(false)
        val view = WebView(context)
        view.settings.javaScriptEnabled = true
        view.settings.allowFileAccess = false
        view.settings.allowContentAccess = false
        view.webViewClient = object : WebViewClient() {
            override fun doUpdateVisitedHistory(v: WebView, url: String, isReload: Boolean) = check(url)
            override fun onPageFinished(v: WebView, url: String) = check(url)
        }
        webView = view
        main.postDelayed(::timeout, TIMEOUT_MS)
        view.loadUrl(link)
    }

    fun cancel() {
        main.removeCallbacksAndMessages(null)
        webView?.apply { stopLoading(); destroy() }
        webView = null
        done = null
        CookieManager.getInstance().removeAllCookies(null)
    }

    private fun check(url: String) {
        val result = LinkReader.read(url)
        if (result is LinkResult.Place) finish(result)
    }

    private fun timeout() = finish(null)

    private fun finish(place: LinkResult.Place?) {
        val callback = done ?: return
        cancel()
        callback(place)
    }

    companion object { const val TIMEOUT_MS = 15_000L }
}
```

- [ ] **Step 2: Write the debug-only probe** (`app/src/debug/java/io/github/secnewt/maps/GoogleProbeActivity.kt`)

```kotlin
package io.github.secnewt.maps

import android.app.Activity
import android.os.Bundle
import android.util.Log
import io.github.secnewt.maps.google.GoogleLookup

/** Debug builds only: `am start -n …/.GoogleProbeActivity -d <link>` logs what GoogleLookup found. */
class GoogleProbeActivity : Activity() {
    private lateinit var lookup: GoogleLookup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lookup = GoogleLookup(this)
        val link = intent.dataString ?: return finish()
        lookup.start(link) { place ->
            Log.i("NewtGoogleProbe", if (place == null) "RESULT none" else "RESULT ${place.lat},${place.lng}")
            finish()
        }
    }

    override fun onDestroy() { lookup.cancel(); super.onDestroy() }
}
```

`app/src/debug/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <!-- Debug only: proves "Ask Google" on the CI emulator. Not in release builds. -->
        <activity android:name=".GoogleProbeActivity" android:exported="true" />
    </application>
</manifest>
```

Add `INTERNET` now (the probe needs it). In `app/src/main/AndroidManifest.xml`, replace the "No permissions" comment with:

```xml
    <!-- Internet: to follow short links (Google/Apple only), look up text-only links on
         Nominatim, and "Ask Google" when the user taps it. Listed in config/allowed-permissions.txt. -->
    <uses-permission android:name="android.permission.INTERNET" />
```

and add to `config/allowed-permissions.txt`:

```
# Follow Google/Apple short links, look up text-only links on Nominatim (OpenStreetMap),
# and "Ask Google" when tapped. Nothing else goes out.
android.permission.INTERNET
```

- [ ] **Step 3: Write `scripts/emulator-google-probe.sh`**

```bash
#!/usr/bin/env bash
# CI only: does "Ask Google" get coordinates for a text-only Google link?
# Expected for 4402 Broadway Blvd, Garland: about 32.860,-96.603 (within ~0.01).
set -euo pipefail
apk=$(ls ./*.apk | head -1)
adb install -r "$apk"
pkg=io.github.secnewt.maps.debug
adb logcat -c
adb shell am start -n "$pkg/io.github.secnewt.maps.GoogleProbeActivity" \
  -d "'https://maps.google.com/?q=4402+Broadway+Blvd+Garland+TX'"
for _ in $(seq 1 25); do
  line=$(adb logcat -d -s NewtGoogleProbe:I | grep RESULT || true)
  [ -n "$line" ] && break
  sleep 1
done
echo "probe: ${line:-no answer}"
case "$line" in
  *"RESULT 32.8"*|*"RESULT 32.9"*) echo "✅ Ask Google works"; exit 0 ;;
  *) echo "❌ Ask Google did not produce coordinates"; exit 1 ;;
esac
```

`chmod +x scripts/emulator-google-probe.sh`.

- [ ] **Step 4: Add the emulator job to `.github/workflows/build.yml`** (after the `newt:` job; copy the pinned SHAs exactly from Newt Phone's `build.yml`):

```yaml
  # Newt Maps only: proves "Ask Google" against the real Google page on an emulator.
  google-probe:
    name: Ask Google probe
    if: github.event_name == 'pull_request' || github.event_name == 'workflow_dispatch'
    needs: newt
    runs-on: ubuntu-latest
    permissions:
      contents: read
    steps:
      - uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7
      - uses: actions/download-artifact@9000827ccba6bdab643e8b6fd33ac0654aef8333 # v8
        with:
          name: newt-maps-test-apk
      - name: Enable KVM
        run: |
          echo 'KERNEL=="kvm", GROUP="kvm", MODE="0666", OPTIONS+="static_node=kvm"' | sudo tee /etc/udev/rules.d/99-kvm4all.rules
          sudo udevadm control --reload-rules
          sudo udevadm trigger --name-match=kvm
      - name: Ask Google for a text-only link
        uses: reactivecircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d # v2
        with:
          api-level: 34
          target: google_apis
          arch: x86_64
          disable-animations: true
          script: bash scripts/emulator-google-probe.sh
```

(`target: google_apis` because the `default` image's WebView may be too old for Google's page. This job is **not** a required check; it's a proof.)

- [ ] **Step 5: Push and read the result**

```bash
git add -A app/src scripts .github config && git commit -m "Ask Google: GoogleLookup + emulator probe" && git push   # + trailer
gh pr checks --watch; gh run view --log --job "$(gh run view --json jobs -q '.jobs[] | select(.name|test("Ask Google")) | .databaseId')" | grep -E "probe:|✅|❌"
```

- [ ] **Step 6: Decide**
  - ✅ **Works** (coordinates near 32.86,-96.60): keep everything. Note in `PLAN.md` § How it works item 4: "Proven on the CI emulator <date>."
  - ❌ **Fails** after one fix attempt (e.g. a longer wait, or reading `view.url` on a timer): delete `GoogleLookup.kt`, the probe activity, the debug manifest, the script and the job; in `PLAN.md` replace the "To prove first" sentence with what happened, and remove **Ask Google** from § The screens. Tell Jerry. Task 7 then uses `canAskGoogle = false` everywhere.
  - Commit either way: `git commit -m "Ask Google: proven"` or `"Ask Google: dropped (did not work, see PLAN.md)"`.

---

### Task 7: PickerScreen

**Files:**
- Create: `app/src/main/java/io/github/secnewt/maps/ui/PickerScreen.kt`
- Test: `app/src/test/java/io/github/secnewt/maps/ui/PickerScreenTest.kt`

**Interfaces:**
- Consumes: `LinkResult.Place`, `MapApp`, `Why`
- Produces:
  - `sealed interface PickerState { data class Working(val message: String); data class Ready(val place: LinkResult.Place, val apps: List<MapApp>); data class Failed(val why: Why, val canAskGoogle: Boolean, val canOpenBrowser: Boolean) }`
  - `@Composable fun PickerScreen(state: PickerState, onOpenIn: (MapApp) -> Unit, onCopy: () -> Unit, onOpenBrowser: () -> Unit, onAskGoogle: () -> Unit)`
  - `fun whyText(why: Why): String`

- [ ] **Step 1: Write the failing tests**

```kotlin
package io.github.secnewt.maps.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import io.github.secnewt.maps.handoff.MapApp
import io.github.secnewt.maps.link.LinkResult.Place
import io.github.secnewt.maps.link.Why
import io.github.secnewt.test.newtScreenshot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class PickerScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private val apps = listOf(
        MapApp("app.organicmaps", "Organic Maps"),
        MapApp("com.generalmagic.magicearth", "Magic Earth"),
        MapApp("app.comaps.fdroid", "CoMaps"),
    )
    private val ready = PickerState.Ready(
        Place(32.749302, -96.681877, "Gonzalez Restaurants", "8121 Bruton Rd, Dallas, TX 75217"), apps,
    )

    @Composable private fun screen(state: PickerState, onOpenIn: (MapApp) -> Unit = {}) =
        PickerScreen(state, onOpenIn = onOpenIn, onCopy = {}, onOpenBrowser = {}, onAskGoogle = {})

    @Test fun readyDark() {
        composeRule.newtScreenshot("picker-ready-dark", dark = true) { screen(ready) }
        composeRule.onNodeWithText("Gonzalez Restaurants").assertIsDisplayed()
        composeRule.onNodeWithText("32.749302, -96.681877").assertIsDisplayed()
    }
    @Test fun readyLight() = composeRule.newtScreenshot("picker-ready-light", dark = false) { screen(ready) }
    @Test fun readyLargeText() = composeRule.newtScreenshot("picker-ready-200pct", dark = true, fontScale = 2f) { screen(ready) }

    @Test fun tappingAnAppHandsOff() {
        var chosen: MapApp? = null
        composeRule.setContent { screen(ready) { chosen = it } }
        composeRule.onNodeWithText("Magic Earth", substring = true).performScrollTo().performClick()
        assertEquals("com.generalmagic.magicearth", chosen?.packageName)
    }

    @Test fun droppedPinWhenNoName() {
        composeRule.setContent { screen(PickerState.Ready(Place(32.91, -96.63), apps)) }
        composeRule.onNodeWithText("Dropped pin").assertIsDisplayed()
    }

    @Test fun workingDark() = composeRule.newtScreenshot("picker-working-dark", dark = true) { screen(PickerState.Working("Looking up the address…")) }

    private val failed = PickerState.Failed(Why.NOTHING_FOUND, canAskGoogle = true, canOpenBrowser = true)
    @Test fun failedDark() {
        composeRule.newtScreenshot("picker-failed-dark", dark = true) { screen(failed) }
        composeRule.onNodeWithText("Nothing found for this address").assertIsDisplayed()
        composeRule.onNodeWithText("Ask Google").assertIsDisplayed()
    }
    @Test fun failedLight() = composeRule.newtScreenshot("picker-failed-light", dark = false) { screen(failed) }
    @Test fun failedLargeText() = composeRule.newtScreenshot("picker-failed-200pct", dark = true, fontScale = 2f) { screen(failed) }

    @Test fun noAppsDark() {
        composeRule.newtScreenshot("picker-noapps-dark", dark = true) { screen(ready.copy(apps = emptyList())) }
        composeRule.onNodeWithText("No map app is installed", substring = true).assertIsDisplayed()
    }

    @Test fun whyTexts() {
        assertEquals("No location in this link", whyText(Why.NO_LOCATION))
        assertEquals("No internet", whyText(Why.NO_INTERNET))
        assertEquals("Nothing found for this address", whyText(Why.NOTHING_FOUND))
        assertEquals("No maps link in what was shared", whyText(Why.NO_LINK))
    }
}
```

- [ ] **Step 2: Stub, push, watch it fail**

```kotlin
package io.github.secnewt.maps.ui

import androidx.compose.runtime.Composable
import io.github.secnewt.maps.handoff.MapApp
import io.github.secnewt.maps.link.LinkResult
import io.github.secnewt.maps.link.Why

sealed interface PickerState {
    data class Working(val message: String) : PickerState
    data class Ready(val place: LinkResult.Place, val apps: List<MapApp>) : PickerState
    data class Failed(val why: Why, val canAskGoogle: Boolean, val canOpenBrowser: Boolean) : PickerState
}

fun whyText(why: Why): String = ""

@Composable
fun PickerScreen(
    state: PickerState,
    onOpenIn: (MapApp) -> Unit,
    onCopy: () -> Unit,
    onOpenBrowser: () -> Unit,
    onAskGoogle: () -> Unit,
) {}
```

```bash
git add app/src && git commit -m "PickerScreen: tests (failing)" && git push && gh pr checks --watch   # + trailer
```

- [ ] **Step 3: Implement** (read `NEWT_STYLE.md` first; reuse `ScreenHeader`/`SectionHeading`/`neonOutline` from `io.github.secnewt.ui` where they fit)

```kotlin
package io.github.secnewt.maps.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.secnewt.maps.handoff.MapApp
import io.github.secnewt.maps.link.LinkResult
import io.github.secnewt.maps.link.Why
import io.github.secnewt.ui.neonOutline
import java.util.Locale

sealed interface PickerState {
    data class Working(val message: String) : PickerState
    data class Ready(val place: LinkResult.Place, val apps: List<MapApp>) : PickerState
    data class Failed(val why: Why, val canAskGoogle: Boolean, val canOpenBrowser: Boolean) : PickerState
}

fun whyText(why: Why): String = when (why) {
    Why.NO_LINK -> "No maps link in what was shared"
    Why.NO_LOCATION -> "No location in this link"
    Why.NO_INTERNET -> "No internet"
    Why.NOTHING_FOUND -> "Nothing found for this address"
}

@Composable
fun PickerScreen(
    state: PickerState,
    onOpenIn: (MapApp) -> Unit,
    onCopy: () -> Unit,
    onOpenBrowser: () -> Unit,
    onAskGoogle: () -> Unit,
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (state) {
                is PickerState.Working -> {
                    CircularProgressIndicator()
                    Text(state.message, style = MaterialTheme.typography.headlineSmall)
                }
                is PickerState.Ready -> Ready(state, onOpenIn, onCopy, onOpenBrowser)
                is PickerState.Failed -> Failed(state, onOpenBrowser, onAskGoogle)
            }
        }
    }
}

@Composable
private fun Ready(state: PickerState.Ready, onOpenIn: (MapApp) -> Unit, onCopy: () -> Unit, onOpenBrowser: () -> Unit) {
    val place = state.place
    Text(
        place.name ?: "Dropped pin",
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
    place.address?.let { Text(it, style = MaterialTheme.typography.titleLarge) }
    Text(coordinates(place), style = MaterialTheme.typography.titleMedium)
    if (state.apps.isEmpty()) {
        Text("No map app is installed. Install Organic Maps, CoMaps or Magic Earth.", style = MaterialTheme.typography.bodyLarge)
    }
    state.apps.forEach { app -> BigButton("Open in ${app.label}", primary = true) { onOpenIn(app) } }
    BigButton("Copy coordinates", onClick = onCopy)
    BigButton("Open in browser", onClick = onOpenBrowser)
}

@Composable
private fun Failed(state: PickerState.Failed, onOpenBrowser: () -> Unit, onAskGoogle: () -> Unit) {
    Text(
        "Couldn't find it",
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { heading() },
    )
    Text(whyText(state.why), style = MaterialTheme.typography.titleLarge)
    if (state.canAskGoogle) {
        BigButton("Ask Google", primary = true, onClick = onAskGoogle)
        Text("Sends this link to Google.", style = MaterialTheme.typography.bodyLarge)
    }
    if (state.canOpenBrowser) BigButton("Open in browser", onClick = onOpenBrowser)
}

@Composable
private fun BigButton(text: String, primary: Boolean = false, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
    if (primary) {
        Button(onClick = onClick, modifier = modifier) { Text(text, style = MaterialTheme.typography.titleLarge) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, border = neonOutline()) {
            Text(text, style = MaterialTheme.typography.titleLarge)
        }
    }
}

fun coordinates(place: LinkResult.Place): String =
    String.format(Locale.US, "%.6f, %.6f", place.lat, place.lng).replace(Regex("""0+(?=,|$)"""), "").replace(Regex("""\.(?=,|$)"""), "")
```

Note: `coordinates()` trims trailing zeros, so `32.749302, -96.681877` stays as is and `32.910000` → `32.91`.

- [ ] **Step 4: Push, watch it pass, check screenshots**

```bash
git add -u && git commit -m "PickerScreen: place, big map-app buttons, couldn't-find-it" && git push && gh pr checks --watch   # + trailer
gh run download --name screenshots -D /tmp/claude-1000/newt-maps-shots   # look at every picker-* PNG
```
Expected: PASS, accessibility checks clean, text readable at 200% with nothing cut off. Fix any contrast or clipping before moving on.

---

### Task 8: HomeScreen + PickActivity + manifest wiring

**Files:**
- Create: `app/src/main/java/io/github/secnewt/maps/ui/HomeScreen.kt`
- Create: `app/src/main/java/io/github/secnewt/maps/PickActivity.kt`
- Modify: `app/src/main/java/io/github/secnewt/maps/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Delete: `app/src/main/java/io/github/secnewt/maps/ui/WelcomeScreen.kt`, `app/src/test/java/io/github/secnewt/maps/ui/WelcomeScreenTest.kt`
- Test: `app/src/test/java/io/github/secnewt/maps/ui/HomeScreenTest.kt`

**Interfaces:**
- Consumes: `LinkPipeline.run`, `Outcome`, `MapApps`, `GoogleLookup` (only if Task 6 passed), `PickerScreen`, `PickerState`
- Produces: `@Composable fun HomeScreen(linksNeedTurningOn: Boolean, onTurnOn: () -> Unit)`; `fun linkHostsAllOn(context: Context): Boolean`

- [ ] **Step 1: Write the failing HomeScreen tests**

```kotlin
package io.github.secnewt.maps.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import io.github.secnewt.test.newtScreenshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class HomeScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun needsTurningOnDark() {
        composeRule.newtScreenshot("home-turnon-dark", dark = true) { HomeScreen(linksNeedTurningOn = true, onTurnOn = {}) }
        composeRule.onNodeWithText("Turn on link opening").assertIsDisplayed()
    }
    @Test fun needsTurningOnLight() = composeRule.newtScreenshot("home-turnon-light", dark = false) { HomeScreen(true) {} }
    @Test fun needsTurningOnLargeText() = composeRule.newtScreenshot("home-turnon-200pct", dark = true, fontScale = 2f) { HomeScreen(true) {} }

    @Test fun allSetDark() {
        composeRule.newtScreenshot("home-allset-dark", dark = true) { HomeScreen(false) {} }
        composeRule.onNodeWithText("Tap a maps link", substring = true).assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Stub `HomeScreen`, delete WelcomeScreen + its test, point MainActivity at the stub, push, watch it fail**

```kotlin
package io.github.secnewt.maps.ui

import androidx.compose.runtime.Composable

@Composable
fun HomeScreen(linksNeedTurningOn: Boolean, onTurnOn: () -> Unit) {}
```

```bash
git rm app/src/main/java/io/github/secnewt/maps/ui/WelcomeScreen.kt app/src/test/java/io/github/secnewt/maps/ui/WelcomeScreenTest.kt
# MainActivity: replace WelcomeScreen() with HomeScreen(linksNeedTurningOn = true, onTurnOn = {}) for now
git add -A app/src && git commit -m "HomeScreen: tests (failing)" && git push && gh pr checks --watch   # + trailer
```

- [ ] **Step 3: Implement `HomeScreen.kt`**

```kotlin
package io.github.secnewt.maps.ui

import android.content.Context
import android.content.pm.verify.domain.DomainVerificationManager
import android.content.pm.verify.domain.DomainVerificationUserState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.secnewt.maps.R

/** True once every maps host in the manifest is turned on under "Open by default". */
fun linkHostsAllOn(context: Context): Boolean {
    val manager = context.getSystemService(DomainVerificationManager::class.java) ?: return false
    val state = manager.getDomainVerificationUserState(context.packageName) ?: return false
    return state.hostToStateMap.values.all { it != DomainVerificationUserState.DOMAIN_STATE_NONE }
}

@Composable
fun HomeScreen(linksNeedTurningOn: Boolean, onTurnOn: () -> Unit) {
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            if (linksNeedTurningOn) {
                Text(
                    "Google and Apple Maps links open in their own apps until you turn this on. It's one time.",
                    style = MaterialTheme.typography.titleLarge,
                )
                Button(onClick = onTurnOn, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
                    Text("Turn on link opening", style = MaterialTheme.typography.titleLarge)
                }
                Text("On the next screen: Add links → turn on every link.", style = MaterialTheme.typography.bodyLarge)
            }
            Text(
                "Tap a maps link, or Share → Newt Maps. Then pick the map app to open it in.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
```

- [ ] **Step 4: Wire `MainActivity`** (re-check the link state on resume, since the user comes back from Settings):

```kotlin
package io.github.secnewt.maps

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import io.github.secnewt.maps.ui.HomeScreen
import io.github.secnewt.maps.ui.linkHostsAllOn
import io.github.secnewt.ui.NewtTheme

class MainActivity : ComponentActivity() {
    private val needsTurningOn = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NewtTheme {
                HomeScreen(linksNeedTurningOn = needsTurningOn.value, onTurnOn = {
                    startActivity(Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, Uri.parse("package:$packageName")))
                })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        needsTurningOn.value = !linkHostsAllOn(this)
    }
}
```

- [ ] **Step 5: Write `PickActivity`**

```kotlin
package io.github.secnewt.maps

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.secnewt.maps.google.GoogleLookup
import io.github.secnewt.maps.handoff.MapApps
import io.github.secnewt.maps.link.LinkPipeline
import io.github.secnewt.maps.link.LinkReader
import io.github.secnewt.maps.link.LinkResult
import io.github.secnewt.maps.link.Outcome
import io.github.secnewt.maps.link.Why
import io.github.secnewt.maps.ui.PickerScreen
import io.github.secnewt.maps.ui.PickerState
import io.github.secnewt.maps.ui.coordinates
import io.github.secnewt.ui.NewtTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Opened by a maps link, a geo: link, or Share → Newt Maps. */
class PickActivity : ComponentActivity() {

    private val pipeline = LinkPipeline()
    private var googleLookup: GoogleLookup? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val shared = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> intent.dataString
        }.orEmpty()
        val apps = MapApps(this)
        setContent {
            NewtTheme {
                var state by remember { mutableStateOf<PickerState>(PickerState.Working("Reading link…")) }
                val link = remember { LinkReader.firstLink(shared) }
                LaunchedEffect(shared) {
                    state = when (val outcome = withContext(Dispatchers.IO) { pipeline.run(shared) }) {
                        is Outcome.Found -> PickerState.Ready(outcome.place, apps.installed())
                        is Outcome.NotFound -> failed(outcome.why, outcome.link)
                    }
                }
                val place = (state as? PickerState.Ready)?.place
                PickerScreen(
                    state = state,
                    onOpenIn = { app -> place?.let { startActivity(apps.handoff(it, app)); finish() } },
                    onCopy = { place?.let(::copy) },
                    onOpenBrowser = { link?.let { openInBrowser(it) } },
                    onAskGoogle = {
                        link?.let { target ->
                            state = PickerState.Working("Asking Google…")
                            googleLookup = GoogleLookup(this).also { lookup ->
                                lookup.start(target) { found ->
                                    state = if (found != null) PickerState.Ready(found.copy(name = found.name ?: nameFrom(target)), apps.installed())
                                    else PickerState.Failed(Why.NOTHING_FOUND, canAskGoogle = false, canOpenBrowser = true)
                                }
                            }
                        }
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        googleLookup?.cancel()
        super.onDestroy()
    }

    private fun failed(why: Why, link: String?) = PickerState.Failed(
        why,
        // Only Google links are worth asking Google about, and only when there's internet.
        canAskGoogle = ASK_GOOGLE_ENABLED && link != null && why != Why.NO_INTERNET && isGoogle(link),
        canOpenBrowser = link != null && !link.startsWith("geo:"),
    )

    private fun isGoogle(link: String) = Uri.parse(link).host?.let { it.endsWith("google.com") || it.endsWith("goo.gl") } == true

    private fun nameFrom(link: String): String? = (LinkReader.read(link) as? LinkResult.Search)?.text

    private fun copy(place: LinkResult.Place) {
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("Coordinates", coordinates(place)))
    }

    private fun openInBrowser(link: String) {
        // A chooser, so the link doesn't bounce straight back into Newt Maps.
        startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(link)), "Open in browser"))
    }

    companion object {
        /** Set to false if Task 6 (the emulator probe) failed. */
        const val ASK_GOOGLE_ENABLED = true
    }
}
```

If Task 6 dropped GoogleLookup: remove the `googleLookup` field, the import, the `onDestroy` override, set `onAskGoogle = {}`, and delete `ASK_GOOGLE_ENABLED` (use `canAskGoogle = false`).

- [ ] **Step 6: Manifest** — add the activity inside `<application>`:

```xml
        <!-- Opens maps links. Android won't let a third-party app own google.com or
             apple.com links on its own, so Home walks the user through "Open by default". -->
        <activity
            android:name=".PickActivity"
            android:exported="true"
            android:excludeFromRecents="true"
            android:taskAffinity="">
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="https" />
                <data android:host="maps.app.goo.gl" />
                <data android:host="maps.google.com" />
                <data android:host="maps.apple.com" />
                <data android:host="maps.apple" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="https" />
                <data android:host="www.google.com" />
                <data android:host="google.com" />
                <data android:host="goo.gl" />
                <data android:pathPrefix="/maps" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="geo" />
            </intent-filter>
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="text/plain" />
            </intent-filter>
        </activity>
```

(Data elements in one filter combine, so the `/maps` path prefix sits in its own filter with only the hosts that need it. `autoVerify` is left off: verification can't succeed for domains we don't own.)

- [ ] **Step 7: Push and watch it pass; check screenshots and lint**

```bash
git add -A app/src && git commit -m "Home + PickActivity: open maps links, geo: and shared text" && git push && gh pr checks --watch   # + trailer
gh run download --name screenshots -D /tmp/claude-1000/newt-maps-shots   # home-* PNGs
```
Expected: every required check PASSES (tests, screenshots/accessibility, lint, permission check, CodeQL). The permission check must show only `INTERNET` plus the two AndroidX receiver permissions.

---

### Task 9: Real-phone check, docs, hand to Jerry

- [ ] **Step 1: Mark the PR ready and give Jerry the checklist**

```bash
gh pr ready && gh pr view --web
```

Jerry installs the PR's test APK (CI artifact `newt-maps-test-apk`) and checks:
1. Home → **Turn on link opening** → Add links → turn all on → back: the card is gone.
2. Tap each real link from `PLAN.md` (from Messages / a note): the picker shows the right place; **Open in Organic Maps** lands on it.
3. The Apple short link shows "Gonzalez Restaurants" and its address.
4. Share a link from Messages → Newt Maps works.
5. Airplane mode + a text-only link → "No internet" + Open in browser.
6. 200% text: nothing cut off.

- [ ] **Step 2: Fix anything he finds** (each fix: test first if it's logic, then push; same loop).

- [ ] **Step 3: Docs**
  - `README.md`: one paragraph on what it does + the one-time "Open by default" step.
  - Vault `personal/AI/newt-maps-idea.md`: dated status entry (what shipped, version, what's verified on his phone vs only in CI, whether Ask Google was kept).
  - Vault `personal/AI/newt-android.md`: a "Newt Maps (started 2026-10-19)" section, like Newt Weather's.
  - Wiki `newt-android-apps.md`: add Newt Maps to the app list (use the `obsidian-wiki` skill).
  - Commit vault changes in `~/Obsidian` (stage only those files).

- [ ] **Step 4: Jerry merges the PR** (his merge rule). The release build on `main` publishes the signed APK for Obtainium.
