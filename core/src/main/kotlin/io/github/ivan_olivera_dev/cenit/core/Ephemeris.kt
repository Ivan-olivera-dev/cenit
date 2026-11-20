package io.github.ivan_olivera_dev.cenit.core

import io.github.cosinekitty.astronomy.Aberration
import io.github.cosinekitty.astronomy.Body
import io.github.cosinekitty.astronomy.Direction
import io.github.cosinekitty.astronomy.EquatorEpoch
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Refraction
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.equator
import io.github.cosinekitty.astronomy.horizon
import io.github.cosinekitty.astronomy.illumination
import io.github.cosinekitty.astronomy.moonPhase as astroMoonPhase
import io.github.cosinekitty.astronomy.nextMoonQuarter
import io.github.cosinekitty.astronomy.searchAltitude
import io.github.cosinekitty.astronomy.searchHourAngle
import io.github.cosinekitty.astronomy.searchMoonQuarter
import io.github.cosinekitty.astronomy.searchRiseSet
import io.github.cosinekitty.astronomy.seasons as astroSeasons
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Solar and lunar ephemeris calculations backed by Astronomy Engine.
 *
 * All daily searches span the half-open local day window `[local midnight, next local midnight)`
 * in the caller's [ZoneId], which naturally handles 23-hour and 25-hour DST transitions.
 */
object Ephemeris {
    private const val MILLIS_PER_DAY = 86_400_000.0
    private const val GOLDEN_UPPER_ALTITUDE_DEG = 6.0
    private const val GOLDEN_BLUE_BOUNDARY_DEG = -4.0
    private const val CIVIL_TWILIGHT_DEG = -6.0
    private const val NAUTICAL_TWILIGHT_DEG = -12.0
    private const val ASTRONOMICAL_TWILIGHT_DEG = -18.0
    private const val QUARTER_LOOKBACK_SECONDS = 60L

    /**
     * Computes the horizontal position of the Sun for [place] at [instant], using
     * equator-of-date coordinates with aberration.
     *
     * With [apparent] (the default) the elevation includes normal atmospheric refraction:
     * where the Sun is seen. With `apparent = false` it is the geometric elevation of the
     * Sun's centre, the quantity the sky phases (golden, blue, twilights) are defined on,
     * so use it to decide which phase an instant belongs to.
     */
    fun sunPosition(place: Place, instant: Instant, apparent: Boolean = true): SkyPosition =
        bodyPosition(Body.Sun, place.toObserver(), instant.toAstroTime(), apparent)

    /**
     * Computes the apparent horizontal position of the Moon for [place] at [instant],
     * using equator-of-date coordinates with aberration and normal atmospheric refraction.
     */
    fun moonPosition(place: Place, instant: Instant): SkyPosition =
        bodyPosition(Body.Moon, place.toObserver(), instant.toAstroTime())

    /**
     * Computes the lunar phase angle and illuminated fraction at [instant].
     */
    fun moonPhase(instant: Instant): MoonPhase {
        val time = instant.toAstroTime()
        return MoonPhase(
            angle = normalizeDegrees(astroMoonPhase(time)),
            illumination = illumination(Body.Moon, time).phaseFraction,
        )
    }

