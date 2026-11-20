package io.github.ivan_olivera_dev.cenit.core.sky.shadow

import io.github.ivan_olivera_dev.cenit.core.SkyPosition
import io.github.ivan_olivera_dev.cenit.core.sky.SkyFormatting

/**
 * Shadow cast on level ground by a vertical object from the Sun's apparent position.
 *
 * @property ratio Shadow length divided by the object's height (`1 / tan(elevation)`).
 * @property azimuthDeg Compass bearing towards which the shadow points, normalised to `[0, 360)`.
 * @property compassIndex 16-point wind index (`0 = N .. 15 = NNW`) via [SkyFormatting.compassIndex].
 */
data class ShadowInfo(
    val ratio: Double,
    val azimuthDeg: Double,
    val compassIndex: Int,
)

/**
 * Pure calculations for shadow direction and length from solar horizontal coordinates.
 */
object ShadowDirection {
    /**
     * Returns the azimuth in `[0, 360)` towards which a vertical object's shadow falls
     * (`(sunPosition.azimuth + 180) mod 360`), or `null` when the Sun is at or below the horizon
     * (`elevation <= 0`, matching [SkyPosition.shadowRatio]) or has non-finite coordinates.
     */
    fun shadowAzimuth(sunPosition: SkyPosition): Double? =
        shadowAzimuth(sunPosition.azimuth, sunPosition.elevation)

    /**
     * Returns the shadow azimuth in `[0, 360)` for a Sun at ([sunAzimuthDeg], [sunElevationDeg]),
     * or `null` when [sunElevationDeg] is at or below `0°` or either coordinate is non-finite.
     */
    fun shadowAzimuth(sunAzimuthDeg: Double, sunElevationDeg: Double): Double? {
        if (!sunAzimuthDeg.isFinite() || !sunElevationDeg.isFinite() || sunElevationDeg <= 0.0) {
            return null
        }
        val raw = ((sunAzimuthDeg + 180.0) % 360.0 + 360.0) % 360.0
        return if (raw >= 360.0 || raw == 0.0) 0.0 else raw
    }

    /**
     * Returns the 16-point compass wind index (`0..15`) towards which the shadow points,
     * or `null` when the Sun is at or below the horizon.
     */
    fun shadowCompassIndex(sunPosition: SkyPosition): Int? =
        shadowAzimuth(sunPosition)?.let { SkyFormatting.compassIndex(it) }

    /**
     * Computes [ShadowInfo] for [sunPosition], or `null` when no shadow is cast
     * (`elevation <= 0` or non-finite coordinates).
     */
    fun fromSunPosition(sunPosition: SkyPosition): ShadowInfo? {
        val ratio = sunPosition.shadowRatio ?: return null
        val azimuth = shadowAzimuth(sunPosition) ?: return null
        return ShadowInfo(
            ratio = ratio,
            azimuthDeg = azimuth,
            compassIndex = SkyFormatting.compassIndex(azimuth),
        )
    }
}
