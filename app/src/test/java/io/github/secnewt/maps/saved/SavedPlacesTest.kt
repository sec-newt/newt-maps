package io.github.secnewt.maps.saved

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SavedPlacesTest {

    @get:Rule val folder = TemporaryFolder()

    private val file: File get() = File(folder.root, "saved.json")
    private val catch = SavedPlace("The Catch", 32.86791, -96.63412, "5949 Broadway Blvd, Garland")
    private val moms = SavedPlace("Mom's", 32.80, -96.70)
    private val house = SavedPlace("1234 Main St", 32.91, -96.64, "Garland")

    @Test fun startsEmpty() {
        val saved = SavedPlaces(file)
        assertNull(saved.home)
        assertTrue(saved.places.isEmpty())
    }

    @Test fun keepsEverythingAfterARestart() {
        SavedPlaces(file).apply { save(catch); save(moms); setHome(house) }
        val again = SavedPlaces(file)
        assertEquals(listOf(catch, moms), again.places)
        assertEquals(house, again.home)
    }

    @Test fun savingTheSameSpotTwiceReplacesIt() {
        val saved = SavedPlaces(file)
        saved.save(catch)
        saved.save(catch.copy(name = "Fish place", lat = catch.lat + 0.0001))
        assertEquals(listOf("Fish place"), saved.places.map { it.name })
    }

    @Test fun findsASavedPlaceNearTheSameSpot() {
        val saved = SavedPlaces(file).apply { save(catch) }
        assertEquals(catch, saved.at(catch.lat + 0.0001, catch.lng))
        assertNull(saved.at(catch.lat + 0.01, catch.lng))
    }

    @Test fun renameAndRemove() {
        val saved = SavedPlaces(file).apply { save(catch); save(moms) }
        saved.rename(catch, "  Fish  ")
        assertEquals("Fish", SavedPlaces(file).places.first().name)
        saved.remove(saved.places.first())
        assertEquals(listOf(moms), SavedPlaces(file).places)
    }

    @Test fun blankRenameIsIgnored() {
        val saved = SavedPlaces(file).apply { save(catch) }
        saved.rename(catch, "   ")
        assertEquals("The Catch", saved.places.single().name)
    }

    @Test fun homeIsSeparateFromTheList() {
        val saved = SavedPlaces(file).apply { setHome(house) }
        assertTrue(saved.places.isEmpty())
        assertTrue(saved.isHome(house.lat, house.lng))
        saved.clearHome()
        assertNull(SavedPlaces(file).home)
        assertFalse(saved.isHome(house.lat, house.lng))
    }

    @Test fun matchingIgnoresCaseAndApostrophes() {
        val saved = SavedPlaces(file).apply { save(catch); save(moms) }
        assertEquals(listOf(moms), saved.matching("moms"))
        assertEquals(listOf(catch), saved.matching("CATCH"))
        assertEquals(listOf(catch), saved.matching("broadway garland")) // the address counts too
        assertEquals(emptyList<SavedPlace>(), saved.matching("catch dallas"))
        assertEquals(emptyList<SavedPlace>(), saved.matching("  "))
    }

    @Test fun homeIsFoundByTheWordHome() {
        val saved = SavedPlaces(file).apply { setHome(house) }
        assertEquals(listOf("Home"), saved.matching("home").map { it.name })
        assertEquals(listOf("Home", "The Catch"), saved.apply { save(catch) }.all().map { it.name })
    }

    @Test fun aDamagedFileStartsEmptyAndIsKept() {
        file.writeText("{not json")
        val saved = SavedPlaces(file)
        assertTrue(saved.places.isEmpty())
        assertTrue(File(file.path + ".damaged").exists())
    }
}
