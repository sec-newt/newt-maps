package io.github.secnewt.maps.handoff

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import io.github.secnewt.maps.link.LinkResult
import java.util.Locale

data class MapApp(val packageName: String, val label: String)

/** geo:lat,lng?q=lat,lng(name) — the form Organic Maps, CoMaps, Magic Earth and OsmAnd all read. */
fun geoUri(place: LinkResult.Place): String {
    val at = "${formatCoord(place.lat)},${formatCoord(place.lng)}"
    val label = place.name?.let { "(${encodeName(it)})" } ?: ""
    return "geo:$at?q=$at$label"
}

/**
 * Plain decimal, never scientific notation (Double.toString() would emit "5.0E-5" for
 * 0.00005, which map apps can't parse as a coordinate). At most 6 decimal places;
 * trailing zeros and a trailing dot are trimmed.
 */
/** geo:0,0?q=<text> — the standard "search for this" form, as GeoShare uses. */
fun geoSearchUri(text: String): String = "geo:0,0?q=${Uri.encode(text)}"

internal fun formatCoord(value: Double): String =
    String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')

/**
 * Uri.encode's default "safe" set leaves '(' and ')' unescaped, which would corrupt the
 * (name) group in the query string (e.g. a name containing "(Downtown)").
 */
private fun encodeName(name: String): String =
    Uri.encode(name).replace("(", "%28").replace(")", "%29")

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

    /** When the address couldn't be found: let the map app run its own search on the text. */
    fun search(text: String, app: MapApp): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(geoSearchUri(text))).setPackage(app.packageName)

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