    /**
     * Computes all solar events, twilights, and golden/blue hour ranges for [date] in [zone].
     *
     * Rise and set use Astronomy Engine's horizon search (refraction plus solar semidiameter).
     * Twilight and golden/blue hour boundaries use geometric altitude searches of the Sun's centre
     * (`-18°`, `-12°`, `-6°`, `-4°`, `+6°`).
     */
    fun sunDay(place: Place, date: LocalDate, zone: ZoneId): SunDay {
        val observer = place.toObserver()
        val windowStart = date.atStartOfDay(zone).toInstant()
        val windowEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
        val startTime = windowStart.toAstroTime()
        val limitDays = Duration.between(windowStart, windowEnd).toMillis() / MILLIS_PER_DAY

        val sunrise = searchRiseSetInWindow(Body.Sun, observer, Direction.Rise, startTime, limitDays, windowStart, windowEnd)
        val sunset = searchRiseSetInWindow(Body.Sun, observer, Direction.Set, startTime, limitDays, windowStart, windowEnd)

        val noonInfo = searchHourAngle(Body.Sun, observer, 0.0, startTime, direction = +1)
        val solarNoon = noonInfo.time.toInstant()
        val noonElevation = noonInfo.hor.altitude

        val civilDawn = searchAltitudeInWindow(observer, Direction.Rise, startTime, limitDays, CIVIL_TWILIGHT_DEG, windowStart, windowEnd)
        val civilDusk = searchAltitudeInWindow(observer, Direction.Set, startTime, limitDays, CIVIL_TWILIGHT_DEG, windowStart, windowEnd)
        val nauticalDawn = searchAltitudeInWindow(observer, Direction.Rise, startTime, limitDays, NAUTICAL_TWILIGHT_DEG, windowStart, windowEnd)
        val nauticalDusk = searchAltitudeInWindow(observer, Direction.Set, startTime, limitDays, NAUTICAL_TWILIGHT_DEG, windowStart, windowEnd)
        val astronomicalDawn = searchAltitudeInWindow(observer, Direction.Rise, startTime, limitDays, ASTRONOMICAL_TWILIGHT_DEG, windowStart, windowEnd)
        val astronomicalDusk = searchAltitudeInWindow(observer, Direction.Set, startTime, limitDays, ASTRONOMICAL_TWILIGHT_DEG, windowStart, windowEnd)

        val riseMinus4 = searchAltitudeInWindow(observer, Direction.Rise, startTime, limitDays, GOLDEN_BLUE_BOUNDARY_DEG, windowStart, windowEnd)
        val risePlus6 = searchAltitudeInWindow(observer, Direction.Rise, startTime, limitDays, GOLDEN_UPPER_ALTITUDE_DEG, windowStart, windowEnd)
        val setPlus6 = searchAltitudeInWindow(observer, Direction.Set, startTime, limitDays, GOLDEN_UPPER_ALTITUDE_DEG, windowStart, windowEnd)
        val setMinus4 = searchAltitudeInWindow(observer, Direction.Set, startTime, limitDays, GOLDEN_BLUE_BOUNDARY_DEG, windowStart, windowEnd)

        val polar = when {
            sunrise != null || sunset != null -> PolarCondition.NONE
            unrefractedSunElevation(observer, noonInfo.time) >= Cenit.RISE_SET_ALTITUDE_DEG -> PolarCondition.MIDNIGHT_SUN
            else -> PolarCondition.POLAR_NIGHT
        }

        return SunDay(
            sunrise = sunrise,
            sunset = sunset,
            solarNoon = solarNoon,
            noonElevation = noonElevation,
            civilDawn = civilDawn,
            civilDusk = civilDusk,
            nauticalDawn = nauticalDawn,
            nauticalDusk = nauticalDusk,
            astronomicalDawn = astronomicalDawn,
            astronomicalDusk = astronomicalDusk,
            blueMorning = rangeOrNull(civilDawn, riseMinus4),
            goldenMorning = rangeOrNull(riseMinus4, risePlus6),
            goldenEvening = rangeOrNull(setPlus6, setMinus4),
            blueEvening = rangeOrNull(setMinus4, civilDusk),
            polar = polar,
        )
    }

