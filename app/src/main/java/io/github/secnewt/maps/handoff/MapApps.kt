package io.github.secnewt.maps.handoff

import android.content.Context
import android.content.Intent
import io.github.secnewt.maps.link.LinkResult

data class MapApp(val packageName: String, val label: String)

fun geoUri(place: LinkResult.Place): String = ""

class MapApps(private val context: Context) {
    fun installed(): List<MapApp> = emptyList()
    fun handoff(place: LinkResult.Place, app: MapApp): Intent = Intent()
}
