package io.github.ivan_olivera_dev.cenit.core.sky

import io.github.ivan_olivera_dev.cenit.core.Ephemeris
import io.github.ivan_olivera_dev.cenit.core.MoonDay
import io.github.ivan_olivera_dev.cenit.core.Place
import io.github.ivan_olivera_dev.cenit.core.SunDay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Kind of solar or lunar event displayed in Cenit's event lists.
 */
enum class SkyEventKind {
    ASTRONOMICAL_DAWN,
    NAUTICAL_DAWN,
    BLUE_MORNING,
    GOLDEN_MORNING,
    SUNRISE,
    SOLAR_NOON,
    GOLDEN_EVENING,
    SUNSET,
    BLUE_EVENING,
    NAUTICAL_DUSK,
    ASTRONOMICAL_DUSK,
    MOONRISE,
    MOONSET,
}

/**
 * One scheduled solar or lunar event for a local calendar day.
 *
 * @property kind Semantic kind of the event.
 * @property instant Start (or sole) instant of the event.
 * @property end End instant for range events (golden/blue hour), or `null`.
 * @property azimuth Compass bearing in degrees `[0, 360)` for rise/set events, or `null`.
 * @property elevation Apparent elevation in degrees for [SkyEventKind.SOLAR_NOON], or `null`.
 */
data class SkyEvent(
    val kind: SkyEventKind,
    val instant: Instant,
    val end: Instant? = null,
    val azimuth: Double? = null,
    val elevation: Double? = null,
)

/**
 * Upcoming [event] selected for display, indicating whether it was drawn from the following
 * calendar day because no key events remained on the selected day.
 *
 * @property event Underlying solar or lunar event.
 * @property isNextDay `true` when [event] belongs to the day after the selected calendar date.
 */
data class UpcomingSkyEvent(
    val event: SkyEvent,
    val isNextDay: Boolean,
)

/**
 * Builds chronological event lists for a local day and selects upcoming events after an instant.
 */
object SkyEvents {
    private val SECONDARY_TWILIGHTS = setOf(
        SkyEventKind.ASTRONOMICAL_DAWN,
        SkyEventKind.NAUTICAL_DAWN,
        SkyEventKind.NAUTICAL_DUSK,
    )

    /**
     * Collects all solar and lunar events occurring during [sunDay] and [moonDay], sorted
     * chronologically by [SkyEvent.instant].
     */
    fun forDay(
        place: Place,
        sunDay: SunDay,
        moonDay: MoonDay,
    ): List<SkyEvent> {
        val events = mutableListOf<SkyEvent>()

        sunDay.astronomicalDawn?.let { events.add(SkyEvent(SkyEventKind.ASTRONOMICAL_DAWN, it)) }
        sunDay.nauticalDawn?.let { events.add(SkyEvent(SkyEventKind.NAUTICAL_DAWN, it)) }
        sunDay.blueMorning?.let { events.add(SkyEvent(SkyEventKind.BLUE_MORNING, it.start, end = it.end)) }
        sunDay.goldenMorning?.let { events.add(SkyEvent(SkyEventKind.GOLDEN_MORNING, it.start, end = it.end)) }
        sunDay.sunrise?.let {
            events.add(
                SkyEvent(
                    kind = SkyEventKind.SUNRISE,
                    instant = it,
                    azimuth = Ephemeris.sunPosition(place, it).azimuth,
                ),
            )
        }
        events.add(
            SkyEvent(
                kind = SkyEventKind.SOLAR_NOON,
                instant = sunDay.solarNoon,
                elevation = sunDay.noonElevation,
            ),
        )
        sunDay.goldenEvening?.let { events.add(SkyEvent(SkyEventKind.GOLDEN_EVENING, it.start, end = it.end)) }
        sunDay.sunset?.let {
            events.add(
                SkyEvent(
                    kind = SkyEventKind.SUNSET,
                    instant = it,
                    azimuth = Ephemeris.sunPosition(place, it).azimuth,
                ),
            )
        }
        sunDay.blueEvening?.let { events.add(SkyEvent(SkyEventKind.BLUE_EVENING, it.start, end = it.end)) }
        sunDay.nauticalDusk?.let { events.add(SkyEvent(SkyEventKind.NAUTICAL_DUSK, it)) }
        sunDay.astronomicalDusk?.let { events.add(SkyEvent(SkyEventKind.ASTRONOMICAL_DUSK, it)) }

        moonDay.moonrise?.let {
            events.add(
                SkyEvent(
                    kind = SkyEventKind.MOONRISE,
                    instant = it,
                    azimuth = Ephemeris.moonPosition(place, it).azimuth,
                ),
            )
        }
        moonDay.moonset?.let {
            events.add(
                SkyEvent(
                    kind = SkyEventKind.MOONSET,
                    instant = it,
                    azimuth = Ephemeris.moonPosition(place, it).azimuth,
                ),
            )
        }

        return events.sortedWith(compareBy<SkyEvent> { it.instant }.thenBy { it.kind.ordinal })
    }

