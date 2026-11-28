package io.github.secnewt.maps

import android.content.ComponentName
import android.content.pm.ActivityInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// The route, place card and trip live in the map screen's memory. If Android rebuilt the
// screen on every turn of the phone, they'd be gone (and the voice would stop mid-drive).
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RotationTest {

    @Test
    fun `turning the phone keeps the map screen instead of starting it over`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        val needed = mapOf(
            "orientation" to ActivityInfo.CONFIG_ORIENTATION,
            "screenSize" to ActivityInfo.CONFIG_SCREEN_SIZE,
            "screenLayout" to ActivityInfo.CONFIG_SCREEN_LAYOUT,
            "smallestScreenSize" to ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE,
            "keyboardHidden" to ActivityInfo.CONFIG_KEYBOARD_HIDDEN,
        )
        for ((name, flag) in needed) {
            assertTrue("MainActivity must handle $name itself", info.configChanges and flag != 0)
        }
    }
}
