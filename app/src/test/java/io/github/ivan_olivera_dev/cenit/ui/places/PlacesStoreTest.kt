package io.github.ivan_olivera_dev.cenit.ui.places

import io.github.ivan_olivera_dev.cenit.core.Place
import io.github.ivan_olivera_dev.cenit.data.PlacesStore
import io.github.ivan_olivera_dev.cenit.data.SavedPlace
import io.github.ivan_olivera_dev.cenit.ui.planner.PlaceSource
import io.github.ivan_olivera_dev.cenit.ui.planner.PlannerPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PlacesStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `missing file yields empty snapshot`() {
        val store = PlacesStore(File(tempFolder.root, "places.json"))
        val snap = store.load()
        assertNull(snap.activePlace)
        assertTrue(snap.savedPlaces.isEmpty())
    }

    @Test
    fun `persists active place with GeoNames zone and GPS place without zone`() {
        val file = File(tempFolder.root, "places.json")
        val store = PlacesStore(file)

        val city = PlannerPlace(
            name = "El Perelló",
            place = Place(39.2817, -0.2763),
            region = "Valencia · España",
            zoneId = "Europe/Madrid",
            source = PlaceSource.CITY,
        )
        store.saveActivePlace(city)

        val reloaded = PlacesStore(file).load().activePlace!!
        assertEquals("El Perelló", reloaded.name)
        assertEquals("Valencia · España", reloaded.region)
        assertEquals(39.2817, reloaded.latitude, 1e-6)
        assertEquals(-0.2763, reloaded.longitude, 1e-6)
        assertEquals("Europe/Madrid", reloaded.zoneId)
        assertEquals(PlaceSource.CITY, reloaded.source)

        val gps = PlannerPlace(
            name = "Mi ubicación",
            place = Place(40.0, -3.5),
            zoneId = null,
            source = PlaceSource.GPS,
        )
        store.saveActivePlace(gps)
        val reloadedGps = PlacesStore(file).load().activePlace!!
        assertNull(reloadedGps.zoneId)
        assertEquals(PlaceSource.GPS, reloadedGps.source)
    }

    @Test
    fun `save, rename, and delete saved places persist cleanly across reloads`() {
        val file = File(tempFolder.root, "places.json")
        val store = PlacesStore(file)

        val p1 = SavedPlace(
            id = "p-1",
            name = "Gola de \"Pujol\" \\ Albufera",
            region = "Valencia",
            latitude = 39.3406,
            longitude = -0.3122,
            zoneId = "Europe/Madrid",
            source = PlaceSource.CITY,
        )
        val p2 = SavedPlace(
            id = "p-2",
            name = "Mirador",
            region = "",
            latitude = 28.2724,
            longitude = -16.6425,
            zoneId = null,
            source = PlaceSource.MANUAL,
        )

        store.savePlace(p1)
        store.savePlace(p2)
        store.saveActivePlace(p1.toPlannerPlace())

        val afterRename = store.renamePlace("p-1", "Gola de Pujol")
        assertEquals("Gola de Pujol", afterRename.savedPlaces.first { it.id == "p-1" }.name)
        assertEquals("Gola de Pujol", afterRename.activePlace?.name)

        val reloaded = PlacesStore(file).load()
        assertEquals(2, reloaded.savedPlaces.size)
        assertEquals("Gola de Pujol", reloaded.savedPlaces[0].name)
        assertEquals("Mirador", reloaded.savedPlaces[1].name)

        val afterDelete = store.deletePlace("p-1")
        assertEquals(1, afterDelete.savedPlaces.size)
        assertEquals("p-2", afterDelete.savedPlaces.single().id)
        // Active place stays valid with its coordinates, no longer tied to deleted saved id
        assertEquals(39.3406, afterDelete.activePlace!!.latitude, 1e-6)
        assertTrue(afterDelete.activePlace!!.id.isEmpty())
    }

    @Test
    fun `corrupt json file recovers to empty snapshot`() {
        val file = File(tempFolder.root, "places.json")
        file.writeText("{not valid json!!!")
        val snap = PlacesStore(file).load()
        assertNull(snap.activePlace)
        assertTrue(snap.savedPlaces.isEmpty())
    }
}
