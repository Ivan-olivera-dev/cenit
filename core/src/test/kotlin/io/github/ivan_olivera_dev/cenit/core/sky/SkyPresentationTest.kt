package io.github.ivan_olivera_dev.cenit.core.sky

import io.github.ivan_olivera_dev.cenit.core.Ephemeris
import io.github.ivan_olivera_dev.cenit.core.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.hypot

class SkyPresentationTest {
    private val madrid = Place(latitude = 40.4165, longitude = -3.7026)
    private val tromso = Place(latitude = 69.6492, longitude = 18.9553)
    private val madridZone = ZoneId.of("Europe/Madrid")
    private val tromsoZone = ZoneId.of("Europe/Oslo")

    private fun at(text: String, zone: ZoneId = madridZone): Instant =
        LocalDateTime.parse(text).atZone(zone).toInstant()

    // --- Dome projection ---

    @Test
    fun `dome projects zenith at origin and cardinal horizon points at horizon radius`() {
        val zenith = SkyDomeProjection.project(elevationDeg = 90.0, azimuthDeg = 123.0)
        assertEquals(0.0, zenith.x, 1e-9)
        assertEquals(0.0, zenith.y, 1e-9)

        val h = SkyDomeProjection.HORIZON_RADIUS
        assertEquals(90.0 / 102.0, h, 1e-9)

        val north = SkyDomeProjection.project(elevationDeg = 0.0, azimuthDeg = 0.0)
        assertEquals(0.0, north.x, 1e-9)
        assertEquals(-h, north.y, 1e-9)

        val east = SkyDomeProjection.project(elevationDeg = 0.0, azimuthDeg = 90.0)
        assertEquals(h, east.x, 1e-9)
        assertEquals(0.0, east.y, 1e-9)

        val south = SkyDomeProjection.project(elevationDeg = 0.0, azimuthDeg = 180.0)
        assertEquals(0.0, south.x, 1e-9)
        assertEquals(h, south.y, 1e-9)

        val west = SkyDomeProjection.project(elevationDeg = 0.0, azimuthDeg = 270.0)
        assertEquals(-h, west.x, 1e-9)
        assertEquals(0.0, west.y, 1e-9)
    }

    @Test
    fun `dome elevation rings and below-horizon twilight radii stay within the unit disc`() {
        val h = SkyDomeProjection.HORIZON_RADIUS
        assertEquals(h / 3.0, SkyDomeProjection.radiusForElevation(60.0), 1e-9)
        assertEquals(h * 2.0 / 3.0, SkyDomeProjection.radiusForElevation(30.0), 1e-9)
        assertEquals(96.0 / 102.0, SkyDomeProjection.radiusForElevation(-6.0), 1e-9)
        assertEquals(1.0, SkyDomeProjection.radiusForElevation(-12.0), 1e-9)
        // Night path below -12 deg clamps to outer disc edge (r = 1.0)
        assertEquals(1.0, SkyDomeProjection.radiusForElevation(-35.0), 1e-9)
        assertEquals(1.0, SkyDomeProjection.radiusForElevation(-89.0), 1e-9)

        val nightPoint = SkyDomeProjection.project(elevationDeg = -45.0, azimuthDeg = 20.0)
        assertEquals(1.0, hypot(nightPoint.x, nightPoint.y), 1e-9)
    }

    @Test
    fun `dome heading rotates target azimuth relative to top of disc`() {
        val eastFacingUp = SkyDomeProjection.project(
            elevationDeg = 0.0,
            azimuthDeg = 90.0,
            headingDeg = 90.0,
        )
        assertEquals(0.0, eastFacingUp.x, 1e-9)
        assertEquals(-SkyDomeProjection.HORIZON_RADIUS, eastFacingUp.y, 1e-9)
    }

    // --- Current phase & boundaries ---

    @Test
    fun `sky phase classifies exact elevation boundaries`() {
        assertEquals(SkyPhase.DAY, SkyPhase.fromElevation(6.01))
        assertEquals(SkyPhase.GOLDEN, SkyPhase.fromElevation(6.0))
        assertEquals(SkyPhase.GOLDEN, SkyPhase.fromElevation(0.0))
        assertEquals(SkyPhase.GOLDEN, SkyPhase.fromElevation(-4.0))
        assertEquals(SkyPhase.BLUE, SkyPhase.fromElevation(-4.001))
        assertEquals(SkyPhase.BLUE, SkyPhase.fromElevation(-6.0))
        assertEquals(SkyPhase.NAUTICAL, SkyPhase.fromElevation(-6.001))
        assertEquals(SkyPhase.NAUTICAL, SkyPhase.fromElevation(-12.0))
        assertEquals(SkyPhase.ASTRONOMICAL, SkyPhase.fromElevation(-12.001))
        assertEquals(SkyPhase.ASTRONOMICAL, SkyPhase.fromElevation(-18.0))
        assertEquals(SkyPhase.NIGHT, SkyPhase.fromElevation(-18.001))
    }

