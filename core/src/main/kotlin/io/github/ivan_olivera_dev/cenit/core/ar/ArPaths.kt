package io.github.ivan_olivera_dev.cenit.core.ar

import io.github.ivan_olivera_dev.cenit.core.Ephemeris
import io.github.ivan_olivera_dev.cenit.core.PathPoint
import io.github.ivan_olivera_dev.cenit.core.Place
import io.github.ivan_olivera_dev.cenit.core.SkyPosition
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Default angular tolerance (in degrees on the celestial sphere) for [crossing].
 */
const val DEFAULT_CROSSING_TOLERANCE_DEG: Double = 4.0

/**
 * Whole local-hour sun position along the day's AR trajectory.
 *
 * @property instant Exact UTC instant of the whole local hour.
 * @property localTime Local wall-clock time in the requested zone (always on the hour, `mm:ss = 00:00`).
 * @property position Apparent horizontal position of the Sun at [instant] (includes normal
 *   atmospheric refraction so the marker matches where the eye sees the Sun).
 */
data class HourMarker(
    val instant: Instant,
    val localTime: LocalTime,
    val position: SkyPosition,
)

/**
 * Sunrise or sunset landmark placed on the visual horizon (`elevationDeg == 0.0`).
 *
 * @property instant Exact UTC instant of sunrise or sunset.
 * @property localTime Local wall-clock time of the event in the requested zone.
 * @property azimuthDeg True-north azimuth in `[0, 360)` degrees at [instant].
 * @property elevationDeg Visual horizon elevation in degrees (`0.0`).
 */
data class HorizonPoint(
    val instant: Instant,
    val localTime: LocalTime,
    val azimuthDeg: Double,
    val elevationDeg: Double = 0.0,
)

/**
 * Pre-sampled celestial trajectories, hourly markers, and horizon rise/set points for one local day
 * in the AR camera view.
 *
 * All sampled elevations are **apparent** (including normal atmospheric refraction, reusing
 * [Ephemeris.sunPath], [Ephemeris.moonPath], and [Ephemeris.sunPosition]) because the AR overlay
 * must align with where the human eye and camera lens actually see the Sun and Moon.
 *
 * @property sunPath Apparent sun trajectory for the requested local day (midnight to next midnight inclusive).
 * @property moonPath Apparent moon trajectory for the requested local day (midnight to next midnight inclusive).
 * @property juneSolsticePath Apparent sun trajectory on the June solstice of the requested date's year.
 * @property decemberSolsticePath Apparent sun trajectory on the December solstice of the requested date's year.
 * @property marchEquinoxPath Apparent sun trajectory on the March equinox of the requested date's year.
 * @property hourMarkers Sun positions at every whole local hour within `[local midnight, next local midnight)`,
 *   naturally reflecting 23-hour spring-forward and 25-hour fall-back DST days.
 * @property sunrise Sunrise point on the horizon, or `null` during polar day or polar night.
 * @property sunset Sunset point on the horizon, or `null` during polar day or polar night.
 */
data class ArPaths(
    val sunPath: List<PathPoint>,
    val moonPath: List<PathPoint>,
    val juneSolsticePath: List<PathPoint>,
    val decemberSolsticePath: List<PathPoint>,
    val marchEquinoxPath: List<PathPoint>,
    val hourMarkers: List<HourMarker>,
    val sunrise: HorizonPoint?,
    val sunset: HorizonPoint?,
) {
    companion object {
        /** Default path sampling interval in minutes. */
        const val DEFAULT_STEP_MINUTES: Int = 10

        /**
         * Samples all AR trajectories, whole local-hour markers, and horizon sunrise/sunset points
         * for [place] on [date] in [zone], advancing by [stepMinutes] (default [DEFAULT_STEP_MINUTES]).
         *
         * Reference solstice and equinox paths use the exact astronomical season dates of
         * `date.year` from [Ephemeris.seasons]. All elevations are **apparent** (refraction applied).
         */
        fun compute(
            place: Place,
            date: LocalDate,
            zone: ZoneId,
            stepMinutes: Int = DEFAULT_STEP_MINUTES,
        ): ArPaths {
            require(stepMinutes > 0) { "stepMinutes must be positive, was $stepMinutes" }
            val step = Duration.ofMinutes(stepMinutes.toLong())

            val sunPath = Ephemeris.sunPath(place, date, zone, step)
            val moonPath = Ephemeris.moonPath(place, date, zone, step)

            val seasons = Ephemeris.seasons(date.year)
            val juneDate = seasons.juneSolstice.atZone(zone).toLocalDate()
            val decemberDate = seasons.decemberSolstice.atZone(zone).toLocalDate()
            val marchDate = seasons.marchEquinox.atZone(zone).toLocalDate()

            val juneSolsticePath = if (juneDate == date) sunPath else Ephemeris.sunPath(place, juneDate, zone, step)
            val decemberSolsticePath = if (decemberDate == date) sunPath else Ephemeris.sunPath(place, decemberDate, zone, step)
            val marchEquinoxPath = if (marchDate == date) sunPath else Ephemeris.sunPath(place, marchDate, zone, step)

            val hourMarkers = buildHourMarkers(place, date, zone, sunPath)

            val sunDay = Ephemeris.sunDay(place, date, zone)
            val sunrise = sunDay.sunrise?.let { instant ->
                HorizonPoint(
                    instant = instant,
                    localTime = instant.atZone(zone).toLocalTime(),
                    azimuthDeg = Ephemeris.sunPosition(place, instant, apparent = true).azimuth,
                )
            }
            val sunset = sunDay.sunset?.let { instant ->
                HorizonPoint(
                    instant = instant,
                    localTime = instant.atZone(zone).toLocalTime(),
                    azimuthDeg = Ephemeris.sunPosition(place, instant, apparent = true).azimuth,
                )
            }

            return ArPaths(
                sunPath = sunPath,
                moonPath = moonPath,
                juneSolsticePath = juneSolsticePath,
                decemberSolsticePath = decemberSolsticePath,
                marchEquinoxPath = marchEquinoxPath,
                hourMarkers = hourMarkers,
                sunrise = sunrise,
                sunset = sunset,
            )
        }

        private fun buildHourMarkers(
            place: Place,
            date: LocalDate,
            zone: ZoneId,
            sunPath: List<PathPoint>,
        ): List<HourMarker> {
            val end = date.plusDays(1).atStartOfDay(zone).toInstant()
            val cachedByInstant = sunPath.associateBy({ it.instant }, { it.position })
            val markers = mutableListOf<HourMarker>()

            var zdt = date.atStartOfDay(zone)
            while (zdt.toInstant().isBefore(end)) {
                val instant = zdt.toInstant()
                val position = cachedByInstant[instant]
                    ?: Ephemeris.sunPosition(place, instant, apparent = true)
                markers.add(
                    HourMarker(
                        instant = instant,
                        localTime = zdt.toLocalTime(),
                        position = position,
                    ),
                )
                val next = instant.plus(Duration.ofHours(1)).atZone(zone)
                zdt = if (next.minute == 0 && next.second == 0) {
                    next
                } else {
                    // Handles unusual non-hour DST transitions (e.g. 30-minute offsets)
                    next.truncatedTo(ChronoUnit.HOURS).plusHours(1)
                }
            }
            return markers
        }
    }
}

