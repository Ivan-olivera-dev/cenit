package io.github.ivan_olivera_dev.cenit.ui.camera

import io.github.ivan_olivera_dev.cenit.core.PathPoint
import io.github.ivan_olivera_dev.cenit.core.SkyPosition
import io.github.ivan_olivera_dev.cenit.core.ar.ArPaths
import io.github.ivan_olivera_dev.cenit.core.ar.DeviceRotation
import io.github.ivan_olivera_dev.cenit.core.format.ClockFormat
import io.github.ivan_olivera_dev.cenit.core.sky.SkyPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Locale

class VisorOverlayStateTest {

    @Test
    fun `default layer state enables sun and moon and disables solstices`() {
        val defaults = VisorLayers.DEFAULT
        assertTrue(defaults.sun)
        assertTrue(defaults.moon)
        assertFalse(defaults.solstices)
    }

    @Test
    fun `pointing at ground triggers strictly below minus 28 degrees`() {
        assertFalse(isPointingAtGround(0.0))
        assertFalse(isPointingAtGround(-28.0))
        assertTrue(isPointingAtGround(-28.1))
        assertTrue(isPointingAtGround(-42.0))
    }

    @Test
    fun `compass calibration notice triggers on low, unreliable, and no-contact accuracy`() {
        assertTrue(needsCompassCalibration(accuracy = -1)) // SENSOR_STATUS_NO_CONTACT
        assertTrue(needsCompassCalibration(accuracy = 0))  // SENSOR_STATUS_UNRELIABLE
        assertTrue(needsCompassCalibration(accuracy = 1))  // SENSOR_STATUS_ACCURACY_LOW
        assertFalse(needsCompassCalibration(accuracy = 2)) // SENSOR_STATUS_ACCURACY_MEDIUM
        assertFalse(needsCompassCalibration(accuracy = 3)) // SENSOR_STATUS_ACCURACY_HIGH
    }

    @Test
    fun `evaluateCrossing prioritizes ground hint over path hits`() {
        val t = Instant.parse("2026-06-21T16:12:00Z")
        val paths = samplePaths(
            sunPath = listOf(PathPoint(t, SkyPosition(azimuth = 240.0, elevation = -35.0))),
        )
        val rotation = DeviceRotation.fromHeadingPitchRoll(headingDeg = 240.0, pitchDeg = -35.0)

        val result = evaluateVisorCrossing(
            rotation = rotation,
            pointingElevationDeg = -35.0,
            paths = paths,
            layers = VisorLayers(sun = true, moon = true, solstices = true),
            moonIlluminationPercent = 42,
            skyPhaseAt = { SkyPhase.NIGHT },
        )

        assertEquals(VisorCrossing.PointingAtGround(upDegrees = 35), result)
    }

    @Test
    fun `evaluateCrossing selects sun crossing with sky phase above horizon and below-horizon variant`() {
        val tAbove = Instant.parse("2026-11-15T17:12:00Z")
        val tBelow = Instant.parse("2026-11-15T18:30:00Z")
        val paths = samplePaths(
            sunPath = listOf(
                PathPoint(tAbove, SkyPosition(azimuth = 240.0, elevation = 3.0)),
                PathPoint(tBelow, SkyPosition(azimuth = 252.0, elevation = -5.0)),
            ),
        )

        val hitAbove = evaluateVisorCrossing(
            rotation = DeviceRotation.fromHeadingPitchRoll(headingDeg = 241.0, pitchDeg = 3.5),
            pointingElevationDeg = 3.5,
            paths = paths,
            layers = VisorLayers(sun = true, moon = true, solstices = false),
            moonIlluminationPercent = 42,
            skyPhaseAt = { SkyPhase.GOLDEN },
        )
        assertEquals(VisorCrossing.Sun(instant = tAbove, phase = SkyPhase.GOLDEN), hitAbove)

        val hitBelow = evaluateVisorCrossing(
            rotation = DeviceRotation.fromHeadingPitchRoll(headingDeg = 252.0, pitchDeg = -5.0),
            pointingElevationDeg = -5.0,
            paths = paths,
            layers = VisorLayers(sun = true, moon = true, solstices = false),
            moonIlluminationPercent = 42,
            skyPhaseAt = { SkyPhase.BLUE },
        )
        assertEquals(VisorCrossing.SunBelowHorizon(instant = tBelow), hitBelow)
    }

