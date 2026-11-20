package io.github.ivan_olivera_dev.cenit.core.sky.chart

import io.github.ivan_olivera_dev.cenit.core.Ephemeris
import io.github.ivan_olivera_dev.cenit.core.LunarQuarter
import io.github.ivan_olivera_dev.cenit.core.PathPoint
import io.github.ivan_olivera_dev.cenit.core.Place
import io.github.ivan_olivera_dev.cenit.core.PolarCondition
import io.github.ivan_olivera_dev.cenit.core.SkyPosition
import io.github.ivan_olivera_dev.cenit.core.sky.SkyFormatting
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

class SkyChartTest {
    private val madrid = Place(latitude = 40.4165, longitude = -3.7026)
    private val tromso = Place(latitude = 69.6492, longitude = 18.9553)
    private val madridZone = ZoneId.of("Europe/Madrid")
    private val tromsoZone = ZoneId.of("Europe/Oslo")

    private fun at(text: String, zone: ZoneId = madridZone): Instant =
        LocalDateTime.parse(text).atZone(zone).toInstant()

    private fun pathPoint(elevationDeg: Double): PathPoint =
        PathPoint(Instant.EPOCH, SkyPosition(azimuth = 180.0, elevation = elevationDeg))

    // --- Chart Y-axis scaling ---

    @Test
    fun `chart Y max rounds peak plus padding to next 10 degrees with 30 to 90 degree bounds`() {
        assertEquals(-20.0, ElevationChartScale.MIN_ELEVATION_DEG, 1e-9)
        assertEquals(30.0, ElevationChartScale.computeYMaxDeg(emptyList(), emptyList()), 1e-9)
        assertEquals(30.0, ElevationChartScale.computeYMaxDeg(listOf(pathPoint(-3.1)), emptyList()), 1e-9)
        assertEquals(30.0, ElevationChartScale.computeYMaxDeg(listOf(pathPoint(24.0)), emptyList()), 1e-9)
        assertEquals(40.0, ElevationChartScale.computeYMaxDeg(listOf(pathPoint(24.1)), emptyList()), 1e-9)
        assertEquals(50.0, ElevationChartScale.computeYMaxDeg(listOf(pathPoint(43.8)), emptyList()), 1e-9)
        assertEquals(80.0, ElevationChartScale.computeYMaxDeg(listOf(pathPoint(73.0)), listOf(pathPoint(40.0))), 1e-9)
        assertEquals(80.0, ElevationChartScale.computeYMaxDeg(listOf(pathPoint(20.0)), listOf(pathPoint(71.5))), 1e-9)
        assertEquals(90.0, ElevationChartScale.computeYMaxDeg(listOf(pathPoint(85.0)), emptyList()), 1e-9)
        assertEquals(90.0, ElevationChartScale.computeYMaxDeg(listOf(pathPoint(90.0)), emptyList()), 1e-9)
    }

    @Test
    fun `chart Y fraction maps yMax to 0, yMin to 1, and horizon proportionally`() {
        val scale = ElevationChartScale(yMinDeg = -20.0, yMaxDeg = 80.0)
        assertEquals(0.0, scale.yFraction(80.0), 1e-9)
        assertEquals(0.8, scale.yFraction(0.0), 1e-9)
        assertEquals(0.8, scale.horizonYFraction, 1e-9)
        assertEquals(0.86, scale.yFraction(-6.0), 1e-9)
        assertEquals(0.92, scale.yFraction(-12.0), 1e-9)
        assertEquals(0.98, scale.yFraction(-18.0), 1e-9)
        assertEquals(1.0, scale.yFraction(-20.0), 1e-9)
        assertEquals(1.1, scale.yFraction(-30.0), 1e-9)
        assertEquals(1.0, scale.yFractionClamped(-30.0), 1e-9)
        assertEquals(0.0, scale.yFractionClamped(95.0), 1e-9)
    }

    // --- Band rectangles from a SunDay ---

