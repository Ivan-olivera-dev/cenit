package io.github.ivan_olivera_dev.cenit.ui.camera

import io.github.ivan_olivera_dev.cenit.core.Cenit
import io.github.ivan_olivera_dev.cenit.core.SkyPosition
import io.github.ivan_olivera_dev.cenit.core.ar.ArPaths
import io.github.ivan_olivera_dev.cenit.core.ar.ArProjector
import io.github.ivan_olivera_dev.cenit.core.ar.DEFAULT_CROSSING_TOLERANCE_DEG
import io.github.ivan_olivera_dev.cenit.core.ar.DeviceRotation
import io.github.ivan_olivera_dev.cenit.core.ar.EdgeArrow
import io.github.ivan_olivera_dev.cenit.core.ar.ViewportRect
import io.github.ivan_olivera_dev.cenit.core.ar.crossing
import io.github.ivan_olivera_dev.cenit.core.format.CenitFormat
import io.github.ivan_olivera_dev.cenit.core.format.ClockFormat
import io.github.ivan_olivera_dev.cenit.core.sky.SkyPhase
import io.github.ivan_olivera_dev.cenit.ui.planner.PlannerState
import java.time.Clock
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Low-pass smoothing alpha for `RotationSmoother` at `SENSOR_DELAY_GAME` (~50 Hz).
 *
 * At 50 Hz (20 ms period), `alpha = 0.14` yields a time constant of ~123 ms (`f_c ≈ 1.3 Hz`),
 * suppressing high-frequency hand tremor and magnetometer jitter (eliminating the Sun Seeker
 * "paths bounce around" complaint) while keeping live panning responsive.
 */
internal const val VISOR_SMOOTHING_ALPHA: Double = 0.14

/** Elevation threshold below which the phone is considered pointing at the ground. */
internal const val GROUND_PITCH_THRESHOLD_DEG: Double = -28.0

/** Angular crossing tolerance for solstice / equinox reference arcs (slightly tighter than sun/moon). */
internal const val SOLSTICE_CROSSING_TOLERANCE_DEG: Double = 3.5

/** Android `SensorManager.SENSOR_STATUS_ACCURACY_LOW` value (`1`); `<= 1` requires calibration. */
private const val ACCURACY_LOW_THRESHOLD: Int = 1

/** Maximum tick interval while the visor follows the device clock in live mode (30 seconds). */
internal const val VISOR_LIVE_MAX_TICK_INTERVAL_MS: Long = 30_000L

private const val MILLIS_PER_MINUTE: Long = 60_000L
private const val EDGE_DIRECTION_EPSILON: Double = 1e-9

/**
 * Whether the camera visor tracks the device clock ([LIVE]) or holds a user-chosen instant ([MANUAL]).
 */
enum class VisorTimeMode {
    LIVE,
    MANUAL,
}

/**
 * User actions in the visor HUD that affect [VisorTimeMode].
 */
enum class VisorTimeEvent {
    SCRUB_TIME,
    STEP_TIME,
    PICK_DATE,
    TAP_NOW,
}

/** Returns `true` when the visor should keep syncing [PlannerState] with the device clock. */
internal fun shouldLiveTick(mode: VisorTimeMode): Boolean = mode == VisorTimeMode.LIVE

/**
 * Pure state transition for [VisorTimeMode].
 *
 * Touching the time slider, ±5 min / ±1 h step actions, or the date chip leaves live mode;
 * tapping "Ahora" returns to live mode.
 */
internal fun transitionVisorTimeMode(
    current: VisorTimeMode,
    event: VisorTimeEvent,
): VisorTimeMode = when (event) {
    VisorTimeEvent.SCRUB_TIME,
    VisorTimeEvent.STEP_TIME,
    VisorTimeEvent.PICK_DATE,
    -> VisorTimeMode.MANUAL

    VisorTimeEvent.TAP_NOW -> VisorTimeMode.LIVE
}

/**
 * Returns the delay in milliseconds until the next live clock check from [now].
 *
 * Caps every wait at [maxIntervalMs] (30 s by default) while landing exactly on the next
 * whole-minute boundary (`:00.000`) so the visor readout and markers advance when the clock does.
 */
internal fun nextLiveTickDelayMillis(
    now: Instant,
    maxIntervalMs: Long = VISOR_LIVE_MAX_TICK_INTERVAL_MS,
): Long {
    require(maxIntervalMs > 0L) { "maxIntervalMs must be positive, was $maxIntervalMs" }
    val millisIntoMinute = Math.floorMod(now.toEpochMilli(), MILLIS_PER_MINUTE)
    val millisUntilNextMinute = MILLIS_PER_MINUTE - millisIntoMinute
    return minOf(millisUntilNextMinute, maxIntervalMs)
}

