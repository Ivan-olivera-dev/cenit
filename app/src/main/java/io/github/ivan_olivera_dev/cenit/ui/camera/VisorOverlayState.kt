package io.github.ivan_olivera_dev.cenit.ui.camera

import io.github.ivan_olivera_dev.cenit.core.Cenit
import io.github.ivan_olivera_dev.cenit.core.ar.ArPaths
import io.github.ivan_olivera_dev.cenit.core.ar.DEFAULT_CROSSING_TOLERANCE_DEG
import io.github.ivan_olivera_dev.cenit.core.ar.DeviceRotation
import io.github.ivan_olivera_dev.cenit.core.ar.crossing
import io.github.ivan_olivera_dev.cenit.core.format.CenitFormat
import io.github.ivan_olivera_dev.cenit.core.format.ClockFormat
import io.github.ivan_olivera_dev.cenit.core.sky.SkyPhase
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

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
