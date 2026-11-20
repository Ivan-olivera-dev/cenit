package io.github.ivan_olivera_dev.cenit.ui.places

import io.github.ivan_olivera_dev.cenit.core.Place
import io.github.ivan_olivera_dev.cenit.data.PlacesStore
import io.github.ivan_olivera_dev.cenit.data.SavedPlace
import io.github.ivan_olivera_dev.cenit.ui.planner.PlaceSource
import io.github.ivan_olivera_dev.cenit.ui.planner.PlannerPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Saved places survive hostile files on disk (from the independent T8 verification, L22). */
class PlacesStoreRobustnessTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `malformed and adversarial files load as empty without throwing`() {
        val file = File(tempFolder.root, "places.json")
        val payloads = listOf(
            "",
            "   \n\t ",
            "null",
            "[]",
            "\"hello\"",
            "{\"version\":1,\"active\":{\"name\":\"x\"",
            "{\"saved\":[{\"name\":\"bad\\u12\"}]}",
            "{\"saved\":[{\"name\":\"bad\\uZZZZ\"}]}",
            "{\"saved\":[{\"name\":\"bad\\u-001\",\"lat\":0,\"lon\":0}]}",
            "{\"saved\":[{\"name\":\"bad\\x\"}]}",
            "{\"saved\":[{\"name\":\"ok\",\"lat\":-,\"lon\":0}]}",
            "{\"saved\":[{\"name\":\"ok\",\"lat\":NaN,\"lon\":0}]}",
            "{\"saved\":[{\"name\":\"ok\",\"lat\":90.1,\"lon\":0}]}",
            "{\"saved\":[{\"name\":\"ok\",\"lat\":0,\"lon\":-180.1}]}",
            "{\"saved\":[{\"name\":\"ok\",\"lat\":1e309,\"lon\":0}]}",
            "{\"version\":\"wrong\",\"active\":123,\"saved\":\"nope\"}",
            "[".repeat(500),
        )
        for (payload in payloads) {
            file.writeText(payload, Charsets.UTF_8)
            val snapshot = PlacesStore(file).load()
            assertNull("active place for payload: $payload", snapshot.activePlace)
            assertTrue("saved places for payload: $payload", snapshot.savedPlaces.isEmpty())
        }
    }

    @Test
    fun `control characters, quotes and backslashes round-trip and leave no temp file`() {
        val file = File(tempFolder.root, "places.json")
        val store = PlacesStore(file)
        val name = "Mirador \"El\" \\ \b\u000C\n\r\t\u0007 — Peña"
        val place = SavedPlace(
            id = "id-\"1\"\\x",
            name = name,
            region = "Región\t1\n2",
            latitude = 40.4165,
            longitude = -3.7026,
            zoneId = "Europe/Madrid",
            source = PlaceSource.CITY,
        )
        store.savePlace(place)
        store.saveActivePlace(place.toPlannerPlace())

        assertFalse(File(tempFolder.root, "places.json.tmp").exists())
        val reloaded = PlacesStore(file).load()
        assertEquals(name.trim(), reloaded.savedPlaces.single().name)
        assertEquals("Región\t1\n2", reloaded.savedPlaces.single().region)
        assertEquals("id-\"1\"\\x", reloaded.savedPlaces.single().id)
        assertEquals(name, reloaded.activePlace?.name)
    }

    @Test
    fun `a corrupt file is replaced by the next save`() {
        val file = File(tempFolder.root, "places.json")
        file.writeText("{corrupt", Charsets.UTF_8)
        val store = PlacesStore(file)
        val teide = PlannerPlace("Teide", Place(28.2724, -16.6425), source = PlaceSource.MANUAL)
        assertEquals("Teide", store.saveActivePlace(teide).activePlace?.name)
        assertEquals("Teide", store.load().activePlace?.name)
    }
}
