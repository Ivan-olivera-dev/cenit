package io.github.ivan_olivera_dev.cenit.ui.today

import io.github.ivan_olivera_dev.cenit.core.Place
import io.github.ivan_olivera_dev.cenit.ui.components.localDateToPickerUtcMillis
import io.github.ivan_olivera_dev.cenit.ui.components.pickerUtcMillisToLocalDate
import io.github.ivan_olivera_dev.cenit.ui.planner.PlaceSource
import io.github.ivan_olivera_dev.cenit.ui.planner.PlannerPlace
import io.github.ivan_olivera_dev.cenit.ui.planner.PlannerState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.TimeZone

class CenitDatePickerTest {

    @Test
    fun `localDateToPickerUtcMillis and pickerUtcMillisToLocalDate round-trip exact UTC midnight`() {
        val dates = listOf(
            LocalDate.of(2026, 11, 20),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31),
            LocalDate.of(2024, 2, 29),
            LocalDate.of(2026, 3, 29),
        )
        for (date in dates) {
            val expectedMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            assertEquals(expectedMillis, localDateToPickerUtcMillis(date))
            assertEquals(date, pickerUtcMillisToLocalDate(expectedMillis))
        }
    }

    @Test
    fun `date picker conversion is not UTC-shifted near midnight in America_Los_Angeles`() {
        val laZone = ZoneId.of("America/Los_Angeles")
        val laPlace = PlannerPlace(
            name = "Los Angeles",
            place = Place(34.0522, -118.2437),
            zoneId = laZone.id,
            source = PlaceSource.CITY,
        )
        // 2026-11-20 23:45 PST (UTC-8) == 2026-11-21T07:45:00Z (already the next UTC day!)
        val instant = LocalDateTime.parse("2026-11-20T23:45:00").atZone(laZone).toInstant()
        assertEquals(LocalDate.of(2026, 11, 21), instant.atZone(ZoneOffset.UTC).toLocalDate())

        val originalDefault = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(laZone))
            val state = PlannerState.now(Clock.fixed(instant, laZone), laZone, laPlace)
            assertEquals(LocalDate.of(2026, 11, 20), state.selectedDate)

            val initialUtcMillis = localDateToPickerUtcMillis(state.selectedDate)
            assertEquals(Instant.parse("2026-11-20T00:00:00Z").toEpochMilli(), initialUtcMillis)
            assertEquals(LocalDate.of(2026, 11, 20), pickerUtcMillisToLocalDate(initialUtcMillis))

            val pickedUtcMillis = Instant.parse("2026-12-25T00:00:00Z").toEpochMilli()
            val pickedDate = pickerUtcMillisToLocalDate(pickedUtcMillis)
            assertEquals(LocalDate.of(2026, 12, 25), pickedDate)

            val updated = state.selectDate(pickedDate)
            assertEquals(LocalDate.of(2026, 12, 25), updated.selectedDate)
            assertEquals(LocalTime.of(23, 45), updated.selectedTime)
            assertEquals(laZone, updated.zone)
        } finally {
            TimeZone.setDefault(originalDefault)
        }
    }

    @Test
    fun `date picker conversion is not UTC-shifted near midnight in Asia_Tokyo`() {
        val tokyoZone = ZoneId.of("Asia/Tokyo")
        val tokyoPlace = PlannerPlace(
            name = "Tokyo",
            place = Place(35.6895, 139.6917),
            zoneId = tokyoZone.id,
            source = PlaceSource.CITY,
        )
        // 2026-11-20 00:15 JST (UTC+9) == 2026-11-19T15:15:00Z (still the previous UTC day!)
        val instant = LocalDateTime.parse("2026-11-20T00:15:00").atZone(tokyoZone).toInstant()
        assertEquals(LocalDate.of(2026, 11, 19), instant.atZone(ZoneOffset.UTC).toLocalDate())

        val originalDefault = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(tokyoZone))
            val state = PlannerState.now(Clock.fixed(instant, tokyoZone), tokyoZone, tokyoPlace)
            assertEquals(LocalDate.of(2026, 11, 20), state.selectedDate)

            val initialUtcMillis = localDateToPickerUtcMillis(state.selectedDate)
            assertEquals(Instant.parse("2026-11-20T00:00:00Z").toEpochMilli(), initialUtcMillis)
            assertEquals(LocalDate.of(2026, 11, 20), pickerUtcMillisToLocalDate(initialUtcMillis))

            val pickedUtcMillis = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()
            val pickedDate = pickerUtcMillisToLocalDate(pickedUtcMillis)
            assertEquals(LocalDate.of(2026, 1, 1), pickedDate)

            val updated = state.selectDate(pickedDate)
            assertEquals(LocalDate.of(2026, 1, 1), updated.selectedDate)
            assertEquals(LocalTime.of(0, 15), updated.selectedTime)
            assertEquals(tokyoZone, updated.zone)
        } finally {
            TimeZone.setDefault(originalDefault)
        }
    }
}