    @Test
    fun `band rectangles from Madrid summer solstice SunDay partition 0 to 1 with exact thresholds`() {
        val date = LocalDate.of(2026, 6, 21)
        val sunDay = Ephemeris.sunDay(madrid, date, madridZone)
        val bands = ElevationChartScale.bandsFromSunDay(sunDay)

        assertEquals(
            listOf(
                ChartSkyBand.DAY,
                ChartSkyBand.CIVIL,
                ChartSkyBand.NAUTICAL,
                ChartSkyBand.ASTRONOMICAL,
                ChartSkyBand.NIGHT,
            ),
            bands.map { it.band },
        )
        assertContiguousBands(bands)

        // Madrid noon ~73.0 deg -> yMax = 80.0, span = 100.0
        assertEquals(80.0, bands[0].upperElevationDeg, 1e-9)
        assertEquals(0.0, bands[0].lowerElevationDeg, 1e-9)
        assertEquals(0.0, bands[0].topFraction, 1e-9)
        assertEquals(0.80, bands[0].bottomFraction, 1e-9)

        assertEquals(0.0, bands[1].upperElevationDeg, 1e-9)
        assertEquals(-6.0, bands[1].lowerElevationDeg, 1e-9)
        assertEquals(0.80, bands[1].topFraction, 1e-9)
        assertEquals(0.86, bands[1].bottomFraction, 1e-9)

        assertEquals(-6.0, bands[2].upperElevationDeg, 1e-9)
        assertEquals(-12.0, bands[2].lowerElevationDeg, 1e-9)
        assertEquals(0.86, bands[2].topFraction, 1e-9)
        assertEquals(0.92, bands[2].bottomFraction, 1e-9)

        assertEquals(-12.0, bands[3].upperElevationDeg, 1e-9)
        assertEquals(-18.0, bands[3].lowerElevationDeg, 1e-9)
        assertEquals(0.92, bands[3].topFraction, 1e-9)
        assertEquals(0.98, bands[3].bottomFraction, 1e-9)

        assertEquals(-18.0, bands[4].upperElevationDeg, 1e-9)
        assertEquals(-20.0, bands[4].lowerElevationDeg, 1e-9)
        assertEquals(0.98, bands[4].topFraction, 1e-9)
        assertEquals(1.00, bands[4].bottomFraction, 1e-9)
    }

    @Test
    fun `band rectangles from Tromso polar night SunDay keep 30 degree minimum ceiling`() {
        val date = LocalDate.of(2026, 12, 21)
        val sunDay = Ephemeris.sunDay(tromso, date, tromsoZone)
        val bands = ElevationChartScale.bandsFromSunDay(sunDay)

        assertContiguousBands(bands)
        assertEquals(30.0, bands[0].upperElevationDeg, 1e-9)
        assertEquals(0.60, bands[0].bottomFraction, 1e-9)
        assertEquals(0.72, bands[1].bottomFraction, 1e-9)
        assertEquals(0.84, bands[2].bottomFraction, 1e-9)
        assertEquals(0.96, bands[3].bottomFraction, 1e-9)
        assertEquals(1.00, bands[4].bottomFraction, 1e-9)
    }

    // --- X-axis scaling, DST hour ticks, and chart build ---

    @Test
    fun `elevation chart for Madrid 2026-06-21 builds paths, ticks, and golden and blue highlights`() {
        val date = LocalDate.of(2026, 6, 21)
        val chart = ElevationChartData.build(madrid, date, madridZone)

        assertEquals(80.0, chart.scale.yMaxDeg, 1e-9)
        assertEquals(-20.0, chart.scale.yMinDeg, 1e-9)
        assertContiguousBands(chart.bands)

        assertEquals(0.0, chart.sunPoints.first().xFraction, 1e-9)
        assertEquals(1.0, chart.sunPoints.last().xFraction, 1e-9)
        assertEquals(0.0, chart.moonPoints.first().xFraction, 1e-9)
        assertEquals(1.0, chart.moonPoints.last().xFraction, 1e-9)

        assertEquals(listOf("00", "06", "12", "18", "24"), chart.hourTicks.map { it.label })
        assertEquals(0.0, chart.hourTicks[0].xFraction, 1e-9)
        assertEquals(0.25, chart.hourTicks[1].xFraction, 1e-9)
        assertEquals(0.50, chart.hourTicks[2].xFraction, 1e-9)
        assertEquals(0.75, chart.hourTicks[3].xFraction, 1e-9)
        assertEquals(1.0, chart.hourTicks[4].xFraction, 1e-9)

        assertEquals(2, chart.goldenRuns.size)
        assertEquals(2, chart.blueRuns.size)
        assertTrue(chart.goldenRuns.all { it.size >= 2 })
        assertTrue(chart.blueRuns.all { it.size >= 2 })

        // Round-trip X fraction <-> Instant
        val noonInstant = at("2026-06-21T12:00:00")
        assertEquals(0.5, chart.xFraction(noonInstant), 1e-9)
        assertEquals(noonInstant, chart.instantForXFraction(0.5))
    }

