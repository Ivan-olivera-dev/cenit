package io.github.ivan_olivera_dev.cenit.core.sky

import io.github.ivan_olivera_dev.cenit.core.Ephemeris
import io.github.ivan_olivera_dev.cenit.core.Place
import java.time.Instant

/**
 * Sky light phases used for the day ribbon and current-moment label.
 *
 * Boundaries follow design/system.md and are defined on the geometric elevation of the Sun's
 * centre (without atmospheric refraction):
 * - [DAY]: `> +6°`
 * - [GOLDEN]: `−4° … +6°` (inclusive)
 * - [BLUE]: `−6° … −4°` (lower civil twilight / blue hour)
 * - [NAUTICAL]: `−12° … −6°`
 * - [ASTRONOMICAL]: `−18° … −12°`
 * - [NIGHT]: `< −18°`
 */
enum class SkyPhase {
    NIGHT,
    ASTRONOMICAL,
    NAUTICAL,
    BLUE,
    GOLDEN,
    DAY;

    companion object {
        const val DAY_THRESHOLD_DEG: Double = 6.0
        const val GOLDEN_LOWER_DEG: Double = -4.0
        const val BLUE_LOWER_DEG: Double = -6.0
        const val NAUTICAL_LOWER_DEG: Double = -12.0
        const val ASTRONOMICAL_LOWER_DEG: Double = -18.0

        /**
         * Classifies a geometric solar elevation (degrees, unrefracted Sun centre) into a [SkyPhase].
         */
        fun fromElevation(geometricElevationDeg: Double): SkyPhase = when {
            geometricElevationDeg > DAY_THRESHOLD_DEG -> DAY
            geometricElevationDeg >= GOLDEN_LOWER_DEG -> GOLDEN
            geometricElevationDeg >= BLUE_LOWER_DEG -> BLUE
            geometricElevationDeg >= NAUTICAL_LOWER_DEG -> NAUTICAL
            geometricElevationDeg >= ASTRONOMICAL_LOWER_DEG -> ASTRONOMICAL
            else -> NIGHT
        }

        /**
         * Determines the [SkyPhase] at [place] and [instant] using the Sun's geometric elevation.
         */
        fun at(place: Place, instant: Instant): SkyPhase {
            val geometricElevation = Ephemeris.sunPosition(place, instant, apparent = false).elevation
            return fromElevation(geometricElevation)
        }
    }
}
