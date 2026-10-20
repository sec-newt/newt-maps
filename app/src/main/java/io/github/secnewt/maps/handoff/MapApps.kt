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
