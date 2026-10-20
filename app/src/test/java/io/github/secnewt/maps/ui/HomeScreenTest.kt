package io.github.secnewt.maps.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
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
        // The heading comes first; the debug build adds " (test)" to it, and "Share → Newt Maps"
        // also matches, so check the first match.
        composeRule.onAllNodesWithText("Newt Maps", substring = true).onFirst().assertIsDisplayed()
    }

    @Test fun needsTurningOnLight() =
        composeRule.newtScreenshot("home-turnon-light", dark = false) { HomeScreen(linksNeedTurningOn = true, onTurnOn = {}) }

    @Test fun needsTurningOnLargeText() =
        composeRule.newtScreenshot("home-turnon-200pct", dark = true, fontScale = 2f) { HomeScreen(linksNeedTurningOn = true, onTurnOn = {}) }

    @Test fun allSetDark() {
        composeRule.newtScreenshot("home-allset-dark", dark = true) { HomeScreen(linksNeedTurningOn = false, onTurnOn = {}) }
        composeRule.onNodeWithText("Tap a maps link", substring = true).assertIsDisplayed()
    }
}
