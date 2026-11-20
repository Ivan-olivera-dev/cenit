package io.github.ivan_olivera_dev.cenit.core

import java.time.Instant
import kotlin.math.tan

/**
 * Geographic location of an observer on Earth.
 *
 * @property latitude Geographic latitude in degrees north (`+`) or south (`-`), in `[-90, 90]`.
 * @property longitude Geographic longitude in degrees east (`+`) or west (`-`), in `[-180, 180]`.
 * @property elevationMeters Elevation above sea level in meters, in `[-500, 100000]`.
 */
data class Place(
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double = 0.0,
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0) {
            "latitude must be finite and in [-90, 90], was $latitude"
        }
        require(longitude.isFinite() && longitude in -180.0..180.0) {
            "longitude must be finite and in [-180, 180], was $longitude"
        }
        require(elevationMeters.isFinite() && elevationMeters in MIN_ELEVATION_METERS..MAX_ELEVATION_METERS) {
            "elevationMeters must be finite and in [$MIN_ELEVATION_METERS, $MAX_ELEVATION_METERS], was $elevationMeters"
        }
    }

    private companion object {
        const val MIN_ELEVATION_METERS = -500.0
        const val MAX_ELEVATION_METERS = 100_000.0
    }
}

/**
 * Apparent horizontal position of a celestial body relative to an observer's local horizon.
 *
 * @property azimuth Compass bearing in degrees clockwise from true north, normalised to `[0, 360)`.
 * @property elevation Apparent altitude in degrees above (`+`) or below (`-`) the horizon.
 */
data class SkyPosition(
    val azimuth: Double,
    val elevation: Double,
) {
    /**
     * Length of a vertical object's shadow divided by the object's height (`1 / tan(elevation)`),
     * or `null` when the body is at or below the horizon (`elevation <= 0`).
     */
    val shadowRatio: Double?
        get() = if (elevation.isFinite() && elevation > 0.0) {
            1.0 / tan(Math.toRadians(elevation))
        } else {
            null
        }
}

/**
 * Closed-open time span `[start, end)` for a sky-light phase such as golden or blue hour.
 *
 * @property start Inclusive start instant of the range.
 * @property end End instant of the range, guaranteed not before [start].
 */
data class TimeRange(
    val start: Instant,
    val end: Instant,
) {
    init {
        require(!end.isBefore(start)) {
            "TimeRange end ($end) must not be before start ($start)"
        }
    }
}

/**
 * Polar daylight regime for a local calendar day when the Sun neither rises nor sets.
 */
enum class PolarCondition {
    /** The Sun crosses the horizon at least once during the local day. */
    NONE,

    /** The Sun remains above the horizon for the entire local day. */
    MIDNIGHT_SUN,

    /** The Sun remains below the horizon for the entire local day. */
    POLAR_NIGHT,
}

/**
 * Solar events, twilight boundaries, and photographic light windows for one local calendar day.
 *
 * Every event instant falls within `[local midnight, next local midnight)` in the requested zone;
 * events that do not occur within that window are `null`. Golden and blue hour ranges are `null`
 * unless both bounds fall in the window.
 *
 * @property sunrise Apparent sunrise (upper limb with standard refraction), or `null`.
 * @property sunset Apparent sunset (upper limb with standard refraction), or `null`.
 * @property solarNoon Instant of upper culmination (hour angle 0).
 * @property noonElevation Apparent elevation of the Sun at [solarNoon], in degrees.
 * @property civilDawn Sun centre ascending through −6°, or `null`.
 * @property civilDusk Sun centre descending through −6°, or `null`.
 * @property nauticalDawn Sun centre ascending through −12°, or `null`.
 * @property nauticalDusk Sun centre descending through −12°, or `null`.
 * @property astronomicalDawn Sun centre ascending through −18°, or `null`.
 * @property astronomicalDusk Sun centre descending through −18°, or `null`.
 * @property blueMorning Morning blue hour (Sun centre −6° to −4°), or `null`.
 * @property goldenMorning Morning golden hour (Sun centre −4° to +6°), or `null`.
 * @property goldenEvening Evening golden hour (Sun centre +6° to −4°), or `null`.
 * @property blueEvening Evening blue hour (Sun centre −4° to −6°), or `null`.
 * @property polar Polar condition when neither sunrise nor sunset occurs.
 */