    @Test
    fun `elevation chart for Madrid spring DST day 2026-03-29 aligns ticks and sunrise to 23-hour day`() {
        val date = LocalDate.of(2026, 3, 29)
        val sunDay = Ephemeris.sunDay(madrid, date, madridZone)
        val chart = ElevationChartData.build(madrid, date, madridZone, sunDay = sunDay)

        assertEquals(23L, Duration.between(chart.dayStart, chart.dayEnd).toHours())
        assertEquals(listOf("00", "06", "12", "18", "24"), chart.hourTicks.map { it.label })
        assertEquals(0.0, chart.hourTicks[0].xFraction, 1e-9)
        assertEquals(5.0 / 23.0, chart.hourTicks[1].xFraction, 1e-9)
        assertEquals(11.0 / 23.0, chart.hourTicks[2].xFraction, 1e-9)
        assertEquals(17.0 / 23.0, chart.hourTicks[3].xFraction, 1e-9)
        assertEquals(1.0, chart.hourTicks[4].xFraction, 1e-9)

        // Sunrise is at 08:03 CEST, which is 7h 03m elapsed from 00:00 CET on a 23h day.
        val sunriseX = chart.xFraction(sunDay.sunrise!!)
        val expectedMin = (7.0 * 60.0 + 3.0) / (23.0 * 60.0)
        val expectedMax = (7.0 * 60.0 + 4.0) / (23.0 * 60.0)
        assertTrue("sunriseX=$sunriseX should fall in [$expectedMin, $expectedMax)", sunriseX in expectedMin..expectedMax)
    }

    @Test
    fun `elevation chart handles Tromso midnight sun and polar night`() {
        val juneChart = ElevationChartData.build(tromso, LocalDate.of(2026, 6, 21), tromsoZone)
        assertTrue(juneChart.sunPoints.all { it.elevationDeg > 0.0 })
        assertTrue(juneChart.blueRuns.isEmpty())
        assertTrue(juneChart.goldenRuns.isNotEmpty())
        assertTrue(juneChart.scale.yMaxDeg >= 50.0)
        assertContiguousBands(juneChart.bands)

        val decChart = ElevationChartData.build(tromso, LocalDate.of(2026, 12, 21), tromsoZone)
        assertTrue(decChart.sunPoints.all { it.elevationDeg < 0.0 })
        assertTrue(decChart.scale.yMaxDeg >= 30.0)
        assertContiguousBands(decChart.bands)
    }

    // --- Complete times list assembly ---

