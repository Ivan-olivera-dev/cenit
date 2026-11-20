package io.github.ivan_olivera_dev.cenit.ui.planner

import io.github.ivan_olivera_dev.cenit.core.Place
import java.time.Clock
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/**
 * Active named location for the planner (fixed default Madrid until T8 replaces it).
 */
data class PlannerPlace(
    val name: String,
    val place: Place,
)

val DefaultMadridPlace = PlannerPlace(
    name = "Madrid",
    place = Place(latitude = 40.4165, longitude = -3.7026),
)

/**
 * Shared date/time/place planner state used across tabs.
 *
 * Time within [selectedDate] is tracked as [minuteOffset] elapsed minutes from local midnight in
 * [zone], so 23-hour and 25-hour DST days stay monotonic and unambiguous.
 */
data class PlannerState(
    val activePlace: PlannerPlace,
    val zone: ZoneId,
    val selectedDate: LocalDate,
    val minuteOffset: Int,
) {
    val dayStart: Instant = selectedDate.atStartOfDay(zone).toInstant()
    val dayEnd: Instant = selectedDate.plusDays(1).atStartOfDay(zone).toInstant()
    val dayMinutes: Int = Duration.between(dayStart, dayEnd).toMinutes().toInt()

    val selectedInstant: Instant = dayStart.plus(minuteOffset.coerceIn(0, dayMinutes - 1).toLong(), ChronoUnit.MINUTES)
    val selectedTime: LocalTime = selectedInstant.atZone(zone).toLocalTime().truncatedTo(ChronoUnit.MINUTES)

    val selectedFraction: Float =
        if (dayMinutes > 1) minuteOffset.coerceIn(0, dayMinutes - 1).toFloat() / dayMinutes.toFloat() else 0f

    fun selectDate(date: LocalDate): PlannerState {
        val targetStart = date.atStartOfDay(zone).toInstant()
        val targetEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
        val targetMax = (Duration.between(targetStart, targetEnd).toMinutes().toInt() - 1).coerceAtLeast(0)
        val candidateInstant = date.atTime(selectedTime).atZone(zone).toInstant()
        val offset = Duration.between(targetStart, candidateInstant).toMinutes().toInt().coerceIn(0, targetMax)
        return copy(selectedDate = date, minuteOffset = offset)
    }

    fun shiftDays(days: Long): PlannerState = selectDate(selectedDate.plusDays(days))

    fun selectMinuteOffset(offset: Int): PlannerState =
        copy(minuteOffset = offset.coerceIn(0, (dayMinutes - 1).coerceAtLeast(0)))

    fun stepMinutes(deltaMinutes: Int): PlannerState =
        selectMinuteOffset(minuteOffset + deltaMinutes)

    fun selectFraction(fraction: Float, stepMinutes: Int = 5): PlannerState {
        val clamped = fraction.coerceIn(0f, 1f)
        val maxStepped = ((dayMinutes - 1) / stepMinutes) * stepMinutes
        val raw = (clamped * maxStepped).roundToInt()
        val snapped = ((raw.toFloat() / stepMinutes).roundToInt() * stepMinutes).coerceIn(0, maxStepped)
        return copy(minuteOffset = snapped)
    }

    fun resetToNow(clock: Clock): PlannerState = now(clock, zone, activePlace)

    fun weekDays(): List<LocalDate> {
        val monday = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return List(7) { monday.plusDays(it.toLong()) }
    }

    fun isAtToday(clock: Clock): Boolean = selectedDate == LocalDate.now(clock.withZone(zone))

    companion object {
        fun now(
            clock: Clock = Clock.systemDefaultZone(),
            zone: ZoneId = ZoneId.systemDefault(),
            activePlace: PlannerPlace = DefaultMadridPlace,
        ): PlannerState {
            val zonedNow = Instant.now(clock).atZone(zone).truncatedTo(ChronoUnit.MINUTES)
            val date = zonedNow.toLocalDate()
            val start = date.atStartOfDay(zone).toInstant()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant()
            val maxOffset = (Duration.between(start, end).toMinutes().toInt() - 1).coerceAtLeast(0)
            val offset = Duration.between(start, zonedNow.toInstant()).toMinutes().toInt().coerceIn(0, maxOffset)
            return PlannerState(
                activePlace = activePlace,
                zone = zone,
                selectedDate = date,
                minuteOffset = offset,
            )
        }
    }
}
