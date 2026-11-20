package io.github.secnewt.maps.ui

import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import io.github.secnewt.maps.search.SearchHit
import io.github.secnewt.test.newtScreenshot
import io.github.secnewt.ui.NewtTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The buttons and cards over the map. The map itself can't draw here (it needs the phone's
 * graphics), so a plain stand-in takes its place; the emulator test screenshots the real one.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class MapScreenTest {

    @get:Rule val composeRule = createComposeRule()

    private val street = MapCard.Street("Main Street", "Street", null)
    private val place = MapCard.Place("The Catch, 5949 Broadway Blvd", 32.86791, -96.63412)

    @Composable
    private fun Screen(
        card: MapCard?,
        onZoomIn: () -> Unit = {},
        onClose: () -> Unit = {},
        preview: RoutePreview? = null,
        guidance: Guidance? = null,
        onStart: () -> Unit = {},
        onDirections: (MapCard.Place) -> Unit = {},
        onPick: (SearchHit) -> Unit = {},
        placeSaved: Boolean = false,
        placeIsHome: Boolean = false,
        onSave: (MapCard.Place, String) -> Unit = { _, _ -> },
        onRemove: (MapCard.Place) -> Unit = {},
        onSetHome: (MapCard.Place) -> Unit = {},
        onHome: () -> Unit = {},
    ) = MapScreen(
        map = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) },
        card = card, searching = false, onSearch = {}, onZoomIn = onZoomIn, onZoomOut = {}, onMyLocation = {},
        onSettings = {}, onCloseCard = onClose, preview = preview, guidance = guidance, onStart = onStart, onDirections = onDirections, onPick = onPick,
        placeSaved = placeSaved, placeIsHome = placeIsHome, onSave = onSave, onRemove = onRemove, onSetHome = onSetHome, onHome = onHome,
    )

    private val preview = RoutePreview("The Catch, 5949 Broadway Blvd", "12 min", "4.3 mi", "3:45 PM", avoidTolls = true, practice = false)
    private val guidance = Guidance(
        turn = io.github.secnewt.maps.nav.Turn.RIGHT, distance = "0.3 mi", road = "Broadway Boulevard",
        spoken = "Turn right onto Broadway Boulevard.", then = "Then Main Street", timeLeft = "9 min", distanceLeft = "3.8 mi",
        arrival = "3:45 PM", muted = false, rerouting = false, practice = false,
    )

    private val results = MapCard.Results(
        "El Fenix",
        listOf(
            SearchHit("El Fenix", "255 Casa Linda Plaza, Dallas", 32.833, -96.7007, 6.38),
            SearchHit("El Fenix Mexican Restaurant", "3904 Towne Crossing Boulevard, Mesquite", 32.81, -96.62, 7.1),
            SearchHit("El Fenix", "Central Expressway, Plano", 33.0198, -96.70, 8.2),
            SearchHit("El Fenix", "West Northwest Highway, Dallas", 32.86, -96.77, 12.0),
        ),
    )

    @Test fun resultsDark() = composeRule.newtScreenshot("search-results-dark", dark = true) { Screen(results) }
    @Test fun resultsLight() = composeRule.newtScreenshot("search-results-light", dark = false) { Screen(results.copy(query = "Alamo Drafthouse Lake Highlands", shortenedTo = "Alamo Drafthouse")) }
    @Test fun resultsLargeText() = composeRule.newtScreenshot("search-results-200pct", dark = true, fontScale = 2f) { Screen(results) }
    @Test fun pickedPlaceWithAddress() = composeRule.newtScreenshot("map-place-address-dark", dark = true) { Screen(place.copy(name = "El Fenix", address = "255 Casa Linda Plaza, Dallas")) }

    @Test
    fun tappingAResultPicksIt() {
        var picked: SearchHit? = null
        composeRule.setContent { NewtTheme(darkTheme = true) { Screen(results, onPick = { picked = it }) } }
        composeRule.onNodeWithContentDescription("El Fenix Mexican Restaurant, 3904 Towne Crossing Boulevard, Mesquite, 7.1 miles").performClick()
        assertEquals(results.hits[1], picked)
    }

    @Test
    fun resultsSayWhenTheSearchWasShortened() {
        composeRule.setContent { NewtTheme(darkTheme = true) { Screen(results.copy(query = "Alamo Drafthouse Lake Highlands", shortenedTo = "Alamo Drafthouse")) } }
        composeRule.onNodeWithText("Nothing for \"Alamo Drafthouse Lake Highlands\". 4 places for \"Alamo Drafthouse\":").assertIsDisplayed()
    }

    @Test fun savedPlaceDark() = composeRule.newtScreenshot("map-place-saved-dark", dark = true) { Screen(place.copy(name = "Fish place", address = "The Catch"), placeSaved = true) }
    @Test fun homeLight() = composeRule.newtScreenshot("map-place-home-light", dark = false) { Screen(place.copy(name = "Home", address = "1234 Main St"), placeIsHome = true) }
    @Test fun savedPlaceLargeText() = composeRule.newtScreenshot("map-place-saved-200pct", dark = true, fontScale = 2f) { Screen(place, placeSaved = true) }
    @Test fun savedInResultsDark() = composeRule.newtScreenshot("search-results-saved-dark", dark = true) {
        Screen(MapCard.Results("", listOf(SearchHit("Home", "1234 Main St", 32.91, -96.64, 0.0, saved = true), SearchHit("Mom's", "", 32.8, -96.7, 9.1, saved = true)), title = "Saved places"))
    }

    /** Moves time on and lets the dialog window attach, without waiting for the cursor to stop blinking. */
    private fun settle() {
        repeat(5) {
            composeRule.mainClock.advanceTimeBy(100)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        }
    }

    @Test
    fun saveAsksForANameFilledIn() {
        var savedAs: String? = null
        composeRule.setContent { NewtTheme(darkTheme = true) { Screen(place, onSave = { _, name -> savedAs = name }) } }
        // The dialog's blinking cursor never stops animating, so step the clock by hand.
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithContentDescription("Save this place").performClick()
        settle()
        composeRule.onNodeWithText("Save as").assertExists()
        composeRule.onNode(hasSetTextAction() and hasText(place.name)).assertExists() // filled in with the place's name
        composeRule.onAllNodesWithText("Save").onLast().performClick()
        settle()
        // Typing can't be tested here (it waits for the cursor to stop blinking); keeping the name is one tap.
        assertEquals(place.name, savedAs)
    }

    @Test
    fun removeAsksFirst() {
        var removed = false
        composeRule.setContent { NewtTheme(darkTheme = true) { Screen(place, placeSaved = true, onRemove = { removed = true }) } }
        composeRule.onNodeWithContentDescription("Remove from saved places").performClick()
        assertEquals(false, removed)
        composeRule.onAllNodesWithText("Remove").onLast().performClick()
        assertEquals(true, removed)
    }

    @Test
    fun aSavedPlaceHasNoSaveButton() {
        composeRule.setContent { NewtTheme(darkTheme = true) { Screen(place, placeSaved = true) } }
        composeRule.onAllNodesWithContentDescription("Save this place").assertCountEquals(0)
        composeRule.onNodeWithText("Saved").assertIsDisplayed()
    }

    @Test
    fun setAsHome() {
        var set = false
        composeRule.setContent { NewtTheme(darkTheme = true) { Screen(place, onSetHome = { set = true }) } }
        composeRule.onNodeWithText("Set as home").performClick()
        assertEquals(true, set)
    }

    @Test
    fun homeHasNoSetAsHomeButton() {
        composeRule.setContent { NewtTheme(darkTheme = true) { Screen(place, placeIsHome = true) } }
        composeRule.onAllNodesWithText("Set as home").assertCountEquals(0)
    }

    @Test
    fun aTallPlaceCardScrollsToItsLastButton() {
        var opened = false
        composeRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(androidx.compose.ui.platform.LocalDensity.current.density, fontScale = 2f),
            ) { NewtTheme(darkTheme = true) { MapScreen(map = {}, card = place, searching = false, onSearch = {}, onZoomIn = {}, onZoomOut = {}, onMyLocation = {}, onSettings = {}, onCloseCard = {}, placeSaved = true, onOpenElsewhere = { opened = true }) } }
        }
        composeRule.onNodeWithContentDescription("Open in another map app").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(true, opened)
    }

    @Test
    fun goHomeButton() {
        var home = false
        composeRule.setContent { NewtTheme(darkTheme = true) { Screen(null, onHome = { home = true }) } }
        composeRule.onNodeWithContentDescription("Go home").performClick()
        assertEquals(true, home)
    }

    @Test fun distances() {
        assertEquals("< 0.1 mi", milesText(0.04))
        assertEquals("4.2 mi", milesText(4.24))
        assertEquals("25 mi", milesText(25.3))
    }

    @Test fun previewDark() = composeRule.newtScreenshot("route-preview-dark", dark = true) { Screen(null, preview = preview) }
    @Test fun previewLargeText() = composeRule.newtScreenshot("route-preview-200pct", dark = false, fontScale = 2f) { Screen(null, preview = preview.copy(practice = true)) }
    @Test fun drivingDark() = composeRule.newtScreenshot("driving-dark", dark = true) { Screen(null, guidance = guidance) }
    @Test fun drivingLight() = composeRule.newtScreenshot("driving-light", dark = false) { Screen(null, guidance = guidance.copy(muted = true, then = null)) }
    @Test fun drivingLargeText() = composeRule.newtScreenshot("driving-200pct", dark = true, fontScale = 2f) { Screen(null, guidance = guidance.copy(rerouting = true)) }

    @Test
    fun directionsAsksForARoute() {
        var routed: MapCard.Place? = null
        composeRule.setContent { NewtTheme(darkTheme = true) { Screen(place, onDirections = { routed = it }) } }
        composeRule.onNodeWithText("Directions").performClick()
        assertEquals(place, routed)
    }

    @Test
    fun startBeginsTheDrive() {
        var started = false
        composeRule.setContent { NewtTheme(darkTheme = true) { Screen(null, preview = preview, onStart = { started = true }) } }
        composeRule.onNodeWithText("12 min · 4.3 mi").assertIsDisplayed()
        composeRule.onNodeWithText("Start").performClick()
        assertEquals(true, started)
    }

    @Test
    fun drivingSaysTheTurnInOneSentence() {
        composeRule.setContent { NewtTheme(darkTheme = true) { Screen(null, guidance = guidance) } }
        composeRule.onNodeWithContentDescription("In 0.3 mi: Turn right onto Broadway Boulevard.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Stop navigation").assertIsDisplayed()
        // Nothing else on the map while driving.
        assertEquals(0, composeRule.onAllNodesWithContentDescription("Zoom in").fetchSemanticsNodes().size)
    }

    @Test fun plainDark() = composeRule.newtScreenshot("map-plain-dark", dark = true) { Screen(null) }
    @Test fun streetDark() = composeRule.newtScreenshot("map-street-dark", dark = true) { Screen(street) }
    @Test fun streetLight() = composeRule.newtScreenshot("map-street-light", dark = false) { Screen(MapCard.Street("Broadway Boulevard", "Main road", "TX 78")) }
    @Test fun streetLargeText() = composeRule.newtScreenshot("map-street-200pct", dark = true, fontScale = 2f) { Screen(street) }
    @Test fun placeDark() = composeRule.newtScreenshot("map-place-dark", dark = true) { Screen(place) }
    @Test fun placeLargeText() = composeRule.newtScreenshot("map-place-200pct", dark = false, fontScale = 2f) { Screen(place) }
    @Test fun noteDark() = composeRule.newtScreenshot("map-note-dark", dark = true) { Screen(MapCard.Note("Couldn't find \"zzqx\".")) }

    @Test
    fun buttonsAreNamedAndWork() {
        var zoomed = 0
        var closed = false
        composeRule.setContent { NewtTheme(darkTheme = true) { Screen(street, onZoomIn = { zoomed++ }, onClose = { closed = true }) } }
        composeRule.onNodeWithContentDescription("Zoom in").performClick()
        composeRule.onNodeWithContentDescription("Zoom out").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Where am I").assertIsDisplayed()
        composeRule.onNodeWithText("Main Street").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Close").performClick()
        assertEquals(1, zoomed)
        assertEquals(true, closed)
    }

    @Test
    fun placeCardButtonsHaveWords() {
        var routed: MapCard.Place? = null
        composeRule.setContent { NewtTheme(darkTheme = true) { Screen(place, onDirections = { routed = it }) } }
        listOf("Share", "Copy", "Open in").forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
        composeRule.onNodeWithText("Directions").performClick()
        assertEquals(place, routed)
    }

    @Test
    fun drivingButtonsSayVoiceAndEnd() {
        composeRule.setContent { NewtTheme(darkTheme = true) { Screen(null, guidance = guidance.copy(muted = true)) } }
        composeRule.onNodeWithText("Voice off").assertIsDisplayed()
        composeRule.onNodeWithText("End").assertIsDisplayed()
    }

    @Test
    fun aStreetHasNoDirections() {
        composeRule.setContent { NewtTheme(darkTheme = true) { Screen(street) } }
        composeRule.onNodeWithText("Directions").assertDoesNotExist()
    }
}
