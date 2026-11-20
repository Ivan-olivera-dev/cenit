package io.github.ivan_olivera_dev.cenit.core.sky.chart

import io.github.ivan_olivera_dev.cenit.core.Ephemeris
import io.github.ivan_olivera_dev.cenit.core.LunarEvent
import io.github.ivan_olivera_dev.cenit.core.MoonDay
import io.github.ivan_olivera_dev.cenit.core.MoonPhase
import io.github.ivan_olivera_dev.cenit.core.MoonPhaseName
import io.github.ivan_olivera_dev.cenit.core.Place
import io.github.ivan_olivera_dev.cenit.core.PolarCondition
import io.github.ivan_olivera_dev.cenit.core.SunDay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * Solar events and photographic light ranges listed in Cielo's complete times table.
 *
 * Enum declaration order acts as a deterministic tiebreaker when two items share an instant
 * (for example [CIVIL_DAWN] and [BLUE_MORNING] start, or [BLUE_EVENING] and [CIVIL_DUSK]).
 */
enum class SunTimeKind {
    ASTRONOMICAL_DAWN,
    NAUTICAL_DAWN,
    CIVIL_DAWN,
    BLUE_MORNING,
    GOLDEN_MORNING,
    SUNRISE,
    SOLAR_NOON,
    GOLDEN_EVENING,
    SUNSET,
    BLUE_EVENING,
    CIVIL_DUSK,
    NAUTICAL_DUSK,
    ASTRONOMICAL_DUSK,
}

/**
 * One solar row in Cielo's complete times list.
 */
data class SunTimeItem(
    val kind: SunTimeKind,
    val instant: Instant,
    val end: Instant? = null,
    val azimuthDeg: Double? = null,
    val elevationDeg: Double? = null,
)

/**
 * Lunar rise/set event kind in Cielo's complete times list.
 */
enum class MoonTimeKind {
    MOONRISE,
    MOONSET,
}

/**
 * One lunar rise/set row in Cielo's complete times list, always carrying its horizon azimuth.
 */
data class MoonTimeItem(
    val kind: MoonTimeKind,
    val instant: Instant,
    val azimuthDeg: Double,
)

/**
 * Complete assembled sun and moon times for one local calendar day on the Cielo screen.
 */
data class SkyDaySchedule(
    val polar: PolarCondition,
    val sunItems: List<SunTimeItem>,
    val daylightDuration: Duration?,
    val moonItems: List<MoonTimeItem>,
    val moonPhase: MoonPhase,
    val moonPhaseName: MoonPhaseName,
    val moonIlluminationPercent: Int,
    val nextQuarters: List<LunarEvent>,
)

/**
 * Pure assembler for Cielo's complete sun and moon times list, ordering events chronologically
 * and resolving selected-time marker placement and past/active states.
 */
object SkyTimesList {
    private const val NEXT_QUARTERS_COUNT: Int = 4