    @Test
    fun `current sky phase for an instant uses geometric elevation without refraction`() {
        val day = Ephemeris.sunDay(madrid, LocalDate.of(2026, 6, 21), madridZone)
        // 30 seconds before goldenMorning.start (-4 deg geometric):
        // geometric elevation is < -4 deg (BLUE), while refracted apparent elevation is > -4 deg.
        val justBeforeGolden = day.goldenMorning!!.start.minusSeconds(30)
        val geometric = Ephemeris.sunPosition(madrid, justBeforeGolden, apparent = false).elevation
        val apparent = Ephemeris.sunPosition(madrid, justBeforeGolden, apparent = true).elevation
        assertTrue("expected geometric < -4, was $geometric", geometric < -4.0)
        assertTrue("expected apparent > -4, was $apparent", apparent > -4.0)
        assertEquals(SkyPhase.BLUE, SkyPhase.at(madrid, justBeforeGolden))

        // 30 seconds before civilDawn (-6 deg geometric): geometric < -6 (NAUTICAL), apparent > -6.
        val justBeforeBlue = day.civilDawn!!.minusSeconds(30)
        assertTrue(Ephemeris.sunPosition(madrid, justBeforeBlue, apparent = false).elevation < -6.0)
        assertTrue(Ephemeris.sunPosition(madrid, justBeforeBlue, apparent = true).elevation > -6.0)
        assertEquals(SkyPhase.NAUTICAL, SkyPhase.at(madrid, justBeforeBlue))

        // At sunrise (geometric ~ -0.833 deg) it is GOLDEN; at solar noon it is DAY.
        assertEquals(SkyPhase.GOLDEN, SkyPhase.at(madrid, day.sunrise!!))
        assertEquals(SkyPhase.DAY, SkyPhase.at(madrid, day.solarNoon))
    }

    // --- Day-ribbon segments ---

    @Test
    fun `day ribbon for Madrid summer solstice partitions the day with exact SunDay boundaries`() {
        val date = LocalDate.of(2026, 6, 21)
        val sunDay = Ephemeris.sunDay(madrid, date, madridZone)
        val segments = DayRibbon.segments(madrid, date, madridZone, sunDay)

        assertEquals(
            listOf(
                SkyPhase.NIGHT,
                SkyPhase.ASTRONOMICAL,
                SkyPhase.NAUTICAL,
                SkyPhase.BLUE,
                SkyPhase.GOLDEN,
                SkyPhase.DAY,
                SkyPhase.GOLDEN,
                SkyPhase.BLUE,
                SkyPhase.NAUTICAL,
                SkyPhase.ASTRONOMICAL,
                SkyPhase.NIGHT,
            ),
            segments.map { it.phase },
        )

        assertContiguousPartition(segments, date, madridZone)
        // Exact boundary checks against SunDay
        assertEquals(sunDay.astronomicalDawn, segments[0].end)
        assertEquals(sunDay.nauticalDawn, segments[1].end)
        assertEquals(sunDay.civilDawn, segments[2].end)
        assertEquals(sunDay.goldenMorning!!.start, segments[3].end)
        assertEquals(sunDay.goldenMorning!!.end, segments[4].end)
        assertEquals(sunDay.goldenEvening!!.start, segments[5].end)
        assertEquals(sunDay.goldenEvening!!.end, segments[6].end)
        assertEquals(sunDay.civilDusk, segments[7].end)
        assertEquals(sunDay.nauticalDusk, segments[8].end)
        assertEquals(sunDay.astronomicalDusk, segments[9].end)
    }

