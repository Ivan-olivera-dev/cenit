package io.github.ivan_olivera_dev.cenit.core.ar

import io.github.ivan_olivera_dev.cenit.core.Ephemeris
import io.github.ivan_olivera_dev.cenit.core.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class ArPathsTest {
    private val madrid = Place(latitude = 40.4165, longitude = -3.7026)
    private val madridZone = ZoneId.of("Europe/Madrid")
    private val tromso = Place(latitude = 69.6492, longitude = 18.9553)
    private val tromsoZone = ZoneId.of("Europe/Oslo")

    @Test
    fun `Madrid 2026-06-21 samples sun moon solstices equinox hour markers and rise set azimuths`() {
        val date = LocalDate.of(2026, 6, 21)
        val paths = ArPaths.compute(madrid, date, madridZone)

        // Default step = 10 minutes -> 24 * 6 + 1 = 145 points from 00:00 to next 00:00 inclusive
        assertEquals(145, paths.sunPath.size)
        assertEquals(145, paths.moonPath.size)
        assertEquals(145, paths.juneSolsticePath.size)
        assertEquals(145, paths.decemberSolsticePath.size)
        assertEquals(145, paths.marchEquinoxPath.size)

        // Whole local hours: 00:00 through 23:00 (24 markers)
        assertEquals(24, paths.hourMarkers.size)
        assertEquals((0..23).map { LocalTime.of(it, 0) }, paths.hourMarkers.map { it.localTime })
        paths.hourMarkers.forEach { marker ->
            assertEquals(0, marker.localTime.minute)
            assertEquals(0, marker.localTime.second)
            assertEquals(Ephemeris.sunPosition(madrid, marker.instant, apparent = true), marker.position)
        }

        // Sunrise and sunset on the horizon match EphemerisTest reference (~58 deg and ~302 deg)
        val sunrise = paths.sunrise
        val sunset = paths.sunset
        assertNotNull(sunrise)
        assertNotNull(sunset)
        assertEquals(58.0, sunrise!!.azimuthDeg, 1.0)
        assertEquals(0.0, sunrise.elevationDeg, 1e-9)
        assertEquals(LocalTime.of(6, 44), LocalTime.of(sunrise.localTime.hour, sunrise.localTime.minute))

        assertEquals(302.0, sunset!!.azimuthDeg, 1.0)
        assertEquals(0.0, sunset.elevationDeg, 1e-9)
        assertEquals(LocalTime.of(21, 48), LocalTime.of(sunset.localTime.hour, sunset.localTime.minute))

        // Solstice & equinox paths ordered by peak elevation in Northern Hemisphere
        val junePeak = paths.juneSolsticePath.maxOf { it.position.elevation }
        val equinoxPeak = paths.marchEquinoxPath.maxOf { it.position.elevation }
        val decPeak = paths.decemberSolsticePath.maxOf { it.position.elevation }
        assertEquals(73.0, junePeak, 0.2)
        assertTrue("Expected June ($junePeak) > Equinox ($equinoxPeak) > Dec ($decPeak)", junePeak > equinoxPeak && equinoxPeak > decPeak)
        assertEquals(26.1, decPeak, 0.3)
    }

    @Test
    fun `sun path uses apparent elevation including atmospheric refraction`() {
        val date = LocalDate.of(2026, 6, 21)
        val paths = ArPaths.compute(madrid, date, madridZone, stepMinutes = 10)
        // Pick a sample close to the horizon where refraction lift is unmistakable (~0.5 deg)
        val nearHorizon = paths.sunPath.first { it.position.elevation in -1.0..2.0 }
        val geometric = Ephemeris.sunPosition(madrid, nearHorizon.instant, apparent = false)
        val apparent = Ephemeris.sunPosition(madrid, nearHorizon.instant, apparent = true)

        assertEquals(apparent.elevation, nearHorizon.position.elevation, 1e-9)
        assertTrue(
            "Apparent elevation (${nearHorizon.position.elevation}) should exceed geometric (${geometric.elevation})",
            nearHorizon.position.elevation > geometric.elevation + 0.1,
        )
    }

    @Test
    fun `Madrid spring-forward DST day 2026-03-29 has 23 whole hours skipping 02 00`() {
        val springDst = LocalDate.of(2026, 3, 29)
        val paths = ArPaths.compute(madrid, springDst, madridZone)

        // 23-hour day: 23 * 6 + 1 = 139 samples at 10-minute step
        assertEquals(139, paths.sunPath.size)
        assertEquals(139, paths.moonPath.size)

        // 23 whole-hour markers; 02:00 does not exist in Europe/Madrid on spring DST
        assertEquals(23, paths.hourMarkers.size)
        assertTrue(paths.hourMarkers.none { it.localTime == LocalTime.of(2, 0) })
        assertEquals(LocalTime.of(1, 0), paths.hourMarkers[1].localTime)
        assertEquals(LocalTime.of(3, 0), paths.hourMarkers[2].localTime)

        assertNotNull(paths.sunrise)
        assertEquals(8, paths.sunrise!!.localTime.hour)
        assertEquals(3, paths.sunrise!!.localTime.minute)
    }

    @Test
    fun `Madrid fall-back DST day 2026-10-25 has 25 whole hours with repeated 02 00`() {
        val fallDst = LocalDate.of(2026, 10, 25)
        val paths = ArPaths.compute(madrid, fallDst, madridZone)

        // 25-hour day: 25 * 6 + 1 = 151 samples at 10-minute step
        assertEquals(151, paths.sunPath.size)
        assertEquals(25, paths.hourMarkers.size)
        assertEquals(2, paths.hourMarkers.count { it.localTime == LocalTime.of(2, 0) })
        // The two 02:00 markers are distinct instants one hour apart
        val twoAmMarkers = paths.hourMarkers.filter { it.localTime == LocalTime.of(2, 0) }
        assertEquals(3600L, twoAmMarkers[1].instant.epochSecond - twoAmMarkers[0].instant.epochSecond)
    }

    @Test
    fun `custom stepMinutes adjusts path density while keeping whole-hour markers`() {
        val date = LocalDate.of(2026, 6, 21)
        val hourly = ArPaths.compute(madrid, date, madridZone, stepMinutes = 60)
        assertEquals(25, hourly.sunPath.size)
        assertEquals(24, hourly.hourMarkers.size)

        // Non-divisor step still yields all 24 exact whole-hour markers
        val oddStep = ArPaths.compute(madrid, date, madridZone, stepMinutes = 7)
        assertEquals(24, oddStep.hourMarkers.size)
        assertEquals((0..23).map { LocalTime.of(it, 0) }, oddStep.hourMarkers.map { it.localTime })
    }

    @Test
    fun `polar day in Tromso reports null sunrise and sunset while keeping all hour markers`() {
        val date = LocalDate.of(2026, 6, 21)
        val paths = ArPaths.compute(tromso, date, tromsoZone)
        assertNull(paths.sunrise)
        assertNull(paths.sunset)
        assertEquals(24, paths.hourMarkers.size)
        assertTrue(paths.hourMarkers.all { it.position.elevation > 0.0 })
    }

    @Test
    fun `rejects non-positive stepMinutes`() {
        val date = LocalDate.of(2026, 6, 21)
        assertThrows(IllegalArgumentException::class.java) {
            ArPaths.compute(madrid, date, madridZone, stepMinutes = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ArPaths.compute(madrid, date, madridZone, stepMinutes = -10)
        }
    }
}
