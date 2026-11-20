package io.github.ivan_olivera_dev.cenit.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Reference values: timeanddate.com for Madrid (40.4165 N, 3.7026 W), June and March 2026
 * (sun) and November 2026 (moon), retrieved on 2026-11-20; USNO for the 2026 seasons.
 * Twilight boundaries are the sun's centre at −6°, −12°, −18° (design/system.md, Sky phases).
 *
 * timeanddate shows times truncated to the minute (every computed solar event of these days
 * falls inside the reference minute, e.g. sunrise 06:44:47 → "06:44"), and Cenit displays
 * them the same way, so [hhmm] truncates.
 */
class EphemerisTest {
    private val madrid = Place(latitude = 40.4165, longitude = -3.7026)
    private val tromso = Place(latitude = 69.6492, longitude = 18.9553)
    private val madridZone = ZoneId.of("Europe/Madrid")
    private val tromsoZone = ZoneId.of("Europe/Oslo")

    private fun hhmm(instant: Instant?, zone: ZoneId = madridZone): String {
        assertNotNull("expected an event", instant)
        return instant!!.atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    private fun at(text: String, zone: ZoneId = madridZone): Instant =
        LocalDateTime.parse(text).atZone(zone).toInstant()

    private fun assertNear(expected: Instant, actual: Instant?, tolerance: Duration) {
        assertNotNull("expected an event near $expected", actual)
        val delta = Duration.between(expected, actual).abs()
        assertTrue("$actual is ${delta.seconds}s from $expected (max ${tolerance.seconds}s)", delta <= tolerance)
    }

    private val twoMinutes = Duration.ofSeconds(120)

    @Test
    fun `Madrid on 21 June 2026 the sun rises at 06 44 and sets at 21 48`() {
        val day = Ephemeris.sunDay(madrid, LocalDate.of(2026, 6, 21), madridZone)
        assertEquals("06:44", hhmm(day.sunrise))
        assertEquals("21:48", hhmm(day.sunset))
        assertEquals(58.0, Ephemeris.sunPosition(madrid, day.sunrise!!).azimuth, 1.0)
        assertEquals(302.0, Ephemeris.sunPosition(madrid, day.sunset!!).azimuth, 1.0)
        assertEquals(PolarCondition.NONE, day.polar)
    }

    @Test
    fun `Madrid on 21 June 2026 twilights and solar noon match the reference`() {
        val day = Ephemeris.sunDay(madrid, LocalDate.of(2026, 6, 21), madridZone)
        assertEquals("06:11", hhmm(day.civilDawn))
        assertEquals("22:21", hhmm(day.civilDusk))
        assertEquals("05:29", hhmm(day.nauticalDawn))
        assertEquals("23:03", hhmm(day.nauticalDusk))
        assertEquals("04:39", hhmm(day.astronomicalDawn))
        assertEquals("23:53", hhmm(day.astronomicalDusk))
        assertEquals("14:16", hhmm(day.solarNoon))
        assertEquals(73.0, day.noonElevation, 0.1)
    }

    @Test
    fun `the day window follows the time zone on the spring daylight saving change`() {
        val day = Ephemeris.sunDay(madrid, LocalDate.of(2026, 3, 29), madridZone)
        assertEquals("08:03", hhmm(day.sunrise))
        assertEquals("20:36", hhmm(day.sunset))
        assertEquals("14:19", hhmm(day.solarNoon))
        assertEquals(53.1, day.noonElevation, 0.1)
    }

    @Test
    fun `golden and blue hour follow the photographic ranges`() {
        val day = Ephemeris.sunDay(madrid, LocalDate.of(2026, 6, 21), madridZone)
        val blueMorning = day.blueMorning!!
        val goldenMorning = day.goldenMorning!!
        val goldenEvening = day.goldenEvening!!
        val blueEvening = day.blueEvening!!
        // blue −6° … −4°, golden −4° … +6° (design/system.md, phase-blue and phase-golden)
        assertEquals(day.civilDawn, blueMorning.start)
        assertEquals(blueMorning.end, goldenMorning.start)
        assertTrue(day.sunrise!! > goldenMorning.start && day.sunrise!! < goldenMorning.end)
        assertTrue(day.sunset!! > goldenEvening.start && day.sunset!! < goldenEvening.end)
        assertEquals(goldenEvening.end, blueEvening.start)
        assertEquals(day.civilDusk, blueEvening.end)
        // The ranges are defined on the geometric elevation of the centre, without refraction.
        fun geometric(t: Instant) = Ephemeris.sunPosition(madrid, t, apparent = false).elevation
        assertEquals(6.0, geometric(goldenMorning.end), 0.01)
        assertEquals(-4.0, geometric(goldenMorning.start), 0.01)
        assertEquals(-6.0, geometric(blueMorning.start), 0.01)
        assertEquals(6.0, geometric(goldenEvening.start), 0.01)
        assertEquals(-4.0, geometric(blueEvening.start), 0.01)
        // Where the sun is seen: refraction lifts it, most near the horizon.
        assertTrue(Ephemeris.sunPosition(madrid, goldenMorning.start).elevation > geometric(goldenMorning.start))
    }

    @Test
    fun `above the Arctic circle there is midnight sun in June and polar night in December`() {
        val june = Ephemeris.sunDay(tromso, LocalDate.of(2026, 6, 21), tromsoZone)
        assertNull(june.sunrise)
        assertNull(june.sunset)
        assertEquals(PolarCondition.MIDNIGHT_SUN, june.polar)

        val december = Ephemeris.sunDay(tromso, LocalDate.of(2026, 12, 21), tromsoZone)
        assertNull(december.sunrise)
        assertNull(december.sunset)
        assertNull(december.goldenMorning)
        assertEquals(PolarCondition.POLAR_NIGHT, december.polar)
    }

    @Test
    fun `Madrid moonrise and moonset in November 2026 match the reference`() {
        val nov10 = Ephemeris.moonDay(madrid, LocalDate.of(2026, 11, 10), madridZone)
        assertNear(at("2026-11-10T09:16"), nov10.moonrise, twoMinutes)
        assertNear(at("2026-11-10T18:22"), nov10.moonset, twoMinutes)
        assertEquals(124.0, Ephemeris.moonPosition(madrid, nov10.moonrise!!).azimuth, 1.5)
        assertTrue(nov10.phase.illumination < 0.05)

        val nov20 = Ephemeris.moonDay(madrid, LocalDate.of(2026, 11, 20), madridZone)
        assertNear(at("2026-11-20T03:11"), nov20.moonset, twoMinutes)
        assertNear(at("2026-11-20T15:12"), nov20.moonrise, twoMinutes)
        assertEquals(0.82, nov20.phase.illumination, 0.05)
    }

    @Test
    fun `lunar quarters of November and December 2026`() {
        val quarters = Ephemeris.nextQuarters(at("2026-11-02T00:00"), count = 5)
        assertEquals(
            listOf(LunarQuarter.NEW, LunarQuarter.FIRST_QUARTER, LunarQuarter.FULL, LunarQuarter.LAST_QUARTER, LunarQuarter.NEW),
            quarters.map { it.quarter },
        )
        assertEquals(LocalDate.of(2026, 11, 9), quarters[0].instant.atZone(madridZone).toLocalDate())
        assertEquals(LocalDate.of(2026, 11, 17), quarters[1].instant.atZone(madridZone).toLocalDate())
        // Astronomy Engine finds quarters on the Moon's geometric ecliptic longitude, about a
        // minute after the apparent-longitude instants published by timeanddate/USNO; that
        // difference is irrelevant for planning a photograph, so allow two minutes.
        assertNear(at("2026-11-24T15:53"), quarters[2].instant, twoMinutes)
        assertNear(at("2026-12-09T01:51"), quarters[4].instant, twoMinutes)
    }

    @Test
    fun `a principal phase is named only on the day it happens`() {
        val full = Ephemeris.moonDay(madrid, LocalDate.of(2026, 11, 24), madridZone)
        assertEquals(MoonPhaseName.FULL, full.phaseName)
        assertTrue(full.phase.illumination > 0.98)
        assertEquals(MoonPhaseName.WAXING_GIBBOUS, Ephemeris.moonDay(madrid, LocalDate.of(2026, 11, 23), madridZone).phaseName)
        assertEquals(MoonPhaseName.WANING_GIBBOUS, Ephemeris.moonDay(madrid, LocalDate.of(2026, 11, 25), madridZone).phaseName)
        assertEquals(MoonPhaseName.NEW, Ephemeris.moonDay(madrid, LocalDate.of(2026, 11, 9), madridZone).phaseName)
        assertEquals(MoonPhaseName.WAXING_CRESCENT, Ephemeris.moonDay(madrid, LocalDate.of(2026, 11, 10), madridZone).phaseName)
    }

    @Test
    fun `seasons of 2026`() {
        val s = Ephemeris.seasons(2026)
        assertNear(Instant.parse("2026-03-20T14:46:00Z"), s.marchEquinox, twoMinutes)
        assertNear(Instant.parse("2026-06-21T08:24:00Z"), s.juneSolstice, twoMinutes)
        assertNear(Instant.parse("2026-09-23T00:05:00Z"), s.septemberEquinox, twoMinutes)
        assertNear(Instant.parse("2026-12-21T20:50:00Z"), s.decemberSolstice, twoMinutes)
    }

    @Test
    fun `the sun path covers the local day hour by hour`() {
        val june = Ephemeris.sunPath(madrid, LocalDate.of(2026, 6, 21), madridZone, Duration.ofHours(1))
        assertEquals(25, june.size)
        assertEquals(at("2026-06-21T00:00"), june.first().instant)
        assertEquals(at("2026-06-22T00:00"), june.last().instant)
        val highest = june.maxBy { it.position.elevation }
        assertEquals(at("2026-06-21T14:00"), highest.instant)
        // 23-hour day: midnight to midnight in 1 h steps
        assertEquals(24, Ephemeris.sunPath(madrid, LocalDate.of(2026, 3, 29), madridZone, Duration.ofHours(1)).size)
        assertEquals(
            Ephemeris.moonPosition(madrid, at("2026-06-21T12:00")),
            Ephemeris.moonPath(madrid, LocalDate.of(2026, 6, 21), madridZone, Duration.ofHours(1))[12].position,
        )
    }

    @Test
    fun `shadow ratio is one over the tangent of the elevation and absent below the horizon`() {
        assertEquals(1.0, SkyPosition(azimuth = 180.0, elevation = 45.0).shadowRatio!!, 1e-9)
        assertEquals(4.33, SkyPosition(azimuth = 250.0, elevation = 13.0).shadowRatio!!, 0.01)
        assertNull(SkyPosition(azimuth = 0.0, elevation = 0.0).shadowRatio)
        assertNull(SkyPosition(azimuth = 0.0, elevation = -5.0).shadowRatio)
    }

    @Test
    fun `places outside the valid coordinate range are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { Place(latitude = 90.5, longitude = 0.0) }
        assertThrows(IllegalArgumentException::class.java) { Place(latitude = 0.0, longitude = -180.5) }
        assertThrows(IllegalArgumentException::class.java) { Place(latitude = Double.NaN, longitude = 0.0) }
        Place(latitude = -90.0, longitude = 180.0)
    }

    @Test
    fun `positions are normalised`() {
        val p = Ephemeris.sunPosition(madrid, at("2026-06-21T14:16"))
        assertTrue(p.azimuth >= 0.0 && p.azimuth < 360.0)
        assertTrue(abs(p.azimuth - 180.0) < 3.0)
    }
}
