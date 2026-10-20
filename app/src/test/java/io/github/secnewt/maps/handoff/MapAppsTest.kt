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

    @Test fun geoUriEscapesParentheses() =
        assertEquals(
            "geo:32.91,-96.63?q=32.91,-96.63(Joe's%20Pizza%20%28Downtown%29)",
            geoUri(Place(32.91, -96.63, "Joe's Pizza (Downtown)")),
        )

    @Test fun geoUriNeverScientific() =
        assertEquals("geo:0.00005,-96.63?q=0.00005,-96.63", geoUri(Place(0.00005, -96.63)))

    @Test fun searchHandsTheTextToTheChosenApp() {
        val intent = MapApps(context).search("The Catch 5949 Broadway Blvd", MapApp("app.organicmaps", "Organic Maps"))
        assertEquals("app.organicmaps", intent.`package`)
        assertEquals("geo:0,0?q=The%20Catch%205949%20Broadway%20Blvd", intent.dataString)
    }

    @Test fun handoffTargetsTheChosenApp() {
        val intent = MapApps(context).handoff(Place(32.91, -96.63, "X"), MapApp("app.organicmaps", "Organic Maps"))
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("app.organicmaps", intent.`package`)
        assertEquals("geo:32.91,-96.63?q=32.91,-96.63(X)", intent.dataString)
    }
}
