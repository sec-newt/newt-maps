package io.github.secnewt.template.ui

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

/**
 * Screenshots (light, dark, and 200% text) and accessibility checks, using the Newt
 * library's newtScreenshot(). Copy this pattern for every new screen; the screenshots
 * land in CI's "screenshots" artifact for review.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class WelcomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun welcomeDark() {
        composeRule.newtScreenshot("welcome-dark", dark = true) { WelcomeScreen() }
        // The debug build adds " (test)" to the name, so match the start.
        composeRule.onNodeWithText("Newt Template", substring = true).assertIsDisplayed()
    }

    @Test
    fun welcomeLight() = composeRule.newtScreenshot("welcome-light", dark = false) { WelcomeScreen() }

    @Test
    fun welcomeLargeText() = composeRule.newtScreenshot("welcome-200pct", dark = true, fontScale = 2f) { WelcomeScreen() }
}
