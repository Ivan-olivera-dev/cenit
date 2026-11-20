package io.github.ivan_olivera_dev.cenit.core.places

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader
import java.time.ZoneId

class CitySearchTest {

    private val fixture = listOf(
        CityEntry("El Perelló", "El Perello", "Valencia", "ES", 39.2817, -0.2763, 2_500, "Europe/Madrid"),
        CityEntry("El Perelló", "El Perello", "Catalonia", "ES", 40.8740, 0.7130, 2_900, "Europe/Madrid"),
        CityEntry("El Perellonet", "El Perellonet", "Valencia", "ES", 39.3083, -0.3011, 1_400, "Europe/Madrid"),
        CityEntry("Madrid", "Madrid", "Madrid", "ES", 40.4165, -3.7026, 3_255_944, "Europe/Madrid"),
        CityEntry("Madrid", "Madrid", "Cundinamarca", "CO", 4.7325, -74.2642, 135_000, "America/Bogota"),
        CityEntry("Málaga", "Malaga", "Andalusia", "ES", 36.7202, -4.4203, 592_346, "Europe/Madrid"),
        CityEntry("La Malahá", "La Malaha", "Andalusia", "ES", 37.1014, -3.7239, 1_800, "Europe/Madrid"),
        CityEntry("Perella", "Perella", "Campania", "IT", 40.8500, 14.2500, 900, "Europe/Rome"),
        CityEntry("Łódź", "Lodz", "Lodz Voivodeship", "PL", 51.7592, 19.4560, 670_642, "Europe/Warsaw"),
        CityEntry("São Paulo", "Sao Paulo", "Sao Paulo", "BR", -23.5475, -46.6361, 12_325_232, "America/Sao_Paulo"),
        CityEntry("Peñíscola", "Peniscola", "Valencia", "ES", 40.3594, 0.4067, 7_683, "Europe/Madrid"),
    )

    private val index = CityIndex.fromEntries(fixture)

    @Test
    fun `perell matches both El Perello entries and El Perellonet with exact prefix ranked first`() {
        val results = index.search("perell")
        val names = results.map { it.city.name }

        assertEquals(
            listOf("Perella", "El Perelló", "El Perelló", "El Perellonet"),
            names,
        )
        // Within non-leading word-start matches, higher population comes first
        val perelloEntries = results.filter { it.city.name == "El Perelló" }
        assertEquals(2, perelloEntries.size)
        assertEquals("Catalonia", perelloEntries[0].city.admin1)
        assertEquals("Valencia", perelloEntries[1].city.admin1)
    }

    @Test
    fun `MADRID and madrid return identical results ranked by population`() {
        val upper = index.search("MADRID")
        val lower = index.search("madrid")

        assertEquals(upper, lower)
        assertEquals(2, lower.size)
        assertEquals("ES", lower[0].city.countryCode)
        assertEquals("CO", lower[1].city.countryCode)
        assertEquals(ZoneId.of("Europe/Madrid"), lower[0].city.zoneIdOrNull())
        assertEquals(ZoneId.of("America/Bogota"), lower[1].city.zoneIdOrNull())
    }

    @Test
    fun `Malaga is found via accent-free prefix mala ahead of mid-name match`() {
        val results = index.search("mala")
        assertEquals(listOf("Málaga", "La Malahá"), results.map { it.city.name })
    }

    @Test
    fun `accented and special Latin characters match ASCII queries`() {
        assertEquals(listOf("Łódź"), index.search("lodz").map { it.city.name })
        assertEquals(listOf("São Paulo"), index.search("sao").map { it.city.name })
        assertEquals(listOf("Peñíscola"), index.search("penis").map { it.city.name })
        assertEquals(
            index.search("perello").map { it.city },
            index.search("PERELLÓ").map { it.city },
        )
    }

    @Test
    fun `empty or blank query returns empty list`() {
        assertTrue(index.search("").isEmpty())
        assertTrue(index.search("   \t  ").isEmpty())
    }

    @Test
    fun `disambiguates equal city names using admin1 and country`() {
        val results = index.search("perell").filter { it.city.name == "El Perelló" }
        assertEquals(2, results.size)
        assertNotEquals(results[0].regionLabel, results[1].regionLabel)
        assertTrue(results[0].regionLabel.contains("Catalonia"))
        assertTrue(results[1].regionLabel.contains("Valencia"))

        val madrids = index.search("madrid") { code ->
            when (code) {
                "ES" -> "España"
                "CO" -> "Colombia"
                else -> code
            }
        }
        assertEquals("España", madrids[0].regionLabel) // admin1 == name, so country alone
        assertEquals("Cundinamarca · Colombia", madrids[1].regionLabel)
    }

    @Test
    fun `search respects default limit of 20 results`() {
        val many = (1..35).map { i ->
            CityEntry("Alpha $i", "Alpha $i", "Region $i", "ES", 40.0, -3.0, (1000 + i).toLong(), "Europe/Madrid")
        }
        val manyIndex = CityIndex.fromEntries(many)
        val results = manyIndex.search("alpha")

        assertEquals(20, results.size)
        assertEquals("Alpha 35", results.first().city.name)
        assertEquals("Alpha 16", results.last().city.name)
    }

    @Test
    fun `tsv parser skips comments and malformed lines and falls back to name when ascii is empty`() {
        val tsv = """
            # source=GeoNames license=CC-BY-4.0 download_date=2026-11-20
            # columns=name\tascii_name\tadmin1_name\tcountry_code\tlat\tlon\tpopulation\ttimezone

            Madrid	Madrid	Madrid	ES	40.4165	-3.7026	3255944	Europe/Madrid
            Málaga		Andalusia	ES	36.7202	-4.4203	592346	Europe/Madrid
            BrokenLineWithoutEnoughTabs
            BadCoords	BadCoords	Region	ES	95.0	0.0	10000	Europe/Madrid
        """.trimIndent()

        val parsed = CityTsvParser.parse(StringReader(tsv))
        assertEquals(2, parsed.size)
        assertEquals("Madrid", parsed[0].name)
        assertEquals("Málaga", parsed[1].name)
        assertEquals("Málaga", parsed[1].asciiName)
        assertEquals(36.7202, parsed[1].latitude, 1e-6)
        assertEquals(-4.4203, parsed[1].longitude, 1e-6)
    }
}
