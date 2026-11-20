package io.github.ivan_olivera_dev.cenit.ui.places

import io.github.ivan_olivera_dev.cenit.core.places.CityIndex
import io.github.ivan_olivera_dev.cenit.core.places.CityTsvParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId

class BundledCitiesAssetTest {

    @Test
    fun `bundled GeoNames asset parses completely with valid IANA zones and expected search hits`() {
        val asset = File("src/main/assets/geonames/cities.tsv")
        assertTrue("Expected bundled asset at ${asset.absolutePath}", asset.isFile)
        assertTrue("Bundled asset must be <= 6 MB (tools/geonames MAX_BYTES)", asset.length() <= 6_000_000L)

        val entries = asset.bufferedReader(Charsets.UTF_8).use { CityTsvParser.parse(it) }
        assertTrue("Expected more than 40,000 cities (Sun Seeker parity), got ${entries.size}", entries.size > 40_000)

        for (entry in entries) {
            assertNotNull("Invalid ZoneId '${entry.timezoneId}' for ${entry.name}", entry.zoneIdOrNull())
        }

        val index = CityIndex.fromEntries(entries)

        val madrid = index.search("MADRID")
        assertEquals(index.search("madrid"), madrid)
        assertTrue(madrid.isNotEmpty())
        assertEquals("Madrid", madrid.first().city.name)
        assertEquals("ES", madrid.first().city.countryCode)
        assertEquals(ZoneId.of("Europe/Madrid"), madrid.first().city.zoneIdOrNull())

        val malaga = index.search("mala")
        assertTrue(malaga.any { it.city.name == "Málaga" && it.city.countryCode == "ES" })

        val valencias = index.search("valencia").filter { it.city.name == "Valencia" }
        assertTrue("Expected multiple disambiguated Valencia entries", valencias.size >= 2)
        assertEquals(valencias.size, valencias.map { it.regionLabel }.toSet().size)

        val valenEs = index.search("valen", preferredCountryCode = "ES")
        assertEquals("Valencia", valenEs.first().city.name)
        assertEquals("ES", valenEs.first().city.countryCode)
        assertEquals("Valencia · ES", valenEs.first().regionLabel)

        val valenVe = index.search("valen", preferredCountryCode = "VE")
        assertEquals("Valencia", valenVe.first().city.name)
        assertEquals("VE", valenVe.first().city.countryCode)
        assertEquals("Carabobo · VE", valenVe.first().regionLabel)
    }
}
