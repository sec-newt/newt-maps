package io.github.secnewt.maps.nav

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AvoidedRoadsTest {

    @get:Rule val folder = TemporaryFolder()

    private val file: File get() = File(folder.root, "avoided.json")

    // Trimmed from a real /locate answer: pressing I 635 finds its main lanes, the express
    // lanes beside them, and the service road.
    private fun edge(names: List<String>, lat: Double, lon: Double, distance: Double, kind: String = "motorway") =
        """{"correlated_lat":$lat,"correlated_lon":$lon,"distance":$distance,"edge":{"classification":{"classification":"$kind"}},"edge_info":{"names":[${names.joinToString { "\"$it\"" }}]}}"""

    private val answer = "[{\"edges\":[" + listOf(
        edge(listOf("Lyndon B. Johnson Service Road"), 32.8509, -96.6415, 30.0, "secondary"),
        edge(listOf("I 635 Frontage Road"), 32.8510, -96.6416, 35.0, "primary"),
        edge(listOf("I 635E"), 32.8511, -96.6417, 40.0),
        edge(listOf("I 635", "Lyndon B. Johnson Freeway"), 32.850387, -96.6419784, 0.0),
        edge(listOf("I 635 TEXPR", "Interstate 635 East TEXpress"), 32.850519, -96.641819, 12.0),
        edge(listOf("I 635", "Lyndon B. Johnson Freeway"), 32.85059, -96.641735, 20.0),
    ).joinToString(",") + "]}]"

    @Test
    fun `pressing a highway catches all its lanes but not the service road beside it`() {
        val road = RoadLocator.roadAt(answer)!!
        assertEquals("I 635 · Lyndon B. Johnson Freeway", road.name)
        assertEquals(listOf(Spot(32.850387, -96.641978), Spot(32.850519, -96.641819), Spot(32.85059, -96.641735)), road.spots)
    }

    @Test
    fun `no named road, or a broken answer, finds nothing`() {
        assertNull(RoadLocator.roadAt("[{\"edges\":[" + edge(emptyList(), 32.0, -96.0, 1.0) + "]}]"))
        assertNull(RoadLocator.roadAt("[{\"edges\":null}]"))
        assertNull(RoadLocator.roadAt("not json"))
        // Named, but missing where it is: skipped, not a crash.
        assertNull(RoadLocator.roadAt("[{\"edges\":[{\"distance\":1.0,\"edge_info\":{\"names\":[\"I 30\"]}}]}]"))
    }

    @Test
    fun `no more spots than the route server takes`() {
        val roads = AvoidedRoads(file)
        val many = (1..AvoidedRoads.MAX_SPOTS).map { Spot(it.toDouble(), 0.0) }
        assertEquals(true, roads.add(AvoidedRoad("I 635", many)))
        assertEquals(false, roads.add(AvoidedRoad("I 30", listOf(Spot(-1.0, 0.0)))))
        assertEquals(listOf("I 635"), roads.roads.map { it.name })
        roads.setOn("I 635", false)
        assertEquals(true, roads.add(AvoidedRoad("I 30", listOf(Spot(-1.0, 0.0)))))
        assertEquals(false, roads.setOn("I 635", true))
    }

    @Test
    fun `roads are kept, merged by name, switched, and forgotten`() {
        val roads = AvoidedRoads(file)
        roads.add(AvoidedRoad("I 635", listOf(Spot(1.0, 2.0))))
        roads.add(AvoidedRoad("I 30", listOf(Spot(3.0, 4.0))))
        roads.setOn("I 635", false)
        roads.add(AvoidedRoad("I 635", listOf(Spot(5.0, 6.0), Spot(1.0, 2.0))))

        val again = AvoidedRoads(file)
        assertEquals(
            listOf(AvoidedRoad("I 635", listOf(Spot(1.0, 2.0), Spot(5.0, 6.0)), on = true), AvoidedRoad("I 30", listOf(Spot(3.0, 4.0)))),
            again.roads,
        )
        again.setOn("I 30", false)
        assertEquals(listOf(Spot(1.0, 2.0), Spot(5.0, 6.0)), again.activeSpots())
        again.remove("I 635")
        assertEquals(emptyList<Spot>(), AvoidedRoads(file).activeSpots())
    }
}
