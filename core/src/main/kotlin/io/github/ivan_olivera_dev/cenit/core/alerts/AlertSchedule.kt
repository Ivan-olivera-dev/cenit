package io.github.ivan_olivera_dev.cenit.core.alerts

import io.github.ivan_olivera_dev.cenit.core.Ephemeris
import io.github.ivan_olivera_dev.cenit.core.Place
import io.github.ivan_olivera_dev.cenit.core.SunDay
import io.github.ivan_olivera_dev.cenit.core.sky.SkyEventKind
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Solar event kinds that Cenit can schedule local notifications for.
 *
 * Covers golden and blue hour starts (morning and evening), sunrise and sunset, and civil,
 * nautical, and astronomical dawn and dusk.
 */
enum class AlertKind {
    ASTRONOMICAL_DAWN,
    NAUTICAL_DAWN,
    CIVIL_DAWN,
    BLUE_MORNING,
    GOLDEN_MORNING,
    SUNRISE,
    GOLDEN_EVENING,
    SUNSET,
    BLUE_EVENING,
    CIVIL_DUSK,
    NAUTICAL_DUSK,
    ASTRONOMICAL_DUSK,
    ;

    companion object {
        /**
         * Maps a [SkyEventKind] displayed on Hoy to its corresponding [AlertKind], or `null`
         * for non-alertable events ([SkyEventKind.SOLAR_NOON], [SkyEventKind.MOONRISE],
         * [SkyEventKind.MOONSET]).
         */
        fun fromSkyEventKind(kind: SkyEventKind): AlertKind? = when (kind) {
            SkyEventKind.ASTRONOMICAL_DAWN -> ASTRONOMICAL_DAWN
            SkyEventKind.NAUTICAL_DAWN -> NAUTICAL_DAWN
            SkyEventKind.BLUE_MORNING -> BLUE_MORNING
            SkyEventKind.GOLDEN_MORNING -> GOLDEN_MORNING
            SkyEventKind.SUNRISE -> SUNRISE
            SkyEventKind.GOLDEN_EVENING -> GOLDEN_EVENING
            SkyEventKind.SUNSET -> SUNSET
            SkyEventKind.BLUE_EVENING -> BLUE_EVENING
            SkyEventKind.NAUTICAL_DUSK -> NAUTICAL_DUSK
            SkyEventKind.ASTRONOMICAL_DUSK -> ASTRONOMICAL_DUSK
            SkyEventKind.SOLAR_NOON,
            SkyEventKind.MOONRISE,
            SkyEventKind.MOONSET,
            -> null
        }
    }
}

/**
 * A scheduled occurrence of [kind] with its minute-truncated [eventInstant] and the earlier
 * [triggerInstant] at which the local alert should fire.
 */
data class AlertTrigger(
    val kind: AlertKind,
    val eventInstant: Instant,
    val triggerInstant: Instant,
)

/**
 * Pure offline alert scheduling built on [Ephemeris].
 */
object AlertSchedule {
    const val DEFAULT_LEAD_MINUTES: Long = 30L
    val DEFAULT_LEAD: Duration = Duration.ofMinutes(DEFAULT_LEAD_MINUTES)
    const val DEFAULT_HORIZON_DAYS: Int = 370

    /**
     * Finds the next occurrence of [kind] at [place] (in [zone]) whose trigger instant
     * (`eventInstant - lead`) is **strictly after** [now].
     *
     * Rules:
     * - Event instants are truncated to the minute (matching Cenit's display rule, L14) before
     *   subtracting [lead], so `Duration.between(triggerInstant, eventInstant) == lead` in
     *   physical elapsed time (including across DST transitions).
     * - When an event is still ahead of [now] but at or less than [lead] away
     *   (`!triggerInstant.isAfter(now)`, including the exact `lead` boundary where
     *   `triggerInstant == now`), today's occurrence is skipped in favour of the next day's
     *   occurrence. Requiring `triggerInstant` to be strictly in the future also ensures that
     *   rescheduling right when an alarm fires never loops on the same occurrence.
     * - Searches today (`now.atZone(zone).toLocalDate()`) and up to [horizonDays] following
     *   local calendar days inclusive, returning `null` if no occurrence falls within the
     *   bounded horizon (for example, during polar midnight sun or polar night).
     */
    fun nextTrigger(
        kind: AlertKind,
        place: Place,
        zone: ZoneId,
        now: Instant,
        lead: Duration = DEFAULT_LEAD,
        horizonDays: Int = DEFAULT_HORIZON_DAYS,
    ): AlertTrigger? {
        require(!lead.isNegative) { "lead must not be negative, was $lead" }
        require(horizonDays >= 0) { "horizonDays must be non-negative, was $horizonDays" }

        val startDate = now.atZone(zone).toLocalDate()
        for (offset in 0..horizonDays) {
            val date = startDate.plusDays(offset.toLong())
            val sunDay = Ephemeris.sunDay(place, date, zone)
            val rawEvent = eventInstantForDay(kind, sunDay) ?: continue
            val eventInstant = rawEvent.truncatedTo(ChronoUnit.MINUTES)
            val triggerInstant = eventInstant.minus(lead)
            if (triggerInstant.isAfter(now)) {
                return AlertTrigger(
                    kind = kind,
                    eventInstant = eventInstant,
                    triggerInstant = triggerInstant,
                )
            }
        }
        return null
    }

    /**
     * Extracts the raw [Instant] of [kind] from a computed [sunDay], or `null` if [kind] does
     * not occur on that local day.
     */
    fun eventInstantForDay(kind: AlertKind, sunDay: SunDay): Instant? = when (kind) {
        AlertKind.ASTRONOMICAL_DAWN -> sunDay.astronomicalDawn
        AlertKind.NAUTICAL_DAWN -> sunDay.nauticalDawn
        AlertKind.CIVIL_DAWN -> sunDay.civilDawn
        AlertKind.BLUE_MORNING -> sunDay.blueMorning?.start
        AlertKind.GOLDEN_MORNING -> sunDay.goldenMorning?.start
        AlertKind.SUNRISE -> sunDay.sunrise
        AlertKind.GOLDEN_EVENING -> sunDay.goldenEvening?.start
        AlertKind.SUNSET -> sunDay.sunset
        AlertKind.BLUE_EVENING -> sunDay.blueEvening?.start
        AlertKind.CIVIL_DUSK -> sunDay.civilDusk
        AlertKind.NAUTICAL_DUSK -> sunDay.nauticalDusk
        AlertKind.ASTRONOMICAL_DUSK -> sunDay.astronomicalDusk
    }
}