/**
 * Applies a live clock tick to [state] using [clock] when [mode] is [VisorTimeMode.LIVE].
 *
 * Returns the updated [PlannerState] only when the minute or date actually changed (including
 * midnight date rollovers and 23 h / 25 h DST days); returns `null` in [VisorTimeMode.MANUAL]
 * or when [state] already matches the current minute.
 */
internal fun applyLiveTick(
    mode: VisorTimeMode,
    state: PlannerState,
    clock: Clock,
): PlannerState? {
    if (!shouldLiveTick(mode)) return null
    val updated = state.resetToNow(clock)
    return if (updated != state) updated else null
}

/**
 * Computes the safe AR overlay drawing rectangle inside the camera preview viewport, excluding
 * system bars / display cutouts, top HUD chrome (top bar, heading tape, notices), bottom dock
 * (portrait), and right side dock (landscape).
 *
 * When a layout measurement is not yet available (`null` or `<= 0`), falls back to the supplied
 * fallback dimension added to the corresponding system-bar inset so the first frame is already
 * clipped safely.
 */
internal fun computeVisorSafeRect(
    viewportWidthPx: Double,
    viewportHeightPx: Double,
    insetsLeftPx: Double = 0.0,
    insetsTopPx: Double = 0.0,
    insetsRightPx: Double = 0.0,
    insetsBottomPx: Double = 0.0,
    topHudBottomPx: Double? = null,
    bottomDockTopPx: Double? = null,
    rightDockLeftPx: Double? = null,
    fallbackTopHudHeightPx: Double = 0.0,
    fallbackBottomDockHeightPx: Double = 0.0,
    fallbackRightDockWidthPx: Double = 0.0,
    topClearancePx: Double = 0.0,
): ViewportRect {
    if (!viewportWidthPx.isFinite() || !viewportHeightPx.isFinite() ||
        viewportWidthPx <= 0.0 || viewportHeightPx <= 0.0
    ) {
        return ViewportRect(0.0, 0.0, 0.0, 0.0)
    }

    val safeInsetsLeft = insetsLeftPx.coerceIn(0.0, viewportWidthPx)
    val safeInsetsTop = insetsTopPx.coerceIn(0.0, viewportHeightPx)
    val safeInsetsRight = insetsRightPx.coerceIn(0.0, viewportWidthPx)
    val safeInsetsBottom = insetsBottomPx.coerceIn(0.0, viewportHeightPx)
    val clearance = topClearancePx.coerceAtLeast(0.0)

    val left = safeInsetsLeft

    val rawTop = if (topHudBottomPx != null && topHudBottomPx.isFinite() && topHudBottomPx > 0.0) {
        topHudBottomPx + clearance
    } else {
        safeInsetsTop + fallbackTopHudHeightPx.coerceAtLeast(0.0) + clearance
    }
    val top = maxOf(rawTop, safeInsetsTop).coerceIn(0.0, viewportHeightPx)

    val maxRight = (viewportWidthPx - safeInsetsRight).coerceIn(left, viewportWidthPx)
    val rawRight = if (rightDockLeftPx != null && rightDockLeftPx.isFinite() && rightDockLeftPx > 0.0) {
        minOf(rightDockLeftPx, maxRight)
    } else {
        maxRight - fallbackRightDockWidthPx.coerceAtLeast(0.0)
    }
    val right = rawRight.coerceIn(left, viewportWidthPx)

    val maxBottom = (viewportHeightPx - safeInsetsBottom).coerceIn(top, viewportHeightPx)
    val rawBottom = if (bottomDockTopPx != null && bottomDockTopPx.isFinite() && bottomDockTopPx > 0.0) {
        minOf(bottomDockTopPx, maxBottom)
    } else {
        maxBottom - fallbackBottomDockHeightPx.coerceAtLeast(0.0)
    }
    val bottom = rawBottom.coerceIn(top, viewportHeightPx)

    return ViewportRect(left = left, top = top, right = right, bottom = bottom)
}

/**
 * Insets [safeRect] by [horizontalInsetPx] and [verticalInsetPx] without inverting bounds.
 */
internal fun insetVisorSafeRect(
    safeRect: ViewportRect,
    horizontalInsetPx: Double,
    verticalInsetPx: Double,
): ViewportRect {
    val hx = horizontalInsetPx.coerceAtLeast(0.0).coerceAtMost(safeRect.width / 2.0)
    val vy = verticalInsetPx.coerceAtLeast(0.0).coerceAtMost(safeRect.height / 2.0)
    return ViewportRect(
        left = safeRect.left + hx,
        top = safeRect.top + vy,
        right = safeRect.right - hx,
        bottom = safeRect.bottom - vy,
    )
}