/**
 * Finds the sample in [path] closest to the camera crosshair direction defined by [rotation] on the
 * celestial sphere, returning `null` if [path] is empty or no sample lies within [toleranceDeg].
 *
 * Uses exact great-circle spherical distance between the camera optical axis (`-Z_d` in world ENU)
 * and each sample's `(azimuth, elevation)`, handling azimuth wrap across `359°/1°` and poles
 * without distortion.
 *
 * @param rotation Current device-to-world rotation.
 * @param path Sampled trajectory points to test.
 * @param toleranceDeg Maximum spherical angular distance in degrees (inclusive, must be `>= 0`).
 */
fun crossing(
    rotation: DeviceRotation,
    path: List<PathPoint>,
    toleranceDeg: Double = DEFAULT_CROSSING_TOLERANCE_DEG,
): PathPoint? {
    require(toleranceDeg.isFinite() && toleranceDeg >= 0.0) {
        "toleranceDeg must be finite and non-negative, was $toleranceDeg"
    }
    if (path.isEmpty()) return null

    // Camera optical axis (-Z_d) in world ENU
    val fx = -rotation[0, 2]
    val fy = -rotation[1, 2]
    val fz = -rotation[2, 2]

    var bestPoint: PathPoint? = null
    var bestDistDeg = Double.POSITIVE_INFINITY

    for (sample in path) {
        val az = Math.toRadians(sample.position.azimuth)
        val el = Math.toRadians(sample.position.elevation)
        val cosEl = cos(el)
        val px = cosEl * sin(az)
        val py = cosEl * cos(az)
        val pz = sin(el)

        val distDeg = unitVectorAngleDeg(fx, fy, fz, px, py, pz)
        if (distDeg <= toleranceDeg && distDeg < bestDistDeg) {
            bestDistDeg = distDeg
            bestPoint = sample
        }
    }

    return bestPoint
}

/**
 * Great-circle angular distance in `[0, 180]` degrees between two sky directions on the unit sphere.
 */
fun angularDistanceDeg(
    azimuth1Deg: Double,
    elevation1Deg: Double,
    azimuth2Deg: Double,
    elevation2Deg: Double,
): Double {
    require(azimuth1Deg.isFinite() && elevation1Deg.isFinite() && elevation1Deg in -90.0..90.0) {
        "First direction must be finite with elevation in [-90, 90], was ($azimuth1Deg, $elevation1Deg)"
    }
    require(azimuth2Deg.isFinite() && elevation2Deg.isFinite() && elevation2Deg in -90.0..90.0) {
        "Second direction must be finite with elevation in [-90, 90], was ($azimuth2Deg, $elevation2Deg)"
    }
    val az1 = Math.toRadians(azimuth1Deg)
    val el1 = Math.toRadians(elevation1Deg)
    val az2 = Math.toRadians(azimuth2Deg)
    val el2 = Math.toRadians(elevation2Deg)
    return unitVectorAngleDeg(
        cos(el1) * sin(az1), cos(el1) * cos(az1), sin(el1),
        cos(el2) * sin(az2), cos(el2) * cos(az2), sin(el2),
    )
}

private fun unitVectorAngleDeg(
    ax: Double,
    ay: Double,
    az: Double,
    bx: Double,
    by: Double,
    bz: Double,
): Double {
    val cx = ay * bz - az * by
    val cy = az * bx - ax * bz
    val cz = ax * by - ay * bx
    val crossNorm = sqrt(cx * cx + cy * cy + cz * cz)
    val dot = ax * bx + ay * by + az * bz
    return Math.toDegrees(atan2(crossNorm, dot))
}