data class SunDay(
    val sunrise: Instant?,
    val sunset: Instant?,
    val solarNoon: Instant,
    val noonElevation: Double,
    val civilDawn: Instant?,
    val civilDusk: Instant?,
    val nauticalDawn: Instant?,
    val nauticalDusk: Instant?,
    val astronomicalDawn: Instant?,
    val astronomicalDusk: Instant?,
    val blueMorning: TimeRange?,
    val goldenMorning: TimeRange?,
    val goldenEvening: TimeRange?,
    val blueEvening: TimeRange?,
    val polar: PolarCondition,
)

/**
 * Lunar phase angle and illuminated fraction at a point in time.
 *
 * @property angle Moon-Sun ecliptic longitude difference in `[0, 360)` degrees
 *   (`0` = new, `90` = first quarter, `180` = full, `270` = last quarter).
 * @property illumination Fraction of the lunar disc illuminated as seen from Earth, in `[0, 1]`.
 */
data class MoonPhase(
    val angle: Double,
    val illumination: Double,
)

/**
 * Eight-part lunar phase classification.
 *
 * Principal phases ([NEW], [FIRST_QUARTER], [FULL], [LAST_QUARTER]) apply only on the local
 * calendar day on which the exact quarter instant falls; all other days use the intermediate names.
 */
enum class MoonPhaseName {
    /** New moon (exact principal phase falls on this local day). */
    NEW,

    /** Waxing crescent (phase angle between 0° and 90°). */
    WAXING_CRESCENT,

    /** First quarter (exact principal phase falls on this local day). */
    FIRST_QUARTER,

    /** Waxing gibbous (phase angle between 90° and 180°). */
    WAXING_GIBBOUS,

    /** Full moon (exact principal phase falls on this local day). */
    FULL,

    /** Waning gibbous (phase angle between 180° and 270°). */
    WANING_GIBBOUS,

    /** Last (third) quarter (exact principal phase falls on this local day). */
    LAST_QUARTER,

    /** Waning crescent (phase angle between 270° and 360°). */
    WANING_CRESCENT,
}

/**
 * Lunar rise/set events and phase summary for one local calendar day.
 *
 * @property moonrise Apparent moonrise within the local day window, or `null`.
 * @property moonset Apparent moonset within the local day window, or `null`.
 * @property phase Lunar phase angle and illumination evaluated at the midpoint of the local day.
 * @property phaseName Phase name for the local day, naming principal phases only on the day their
 *   exact instant occurs.
 */
data class MoonDay(
    val moonrise: Instant?,
    val moonset: Instant?,
    val phase: MoonPhase,
    val phaseName: MoonPhaseName,
)

/**
 * One of the four principal lunar quarters.
 */
enum class LunarQuarter {
    /** New moon (0°). */
    NEW,

    /** First quarter (90°). */
    FIRST_QUARTER,

    /** Full moon (180°). */
    FULL,

    /** Last / third quarter (270°). */
    LAST_QUARTER,
}

/**
 * Exact occurrence of a principal lunar quarter.
 *
 * @property quarter Which principal quarter occurs.
 * @property instant Exact UTC instant of the quarter.
 */
data class LunarEvent(
    val quarter: LunarQuarter,
    val instant: Instant,
)

/**
 * Equinoxes and solstices for a calendar year.
 *
 * @property marchEquinox March equinox instant (Sun ecliptic longitude 0°).
 * @property juneSolstice June solstice instant (Sun ecliptic longitude 90°).
 * @property septemberEquinox September equinox instant (Sun ecliptic longitude 180°).
 * @property decemberSolstice December solstice instant (Sun ecliptic longitude 270°).
 */
data class Seasons(
    val marchEquinox: Instant,
    val juneSolstice: Instant,
    val septemberEquinox: Instant,
    val decemberSolstice: Instant,
)

/**
 * Sampled celestial body position along a daily trajectory.
 *
 * @property instant UTC instant of the sample.
 * @property position Apparent horizontal position at [instant].
 */
data class PathPoint(
    val instant: Instant,
    val position: SkyPosition,
)
