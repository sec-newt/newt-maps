package io.github.secnewt.maps.map

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// org.json is Android's, so this runs under Robolectric.
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MapStyleTest {

    private fun layers(dark: Boolean, scale: Float = 1f): Map<String, JSONObject> {
        val array = JSONObject(MapStyle.json(dark, scale)).getJSONArray("layers")
        return (0 until array.length()).map { array.getJSONObject(it) }.associateBy { it.getString("id") }
    }

    /** The biggest street-name size, at full zoom. */
    private fun biggestName(layers: Map<String, JSONObject>): Double {
        val size = layers.getValue("street-names").getJSONObject("layout").getJSONArray("text-size")
        return size.getDouble(size.length() - 1)
    }

    @Test
    fun `street names are big, bold and outlined`() {
        val names = layers(dark = true).getValue("street-names")
        assertEquals("Noto Sans Bold", names.getJSONObject("layout").getJSONArray("text-font").getString(0))
        assertTrue(names.getJSONObject("paint").getInt("text-halo-width") >= 3)
        assertTrue(biggestName(layers(dark = true)) >= 24)
    }

    @Test
    fun `street names grow with the phone's text size, up to double`() {
        assertEquals(biggestName(layers(true)) * 2, biggestName(layers(true, 2f)), 0.01)
        assertEquals(biggestName(layers(true, 2f)), biggestName(layers(true, 3f)), 0.01)
    }

    @Test
    fun `every road kind is drawn, and named roads can be tapped`() {
        val all = layers(dark = true)
        listOf("road-small", "road-street", "road-main", "road-highway", MapStyle.STREET_HIT_LAYER).forEach { assertTrue(it, it in all) }
        assertEquals("transportation_name", all.getValue(MapStyle.STREET_HIT_LAYER).getString("source-layer"))
    }

    @Test
    fun `dark and light have their own colors, with names in contrast to their outline`() {
        val dark = layers(true).getValue("street-names").getJSONObject("paint")
        val light = layers(false).getValue("street-names").getJSONObject("paint")
        assertNotEquals(dark.getString("text-color"), light.getString("text-color"))
        assertEquals("#FFFFFF", dark.getString("text-color"))
        assertEquals("#000000", dark.getString("text-halo-color"))
    }

    @Test
    fun `the found place has a source to draw it in`() {
        val sources = JSONObject(MapStyle.json(true)).getJSONObject("sources")
        assertEquals("geojson", sources.getJSONObject(MapStyle.PLACE_SOURCE).getString("type"))
        assertEquals(MapStyle.TILES, sources.getJSONObject("openmaptiles").getString("url"))
        assertTrue(JSONObject(MapStyle.json(true)).get("layers") is JSONArray)
    }

    @Test
    fun `road kinds in plain words`() {
        assertEquals("Highway", roadKind("motorway"))
        assertEquals("Main road", roadKind("primary"))
        assertEquals("Street", roadKind("minor"))
        assertNull(roadKind("ferry"))
    }
}
