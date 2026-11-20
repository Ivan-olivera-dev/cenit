package io.github.ivan_olivera_dev.cenit.ui.today

import io.github.ivan_olivera_dev.cenit.core.Place
import io.github.ivan_olivera_dev.cenit.ui.planner.DefaultMadridPlace
import io.github.ivan_olivera_dev.cenit.ui.planner.PlaceSource
import io.github.ivan_olivera_dev.cenit.ui.planner.PlannerPlace
import io.github.ivan_olivera_dev.cenit.ui.planner.PlannerState
import io.github.ivan_olivera_dev.cenit.ui.planner.resolveInitialPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class PlannerStateTest {
    private val madridZone = ZoneId.of("Europe/Madrid")

    private fun fixedClock(isoLocal: String, zone: ZoneId = madridZone): Clock {
        val instant = LocalDateTime.parse(isoLocal).atZone(zone).toInstant()
        return Clock.fixed(instant, zone)
    }

    @Test
    fun `default place is Madrid with exact coordinates`() {
        assertEquals("Madrid", DefaultMadridPlace.name)
        assertEquals(40.4165, DefaultMadridPlace.place.latitude, 1e-6)
        assertEquals(-3.7026, DefaultMadridPlace.place.longitude, 1e-6)
    }

    @Test
    fun `now initializes date and minute-truncated time and resets cleanly`() {
        val clock = fixedClock("2026-06-21T16:20:47")
        val initial = PlannerState.now(clock, madridZone)

        assertEquals(LocalDate.of(2026, 6, 21), initial.selectedDate)
        assertEquals(LocalTime.of(16, 20), initial.selectedTime)
        assertEquals(1440, initial.dayMinutes)
        assertTrue(initial.isAtToday(clock))

        val moved = initial.shiftDays(3).stepMinutes(-65)
        assertEquals(LocalDate.of(2026, 6, 24), moved.selectedDate)
        assertEquals(LocalTime.of(15, 15), moved.selectedTime)
        assertFalse(moved.isAtToday(clock))

        val reset = moved.resetToNow(clock)
        assertEquals(initial.selectedDate, reset.selectedDate)
        assertEquals(initial.selectedTime, reset.selectedTime)
        assertEquals(initial.selectedInstant, reset.selectedInstant)
    }

    @Test
    fun `stepMinutes clamps inside the day for 5-minute and 1-hour actions`() {
        val clock = fixedClock("2026-06-21T00:10:00")
        val state = PlannerState.now(clock, madridZone)

        assertEquals(LocalTime.of(0, 15), state.stepMinutes(5).selectedTime)
        assertEquals(LocalTime.of(1, 10), state.stepMinutes(60).selectedTime)
        assertEquals(LocalTime.of(0, 0), state.stepMinutes(-60).selectedTime)

        val late = state.selectMinuteOffset(1435)
        assertEquals(LocalTime.of(23, 55), late.selectedTime)
        assertEquals(LocalTime.of(23, 59), late.stepMinutes(60).selectedTime)
    }

    @Test
    fun `selectFraction quantizes to 5-minute scrubber steps`() {
        val clock = fixedClock("2026-06-21T12:00:00")
        val state = PlannerState.now(clock, madridZone)

        val atHalf = state.selectFraction(0.5f, stepMinutes = 5)
        assertEquals(LocalTime.of(12, 0), atHalf.selectedTime)

        val atStart = state.selectFraction(-0.2f, stepMinutes = 5)
        assertEquals(0, atStart.minuteOffset)

        val atEnd = state.selectFraction(1.2f, stepMinutes = 5)
        assertEquals(1435, atEnd.minuteOffset)
        assertEquals(LocalTime.of(23, 55), atEnd.selectedTime)
    }

    @Test
    fun `DST spring transition day has 1380 minutes and preserves wall time across shiftDays`() {
        val clock = fixedClock("2026-03-29T14:15:00")
        val dstDay = PlannerState.now(clock, madridZone)

        assertEquals(1380, dstDay.dayMinutes)
        assertEquals(LocalTime.of(14, 15), dstDay.selectedTime)

        val nextDay = dstDay.shiftDays(1)
        assertEquals(LocalDate.of(2026, 3, 30), nextDay.selectedDate)
        assertEquals(1440, nextDay.dayMinutes)
        assertEquals(LocalTime.of(14, 15), nextDay.selectedTime)
    }

    @Test
    fun `weekDays returns Monday through Sunday containing selectedDate`() {
        val clock = fixedClock("2026-11-20T16:20:00") // Friday
        val state = PlannerState.now(clock, madridZone)
        val week = state.weekDays()

        assertEquals(7, week.size)
        assertEquals(DayOfWeek.MONDAY, week.first().dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, week.last().dayOfWeek)
        assertEquals(LocalDate.of(2026, 11, 16), week.first())
        assertEquals(LocalDate.of(2026, 11, 22), week.last())
        assertTrue(state.selectedDate in week)
    }

    @Test
    fun `selectPlace uses GeoNames timezone for cities and preserves physical instant`() {
        val clock = fixedClock("2026-11-20T16:20:00", madridZone)
        val state = PlannerState.now(clock, madridZone)
        val tokyo = PlannerPlace(
            name = "Tokyo",
            place = Place(35.6895, 139.6917),
            region = "Japan",
            zoneId = "Asia/Tokyo",
            source = PlaceSource.CITY,
        )

        val switched = state.selectPlace(tokyo, deviceZone = madridZone)
        assertEquals(ZoneId.of("Asia/Tokyo"), switched.zone)
        assertEquals(tokyo, switched.activePlace)
        assertEquals(state.selectedInstant, switched.selectedInstant)
        // 16:20 CET (UTC+1) = 00:20 JST (UTC+9) next day
        assertEquals(LocalDate.of(2026, 11, 21), switched.selectedDate)
        assertEquals(LocalTime.of(0, 20), switched.selectedTime)
    }

    @Test
    fun `selectPlace uses device timezone for GPS and manual coordinates`() {
        val tokyoZone = ZoneId.of("Asia/Tokyo")
        val clock = fixedClock("2026-11-20T16:20:00", madridZone)
        val initial = PlannerState.now(clock, tokyoZone)

        val gps = PlannerPlace(
            name = "My location",
            place = Place(39.2817, -0.2763),
            zoneId = null,
            source = PlaceSource.GPS,
        )
        val manual = PlannerPlace(
            name = "Mirador",
            place = Place(40.0, -0.5),
            zoneId = null,
            source = PlaceSource.MANUAL,
        )

        assertEquals(madridZone, initial.selectPlace(gps, deviceZone = madridZone).zone)
        assertEquals(madridZone, initial.selectPlace(manual, deviceZone = madridZone).zone)
    }

    @Test
    fun `resolveInitialPlace prefers saved active place, then permitted device location, then Madrid`() {
        val saved = PlannerPlace("Valencia", Place(39.4739, -0.3797), zoneId = "Europe/Madrid", source = PlaceSource.CITY)
        val device = PlannerPlace("My location", Place(36.7202, -4.4203), zoneId = null, source = PlaceSource.GPS)

        assertEquals(saved, resolveInitialPlace(lastActive = saved, permittedDevicePlace = device))
        assertEquals(device, resolveInitialPlace(lastActive = null, permittedDevicePlace = device))
        assertEquals(DefaultMadridPlace, resolveInitialPlace(lastActive = null, permittedDevicePlace = null))
    }

    @Test
    fun `forPlaceNow resolves active place timezone automatically`() {
        val clock = fixedClock("2026-06-21T12:00:00", ZoneId.of("UTC"))
        val nyc = PlannerPlace(
            name = "New York",
            place = Place(40.7128, -74.0060),
            zoneId = "America/New_York",
            source = PlaceSource.CITY,
        )
        val state = PlannerState.forPlaceNow(nyc, clock = clock, deviceZone = madridZone)
        assertEquals(ZoneId.of("America/New_York"), state.zone)
        assertEquals(LocalTime.of(8, 0), state.selectedTime)
    }
}