    /**
     * Returns key photographic and rise/set events occurring strictly after [after],
     * omitting secondary nautical/astronomical dawn and nautical dusk just like the artboard.
     */
    fun nextKeyEvents(events: List<SkyEvent>, after: Instant): List<SkyEvent> =
        events
            .filter { it.kind !in SECONDARY_TWILIGHTS && it.instant.isAfter(after) }
            .sortedWith(compareBy<SkyEvent> { it.instant }.thenBy { it.kind.ordinal })

    /**
     * Returns the first key event strictly after [after], or `null` if none remain today.
     */
    fun nextKeyEvent(events: List<SkyEvent>, after: Instant): SkyEvent? =
        nextKeyEvents(events, after).firstOrNull()

    /**
     * Returns up to [limit] key events strictly after [after] from [todayEvents]; when none
     * remain on the selected day, falls back to the first [limit] key events from
     * [tomorrowEvents] with [UpcomingSkyEvent.isNextDay] set to `true`.
     *
     * [tomorrowEvents] is invoked lazily only when [todayEvents] has no remaining key events.
     */
    fun nextKeyEventsOrTomorrow(
        todayEvents: List<SkyEvent>,
        after: Instant,
        limit: Int = 3,
        tomorrowEvents: () -> List<SkyEvent>,
    ): List<UpcomingSkyEvent> {
        require(limit >= 0) { "limit must be non-negative, was $limit" }
        if (limit == 0) return emptyList()
        val remainingToday = nextKeyEvents(todayEvents, after)
        if (remainingToday.isNotEmpty()) {
            return remainingToday.take(limit).map { UpcomingSkyEvent(it, isNextDay = false) }
        }
        return nextKeyEvents(tomorrowEvents(), after)
            .take(limit)
            .map { UpcomingSkyEvent(it, isNextDay = true) }
    }

    /**
     * Resolves key events for [selectedDate] strictly after [after], falling back to the first
     * [limit] key events of `selectedDate.plusDays(1)` when none remain on [selectedDate].
     */
    fun nextKeyEventsOrTomorrow(
        place: Place,
        selectedDate: LocalDate,
        zone: ZoneId,
        after: Instant,
        limit: Int = 3,
    ): List<UpcomingSkyEvent> {
        val todayEvents = forDay(
            place,
            Ephemeris.sunDay(place, selectedDate, zone),
            Ephemeris.moonDay(place, selectedDate, zone),
        )
        return nextKeyEventsOrTomorrow(
            todayEvents = todayEvents,
            after = after,
            limit = limit,
            tomorrowEvents = {
                val tomorrow = selectedDate.plusDays(1)
                forDay(
                    place,
                    Ephemeris.sunDay(place, tomorrow, zone),
                    Ephemeris.moonDay(place, tomorrow, zone),
                )
            },
        )
    }
}