    @Test
    fun `day ribbon handles 23-hour spring DST transition in Madrid`() {
        val date = LocalDate.of(2026, 3, 29)
        val sunDay = Ephemeris.sunDay(madrid, date, madridZone)
        val segments = DayRibbon.segments(madrid, date, madridZone, sunDay)

        assertContiguousPartition(segments, date, madridZone)
        val totalHours = Duration.between(segments.first().start, segments.last().end).toHours()
        assertEquals(23L, totalHours)

        val sunriseSeg = segments.first { !sunDay.sunrise!!.isBefore(it.start) && sunDay.sunrise!!.isBefore(it.end) }
        assertEquals(SkyPhase.GOLDEN, sunriseSeg.phase)

        val noonSeg = segments.first { !sunDay.solarNoon.isBefore(it.start) && sunDay.solarNoon.isBefore(it.end) }
        assertEquals(SkyPhase.DAY, noonSeg.phase)

        val sunsetSeg = segments.first { !sunDay.sunset!!.isBefore(it.start) && sunDay.sunset!!.isBefore(it.end) }
        assertEquals(SkyPhase.GOLDEN, sunsetSeg.phase)
    }

    @Test
    fun `day ribbon handles Tromso midnight sun and polar night`() {
        // Midnight sun: June 21 — sun stays above horizon (~+3 deg minimum), dips into GOLDEN near midnight, DAY otherwise
        val juneDate = LocalDate.of(2026, 6, 21)
        val juneSun = Ephemeris.sunDay(tromso, juneDate, tromsoZone)
        val juneSegments = DayRibbon.segments(tromso, juneDate, tromsoZone, juneSun)

        assertContiguousPartition(juneSegments, juneDate, tromsoZone)
        assertTrue(juneSegments.all { it.phase == SkyPhase.DAY || it.phase == SkyPhase.GOLDEN })
        assertTrue(juneSegments.any { it.phase == SkyPhase.DAY })
        assertTrue(juneSegments.any { it.phase == SkyPhase.GOLDEN })

        // Polar night: December 21 — sun stays below horizon (peaks ~-3.1 deg in GOLDEN), no DAY segment
        val decDate = LocalDate.of(2026, 12, 21)
        val decSun = Ephemeris.sunDay(tromso, decDate, tromsoZone)
        val decSegments = DayRibbon.segments(tromso, decDate, tromsoZone, decSun)

        assertContiguousPartition(decSegments, decDate, tromsoZone)
        assertFalse(decSegments.any { it.phase == SkyPhase.DAY })
        assertTrue(decSegments.any { it.phase == SkyPhase.NIGHT })
        assertTrue(decSegments.any { it.phase == SkyPhase.ASTRONOMICAL })
        assertTrue(decSegments.any { it.phase == SkyPhase.NAUTICAL })
        assertTrue(decSegments.any { it.phase == SkyPhase.BLUE })
        assertTrue(decSegments.any { it.phase == SkyPhase.GOLDEN })
    }

    // --- Next events ---

    @Test
    fun `next events selects events strictly after an instant in chronological order`() {
        val date = LocalDate.of(2026, 6, 21)
        val sunDay = Ephemeris.sunDay(madrid, date, madridZone)
        val moonDay = Ephemeris.moonDay(madrid, date, madridZone)
        val all = SkyEvents.forDay(madrid, sunDay, moonDay)

        val afternoon = at("2026-06-21T16:20")
        val upcoming = SkyEvents.nextKeyEvents(all, after = afternoon)
        assertTrue(upcoming.isNotEmpty())
        assertTrue(upcoming.all { it.instant.isAfter(afternoon) })
        assertEquals(upcoming.sortedBy { it.instant }, upcoming)

        val sunset = upcoming.first { it.kind == SkyEventKind.SUNSET }
        assertEquals("21:48", SkyFormatting.formatTimeTruncated(sunset.instant, madridZone))
        assertEquals(302.0, sunset.azimuth!!, 1.0)

        // Exact event instant is excluded (strictly after)
        val afterSunset = SkyEvents.nextKeyEvents(all, after = sunset.instant)
        assertFalse(afterSunset.any { it.kind == SkyEventKind.SUNSET })

        // After the last event of the day, none remain
        val lateNight = at("2026-06-21T23:59")
        assertTrue(SkyEvents.nextKeyEvents(all, after = lateNight).isEmpty())
        assertNull(SkyEvents.nextKeyEvent(all, after = lateNight))
    }

