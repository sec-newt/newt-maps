package io.github.secnewt.maps.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
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
    private fun Screen(card: MapCard?, onZoomIn: () -> Unit = {}, onClose: () -> Unit = {}) = MapScreen(
        map = { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) },
        card = card, searching = false, onSearch = {}, onZoomIn = onZoomIn, onZoomOut = {}, onMyLocation = {},
        onSettings = {}, onCloseCard = onClose,
    )

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
}