/** Returns `true` when `(x, y)` lies within [safeRect] (inclusive). */
internal fun isInsideSafeRect(x: Double, y: Double, safeRect: ViewportRect): Boolean =
    x >= safeRect.left && x <= safeRect.right && y >= safeRect.top && y <= safeRect.bottom

/**
 * Computes an [EdgeArrow] clamped onto the inset perimeter of [safeRect] pointing from the camera
 * preview centre (crosshair) toward [position].
 *
 * Returns `null` when [position] projects inside [safeRect] or when [safeRect] has zero area.
 * Unlike `ArProjector.edgeArrow`, targets that lie inside the full camera preview but behind the
 * top HUD, bottom dock, or landscape side dock still produce a valid edge arrow on the safe border.
 */
internal fun computeSafeEdgeArrow(
    projector: ArProjector,
    rotation: DeviceRotation,
    position: SkyPosition,
    safeRect: ViewportRect,
    insetPx: Double = 0.0,
): EdgeArrow? {
    if (safeRect.width <= 0.0 || safeRect.height <= 0.0) return null

    val projected = projector.project(rotation, position)
    if (projected != null && isInsideSafeRect(projected.x, projected.y, safeRect)) {
        return null
    }

    val centerX = projector.widthPx / 2.0
    val centerY = projector.heightPx / 2.0

    var dirX: Double
    var dirY: Double
    if (projected != null) {
        dirX = projected.x - centerX
        dirY = projected.y - centerY
    } else {
        val raw = projector.edgeArrow(rotation, position, insetPx = 0.0) ?: return null
        dirX = cos(raw.angleRad)
        dirY = sin(raw.angleRad)
    }

    if (hypot(dirX, dirY) < EDGE_DIRECTION_EPSILON) {
        dirX = 0.0
        dirY = 1.0
    }

    val insetRect = insetVisorSafeRect(safeRect, insetPx, insetPx)
    val originX = centerX.coerceIn(insetRect.left, insetRect.right)
    val originY = centerY.coerceIn(insetRect.top, insetRect.bottom)

    val tx = when {
        dirX > EDGE_DIRECTION_EPSILON -> (insetRect.right - originX) / dirX
        dirX < -EDGE_DIRECTION_EPSILON -> (insetRect.left - originX) / dirX
        else -> Double.POSITIVE_INFINITY
    }
    val ty = when {
        dirY > EDGE_DIRECTION_EPSILON -> (insetRect.bottom - originY) / dirY
        dirY < -EDGE_DIRECTION_EPSILON -> (insetRect.top - originY) / dirY
        else -> Double.POSITIVE_INFINITY
    }
    val t = minOf(tx, ty).let { if (it.isFinite() && it >= 0.0) it else 0.0 }

    val x = (originX + t * dirX).coerceIn(insetRect.left, insetRect.right)
    val y = (originY + t * dirY).coerceIn(insetRect.top, insetRect.bottom)
    val angleRad = atan2(dirY, dirX)
    return EdgeArrow(
        x = if (x == 0.0) 0.0 else x,
        y = if (y == 0.0) 0.0 else y,
        angleRad = if (angleRad == 0.0) 0.0 else angleRad,
    )
}

/**
 * Visibility toggles for the three AR overlay layers (`01 · Visor` bottom dock).
 *
 * Defaults: sun and moon enabled, solstices/equinox disabled (persisted in `rememberSaveable`).
 */
data class VisorLayers(
    val sun: Boolean = true,
    val moon: Boolean = true,
    val solstices: Boolean = false,
) {
    companion object {
        val DEFAULT: VisorLayers = VisorLayers(sun = true, moon = true, solstices = false)
    }
}

/** Which seasonal reference arc the crosshair intersected. */
enum class SolsticeReferenceKind {
    JUNE_SOLSTICE,
    MARCH_EQUINOX,
    DECEMBER_SOLSTICE,
}

/**
 * Outcome of testing the camera crosshair against the visible AR trajectories.
 */
sealed interface VisorCrossing {
    /** Phone is pitched below [GROUND_PITCH_THRESHOLD_DEG]; prompt user to raise the phone. */
    data class PointingAtGround(val upDegrees: Int) : VisorCrossing

    /** Crosshair lies on today's sun path above the visual horizon. */
    data class Sun(val instant: Instant, val phase: SkyPhase) : VisorCrossing

    /** Crosshair lies on today's sun path below the visual horizon. */
    data class SunBelowHorizon(val instant: Instant) : VisorCrossing

    /** Crosshair lies on today's moon path above the visual horizon. */
    data class Moon(val instant: Instant, val illuminationPercent: Int) : VisorCrossing

    /** Crosshair lies on a seasonal solstice or equinox reference path above the horizon. */
    data class Solstice(val kind: SolsticeReferenceKind, val instant: Instant) : VisorCrossing