    @Test
    fun `evaluateCrossing selects moon and solstice crossings respecting active layer toggles`() {
        val tMoon = Instant.parse("2026-11-15T20:15:00Z")
        val tJune = Instant.parse("2026-06-21T18:00:00Z")
        val paths = samplePaths(
            moonPath = listOf(PathPoint(tMoon, SkyPosition(azimuth = 120.0, elevation = 22.0))),
            juneSolsticePath = listOf(PathPoint(tJune, SkyPosition(azimuth = 290.0, elevation = 14.0))),
        )

        val moonHit = evaluateVisorCrossing(
            rotation = DeviceRotation.fromHeadingPitchRoll(headingDeg = 121.0, pitchDeg = 21.0),
            pointingElevationDeg = 21.0,
            paths = paths,
            layers = VisorLayers(sun = true, moon = true, solstices = false),
            moonIlluminationPercent = 67,
            skyPhaseAt = { SkyPhase.NIGHT },
        )
        assertEquals(VisorCrossing.Moon(instant = tMoon, illuminationPercent = 67), moonHit)

        // Moon layer disabled -> no crossing
        val moonDisabled = evaluateVisorCrossing(
            rotation = DeviceRotation.fromHeadingPitchRoll(headingDeg = 121.0, pitchDeg = 21.0),
            pointingElevationDeg = 21.0,
            paths = paths,
            layers = VisorLayers(sun = true, moon = false, solstices = false),
            moonIlluminationPercent = 67,
            skyPhaseAt = { SkyPhase.NIGHT },
        )
        assertEquals(VisorCrossing.None, moonDisabled)

        // Solstice hidden when layer is off, shown when layer is on
        val solsticeOff = evaluateVisorCrossing(
            rotation = DeviceRotation.fromHeadingPitchRoll(headingDeg = 290.0, pitchDeg = 14.0),
            pointingElevationDeg = 14.0,
            paths = paths,
            layers = VisorLayers(sun = true, moon = true, solstices = false),
            moonIlluminationPercent = 67,
            skyPhaseAt = { SkyPhase.DAY },
        )
        assertEquals(VisorCrossing.None, solsticeOff)

        val solsticeOn = evaluateVisorCrossing(
            rotation = DeviceRotation.fromHeadingPitchRoll(headingDeg = 290.0, pitchDeg = 14.0),
            pointingElevationDeg = 14.0,
            paths = paths,
            layers = VisorLayers(sun = true, moon = true, solstices = true),
            moonIlluminationPercent = 67,
            skyPhaseAt = { SkyPhase.DAY },
        )
        assertEquals(
            VisorCrossing.Solstice(kind = SolsticeReferenceKind.JUNE_SOLSTICE, instant = tJune),
            solsticeOn,
        )
    }

    @Test
    fun `formatArHourLabel respects 24-hour and 12-hour settings`() {
        assertEquals("06h", formatArHourLabel(6, ClockFormat.H24, Locale.US))
        assertEquals("16h", formatArHourLabel(16, ClockFormat.H24, Locale.US))
        assertEquals("6 AM", formatArHourLabel(6, ClockFormat.H12, Locale.US))
        assertEquals("4 PM", formatArHourLabel(16, ClockFormat.H12, Locale.US))
    }

    @Test
    fun `formatCrosshairElevation includes plus sign for positive angles and honors decimal setting`() {
        assertEquals("+5°", formatCrosshairElevation(5.4, withDecimals = false, locale = Locale.US))
        assertEquals("+5.4°", formatCrosshairElevation(5.4, withDecimals = true, locale = Locale.US))
        assertEquals("+5,4°", formatCrosshairElevation(5.4, withDecimals = true, locale = Locale.forLanguageTag("es")))
        assertEquals("0°", formatCrosshairElevation(0.0, withDecimals = false, locale = Locale.US))
        assertEquals("0°", formatCrosshairElevation(-0.2, withDecimals = false, locale = Locale.US))
        assertEquals("\u221212.3°", formatCrosshairElevation(-12.3, withDecimals = true, locale = Locale.US))
    }

    private fun samplePaths(
        sunPath: List<PathPoint> = emptyList(),
        moonPath: List<PathPoint> = emptyList(),
        juneSolsticePath: List<PathPoint> = emptyList(),
        decemberSolsticePath: List<PathPoint> = emptyList(),
        marchEquinoxPath: List<PathPoint> = emptyList(),
    ): ArPaths = ArPaths(
        sunPath = sunPath,
        moonPath = moonPath,
        juneSolsticePath = juneSolsticePath,
        decemberSolsticePath = decemberSolsticePath,
        marchEquinoxPath = marchEquinoxPath,
        hourMarkers = emptyList(),
        sunrise = null,
        sunset = null,
    )
}
