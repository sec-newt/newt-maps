package io.github.secnewt.maps.ui

import org.junit.Assert.assertTrue
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import io.github.secnewt.test.newtScreenshot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
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
        composeRule.newtScreenshot("home-turnon-dark", dark = true) { HomeScreen(linksNeedTurningOn = true, onTurnOn = {}, mapApp = "Organic Maps") }
        composeRule.onNodeWithText("Turn on").assertIsDisplayed()
        // The heading comes first; the debug build adds " (test)" to it, and "Share → Newt Maps"
        // also matches, so check the first match.
        composeRule.onAllNodesWithText("Newt Maps", substring = true).onFirst().assertIsDisplayed()
    }

    @Test fun needsTurningOnLight() =
        composeRule.newtScreenshot("home-turnon-light", dark = false) { HomeScreen(linksNeedTurningOn = true, onTurnOn = {}, mapApp = "Organic Maps") }

    @Test fun needsTurningOnLargeText() =
        composeRule.newtScreenshot("home-turnon-200pct", dark = true, fontScale = 2f) { HomeScreen(linksNeedTurningOn = true, onTurnOn = {}, mapApp = "Organic Maps") }

    @Test fun homeSetDark() =
        composeRule.newtScreenshot("settings-home-dark", dark = true) {
            HomeScreen(linksNeedTurningOn = false, onTurnOn = {}, onBack = {}, mapApp = "Organic Maps", home = "1234 Main St, Garland")
        }

    @Test
    fun clearHome() {
        var cleared = false
        composeRule.setContent {
            io.github.secnewt.ui.NewtTheme(darkTheme = true) {
                HomeScreen(linksNeedTurningOn = false, onTurnOn = {}, onBack = {}, mapApp = "Organic Maps", home = "1234 Main St, Garland", onClearHome = { cleared = true })
            }
        }
        composeRule.onNodeWithText("Clear home").performScrollTo().performClick()
        assertTrue(cleared)
    }

    @Test fun allSetDark() {
        composeRule.newtScreenshot("home-allset-dark", dark = true) { HomeScreen(linksNeedTurningOn = false, onTurnOn = {}, mapApp = "Organic Maps") }
        composeRule.onNodeWithContentDescription("Step 3 of 3, Try it, current").assertIsDisplayed()
    }

    @Test fun noMapAppDark() {
        composeRule.newtScreenshot("home-nomapapp-dark", dark = true) { HomeScreen(linksNeedTurningOn = true, onTurnOn = {}, mapApp = null) }
        composeRule.onNodeWithContentDescription("Step 1 of 3, Install a map app, current").assertExists()
        composeRule.onNodeWithText("Install Organic Maps, CoMaps or Magic Earth").assertExists()
        composeRule.onNodeWithContentDescription("Step 2 of 3, Turn on link opening, not yet").assertExists()
    }

    @Test fun aFoundMapAppIsDone() {
        var turned = 0
        composeRule.setContent { io.github.secnewt.ui.NewtTheme(darkTheme = true) { HomeScreen(linksNeedTurningOn = true, onTurnOn = { turned++ }, mapApp = "Organic Maps") } }
        composeRule.onNodeWithContentDescription("Step 1 of 3, Install a map app, done").assertExists()
        composeRule.onNodeWithText("Organic Maps found").assertExists()
        composeRule.onNodeWithText("Turn on").performClick()
        org.junit.Assert.assertEquals(1, turned)
    }
}