    /** Crosshair is not within tolerance of any active path. */
    data object None : VisorCrossing
}

/** Returns `true` when the camera elevation is strictly below [GROUND_PITCH_THRESHOLD_DEG]. */
internal fun isPointingAtGround(elevationDeg: Double): Boolean =
    elevationDeg.isFinite() && elevationDeg < GROUND_PITCH_THRESHOLD_DEG

/**
 * Returns `true` when the Android sensor accuracy code is `LOW` (`1`), `UNRELIABLE` (`0`), or
 * `NO_CONTACT` (`-1`).
 */
internal fun needsCompassCalibration(accuracy: Int): Boolean =
    accuracy <= ACCURACY_LOW_THRESHOLD

/**
 * Selects the highest-priority crosshair crossing label for the current camera orientation,
 * delegating spherical distance checks to [crossing].
 *
 * Priority: ground-pointing notice > sun path > moon path (above horizon) > solstice/equinox
 * reference paths (above horizon) > [VisorCrossing.None].
 */
internal fun evaluateVisorCrossing(
    rotation: DeviceRotation,
    pointingElevationDeg: Double,
    paths: ArPaths?,
    layers: VisorLayers,
    moonIlluminationPercent: Int,
    skyPhaseAt: (Instant) -> SkyPhase,
    toleranceDeg: Double = DEFAULT_CROSSING_TOLERANCE_DEG,
): VisorCrossing {
    if (isPointingAtGround(pointingElevationDeg)) {
        return VisorCrossing.PointingAtGround(upDegrees = abs(pointingElevationDeg).roundToInt().coerceAtLeast(1))
    }
    if (paths == null) return VisorCrossing.None

    if (layers.sun) {
        val sunHit = crossing(rotation, paths.sunPath, toleranceDeg)
        if (sunHit != null) {
            return if (sunHit.position.elevation < Cenit.RISE_SET_ALTITUDE_DEG) {
                VisorCrossing.SunBelowHorizon(instant = sunHit.instant)
            } else {
                VisorCrossing.Sun(
                    instant = sunHit.instant,
                    phase = skyPhaseAt(sunHit.instant),
                )
            }
        }
    }

    if (layers.moon) {
        val moonHit = crossing(rotation, paths.moonPath, toleranceDeg)
        if (moonHit != null && moonHit.position.elevation > Cenit.RISE_SET_ALTITUDE_DEG) {
            return VisorCrossing.Moon(
                instant = moonHit.instant,
                illuminationPercent = moonIlluminationPercent.coerceIn(0, 100),
            )
        }
    }

    if (layers.solstices) {
        val solTolerance = minOf(toleranceDeg, SOLSTICE_CROSSING_TOLERANCE_DEG)
        val checks = arrayOf(
            SolsticeReferenceKind.JUNE_SOLSTICE to paths.juneSolsticePath,
            SolsticeReferenceKind.MARCH_EQUINOX to paths.marchEquinoxPath,
            SolsticeReferenceKind.DECEMBER_SOLSTICE to paths.decemberSolsticePath,
        )
        for ((kind, refPath) in checks) {
            val hit = crossing(rotation, refPath, solTolerance)
            if (hit != null && hit.position.elevation > Cenit.RISE_SET_ALTITUDE_DEG) {
                return VisorCrossing.Solstice(kind = kind, instant = hit.instant)
            }
        }
    }

    return VisorCrossing.None
}

/**
 * Formats an hourly sun dot label along the AR arc (`"06h"` in 24-hour mode matching the artboard;
 * localized `"6 AM"` / `"4 PM"` via [CenitFormat.formatAxisHour] in 12-hour mode).
 */
internal fun formatArHourLabel(
    hour: Int,
    clock: ClockFormat,
    locale: Locale,
): String = when (clock) {
    ClockFormat.H24 -> "${CenitFormat.formatAxisHour(hour, ClockFormat.H24, locale)}h"
    ClockFormat.H12 -> CenitFormat.formatAxisHour(hour, ClockFormat.H12, locale)
}

/**
 * Formats the crosshair elevation angle with an explicit `+` prefix for strictly positive rounded
 * angles, typographic minus `−` for negative angles, and `0°` / `0.0°` without sign at zero.
 */
internal fun formatCrosshairElevation(
    elevationDeg: Double,
    withDecimals: Boolean,
    locale: Locale,
): String {
    val formatted = CenitFormat.formatAngle(elevationDeg, withDecimals, locale)
    val zeroFormatted = CenitFormat.formatAngle(0.0, withDecimals, locale)
    return if (elevationDeg > 0.0 && formatted != zeroFormatted) {
        "+$formatted"
    } else {
        formatted
    }
}
