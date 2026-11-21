package io.github.ivan_olivera_dev.cenit.core.ar

import io.github.ivan_olivera_dev.cenit.core.compass.CompassMath
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Single tick along the AR heading tape.
 *
 * @property azimuthDeg Compass azimuth in `0..359` (multiples of `5°`).
 * @property normalizedPosition Horizontal position along the tape in `[0, 1]`, where `0.5` is the
 *   center pointing azimuth.
 * @property isMajor `true` every `15°` (longer tick with a label).
 * @property isCardinal `true` every `45°` (uses a localized compass wind name instead of degrees).
 * @property isQuadrant `true` every `90°` (`N`, `E`, `S`, `W` — highlighted weight & colour).
 * @property windIndex 16-point wind index (`0..15`) when [isCardinal] is `true`, otherwise `null`.
 */
data class HeadingTapeTick(
    val azimuthDeg: Int,
    val normalizedPosition: Double,
    val isMajor: Boolean,
    val isCardinal: Boolean,
    val isQuadrant: Boolean,
    val windIndex: Int?,
)

/**
 * Pure tick and marker layout math for the top AR heading tape (matching `01 · Visor` artboard).
 */
object HeadingTapeModel {
    const val DEFAULT_SPAN_DEG: Double = 96.0
    const val TICK_STEP_DEG: Int = 5
    const val MAJOR_STEP_DEG: Int = 15
    const val CARDINAL_STEP_DEG: Int = 45
    const val QUADRANT_STEP_DEG: Int = 90

    /**
     * Generates all `5°` ticks visible inside `[headingDeg - spanDeg/2, headingDeg + spanDeg/2]`
     * in strictly ascending left-to-right [HeadingTapeTick.normalizedPosition] order, wrapping
     * cleanly across `359° → 0°`.
     */
    fun ticksForHeading(
        headingDeg: Double,
        spanDeg: Double = DEFAULT_SPAN_DEG,
    ): List<HeadingTapeTick> {
        require(headingDeg.isFinite()) { "headingDeg must be finite, was $headingDeg" }
        require(spanDeg.isFinite() && spanDeg > 0.0 && spanDeg <= 360.0) {
            "spanDeg must be finite and in (0, 360], was $spanDeg"
        }
        val center = CompassMath.normalizeDegrees(headingDeg)
        val halfSpan = spanDeg / 2.0
        val startMultiple = ceil((center - halfSpan) / TICK_STEP_DEG).toInt() * TICK_STEP_DEG
        val endMultiple = floor((center + halfSpan) / TICK_STEP_DEG).toInt() * TICK_STEP_DEG

        val result = ArrayList<HeadingTapeTick>((spanDeg / TICK_STEP_DEG).toInt() + 2)
        var rawAz = startMultiple
        while (rawAz <= endMultiple) {
            val offsetDeg = rawAz.toDouble() - center
            val normalizedPos = 0.5 + offsetDeg / spanDeg
            if (normalizedPos in 0.0..1.0) {
                val az = ((rawAz % 360) + 360) % 360
                val isMajor = az % MAJOR_STEP_DEG == 0
                val isCardinal = az % CARDINAL_STEP_DEG == 0
                val isQuadrant = az % QUADRANT_STEP_DEG == 0
                val windIndex = if (isCardinal) (az / 45) * 2 else null
                result.add(
                    HeadingTapeTick(
                        azimuthDeg = az,
                        normalizedPosition = normalizedPos,
                        isMajor = isMajor,
                        isCardinal = isCardinal,
                        isQuadrant = isQuadrant,
                        windIndex = windIndex,
                    ),
                )
            }
            rawAz += TICK_STEP_DEG
        }
        return result
    }

    /**
     * Maps [targetAzimuthDeg] onto the heading tape's `[0, 1]` horizontal span around [headingDeg],
     * returning `null` when the target lies outside the visible tape span.
     */
    fun markerPosition(
        headingDeg: Double,
        targetAzimuthDeg: Double,
        spanDeg: Double = DEFAULT_SPAN_DEG,
    ): Double? {
        require(headingDeg.isFinite()) { "headingDeg must be finite, was $headingDeg" }
        require(targetAzimuthDeg.isFinite()) { "targetAzimuthDeg must be finite, was $targetAzimuthDeg" }
        require(spanDeg.isFinite() && spanDeg > 0.0 && spanDeg <= 360.0) {
            "spanDeg must be finite and in (0, 360], was $spanDeg"
        }
        val delta = CompassMath.shortestArcDelta(fromDeg = headingDeg, toDeg = targetAzimuthDeg)
        val pos = 0.5 + delta / spanDeg
        return if (pos in 0.0..1.0) pos else null
    }
}
