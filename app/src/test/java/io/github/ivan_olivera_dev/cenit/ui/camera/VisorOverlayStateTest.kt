package io.github.ivan_olivera_dev.cenit.ui.camera

import io.github.ivan_olivera_dev.cenit.core.PathPoint
import io.github.ivan_olivera_dev.cenit.core.SkyPosition
import io.github.ivan_olivera_dev.cenit.core.ar.ArPaths
import io.github.ivan_olivera_dev.cenit.core.ar.ArProjector
import io.github.ivan_olivera_dev.cenit.core.ar.CameraFov
import io.github.ivan_olivera_dev.cenit.core.ar.DeviceRotation
import io.github.ivan_olivera_dev.cenit.core.ar.ViewportRect
import io.github.ivan_olivera_dev.cenit.core.format.ClockFormat
import io.github.ivan_olivera_dev.cenit.core.sky.SkyPhase
import io.github.ivan_olivera_dev.cenit.ui.planner.DefaultMadridPlace
import io.github.ivan_olivera_dev.cenit.ui.planner.PlannerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs

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

    @Test
    fun `visor time mode transitions between live and manual on user actions`() {
        assertTrue(shouldLiveTick(VisorTimeMode.LIVE))
        assertFalse(shouldLiveTick(VisorTimeMode.MANUAL))

        assertEquals(
            VisorTimeMode.MANUAL,
            transitionVisorTimeMode(VisorTimeMode.LIVE, VisorTimeEvent.SCRUB_TIME),
        )
        assertEquals(
            VisorTimeMode.MANUAL,
            transitionVisorTimeMode(VisorTimeMode.LIVE, VisorTimeEvent.STEP_TIME),
        )
        assertEquals(
            VisorTimeMode.MANUAL,
            transitionVisorTimeMode(VisorTimeMode.LIVE, VisorTimeEvent.PICK_DATE),
        )
        assertEquals(
            VisorTimeMode.LIVE,
            transitionVisorTimeMode(VisorTimeMode.MANUAL, VisorTimeEvent.TAP_NOW),
        )
        assertEquals(
            VisorTimeMode.LIVE,
            transitionVisorTimeMode(VisorTimeMode.LIVE, VisorTimeEvent.TAP_NOW),
        )
    }

    @Test
    fun `nextLiveTickDelayMillis caps at 30s and aligns cleanly to the minute boundary`() {
        // Exactly on a minute boundary: tick in 30 s, then at :30 tick in 30 s onto next :00
        assertEquals(30_000L, nextLiveTickDelayMillis(Instant.parse("2026-06-21T13:12:00.000Z")))
        assertEquals(30_000L, nextLiveTickDelayMillis(Instant.parse("2026-06-21T13:12:30.000Z")))

        // Early in a minute: cap at 30 s, then remaining time hits exact minute boundary
        assertEquals(30_000L, nextLiveTickDelayMillis(Instant.parse("2026-06-21T13:12:10.000Z")))
        assertEquals(20_000L, nextLiveTickDelayMillis(Instant.parse("2026-06-21T13:12:40.000Z")))

        // Sub-second precision near the minute boundary
        assertEquals(14_750L, nextLiveTickDelayMillis(Instant.parse("2026-06-21T13:12:45.250Z")))
        assertEquals(1L, nextLiveTickDelayMillis(Instant.parse("2026-06-21T13:12:59.999Z")))

        // Repeated delays from an arbitrary millisecond land exactly on the next minute boundary
        var cursor = Instant.parse("2026-06-21T13:12:17.345Z")
        val target = Instant.parse("2026-06-21T13:13:00.000Z")
        var steps = 0
        while (cursor < target && steps < 5) {
            val d = nextLiveTickDelayMillis(cursor)
            assertTrue("delay must be in 1..30000 ms, was $d", d in 1L..VISOR_LIVE_MAX_TICK_INTERVAL_MS)
            cursor = cursor.plusMillis(d)
            steps++
        }
        assertEquals(target, cursor)
        assertEquals(2, steps)
    }

    @Test
    fun `applyLiveTick syncs on minute boundary and ignores ticks in manual mode`() {
        val madrid = ZoneId.of("Europe/Madrid")
        val stale = PlannerState(
            activePlace = DefaultMadridPlace,
            zone = madrid,
            selectedDate = LocalDate.of(2026, 11, 21),
            minuteOffset = 13 * 60 + 3, // 13:03
        )

        // Manual mode never overwrites user-selected time
        val clock1312 = Clock.fixed(Instant.parse("2026-11-21T12:12:20Z"), madrid) // 13:12:20 CET
        assertNull(applyLiveTick(VisorTimeMode.MANUAL, stale, clock1312))

        // Live mode snaps stale 13:03 to 13:12 on open
        val synced = applyLiveTick(VisorTimeMode.LIVE, stale, clock1312)
        assertNotNull(synced)
        assertEquals(LocalTime.of(13, 12), synced!!.selectedTime)

        // Mid-minute tick at 13:12:50 produces no redundant update
        val clock131250 = Clock.fixed(Instant.parse("2026-11-21T12:12:50Z"), madrid)
        assertNull(applyLiveTick(VisorTimeMode.LIVE, synced, clock131250))

        // Crossing into 13:13:00 advances the selected minute
        val clock131300 = Clock.fixed(Instant.parse("2026-11-21T12:13:00Z"), madrid)
        val advanced = applyLiveTick(VisorTimeMode.LIVE, synced, clock131300)
        assertNotNull(advanced)
        assertEquals(LocalTime.of(13, 13), advanced!!.selectedTime)
        assertEquals(synced.minuteOffset + 1, advanced.minuteOffset)
    }

    @Test
    fun `applyLiveTick rolls date across midnight in live mode`() {
        val madrid = ZoneId.of("Europe/Madrid")
        val beforeMidnight = PlannerState.now(
            clock = Clock.fixed(Instant.parse("2026-06-21T21:59:30Z"), madrid), // 23:59:30 CEST
            zone = madrid,
            activePlace = DefaultMadridPlace,
        )
        assertEquals(LocalDate.of(2026, 6, 21), beforeMidnight.selectedDate)
        assertEquals(LocalTime.of(23, 59), beforeMidnight.selectedTime)

        val afterMidnightClock = Clock.fixed(Instant.parse("2026-06-21T22:00:00Z"), madrid) // 00:00:00 CEST next day
        val rolled = applyLiveTick(VisorTimeMode.LIVE, beforeMidnight, afterMidnightClock)
        assertNotNull(rolled)
        assertEquals(LocalDate.of(2026, 6, 22), rolled!!.selectedDate)
        assertEquals(0, rolled.minuteOffset)
        assertEquals(LocalTime.of(0, 0), rolled.selectedTime)
    }

    @Test
    fun `applyLiveTick handles DST spring-forward and fall-back transitions cleanly`() {
        val madrid = ZoneId.of("Europe/Madrid")

        // Spring forward: 2026-03-29 (23-hour day), 01:59 CET -> 03:00 CEST
        val beforeSpring = PlannerState.now(
            clock = Clock.fixed(Instant.parse("2026-03-29T00:59:30Z"), madrid),
            zone = madrid,
            activePlace = DefaultMadridPlace,
        )
        assertEquals(1380, beforeSpring.dayMinutes)
        assertEquals(119, beforeSpring.minuteOffset)
        assertEquals(LocalTime.of(1, 59), beforeSpring.selectedTime)

        val springTick = applyLiveTick(
            VisorTimeMode.LIVE,
            beforeSpring,
            Clock.fixed(Instant.parse("2026-03-29T01:00:00Z"), madrid),
        )
        assertNotNull(springTick)
        assertEquals(LocalDate.of(2026, 3, 29), springTick!!.selectedDate)
        assertEquals(120, springTick.minuteOffset)
        assertEquals(LocalTime.of(3, 0), springTick.selectedTime)

        // Fall back: 2026-10-25 (25-hour day), 02:59 CEST -> 02:00 CET (second 02:00)
        val beforeFall = PlannerState.now(
            clock = Clock.fixed(Instant.parse("2026-10-25T00:59:30Z"), madrid),
            zone = madrid,
            activePlace = DefaultMadridPlace,
        )
        assertEquals(1500, beforeFall.dayMinutes)
        assertEquals(179, beforeFall.minuteOffset)
        assertEquals(LocalTime.of(2, 59), beforeFall.selectedTime)

        val fallInstant = Instant.parse("2026-10-25T01:00:00Z")
        val fallTick = applyLiveTick(
            VisorTimeMode.LIVE,
            beforeFall,
            Clock.fixed(fallInstant, madrid),
        )
        assertNotNull(fallTick)
        assertEquals(LocalDate.of(2026, 10, 25), fallTick!!.selectedDate)
        assertEquals(180, fallTick.minuteOffset)
        assertEquals(LocalTime.of(2, 0), fallTick.selectedTime)
        assertEquals(fallInstant, fallTick.selectedInstant)
    }

    @Test
    fun `computeVisorSafeRect uses measured HUD bounds and falls back safely with WindowInsets`() {
        // Portrait with measured top HUD (status bar + top bar + heading tape) and bottom dock
        val portrait = computeVisorSafeRect(
            viewportWidthPx = 1080.0,
            viewportHeightPx = 2412.0,
            insetsTopPx = 120.0,
            insetsBottomPx = 64.0,
            topHudBottomPx = 372.0,
            bottomDockTopPx = 1872.0,
            topClearancePx = 10.0,
        )
        assertEquals(ViewportRect(left = 0.0, top = 382.0, right = 1080.0, bottom = 1872.0), portrait)

        // First-frame fallback before layout measurement still respects WindowInsets
        val fallback = computeVisorSafeRect(
            viewportWidthPx = 1080.0,
            viewportHeightPx = 2412.0,
            insetsTopPx = 120.0,
            insetsBottomPx = 64.0,
            topHudBottomPx = null,
            bottomDockTopPx = null,
            fallbackTopHudHeightPx = 252.0,
            fallbackBottomDockHeightPx = 520.0,
            topClearancePx = 10.0,
        )
        assertEquals(ViewportRect(left = 0.0, top = 382.0, right = 1080.0, bottom = 1828.0), fallback)

        // Landscape excludes top heading tape, left cutout, and right side dock
        val landscape = computeVisorSafeRect(
            viewportWidthPx = 2412.0,
            viewportHeightPx = 1080.0,
            insetsLeftPx = 120.0,
            insetsTopPx = 24.0,
            insetsBottomPx = 48.0,
            topHudBottomPx = 160.0,
            rightDockLeftPx = 1896.0,
            topClearancePx = 10.0,
        )
        assertEquals(ViewportRect(left = 120.0, top = 170.0, right = 1896.0, bottom = 1032.0), landscape)

        // Degenerate / overlapping bounds stay valid and never throw
        val tiny = computeVisorSafeRect(
            viewportWidthPx = 300.0,
            viewportHeightPx = 200.0,
            insetsTopPx = 80.0,
            topHudBottomPx = 180.0,
            bottomDockTopPx = 100.0,
        )
        assertTrue(tiny.right >= tiny.left)
        assertTrue(tiny.bottom >= tiny.top)
        assertEquals(ViewportRect(0.0, 0.0, 0.0, 0.0), computeVisorSafeRect(0.0, 0.0))
    }

    @Test
    fun `computeSafeEdgeArrow clamps inside safe rect including targets hidden behind top HUD or bottom dock`() {
        val projector = ArProjector(
            fov = CameraFov(horizontalDeg = 40.0, verticalDeg = 72.0),
            widthPx = 1080.0,
            heightPx = 2400.0,
        )
        val rotation = DeviceRotation.fromHeadingPitchRoll(headingDeg = 180.0, pitchDeg = 0.0)
        val safeRect = ViewportRect(left = 0.0, top = 400.0, right = 1080.0, bottom = 1850.0)
        val inset = 24.0

        // Target at center of view -> inside safeRect -> no edge arrow
        assertNull(
            computeSafeEdgeArrow(
                projector = projector,
                rotation = rotation,
                position = SkyPosition(azimuth = 180.0, elevation = 0.0),
                safeRect = safeRect,
                insetPx = inset,
            ),
        )

        // Target inside full preview (+30° up) but hidden behind top HUD (y < 400) -> arrow on top edge of safeRect
        val behindTopHudPos = SkyPosition(azimuth = 180.0, elevation = 30.0)
        val projectedTop = projector.project(rotation, behindTopHudPos)
        assertNotNull(projectedTop)
        assertTrue("sanity: target is inside full viewport", projectedTop!!.inView)
        assertTrue("sanity: target is above safeRect.top", projectedTop.y < safeRect.top)

        val topArrow = computeSafeEdgeArrow(projector, rotation, behindTopHudPos, safeRect, insetPx = inset)
        assertNotNull(topArrow)
        assertEquals(540.0, topArrow!!.x, 1.0)
        assertEquals(safeRect.top + inset, topArrow.y, 1e-6)
        assertEquals(-PI / 2.0, topArrow.angleRad, 1e-3)

        // Target inside full preview (-28° down) but hidden behind bottom dock (y > 1850) -> arrow on bottom edge of safeRect
        val behindBottomDockPos = SkyPosition(azimuth = 180.0, elevation = -28.0)
        val projectedBottom = projector.project(rotation, behindBottomDockPos)
        assertNotNull(projectedBottom)
        assertTrue("sanity: target is inside full viewport", projectedBottom!!.inView)
        assertTrue("sanity: target is below safeRect.bottom", projectedBottom.y > safeRect.bottom)

        val bottomArrow = computeSafeEdgeArrow(projector, rotation, behindBottomDockPos, safeRect, insetPx = inset)
        assertNotNull(bottomArrow)
        assertEquals(540.0, bottomArrow!!.x, 1.0)
        assertEquals(safeRect.bottom - inset, bottomArrow.y, 1e-6)
        assertEquals(PI / 2.0, bottomArrow.angleRad, 1e-3)

        // Target far off-screen up-right -> arrow stays strictly inside inset safeRect
        val farUpRight = SkyPosition(azimuth = 215.0, elevation = 40.0)
        val upRightArrow = computeSafeEdgeArrow(projector, rotation, farUpRight, safeRect, insetPx = inset)
        assertNotNull(upRightArrow)
        assertTrue(upRightArrow!!.x in (safeRect.left + inset)..(safeRect.right - inset))
        assertTrue(upRightArrow.y in (safeRect.top + inset)..(safeRect.bottom - inset))

        // Target directly behind the camera (azimuth 0° when facing 180°) -> arrow clamped inside safeRect
        val behindCamera = SkyPosition(azimuth = 0.0, elevation = 0.0)
        assertNull(projector.project(rotation, behindCamera))
        val behindArrow = computeSafeEdgeArrow(projector, rotation, behindCamera, safeRect, insetPx = inset)
        assertNotNull(behindArrow)
        assertTrue(behindArrow!!.x in (safeRect.left + inset)..(safeRect.right - inset))
        assertTrue(behindArrow.y in (safeRect.top + inset)..(safeRect.bottom - inset))
        assertEquals(PI / 2.0, abs(behindArrow.angleRad), 1e-3)

        // Landscape target hidden behind the right side dock -> arrow clamped on right edge of safeRect
        val landProjector = ArProjector(
            fov = CameraFov(horizontalDeg = 72.0, verticalDeg = 40.0),
            widthPx = 2400.0,
            heightPx = 1080.0,
        )
        val landSafeRect = ViewportRect(left = 0.0, top = 160.0, right = 1880.0, bottom = 1080.0)
        val behindRightDock = SkyPosition(azimuth = 210.0, elevation = 0.0)
        val rightArrow = computeSafeEdgeArrow(landProjector, rotation, behindRightDock, landSafeRect, insetPx = inset)
        assertNotNull(rightArrow)
        assertEquals(landSafeRect.right - inset, rightArrow!!.x, 1e-6)
        assertEquals(540.0, rightArrow.y, 1.0)
        assertEquals(0.0, rightArrow.angleRad, 1e-3)
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
