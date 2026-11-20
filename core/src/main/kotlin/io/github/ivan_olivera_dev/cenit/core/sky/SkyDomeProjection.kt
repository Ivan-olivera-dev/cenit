package io.github.ivan_olivera_dev.cenit.core.sky

import kotlin.math.cos
import kotlin.math.sin

/**
 * Point in the sky dome's unit disc (`x² + y² <= 1`), where `(0, 0)` is the zenith,
 * `-y` points to the top of the disc (North when `headingDeg = 0`), and `+x` points right (East).
 */
data class DomePoint(
    val x: Double,
    val y: Double,
)

/**
 * Equidistant azimuthal projection for the flat-compass sky dome (design/system.md, `sky-dome`).
 *
 * The zenith (`+90°`) lies at radius `0`, the horizon (`0°`) at [HORIZON_RADIUS] (`90/102`),
 * civil twilight (`−6°`) at [CIVIL_TWILIGHT_RADIUS] (`96/102`), and nautical twilight (`−12°`)
 * at the unit disc edge [NAUTICAL_TWILIGHT_RADIUS] (`1.0`). Elevations below `−12°` clamp to
 * `1.0` so the night path stays inside the outer twilight ring.
 */
object SkyDomeProjection {
    const val MIN_ELEVATION_DEG: Double = -12.0
    const val MAX_ELEVATION_DEG: Double = 90.0
    private const val ZENITH_TO_OUTER_DEG: Double = MAX_ELEVATION_DEG - MIN_ELEVATION_DEG

    const val HORIZON_RADIUS: Double = 90.0 / ZENITH_TO_OUTER_DEG
    const val CIVIL_TWILIGHT_RADIUS: Double = 96.0 / ZENITH_TO_OUTER_DEG
    const val NAUTICAL_TWILIGHT_RADIUS: Double = 1.0

    /**
     * Returns the normalised radius in `[0, 1]` corresponding to [elevationDeg].
     */
    fun radiusForElevation(elevationDeg: Double): Double {
        val clamped = elevationDeg.coerceIn(MIN_ELEVATION_DEG, MAX_ELEVATION_DEG)
        return (MAX_ELEVATION_DEG - clamped) / ZENITH_TO_OUTER_DEG
    }

    /**
     * Projects a sky coordinate ([elevationDeg], [azimuthDeg]) into the unit disc,
     * rotated by [headingDeg] so that `azimuthDeg == headingDeg` lies at the top (`-y`).
     */
    fun project(
        elevationDeg: Double,
        azimuthDeg: Double,
        headingDeg: Double = 0.0,
    ): DomePoint = pointOnRing(
        radius = radiusForElevation(elevationDeg),
        azimuthDeg = azimuthDeg,
        headingDeg = headingDeg,
    )

    /**
     * Returns a point at normalised [radius] and [azimuthDeg] rotated by [headingDeg].
     */
    fun pointOnRing(
        radius: Double,
        azimuthDeg: Double,
        headingDeg: Double = 0.0,
    ): DomePoint {
        val angleRad = Math.toRadians(azimuthDeg - headingDeg)
        return DomePoint(
            x = radius * sin(angleRad),
            y = -radius * cos(angleRad),
        )
    }
}