    /**
     * Assembles all solar times, polar condition, lunar rise/set with azimuths, moon phase and
     * illumination, and the next four lunar quarters starting from [date]'s local midnight.
     */
    fun build(
        place: Place,
        date: LocalDate,
        zone: ZoneId,
        sunDay: SunDay = Ephemeris.sunDay(place, date, zone),
        moonDay: MoonDay = Ephemeris.moonDay(place, date, zone),
    ): SkyDaySchedule {
        val dayStart = date.atStartOfDay(zone).toInstant()

        val sunItems = buildList {
            sunDay.astronomicalDawn?.let { add(SunTimeItem(SunTimeKind.ASTRONOMICAL_DAWN, it)) }
            sunDay.nauticalDawn?.let { add(SunTimeItem(SunTimeKind.NAUTICAL_DAWN, it)) }
            sunDay.civilDawn?.let { add(SunTimeItem(SunTimeKind.CIVIL_DAWN, it)) }
            sunDay.blueMorning?.let { add(SunTimeItem(SunTimeKind.BLUE_MORNING, it.start, end = it.end)) }
            sunDay.goldenMorning?.let { add(SunTimeItem(SunTimeKind.GOLDEN_MORNING, it.start, end = it.end)) }
            sunDay.sunrise?.let {
                add(
                    SunTimeItem(
                        kind = SunTimeKind.SUNRISE,
                        instant = it,
                        azimuthDeg = Ephemeris.sunPosition(place, it).azimuth,
                    ),
                )
            }
            add(
                SunTimeItem(
                    kind = SunTimeKind.SOLAR_NOON,
                    instant = sunDay.solarNoon,
                    elevationDeg = sunDay.noonElevation,
                ),
            )
            sunDay.goldenEvening?.let { add(SunTimeItem(SunTimeKind.GOLDEN_EVENING, it.start, end = it.end)) }
            sunDay.sunset?.let {
                add(
                    SunTimeItem(
                        kind = SunTimeKind.SUNSET,
                        instant = it,
                        azimuthDeg = Ephemeris.sunPosition(place, it).azimuth,
                    ),
                )
            }
            sunDay.blueEvening?.let { add(SunTimeItem(SunTimeKind.BLUE_EVENING, it.start, end = it.end)) }
            sunDay.civilDusk?.let { add(SunTimeItem(SunTimeKind.CIVIL_DUSK, it)) }
            sunDay.nauticalDusk?.let { add(SunTimeItem(SunTimeKind.NAUTICAL_DUSK, it)) }
            sunDay.astronomicalDusk?.let { add(SunTimeItem(SunTimeKind.ASTRONOMICAL_DUSK, it)) }
        }.sortedWith(compareBy<SunTimeItem> { it.instant }.thenBy { it.kind.ordinal })

        val daylightDuration = if (sunDay.sunrise != null && sunDay.sunset != null && !sunDay.sunset!!.isBefore(sunDay.sunrise)) {
            Duration.between(sunDay.sunrise, sunDay.sunset)
        } else {
            null
        }

        val moonItems = buildList {
            moonDay.moonrise?.let {
                add(
                    MoonTimeItem(
                        kind = MoonTimeKind.MOONRISE,
                        instant = it,
                        azimuthDeg = Ephemeris.moonPosition(place, it).azimuth,
                    ),
                )
            }
            moonDay.moonset?.let {
                add(
                    MoonTimeItem(
                        kind = MoonTimeKind.MOONSET,
                        instant = it,
                        azimuthDeg = Ephemeris.moonPosition(place, it).azimuth,
                    ),
                )
            }
        }.sortedWith(compareBy<MoonTimeItem> { it.instant }.thenBy { it.kind.ordinal })

        val illuminationPercent = (moonDay.phase.illumination * 100.0).roundToInt().coerceIn(0, 100)
        val nextQuarters = Ephemeris.nextQuarters(from = dayStart, count = NEXT_QUARTERS_COUNT)

        return SkyDaySchedule(
            polar = sunDay.polar,
            sunItems = sunItems,
            daylightDuration = daylightDuration,
            moonItems = moonItems,
            moonPhase = moonDay.phase,
            moonPhaseName = moonDay.phaseName,
            moonIlluminationPercent = illuminationPercent,
            nextQuarters = nextQuarters,
        )
    }

    /**
     * Returns the index in [items] where the selected-time marker row should be inserted
     * (immediately before the first item starting strictly after [selectedInstant], or at the end).
     */
    fun selectedTimeInsertIndex(
        items: List<SunTimeItem>,
        selectedInstant: Instant,
    ): Int {
        val index = items.indexOfFirst { it.instant.isAfter(selectedInstant) }
        return if (index == -1) items.size else index
    }

    /**
     * Returns `true` when [item] has already finished before [selectedInstant].
     * Range items stay active (not past) until their [SunTimeItem.end] has passed.
     */
    fun isPast(item: SunTimeItem, selectedInstant: Instant): Boolean {
        val boundary = item.end ?: item.instant
        return boundary.isBefore(selectedInstant)
    }
}
