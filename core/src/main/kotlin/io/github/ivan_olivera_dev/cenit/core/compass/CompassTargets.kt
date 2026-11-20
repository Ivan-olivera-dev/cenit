package io.github.ivan_olivera_dev.cenit.core.compass

import io.github.ivan_olivera_dev.cenit.core.Cenit
import io.github.ivan_olivera_dev.cenit.core.Ephemeris
import io.github.ivan_olivera_dev.cenit.core.MoonDay
import io.github.ivan_olivera_dev.cenit.core.Place
import io.github.ivan_olivera_dev.cenit.core.SkyPosition
import io.github.ivan_olivera_dev.cenit.core.SunDay
import java.time.Instant

/**
 * Celestial body represented by a compass target or ray.
 */
enum class CompassBody {
    SUN,
    MOON,
}

/**
 * Kind of directional target shown in the Brújula target list.
 */
enum class CompassTargetKind {
    SUNSET,
    SUN_NOW,
    SUNRISE,
    MOONRISE,
    MOON_NOW,
    MOONSET,
}

/**
 * A directional azimuth target (rise/set event or live sun/moon position) for the Brújula screen.
 */
data class CompassTarget(
    val kind: CompassTargetKind,
    val body: CompassBody,
    val azimuthDeg: Double,
    val instant: Instant? = null,
)

/**
 * Builds the prioritised list of sun and moon compass targets for a given moment and day,
 * mirroring Artboard 02 (`targets()` in `tools/design-kit/gen.js`) while ensuring polar days
 * and nights still surface the active celestial directions.
 */
object CompassTargets {
    fun forMoment(
        place: Place,
        sunDay: SunDay,
        moonDay: MoonDay,
        sunPosition: SkyPosition,
        moonPosition: SkyPosition,
        maxTargets: Int = 5,
    ): List<CompassTarget> {
        require(maxTargets >= 1) { "maxTargets must be at least 1, was $maxTargets" }

        val candidates = buildList {
            sunDay.sunset?.let { instant ->
                add(
                    CompassTarget(
                        kind = CompassTargetKind.SUNSET,
                        body = CompassBody.SUN,
                        azimuthDeg = CompassMath.normalizeDegrees(Ephemeris.sunPosition(place, instant).azimuth),
                        instant = instant,
                    ),
                )
            }
            if (sunPosition.elevation > Cenit.RISE_SET_ALTITUDE_DEG) {
                add(
                    CompassTarget(
                        kind = CompassTargetKind.SUN_NOW,
                        body = CompassBody.SUN,
                        azimuthDeg = CompassMath.normalizeDegrees(sunPosition.azimuth),
                    ),
                )
            }
            sunDay.sunrise?.let { instant ->
                add(
                    CompassTarget(
                        kind = CompassTargetKind.SUNRISE,
                        body = CompassBody.SUN,
                        azimuthDeg = CompassMath.normalizeDegrees(Ephemeris.sunPosition(place, instant).azimuth),
                        instant = instant,
                    ),
                )
            }
            moonDay.moonrise?.let { instant ->
                add(
                    CompassTarget(
                        kind = CompassTargetKind.MOONRISE,
                        body = CompassBody.MOON,
                        azimuthDeg = CompassMath.normalizeDegrees(Ephemeris.moonPosition(place, instant).azimuth),
                        instant = instant,
                    ),
                )
            }
            if (moonPosition.elevation > Cenit.RISE_SET_ALTITUDE_DEG) {
                add(
                    CompassTarget(
                        kind = CompassTargetKind.MOON_NOW,
                        body = CompassBody.MOON,
                        azimuthDeg = CompassMath.normalizeDegrees(moonPosition.azimuth),
                    ),
                )
            }
            moonDay.moonset?.let { instant ->
                add(
                    CompassTarget(
                        kind = CompassTargetKind.MOONSET,
                        body = CompassBody.MOON,
                        azimuthDeg = CompassMath.normalizeDegrees(Ephemeris.moonPosition(place, instant).azimuth),
                        instant = instant,
                    ),
                )
            }
        }

        if (candidates.isNotEmpty()) {
            return candidates.take(maxTargets)
        }

        // Polar night with neither rise/set nor moon above horizon: still expose current sun bearing.
        return listOf(
            CompassTarget(
                kind = CompassTargetKind.SUN_NOW,
                body = CompassBody.SUN,
                azimuthDeg = CompassMath.normalizeDegrees(sunPosition.azimuth),
            ),
        )
    }
}
