package io.github.ivan_olivera_dev.cenit.core.compass

import io.github.ivan_olivera_dev.cenit.core.sky.SkyFormatting
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Relative direction of a target azimuth with respect to the current compass heading.
 */
enum class RelativeDirection {
    /** Within [CompassMath.AHEAD_THRESHOLD_DEG] of the current heading. */
    AHEAD,

    /** Counter-clockwise (to the left) of the current heading. */
    LEFT,

    /** Clockwise (to the right) of the current heading (including exact 180° opposite). */
    RIGHT,
}

/**
 * Signed and classified bearing from a current heading to a target azimuth.
 *
 * @property signedDegrees Shortest signed angular offset in `(-180, 180]` degrees.
 * @property absDegrees Magnitude of [signedDegrees] in `[0, 180]` degrees.
 * @property direction Coarse directional cue ([RelativeDirection.AHEAD], [RelativeDirection.LEFT],
 *   or [RelativeDirection.RIGHT]).
 */
data class RelativeBearing(
    val signedDegrees: Double,
    val absDegrees: Double,
    val direction: RelativeDirection,
)

/**
 * Pure compass mathematics shared by the Brújula screen and its tests:
 * angle normalisation, shortest-arc smoothing across 359°→0°, magnetic→true north conversion,
 * 16-point wind indexing, and relative target bearings.
 */
object CompassMath {
    /** Angles within `[-1°, +1°)` of the heading are reported as directly ahead. */
    const val AHEAD_THRESHOLD_DEG: Double = 1.0

    /**
     * Normalises any finite angle in degrees into the half-open interval `[0, 360)`.
     */
    fun normalizeDegrees(degrees: Double): Double {
        require(degrees.isFinite()) { "degrees must be finite, was $degrees" }
        val mod = ((degrees % 360.0) + 360.0) % 360.0
        return if (mod >= 360.0 || mod == 0.0) 0.0 else mod
    }

    /**
     * Signed shortest angular offset from [fromDeg] to [toDeg] in `(-180, 180]` degrees.
     * Positive values rotate clockwise (right); negative values rotate counter-clockwise (left).
     */
    fun shortestArcDelta(fromDeg: Double, toDeg: Double): Double {
        val from = normalizeDegrees(fromDeg)
        val to = normalizeDegrees(toDeg)
        val raw = normalizeDegrees(to - from)
        val signed = if (raw > 180.0) raw - 360.0 else raw
        return if (signed == 0.0) 0.0 else signed
    }

    /**
     * Applies one step of angle-aware exponential low-pass filtering from [previousDeg] toward
     * [targetDeg] along the shortest arc across the `359° → 0°` wrap.
     *
     * @param alpha Smoothing factor in `[0, 1]`, where `0` keeps [previousDeg] and `1` jumps to
     *   [targetDeg].
     */
    fun smoothHeading(
        previousDeg: Double,
        targetDeg: Double,
        alpha: Double,
    ): Double {
        require(alpha.isFinite() && alpha in 0.0..1.0) {
            "alpha must be finite and in [0, 1], was $alpha"
        }
        val prev = normalizeDegrees(previousDeg)
        val delta = shortestArcDelta(prev, targetDeg)
        return normalizeDegrees(prev + alpha * delta)
    }

    /**
     * Converts a magnetic compass heading to true north by adding [declinationDeg] (east positive,
     * west negative — matching `android.hardware.GeomagneticField.getDeclination()`).
     */
    fun magneticToTrueNorth(
        magneticHeadingDeg: Double,
        declinationDeg: Double,
    ): Double {
        require(declinationDeg.isFinite()) { "declinationDeg must be finite, was $declinationDeg" }
        return normalizeDegrees(normalizeDegrees(magneticHeadingDeg) + declinationDeg)
    }

    /**
     * Rounds any finite angle in degrees to the nearest whole degree in `0..359`,
     * folding `359.5°..359.999°` to `0` so displays never show `360°`.
     */
    fun roundToWholeDegree(degrees: Double): Int =
        normalizeDegrees(degrees).roundToInt() % 360

    /**
     * Maps any finite azimuth in degrees to a 16-point compass wind index in `0..15`
     * (`0 = N`, `4 = E`, `8 = S`, `12 = W`), reusing [SkyFormatting.compassIndex].
     */
    fun windIndex(azimuthDeg: Double): Int =
        SkyFormatting.compassIndex(normalizeDegrees(azimuthDeg))

    /**
     * Computes the signed relative bearing from [headingDeg] to [targetAzimuthDeg].
     */
    fun relativeBearing(
        headingDeg: Double,
        targetAzimuthDeg: Double,
    ): RelativeBearing {
        val signed = shortestArcDelta(fromDeg = headingDeg, toDeg = targetAzimuthDeg)
        val magnitude = abs(signed)
        val direction = when {
            magnitude < AHEAD_THRESHOLD_DEG -> RelativeDirection.AHEAD
            signed < 0.0 -> RelativeDirection.LEFT
            else -> RelativeDirection.RIGHT
        }
        return RelativeBearing(
            signedDegrees = signed,
            absDegrees = magnitude,
            direction = direction,
        )
    }
}

/**
 * Stateful exponential smoother for live compass heading updates.
 *
 * Snaps immediately to the first sample so opening the screen never drifts from `0°`, then
 * filters subsequent updates along the shortest arc across `359° → 0°`.
 */
class HeadingSmoother(
    val alpha: Double = DEFAULT_ALPHA,
) {
    init {
        require(alpha.isFinite() && alpha in 0.0..1.0) {
            "alpha must be finite and in [0, 1], was $alpha"
        }
    }

    var currentHeadingDeg: Double? = null
        private set

    fun update(sampleDeg: Double): Double {
        val normalized = CompassMath.normalizeDegrees(sampleDeg)
        val next = currentHeadingDeg?.let { prev ->
            CompassMath.smoothHeading(prev, normalized, alpha)
        } ?: normalized
        currentHeadingDeg = next
        return next
    }

    fun reset() {
        currentHeadingDeg = null
    }

    companion object {
        /** Responsive yet steady low-pass gain for `SENSOR_DELAY_UI` compass updates. */
        const val DEFAULT_ALPHA: Double = 0.18
    }
}
