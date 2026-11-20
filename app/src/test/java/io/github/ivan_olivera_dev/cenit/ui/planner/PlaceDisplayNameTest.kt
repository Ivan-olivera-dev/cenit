package io.github.ivan_olivera_dev.cenit.ui.planner

import io.github.ivan_olivera_dev.cenit.alerts.AlertNotificationFormatter
import io.github.ivan_olivera_dev.cenit.core.format.ClockFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

class PlaceDisplayNameTest {

    private val enGps = "My location"
    private val esGps = "Mi ubicación"
    private val enManual = "Manual coordinates"
    private val esManual = "Coordenadas manuales"
    private val enGpsRegion = "Phone GPS · offline"
    private val esGpsRegion = "GPS del móvil · sin conexión"

    @Test
    fun `gps default name localizes across shipped languages`() {
        assertEquals(
            enGps,
            PlaceDisplayName.resolve(
                storedName = esGps,
                source = PlaceSource.GPS,
                gpsLabel = enGps,
                manualLabel = enManual,
            ),
        )
        assertEquals(
            esGps,
            PlaceDisplayName.resolve(
                storedName = enGps,
                source = PlaceSource.GPS,
                gpsLabel = esGps,
                manualLabel = esManual,
            ),
        )
        assertEquals(
            enGps,
            PlaceDisplayName.resolve(
                storedName = "  $esGps  ",
                source = PlaceSource.GPS,
                gpsLabel = enGps,
                manualLabel = enManual,
            ),
        )
    }

    @Test
    fun `renamed gps place keeps its stored name`() {
        assertEquals(
            "Mirador del Teide",
            PlaceDisplayName.resolve(
                storedName = "Mirador del Teide",
                source = PlaceSource.GPS,
                gpsLabel = enGps,
                manualLabel = enManual,
            ),
        )
        // A GPS place explicitly renamed to the manual default label is not rewritten as manual.
        assertEquals(
            esManual,
            PlaceDisplayName.resolve(
                storedName = esManual,
                source = PlaceSource.GPS,
                gpsLabel = enGps,
                manualLabel = enManual,
            ),
        )
    }

    @Test
    fun `manual default name localizes across shipped languages while custom manual name stays unchanged`() {
        assertEquals(
            enManual,
            PlaceDisplayName.resolve(
                storedName = esManual,
                source = PlaceSource.MANUAL,
                gpsLabel = enGps,
                manualLabel = enManual,
            ),
        )
        assertEquals(
            esManual,
            PlaceDisplayName.resolve(
                storedName = enManual,
                source = PlaceSource.MANUAL,
                gpsLabel = esGps,
                manualLabel = esManual,
            ),
        )
        assertEquals(
            "Teide Overlook",
            PlaceDisplayName.resolve(
                storedName = "Teide Overlook",
                source = PlaceSource.MANUAL,
                gpsLabel = esGps,
                manualLabel = esManual,
            ),
        )
        // A MANUAL place explicitly named with the GPS label is not rewritten as GPS.
        assertEquals(
            esGps,
            PlaceDisplayName.resolve(
                storedName = esGps,
                source = PlaceSource.MANUAL,
                gpsLabel = enGps,
                manualLabel = enManual,
            ),
        )
    }

    @Test
    fun `city and default places always keep their stored names`() {
        assertEquals(
            "Madrid",
            PlaceDisplayName.resolve(
                storedName = "Madrid",
                source = PlaceSource.CITY,
                gpsLabel = enGps,
                manualLabel = enManual,
            ),
        )
        assertEquals(
            "El Perelló",
            PlaceDisplayName.resolve(
                storedName = "El Perelló",
                source = PlaceSource.CITY,
                gpsLabel = enGps,
                manualLabel = enManual,
            ),
        )
        assertEquals(
            esGps,
            PlaceDisplayName.resolve(
                storedName = esGps,
                source = PlaceSource.CITY,
                gpsLabel = enGps,
                manualLabel = enManual,
            ),
        )
        assertEquals(
            "Madrid",
            PlaceDisplayName.resolve(
                storedName = "Madrid",
                source = PlaceSource.DEFAULT,
                gpsLabel = enGps,
                manualLabel = enManual,
            ),
        )
    }