    @Test
    fun `times list for Madrid 2026-06-21 assembles all 13 sun events in order plus moon and 4 quarters`() {
        val date = LocalDate.of(2026, 6, 21)
        val schedule = SkyTimesList.build(madrid, date, madridZone)

        assertEquals(PolarCondition.NONE, schedule.polar)
        assertEquals(
            listOf(
                SunTimeKind.ASTRONOMICAL_DAWN,
                SunTimeKind.NAUTICAL_DAWN,
                SunTimeKind.CIVIL_DAWN,
                SunTimeKind.BLUE_MORNING,
                SunTimeKind.GOLDEN_MORNING,
                SunTimeKind.SUNRISE,
                SunTimeKind.SOLAR_NOON,
                SunTimeKind.GOLDEN_EVENING,
                SunTimeKind.SUNSET,
                SunTimeKind.BLUE_EVENING,
                SunTimeKind.CIVIL_DUSK,
                SunTimeKind.NAUTICAL_DUSK,
                SunTimeKind.ASTRONOMICAL_DUSK,
            ),
            schedule.sunItems.map { it.kind },
        )
        assertChronologicalSun(schedule.sunItems)

        fun item(kind: SunTimeKind) = schedule.sunItems.first { it.kind == kind }
        fun hhmm(instant: Instant) = SkyFormatting.formatTimeTruncated(instant, madridZone)

        assertEquals("04:39", hhmm(item(SunTimeKind.ASTRONOMICAL_DAWN).instant))
        assertEquals("05:29", hhmm(item(SunTimeKind.NAUTICAL_DAWN).instant))
        assertEquals("06:11", hhmm(item(SunTimeKind.CIVIL_DAWN).instant))
        assertEquals("06:11", hhmm(item(SunTimeKind.BLUE_MORNING).instant))
        assertNotNull(item(SunTimeKind.BLUE_MORNING).end)
        assertNotNull(item(SunTimeKind.GOLDEN_MORNING).end)
        assertEquals("06:44", hhmm(item(SunTimeKind.SUNRISE).instant))
        assertEquals(58.0, item(SunTimeKind.SUNRISE).azimuthDeg!!, 1.0)
        assertEquals("14:16", hhmm(item(SunTimeKind.SOLAR_NOON).instant))
        assertEquals(73.0, item(SunTimeKind.SOLAR_NOON).elevationDeg!!, 0.5)
        assertNotNull(item(SunTimeKind.GOLDEN_EVENING).end)
        assertEquals("21:48", hhmm(item(SunTimeKind.SUNSET).instant))
        assertEquals(302.0, item(SunTimeKind.SUNSET).azimuthDeg!!, 1.0)
        assertEquals("22:21", hhmm(item(SunTimeKind.BLUE_EVENING).end!!))
        assertEquals("22:21", hhmm(item(SunTimeKind.CIVIL_DUSK).instant))
        assertEquals("23:03", hhmm(item(SunTimeKind.NAUTICAL_DUSK).instant))
        assertEquals("23:53", hhmm(item(SunTimeKind.ASTRONOMICAL_DUSK).instant))

        assertNotNull(schedule.daylightDuration)
        assertEquals(15L, schedule.daylightDuration!!.toHours())

        // Selected time insertion & past checks
        assertEquals(0, SkyTimesList.selectedTimeInsertIndex(schedule.sunItems, at("2026-06-21T00:00")))
        assertEquals(
            schedule.sunItems.size,
            SkyTimesList.selectedTimeInsertIndex(schedule.sunItems, at("2026-06-21T23:59")),
        )
        val duringMorningGolden = at("2026-06-21T06:40")
        assertTrue(SkyTimesList.isPast(item(SunTimeKind.CIVIL_DAWN), duringMorningGolden))
        assertFalse(SkyTimesList.isPast(item(SunTimeKind.GOLDEN_MORNING), duringMorningGolden))
        assertFalse(SkyTimesList.isPast(item(SunTimeKind.SUNRISE), duringMorningGolden))

        // Moon items & 4 quarters
        assertChronologicalMoon(schedule.moonItems)
        assertTrue(schedule.moonItems.all { it.azimuthDeg in 0.0..360.0 })
        assertTrue(schedule.moonIlluminationPercent in 0..100)
        assertFourOrderedQuarters(schedule, date, madridZone)
    }

    @Test
    fun `times list for Madrid DST day 2026-03-29 preserves chronological order and exact times`() {
        val date = LocalDate.of(2026, 3, 29)
        val schedule = SkyTimesList.build(madrid, date, madridZone)

        assertEquals(PolarCondition.NONE, schedule.polar)
        assertEquals(13, schedule.sunItems.size)
        assertChronologicalSun(schedule.sunItems)

        val sunrise = schedule.sunItems.first { it.kind == SunTimeKind.SUNRISE }
        val noon = schedule.sunItems.first { it.kind == SunTimeKind.SOLAR_NOON }
        val sunset = schedule.sunItems.first { it.kind == SunTimeKind.SUNSET }
        assertEquals("08:03", SkyFormatting.formatTimeTruncated(sunrise.instant, madridZone))
        assertEquals("14:19", SkyFormatting.formatTimeTruncated(noon.instant, madridZone))
        assertEquals(53.1, noon.elevationDeg!!, 0.5)
        assertEquals("20:36", SkyFormatting.formatTimeTruncated(sunset.instant, madridZone))
        assertFourOrderedQuarters(schedule, date, madridZone)
    }

    @Test
    fun `elevation chart handles 25-hour autumn DST transition and clamps scrub fractions`() {
        val date = LocalDate.of(2026, 10, 25)
        val chart = ElevationChartData.build(madrid, date, madridZone)

        assertEquals(25L, Duration.between(chart.dayStart, chart.dayEnd).toHours())
        assertEquals(0.0, chart.hourTicks[0].xFraction, 1e-9)
        assertEquals(7.0 / 25.0, chart.hourTicks[1].xFraction, 1e-9)
        assertEquals(13.0 / 25.0, chart.hourTicks[2].xFraction, 1e-9)
        assertEquals(19.0 / 25.0, chart.hourTicks[3].xFraction, 1e-9)
        assertEquals(1.0, chart.hourTicks[4].xFraction, 1e-9)

        assertEquals(chart.dayStart, chart.instantForXFraction(-0.5))
        assertEquals(chart.dayEnd, chart.instantForXFraction(1.5))
    }

