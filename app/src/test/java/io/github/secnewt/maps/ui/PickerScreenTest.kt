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
        Place(32.749302, -96.681877, "Gonzalez Restaurants", "8121 Bruton Rd, Dallas, TX 75217"),
        apps,
    )
    private val failed = PickerState.Failed(Why.NOTHING_FOUND, canOpenBrowser = true)

    @Composable private fun screen(state: PickerState, onOpenIn: (MapApp) -> Unit = {}) =
        PickerScreen(state, onOpenIn = onOpenIn, onCopy = {}, onOpenBrowser = {})

    @Test fun readyDark() {
        composeRule.newtScreenshot("picker-ready-dark", dark = true) { screen(ready) }
        composeRule.onNodeWithText("Gonzalez Restaurants").assertIsDisplayed()
        composeRule.onNodeWithText("32.749302, -96.681877").assertIsDisplayed()
    }

    @Test fun readyLight() = composeRule.newtScreenshot("picker-ready-light", dark = false) { screen(ready) }

    @Test fun readyLargeText() =
        composeRule.newtScreenshot("picker-ready-200pct", dark = true, fontScale = 2f) { screen(ready) }

    @Test fun tappingAnAppHandsOff() {
        var chosen: MapApp? = null
        composeRule.setContent { screen(ready) { chosen = it } }
        composeRule.onNodeWithText("Magic Earth", substring = true).performScrollTo().performClick()
        assertEquals("com.generalmagic.magicearth", chosen?.packageName)
    }

    @Test fun droppedPinWhenNoName() {
        composeRule.setContent { screen(PickerState.Ready(Place(32.91, -96.63), apps)) }
        composeRule.onNodeWithText("Dropped pin").assertIsDisplayed()
        composeRule.onNodeWithText("32.91, -96.63").assertIsDisplayed()
    }

    @Test fun workingDark() =
        composeRule.newtScreenshot("picker-working-dark", dark = true) { screen(PickerState.Working("Looking up the address…")) }

    @Test fun failedDark() {
        composeRule.newtScreenshot("picker-failed-dark", dark = true) { screen(failed) }
        composeRule.onNodeWithText("Nothing found for this address").assertIsDisplayed()
        composeRule.onNodeWithText("Open in browser").assertIsDisplayed()
    }

    @Test fun failedLight() = composeRule.newtScreenshot("picker-failed-light", dark = false) { screen(failed) }

    @Test fun failedLargeText() =
        composeRule.newtScreenshot("picker-failed-200pct", dark = true, fontScale = 2f) { screen(failed) }

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