    @Test
    fun `unknown source (alert subscription) localizes known defaults and preserves custom or blank names`() {
        assertEquals(
            enGps,
            PlaceDisplayName.resolve(esGps, source = null, gpsLabel = enGps, manualLabel = enManual),
        )
        assertEquals(
            esGps,
            PlaceDisplayName.resolve(enGps, source = null, gpsLabel = esGps, manualLabel = esManual),
        )
        assertEquals(
            enManual,
            PlaceDisplayName.resolve(esManual, source = null, gpsLabel = enGps, manualLabel = enManual),
        )
        assertEquals(
            esManual,
            PlaceDisplayName.resolve(enManual, source = null, gpsLabel = esGps, manualLabel = esManual),
        )
        assertEquals(
            "Madrid",
            PlaceDisplayName.resolve("Madrid", source = null, gpsLabel = enGps, manualLabel = enManual),
        )
        assertEquals(
            "Casa",
            PlaceDisplayName.resolve("Casa", source = null, gpsLabel = enGps, manualLabel = enManual),
        )
        assertEquals(
            "   ",
            PlaceDisplayName.resolve("   ", source = null, gpsLabel = enGps, manualLabel = enManual),
        )

        val zone = ZoneId.of("Europe/Madrid")
        val eventInstant = LocalDateTime.parse("2026-11-21T07:22:00").atZone(zone).toInstant()
        val resolvedName = PlaceDisplayName.resolve(
            storedName = esGps,
            source = null,
            gpsLabel = enGps,
            manualLabel = enManual,
        )
        val formatted = AlertNotificationFormatter.format(
            eventLabel = "Blue hour",
            leadMinutes = 30,
            eventInstant = eventInstant,
            zone = zone,
            placeName = resolvedName,
            titleTemplate = "%1\$s in %2\$d minutes",
            bodyWithPlaceTemplate = "%1\$s · %2\$s",
            summaryTemplate = "%1\$s · %2\$s",
            locale = Locale.US,
            clock = ClockFormat.H24,
        )
        assertEquals("07:22 · My location", formatted.body)
    }

    @Test
    fun `gps default region localizes while city and blank regions stay unchanged`() {
        assertEquals(
            enGpsRegion,
            PlaceDisplayName.resolveRegion(esGpsRegion, PlaceSource.GPS, enGpsRegion),
        )
        assertEquals(
            esGpsRegion,
            PlaceDisplayName.resolveRegion(enGpsRegion, PlaceSource.GPS, esGpsRegion),
        )
        assertEquals(
            "",
            PlaceDisplayName.resolveRegion("", PlaceSource.GPS, enGpsRegion),
        )
        assertEquals(
            "Valencia · España",
            PlaceDisplayName.resolveRegion("Valencia · España", PlaceSource.CITY, enGpsRegion),
        )
        assertEquals(
            esGpsRegion,
            PlaceDisplayName.resolveRegion(esGpsRegion, PlaceSource.CITY, enGpsRegion),
        )
    }

    @Test
    fun `known default labels match every shipped strings_places resource`() {
        val resDir = File("src/main/res")
        assertTrue("Expected res directory at ${resDir.absolutePath}", resDir.isDirectory)

        val stringFiles = resDir.listFiles()
            ?.filter { it.isDirectory && (it.name == "values" || it.name.startsWith("values-")) }
            ?.map { File(it, "strings_places.xml") }
            ?.filter { it.isFile }
            .orEmpty()
        assertTrue("Expected at least en and es strings_places.xml files", stringFiles.size >= 2)

        val gpsLabels = mutableSetOf<String>()
        val manualLabels = mutableSetOf<String>()
        val gpsSubtitles = mutableSetOf<String>()

        val factory = DocumentBuilderFactory.newInstance()
        for (file in stringFiles) {
            val doc = factory.newDocumentBuilder().parse(file)
            val nodes = doc.getElementsByTagName("string")
            for (i in 0 until nodes.length) {
                val elem = nodes.item(i)
                val name = elem.attributes?.getNamedItem("name")?.nodeValue
                val text = elem.textContent
                when (name) {
                    "places_my_location" -> gpsLabels += text
                    "places_manual_default_name" -> manualLabels += text
                    "places_gps_subtitle" -> gpsSubtitles += text
                }
            }
        }

        assertEquals(gpsLabels, PlaceDisplayName.GPS_DEFAULT_NAMES)
        assertEquals(manualLabels, PlaceDisplayName.MANUAL_DEFAULT_NAMES)
        assertEquals(gpsSubtitles, PlaceDisplayName.GPS_DEFAULT_REGIONS)
    }
}