    /**
     * Computes moonrise, moonset, lunar phase, and phase name for [date] in [zone].
     *
     * A principal phase ([MoonPhaseName.NEW], [MoonPhaseName.FIRST_QUARTER], [MoonPhaseName.FULL],
     * [MoonPhaseName.LAST_QUARTER]) is reported only when its exact instant falls within the local
     * day window `[local midnight, next local midnight)`; otherwise the intermediate phase is
     * derived from the phase angle at the midpoint of the local day.
     */
    fun moonDay(place: Place, date: LocalDate, zone: ZoneId): MoonDay {
        val observer = place.toObserver()
        val windowStart = date.atStartOfDay(zone).toInstant()
        val windowEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
        val startTime = windowStart.toAstroTime()
        val limitDays = Duration.between(windowStart, windowEnd).toMillis() / MILLIS_PER_DAY

        val moonrise = searchRiseSetInWindow(Body.Moon, observer, Direction.Rise, startTime, limitDays, windowStart, windowEnd)
        val moonset = searchRiseSetInWindow(Body.Moon, observer, Direction.Set, startTime, limitDays, windowStart, windowEnd)

        val midpoint = windowStart.plus(Duration.between(windowStart, windowEnd).dividedBy(2))
        val phase = moonPhase(midpoint)
        val phaseName = quarterInWindow(windowStart, windowEnd)?.toPhaseName()
            ?: intermediatePhaseName(phase.angle)

        return MoonDay(
            moonrise = moonrise,
            moonset = moonset,
            phase = phase,
            phaseName = phaseName,
        )
    }

    /**
     * Returns the next [count] principal lunar quarters occurring at or after [from].
     */
    fun nextQuarters(from: Instant, count: Int): List<LunarEvent> {
        require(count >= 0) { "count must be non-negative, was $count" }
        if (count == 0) return emptyList()

        val events = ArrayList<LunarEvent>(count)
        var current = searchMoonQuarter(from.toAstroTime())
        events.add(LunarEvent(current.quarter.toLunarQuarter(), current.time.toInstant()))
        repeat(count - 1) {
            current = nextMoonQuarter(current)
            events.add(LunarEvent(current.quarter.toLunarQuarter(), current.time.toInstant()))
        }
        return events
    }

    /**
     * Computes the equinoxes and solstices for calendar [year].
     */
    fun seasons(year: Int): Seasons {
        val info = astroSeasons(year)
        return Seasons(
            marchEquinox = info.marchEquinox.toInstant(),
            juneSolstice = info.juneSolstice.toInstant(),
            septemberEquinox = info.septemberEquinox.toInstant(),
            decemberSolstice = info.decemberSolstice.toInstant(),
        )
    }

    /**
     * Samples the Sun's apparent position from local midnight to next local midnight inclusive,
     * advancing by [step].
     */
    fun sunPath(
        place: Place,
        date: LocalDate,
        zone: ZoneId,
        step: Duration = Duration.ofMinutes(15),
    ): List<PathPoint> = bodyPath(Body.Sun, place, date, zone, step)

    /**
     * Samples the Moon's apparent position from local midnight to next local midnight inclusive,
     * advancing by [step].
     */
    fun moonPath(
        place: Place,
        date: LocalDate,
        zone: ZoneId,
        step: Duration = Duration.ofMinutes(15),
    ): List<PathPoint> = bodyPath(Body.Moon, place, date, zone, step)

    private fun bodyPath(
        body: Body,
        place: Place,
        date: LocalDate,
        zone: ZoneId,
        step: Duration,
    ): List<PathPoint> {
        require(!step.isNegative && !step.isZero) { "step must be positive, was $step" }
        val observer = place.toObserver()
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        val points = mutableListOf<PathPoint>()

        var instant = start
        while (!instant.isAfter(end)) {
            points.add(PathPoint(instant, bodyPosition(body, observer, instant.toAstroTime())))
            instant = instant.plus(step)
        }
        if (points.last().instant.isBefore(end)) {
            points.add(PathPoint(end, bodyPosition(body, observer, end.toAstroTime())))
        }
        return points
    }

    private fun bodyPosition(body: Body, observer: Observer, time: Time, apparent: Boolean = true): SkyPosition {
        val eq = equator(body, time, observer, EquatorEpoch.OfDate, Aberration.Corrected)
        val hor = horizon(time, observer, eq.ra, eq.dec, if (apparent) Refraction.Normal else Refraction.None)
        return SkyPosition(
            azimuth = normalizeDegrees(hor.azimuth),
            elevation = hor.altitude,
        )
    }