    @Test
    fun `times list orders moonset before moonrise when moon sets in early morning`() {
        // Reference from L13: Madrid 2026-11-20 moonset 03:11, moonrise 15:12
        val date = LocalDate.of(2026, 11, 20)
        val schedule = SkyTimesList.build(madrid, date, madridZone)

        assertEquals(listOf(MoonTimeKind.MOONSET, MoonTimeKind.MOONRISE), schedule.moonItems.map { it.kind })
        assertEquals("03:11", SkyFormatting.formatTimeTruncated(schedule.moonItems[0].instant, madridZone))
        assertEquals("15:12", SkyFormatting.formatTimeTruncated(schedule.moonItems[1].instant, madridZone))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `chart scale rejects yMax less than or equal to yMin`() {
        ElevationChartScale(yMinDeg = -20.0, yMaxDeg = -20.0)
    }

    @Test
    fun `times list for Tromso 2026-06-21 reports midnight sun with solar noon only`() {
        val date = LocalDate.of(2026, 6, 21)
        val schedule = SkyTimesList.build(tromso, date, tromsoZone)

        assertEquals(PolarCondition.MIDNIGHT_SUN, schedule.polar)
        assertEquals(listOf(SunTimeKind.SOLAR_NOON), schedule.sunItems.map { it.kind })
        assertTrue(schedule.sunItems.single().elevationDeg!! > 40.0)
        assertNull(schedule.daylightDuration)
        assertFourOrderedQuarters(schedule, date, tromsoZone)
    }

    @Test
    fun `times list for Tromso 2026-12-21 reports polar night with twilights and blue hours`() {
        val date = LocalDate.of(2026, 12, 21)
        val schedule = SkyTimesList.build(tromso, date, tromsoZone)

        assertEquals(PolarCondition.POLAR_NIGHT, schedule.polar)
        assertEquals(
            listOf(
                SunTimeKind.ASTRONOMICAL_DAWN,
                SunTimeKind.NAUTICAL_DAWN,
                SunTimeKind.CIVIL_DAWN,
                SunTimeKind.BLUE_MORNING,
                SunTimeKind.SOLAR_NOON,
                SunTimeKind.BLUE_EVENING,
                SunTimeKind.CIVIL_DUSK,
                SunTimeKind.NAUTICAL_DUSK,
                SunTimeKind.ASTRONOMICAL_DUSK,
            ),
            schedule.sunItems.map { it.kind },
        )
        assertChronologicalSun(schedule.sunItems)
        assertTrue(schedule.sunItems.first { it.kind == SunTimeKind.SOLAR_NOON }.elevationDeg!! < 0.0)
        assertNull(schedule.daylightDuration)
        assertFourOrderedQuarters(schedule, date, tromsoZone)
    }

    private fun assertContiguousBands(bands: List<ChartBandRect>) {
        assertEquals(5, bands.size)
        assertEquals(0.0, bands.first().topFraction, 1e-9)
        assertEquals(1.0, bands.last().bottomFraction, 1e-9)
        for (i in 0 until bands.lastIndex) {
            assertEquals(bands[i].bottomFraction, bands[i + 1].topFraction, 1e-9)
            assertTrue("band ${bands[i].band} height must be > 0", bands[i].heightFraction > 0.0)
        }
        assertTrue("last band height must be > 0", bands.last().heightFraction > 0.0)
    }

    private fun assertChronologicalSun(items: List<SunTimeItem>) {
        for (i in 0 until items.lastIndex) {
            assertFalse(
                "item ${items[i].kind} (${items[i].instant}) must not be after ${items[i + 1].kind} (${items[i + 1].instant})",
                items[i].instant.isAfter(items[i + 1].instant),
            )
        }
    }

    private fun assertChronologicalMoon(items: List<MoonTimeItem>) {
        for (i in 0 until items.lastIndex) {
            assertFalse(items[i].instant.isAfter(items[i + 1].instant))
        }
    }

    private fun assertFourOrderedQuarters(schedule: SkyDaySchedule, date: LocalDate, zone: ZoneId) {
        assertEquals(4, schedule.nextQuarters.size)
        val dayStart = date.atStartOfDay(zone).toInstant()
        assertFalse(schedule.nextQuarters.first().instant.isBefore(dayStart))
        for (i in 0 until schedule.nextQuarters.lastIndex) {
            assertTrue(schedule.nextQuarters[i].instant.isBefore(schedule.nextQuarters[i + 1].instant))
            val expectedNext = LunarQuarter.entries[(schedule.nextQuarters[i].quarter.ordinal + 1) % 4]
            assertEquals(expectedNext, schedule.nextQuarters[i + 1].quarter)
        }
    }
}