    @Test
    fun `next events works on DST and polar days`() {
        val dstDate = LocalDate.of(2026, 3, 29)
        val dstEvents = SkyEvents.forDay(
            madrid,
            Ephemeris.sunDay(madrid, dstDate, madridZone),
            Ephemeris.moonDay(madrid, dstDate, madridZone),
        )
        val dstUpcoming = SkyEvents.nextKeyEvents(dstEvents, after = at("2026-03-29T01:30"))
        val dstSunrise = dstUpcoming.first { it.kind == SkyEventKind.SUNRISE }
        assertEquals("08:03", SkyFormatting.formatTimeTruncated(dstSunrise.instant, madridZone))

        val juneDate = LocalDate.of(2026, 6, 21)
        val midnightSunEvents = SkyEvents.forDay(
            tromso,
            Ephemeris.sunDay(tromso, juneDate, tromsoZone),
            Ephemeris.moonDay(tromso, juneDate, tromsoZone),
        )
        assertFalse(midnightSunEvents.any { it.kind == SkyEventKind.SUNRISE || it.kind == SkyEventKind.SUNSET })
        assertNotNull(midnightSunEvents.firstOrNull { it.kind == SkyEventKind.SOLAR_NOON })

        val decDate = LocalDate.of(2026, 12, 21)
        val polarNightEvents = SkyEvents.forDay(
            tromso,
            Ephemeris.sunDay(tromso, decDate, tromsoZone),
            Ephemeris.moonDay(tromso, decDate, tromsoZone),
        )
        assertFalse(polarNightEvents.any { it.kind == SkyEventKind.SUNRISE || it.kind == SkyEventKind.SUNSET })
        assertNotNull(polarNightEvents.firstOrNull { it.kind == SkyEventKind.BLUE_MORNING })
        assertNotNull(polarNightEvents.firstOrNull { it.kind == SkyEventKind.SOLAR_NOON })
    }

    // --- Time & number formatting ---

    @Test
    fun `time formatting truncates seconds and never rounds up`() {
        assertEquals("06:44", SkyFormatting.formatTimeTruncated(at("2026-06-21T06:44:00"), madridZone))
        assertEquals("06:44", SkyFormatting.formatTimeTruncated(at("2026-06-21T06:44:47"), madridZone))
        assertEquals(
            "06:44",
            SkyFormatting.formatTimeTruncated(at("2026-06-21T06:44:59").plusMillis(999), madridZone),
        )
        assertEquals("23:59", SkyFormatting.formatTimeTruncated(at("2026-06-21T23:59:59"), madridZone))
        assertEquals("00:00", SkyFormatting.formatTimeTruncated(at("2026-06-21T00:00:59"), madridZone))

        val day = Ephemeris.sunDay(madrid, LocalDate.of(2026, 6, 21), madridZone)
        assertEquals("06:44", SkyFormatting.formatTimeTruncated(day.sunrise!!, madridZone))
        assertEquals("21:48", SkyFormatting.formatTimeTruncated(day.sunset!!, madridZone))
    }

    @Test
    fun `decimal and compass formatting match locale and 16-point rose`() {
        val es = Locale.forLanguageTag("es-ES")
        assertEquals("4,4", SkyFormatting.formatDecimal(4.36, 1, es))
        assertEquals("4.4", SkyFormatting.formatDecimal(4.36, 1, Locale.US))
        assertEquals("−4,0", SkyFormatting.formatDecimal(-4.0, 1, es))
        assertEquals("0,0", SkyFormatting.formatDecimal(-0.01, 1, es))
        assertEquals("40,42", SkyFormatting.formatDecimal(40.4165, 2, es))

        assertEquals(0, SkyFormatting.compassIndex(0.0))
        assertEquals(0, SkyFormatting.compassIndex(359.0))
        assertEquals(4, SkyFormatting.compassIndex(90.0))
        assertEquals(8, SkyFormatting.compassIndex(180.0))
        assertEquals(11, SkyFormatting.compassIndex(247.0))
        assertEquals(12, SkyFormatting.compassIndex(270.0))
    }

    private fun assertContiguousPartition(
        segments: List<DayRibbonSegment>,
        date: LocalDate,
        zone: ZoneId,
    ) {
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        assertTrue(segments.isNotEmpty())
        assertEquals(start, segments.first().start)
        assertEquals(end, segments.last().end)
        assertEquals(0.0, segments.first().startFraction, 1e-9)
        assertEquals(1.0, segments.last().endFraction, 1e-9)
        for (i in 0 until segments.lastIndex) {
            assertEquals(segments[i].end, segments[i + 1].start)
            assertEquals(segments[i].endFraction, segments[i + 1].startFraction, 1e-9)
            assertTrue("adjacent segments should not have same phase", segments[i].phase != segments[i + 1].phase)
        }
    }
}
