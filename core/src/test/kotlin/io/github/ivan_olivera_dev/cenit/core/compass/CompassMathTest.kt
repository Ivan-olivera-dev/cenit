package io.github.ivan_olivera_dev.cenit.core.compass

import io.github.ivan_olivera_dev.cenit.core.Ephemeris
import io.github.ivan_olivera_dev.cenit.core.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class CompassMathTest {

    // --- Angle normalisation ---

    @Test
    fun `normalizeDegrees maps values into half-open 0 to 360 range`() {
        assertEquals(0.0, CompassMath.normalizeDegrees(0.0), 1e-9)
        assertEquals(0.0, CompassMath.normalizeDegrees(-0.0), 1e-9)
        assertEquals(0.0, CompassMath.normalizeDegrees(360.0), 1e-9)
        assertEquals(0.0, CompassMath.normalizeDegrees(-360.0), 1e-9)
        assertEquals(0.0, CompassMath.normalizeDegrees(720.0), 1e-9)
        assertEquals(0.0, CompassMath.normalizeDegrees(-1e-16), 1e-9)
        assertEquals(45.5, CompassMath.normalizeDegrees(45.5), 1e-9)
        assertEquals(10.0, CompassMath.normalizeDegrees(370.0), 1e-9)
        assertEquals(350.0, CompassMath.normalizeDegrees(-10.0), 1e-9)
        assertEquals(355.0, CompassMath.normalizeDegrees(-725.0), 1e-9)
        assertEquals(359.999, CompassMath.normalizeDegrees(359.999), 1e-9)
    }

    @Test
    fun `roundToWholeDegree folds 359_5 and above to 0 and never returns 360`() {
        assertEquals(0, CompassMath.roundToWholeDegree(0.0))
        assertEquals(0, CompassMath.roundToWholeDegree(0.49))
        assertEquals(1, CompassMath.roundToWholeDegree(0.5))
        assertEquals(247, CompassMath.roundToWholeDegree(247.4))
        assertEquals(359, CompassMath.roundToWholeDegree(359.49))
        assertEquals(0, CompassMath.roundToWholeDegree(359.5))
        assertEquals(0, CompassMath.roundToWholeDegree(359.9))
        assertEquals(0, CompassMath.roundToWholeDegree(-0.1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `normalizeDegrees rejects NaN`() {
        CompassMath.normalizeDegrees(Double.NaN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `normalizeDegrees rejects infinity`() {
        CompassMath.normalizeDegrees(Double.POSITIVE_INFINITY)
    }

    // --- Shortest-arc delta & low-pass smoothing ---

    @Test
    fun `shortestArcDelta crosses north wrap via the shortest arc`() {
        assertEquals(0.0, CompassMath.shortestArcDelta(0.0, 0.0), 1e-9)
        assertEquals(0.0, CompassMath.shortestArcDelta(359.0, -1.0), 1e-9)
        assertEquals(20.0, CompassMath.shortestArcDelta(350.0, 10.0), 1e-9)
        assertEquals(-20.0, CompassMath.shortestArcDelta(10.0, 350.0), 1e-9)
        assertEquals(180.0, CompassMath.shortestArcDelta(0.0, 180.0), 1e-9)
        assertEquals(180.0, CompassMath.shortestArcDelta(90.0, 270.0), 1e-9)
        assertEquals(-179.0, CompassMath.shortestArcDelta(0.0, 181.0), 1e-9)
        assertEquals(179.0, CompassMath.shortestArcDelta(0.0, 179.0), 1e-9)
    }

    @Test
    fun `smoothHeading averages 350 and 10 around 0 and never 180`() {
        assertEquals(0.0, CompassMath.smoothHeading(350.0, 10.0, alpha = 0.5), 1e-9)
        assertEquals(0.0, CompassMath.smoothHeading(10.0, 350.0, alpha = 0.5), 1e-9)
        assertEquals(355.0, CompassMath.smoothHeading(350.0, 10.0, alpha = 0.25), 1e-9)
        assertEquals(5.0, CompassMath.smoothHeading(350.0, 10.0, alpha = 0.75), 1e-9)
        assertEquals(357.0, CompassMath.smoothHeading(355.0, 5.0, alpha = 0.2), 1e-9)
        assertEquals(350.0, CompassMath.smoothHeading(350.0, 10.0, alpha = 0.0), 1e-9)
        assertEquals(10.0, CompassMath.smoothHeading(350.0, 10.0, alpha = 1.0), 1e-9)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `smoothHeading rejects alpha outside 0 to 1`() {
        CompassMath.smoothHeading(10.0, 20.0, alpha = 1.1)
    }

    @Test
    fun `headingSmoother snaps on first sample and converges smoothly across 359 to 0`() {
        val smoother = HeadingSmoother(alpha = 0.25)
        assertNull(smoother.currentHeadingDeg)

        val first = smoother.update(356.0)
        assertEquals(356.0, first, 1e-9)
        assertEquals(356.0, smoother.currentHeadingDeg!!, 1e-9)

        // Step across 0°: 356° toward 4° (delta = +8°), 25% step -> 358°
        assertEquals(358.0, smoother.update(4.0), 1e-9)
        // Next step from 358° toward 4° (delta = +6°), 25% step -> 359.5°
        assertEquals(359.5, smoother.update(4.0), 1e-9)
        // Next step from 359.5° toward 4° (delta = +4.5°), 25% step -> 0.625°
        assertEquals(0.625, smoother.update(4.0), 1e-9)

        smoother.reset()
        assertNull(smoother.currentHeadingDeg)
        assertEquals(120.0, smoother.update(120.0), 1e-9)
    }

    // --- Magnetic to true north conversion ---

    @Test
    fun `magneticToTrueNorth adds east positive and west negative declination with wrap`() {
        assertEquals(5.0, CompassMath.magneticToTrueNorth(0.0, declinationDeg = 5.0), 1e-9)
        assertEquals(355.0, CompassMath.magneticToTrueNorth(0.0, declinationDeg = -5.0), 1e-9)
        assertEquals(3.0, CompassMath.magneticToTrueNorth(358.0, declinationDeg = 5.0), 1e-9)
        assertEquals(357.0, CompassMath.magneticToTrueNorth(2.0, declinationDeg = -5.0), 1e-9)
        assertEquals(247.0, CompassMath.magneticToTrueNorth(247.0, declinationDeg = 0.0), 1e-9)
        // Madrid ~ -0.5° declination
        assertEquals(246.5, CompassMath.magneticToTrueNorth(247.0, declinationDeg = -0.5), 1e-9)
    }

    // --- 16-point wind index ---

    @Test
    fun `windIndex maps all 16 compass points and exact sector boundaries`() {
        for (i in 0 until 16) {
            assertEquals("center of sector $i", i, CompassMath.windIndex(i * 22.5))
        }

        // Exact boundaries: sector i spans [i*22.5 - 11.25, i*22.5 + 11.25)
        assertEquals(0, CompassMath.windIndex(0.0))
        assertEquals(0, CompassMath.windIndex(11.24))
        assertEquals(1, CompassMath.windIndex(11.25))
        assertEquals(1, CompassMath.windIndex(33.74))
        assertEquals(2, CompassMath.windIndex(33.75))
        assertEquals(4, CompassMath.windIndex(90.0))
        assertEquals(8, CompassMath.windIndex(180.0))
        assertEquals(11, CompassMath.windIndex(247.0)) // WSW / OSO
        assertEquals(12, CompassMath.windIndex(270.0))
        assertEquals(15, CompassMath.windIndex(348.74))
        assertEquals(0, CompassMath.windIndex(348.75))
        assertEquals(0, CompassMath.windIndex(359.99))
        assertEquals(0, CompassMath.windIndex(360.0))
        assertEquals(12, CompassMath.windIndex(-90.0))
    }

    // --- Relative bearing ---

    @Test
    fun `relativeBearing computes signed delta and left right ahead classification`() {
        val aheadExact = CompassMath.relativeBearing(headingDeg = 247.0, targetAzimuthDeg = 247.0)
        assertEquals(0.0, aheadExact.signedDegrees, 1e-9)
        assertEquals(0.0, aheadExact.absDegrees, 1e-9)
        assertEquals(RelativeDirection.AHEAD, aheadExact.direction)

        val aheadWithinThreshold = CompassMath.relativeBearing(headingDeg = 247.0, targetAzimuthDeg = 246.1)
        assertEquals(-0.9, aheadWithinThreshold.signedDegrees, 1e-9)
        assertEquals(0.9, aheadWithinThreshold.absDegrees, 1e-9)
        assertEquals(RelativeDirection.AHEAD, aheadWithinThreshold.direction)

        val leftExactBoundary = CompassMath.relativeBearing(headingDeg = 247.0, targetAzimuthDeg = 246.0)
        assertEquals(-1.0, leftExactBoundary.signedDegrees, 1e-9)
        assertEquals(1.0, leftExactBoundary.absDegrees, 1e-9)
        assertEquals(RelativeDirection.LEFT, leftExactBoundary.direction)

        val rightExactBoundary = CompassMath.relativeBearing(headingDeg = 247.0, targetAzimuthDeg = 248.0)
        assertEquals(1.0, rightExactBoundary.signedDegrees, 1e-9)
        assertEquals(1.0, rightExactBoundary.absDegrees, 1e-9)
        assertEquals(RelativeDirection.RIGHT, rightExactBoundary.direction)

        // Across 360°/0° wrap
        val rightAcrossWrap = CompassMath.relativeBearing(headingDeg = 355.0, targetAzimuthDeg = 5.0)
        assertEquals(10.0, rightAcrossWrap.signedDegrees, 1e-9)
        assertEquals(10.0, rightAcrossWrap.absDegrees, 1e-9)
        assertEquals(RelativeDirection.RIGHT, rightAcrossWrap.direction)

        val leftAcrossWrap = CompassMath.relativeBearing(headingDeg = 5.0, targetAzimuthDeg = 355.0)
        assertEquals(-10.0, leftAcrossWrap.signedDegrees, 1e-9)
        assertEquals(10.0, leftAcrossWrap.absDegrees, 1e-9)
        assertEquals(RelativeDirection.LEFT, leftAcrossWrap.direction)

        // Exact opposite (180°)
        val behind = CompassMath.relativeBearing(headingDeg = 10.0, targetAzimuthDeg = 190.0)
        assertEquals(180.0, behind.signedDegrees, 1e-9)
        assertEquals(180.0, behind.absDegrees, 1e-9)
        assertEquals(RelativeDirection.RIGHT, behind.direction)
    }

    // --- Compass targets ---

    @Test
    fun `compass targets include rise set and current sun moon directions for Madrid`() {
        val madrid = Place(latitude = 40.4165, longitude = -3.7026)
        val zone = ZoneId.of("Europe/Madrid")
        val date = LocalDate.of(2026, 6, 21)
        val instant = LocalDateTime.parse("2026-06-21T16:20").atZone(zone).toInstant()

        val sunDay = Ephemeris.sunDay(madrid, date, zone)
        val moonDay = Ephemeris.moonDay(madrid, date, zone)
        val sunPos = Ephemeris.sunPosition(madrid, instant)
        val moonPos = Ephemeris.moonPosition(madrid, instant)

        val targets = CompassTargets.forMoment(
            place = madrid,
            sunDay = sunDay,
            moonDay = moonDay,
            sunPosition = sunPos,
            moonPosition = moonPos,
        )

        assertTrue(targets.isNotEmpty())
        assertTrue(targets.any { it.kind == CompassTargetKind.SUNSET && it.body == CompassBody.SUN })
        assertTrue(targets.any { it.kind == CompassTargetKind.SUN_NOW && it.body == CompassBody.SUN })
        assertTrue(targets.any { it.kind == CompassTargetKind.SUNRISE && it.body == CompassBody.SUN })

        val sunset = targets.first { it.kind == CompassTargetKind.SUNSET }
        assertEquals(302.0, sunset.azimuthDeg, 1.0)
        assertEquals(sunDay.sunset, sunset.instant)

        val sunrise = targets.first { it.kind == CompassTargetKind.SUNRISE }
        assertEquals(58.0, sunrise.azimuthDeg, 1.0)
        assertEquals(sunDay.sunrise, sunrise.instant)
    }

    @Test
    fun `compass targets handle polar day without sunrise or sunset`() {
        val tromso = Place(latitude = 69.6492, longitude = 18.9553)
        val zone = ZoneId.of("Europe/Oslo")
        val date = LocalDate.of(2026, 6, 21)
        val instant = LocalDateTime.parse("2026-06-21T12:00").atZone(zone).toInstant()

        val sunDay = Ephemeris.sunDay(tromso, date, zone)
        val moonDay = Ephemeris.moonDay(tromso, date, zone)
        val sunPos = Ephemeris.sunPosition(tromso, instant)
        val moonPos = Ephemeris.moonPosition(tromso, instant)

        val targets = CompassTargets.forMoment(
            place = tromso,
            sunDay = sunDay,
            moonDay = moonDay,
            sunPosition = sunPos,
            moonPosition = moonPos,
        )

        assertTrue(targets.none { it.kind == CompassTargetKind.SUNRISE || it.kind == CompassTargetKind.SUNSET })
        assertTrue(targets.any { it.kind == CompassTargetKind.SUN_NOW })
    }
}