    private fun unrefractedSunElevation(observer: Observer, time: Time): Double {
        val eq = equator(Body.Sun, time, observer, EquatorEpoch.OfDate, Aberration.Corrected)
        return horizon(time, observer, eq.ra, eq.dec, Refraction.None).altitude
    }

    private fun searchRiseSetInWindow(
        body: Body,
        observer: Observer,
        direction: Direction,
        startTime: Time,
        limitDays: Double,
        windowStart: Instant,
        windowEnd: Instant,
    ): Instant? = searchRiseSet(body, observer, direction, startTime, limitDays)
        ?.toInstant()
        ?.takeIf { !it.isBefore(windowStart) && it.isBefore(windowEnd) }

    private fun searchAltitudeInWindow(
        observer: Observer,
        direction: Direction,
        startTime: Time,
        limitDays: Double,
        altitudeDeg: Double,
        windowStart: Instant,
        windowEnd: Instant,
    ): Instant? = searchAltitude(Body.Sun, observer, direction, startTime, limitDays, altitudeDeg)
        ?.toInstant()
        ?.takeIf { !it.isBefore(windowStart) && it.isBefore(windowEnd) }

    private fun quarterInWindow(windowStart: Instant, windowEnd: Instant): LunarQuarter? {
        // Look back slightly so a quarter falling right on windowStart is not skipped.
        var candidate = searchMoonQuarter(windowStart.minusSeconds(QUARTER_LOOKBACK_SECONDS).toAstroTime())
        var instant = candidate.time.toInstant()
        if (instant.isBefore(windowStart)) {
            candidate = nextMoonQuarter(candidate)
            instant = candidate.time.toInstant()
        }
        return if (!instant.isBefore(windowStart) && instant.isBefore(windowEnd)) {
            candidate.quarter.toLunarQuarter()
        } else {
            null
        }
    }

    private fun rangeOrNull(start: Instant?, end: Instant?): TimeRange? =
        if (start != null && end != null && !end.isBefore(start)) TimeRange(start, end) else null

    private fun intermediatePhaseName(angleDeg: Double): MoonPhaseName = when {
        angleDeg < 90.0 -> MoonPhaseName.WAXING_CRESCENT
        angleDeg < 180.0 -> MoonPhaseName.WAXING_GIBBOUS
        angleDeg < 270.0 -> MoonPhaseName.WANING_GIBBOUS
        else -> MoonPhaseName.WANING_CRESCENT
    }

    private fun Int.toLunarQuarter(): LunarQuarter = when (this) {
        0 -> LunarQuarter.NEW
        1 -> LunarQuarter.FIRST_QUARTER
        2 -> LunarQuarter.FULL
        3 -> LunarQuarter.LAST_QUARTER
        else -> error("Unexpected Astronomy Engine moon quarter index: $this")
    }

    private fun LunarQuarter.toPhaseName(): MoonPhaseName = when (this) {
        LunarQuarter.NEW -> MoonPhaseName.NEW
        LunarQuarter.FIRST_QUARTER -> MoonPhaseName.FIRST_QUARTER
        LunarQuarter.FULL -> MoonPhaseName.FULL
        LunarQuarter.LAST_QUARTER -> MoonPhaseName.LAST_QUARTER
    }

    private fun Place.toObserver(): Observer = Observer(latitude, longitude, elevationMeters)

    private fun Instant.toAstroTime(): Time = Time.fromMillisecondsSince1970(toEpochMilli())

    private fun Time.toInstant(): Instant = Instant.ofEpochMilli(toMillisecondsSince1970())

    private fun normalizeDegrees(degrees: Double): Double {
        val normalized = ((degrees % 360.0) + 360.0) % 360.0
        return if (normalized >= 360.0 || normalized == 0.0) 0.0 else normalized
    }
}
