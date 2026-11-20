package io.github.ivan_olivera_dev.cenit.alerts

import io.github.ivan_olivera_dev.cenit.core.Place
import io.github.ivan_olivera_dev.cenit.core.alerts.AlertKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AlertSubscriptionsStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val madrid = Place(latitude = 40.4165, longitude = -3.7026)
    private val barcelona = Place(latitude = 41.3888, longitude = 2.1590)

    @Test
    fun `missing file returns empty list`() {
        val store = AlertSubscriptionsStore(File(tempFolder.root, "alerts.json"))
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun `add, match, reload, and remove round-trip cleanly`() {
        val file = File(tempFolder.root, "alerts.json")
        val store = AlertSubscriptionsStore(file)

        val golden = AlertSubscription.create(
            kind = AlertKind.GOLDEN_EVENING,
            placeName = "Madrid",
            place = madrid,
            zoneId = "Europe/Madrid",
        )
        val sunrise = AlertSubscription.create(
            kind = AlertKind.SUNRISE,
            placeName = "Gola de \"Pujol\" \\ Albufera\n\t\u0007",
            place = barcelona,
            zoneId = "Europe/Madrid",
        )

        store.add(golden)
        store.add(sunrise)

        val reloaded = AlertSubscriptionsStore(file).load()
        assertEquals(2, reloaded.size)
        assertEquals(golden, reloaded[0])
        assertEquals(sunrise, reloaded[1])

        // Matching finds by kind + coordinates (within epsilon) and distinguishes places and kinds.
        assertNotNull(store.findMatching(AlertKind.GOLDEN_EVENING, Place(40.41652, -3.70261)))
        assertNull(store.findMatching(AlertKind.SUNRISE, madrid))
        assertNull(store.findMatching(AlertKind.GOLDEN_EVENING, barcelona))

        // Upserting the same kind+place replaces instead of duplicating.
        val updatedGolden = golden.copy(placeName = "Madrid Centro")
        val afterUpsert = store.add(updatedGolden)
        assertEquals(2, afterUpsert.size)
        assertEquals("Madrid Centro", store.findById(golden.id)?.placeName)

        // Removing deletes only the matching subscription.
        val afterRemove = store.removeMatching(AlertKind.GOLDEN_EVENING, madrid)
        assertEquals(1, afterRemove.size)
        assertEquals(sunrise.id, afterRemove.single().id)
        assertNull(store.findById(golden.id))
    }

    @Test
    fun `corrupt and hostile json files recover to empty list and are overwritten on next save`() {
        val file = File(tempFolder.root, "alerts.json")
        val hostileInputs = listOf(
            "",
            "{not valid json",
            "[]",
            "{\"version\":1,\"subscriptions\":\"wrong\"}",
            "{\"version\":1,\"subscriptions\":[{\"id\":\"a\",\"kind\":\"SUNRISE\",\"placeName\":\"\\u-001\",\"lat\":40.0,\"lon\":-3.0,\"zoneId\":\"Europe/Madrid\"}]}",
            "{\"version\":1,\"subscriptions\":[{\"id\":\"a\",\"kind\":\"UNKNOWN\",\"placeName\":\"Madrid\",\"lat\":40.0,\"lon\":-3.0,\"zoneId\":\"Europe/Madrid\"}]}",
            "{\"version\":1,\"subscriptions\":[{\"id\":\"a\",\"kind\":\"SUNRISE\",\"placeName\":\"Madrid\",\"lat\":95.0,\"lon\":-3.0,\"zoneId\":\"Europe/Madrid\"}]}",
            "{\"version\":1,\"subscriptions\":[{\"id\":\"a\",\"kind\":\"SUNRISE\",\"placeName\":\"Madrid\",\"lat\":40.0,\"lon\":-3.0,\"zoneId\":\"\"}]}",
        )

        for (raw in hostileInputs) {
            file.writeText(raw)
            val loaded = AlertSubscriptionsStore(file).load()
            assertTrue("Expected empty list for hostile input: $raw", loaded.isEmpty())
        }

        // Next write atomically replaces the corrupt file with valid data.
        file.writeText("{corrupt!!!")
        val store = AlertSubscriptionsStore(file)
        val sub = AlertSubscription.create(AlertKind.SUNSET, "Madrid", madrid, "Europe/Madrid")
        store.add(sub)
        assertEquals(listOf(sub), AlertSubscriptionsStore(file).load())
        assertTrue(!File(tempFolder.root, "alerts.json.tmp").exists())
    }
}
