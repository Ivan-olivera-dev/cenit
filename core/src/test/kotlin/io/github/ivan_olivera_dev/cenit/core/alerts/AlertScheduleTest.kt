package io.github.ivan_olivera_dev.cenit.core.alerts

import io.github.ivan_olivera_dev.cenit.core.Ephemeris
import io.github.ivan_olivera_dev.cenit.core.Place
import io.github.ivan_olivera_dev.cenit.core.sky.SkyEventKind
import io.github.ivan_olivera_dev.cenit.core.sky.SkyFormatting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class AlertScheduleTest {
    private val madrid = Place(latitude = 40.4165, longitude = -3.7026)
    private val madridZone = ZoneId.of("Europe/Madrid")
    private val tromso = Place(latitude = 69.6492, longitude = 18.9553)
    private val tromsoZone = ZoneId.of("Europe/Oslo")

    private fun at(text: String, zone: ZoneId = madridZone): Instant =
        LocalDateTime.parse(text).atZone(zone).toInstant()

    private fun hhmm(instant: Instant, zone: ZoneId = madridZone): String =
        SkyFormatting.formatTimeTruncated(instant, zone)

    @Test
    fun `Madrid 2026-06-21 sunrise at 05 00 schedules event 06 44 and trigger 06 14 minute-truncated`() {
        val trigger = AlertSchedule.nextTrigger(
            kind = AlertKind.SUNRISE,
            place = madrid,
            zone = madridZone,
            now = at("2026-06-21T05:00:00"),
        )
        assertNotNull(trigger)
        trigger!!
        assertEquals(AlertKind.SUNRISE, trigger.kind)
        assertEquals(at("2026-06-21T06:44:00"), trigger.eventInstant)
        assertEquals(at("2026-06-21T06:14:00"), trigger.triggerInstant)
        assertEquals("06:44", hhmm(trigger.eventInstant))
        assertEquals("06:14", hhmm(trigger.triggerInstant))
        assertEquals(Duration.ofMinutes(30), Duration.between(trigger.triggerInstant, trigger.eventInstant))
    }

    @Test
    fun `all 12 alert kinds on Madrid 2026-06-21 match minute-truncated sunDay instants minus 30 min`() {
        val date = LocalDate.of(2026, 6, 21)
        val day = Ephemeris.sunDay(madrid, date, madridZone)
        val startOfDay = at("2026-06-21T00:00:00")

        val expectedByKind = mapOf(
            AlertKind.ASTRONOMICAL_DAWN to day.astronomicalDawn!!,
            AlertKind.NAUTICAL_DAWN to day.nauticalDawn!!,
            AlertKind.CIVIL_DAWN to day.civilDawn!!,
            AlertKind.BLUE_MORNING to day.blueMorning!!.start,
            AlertKind.GOLDEN_MORNING to day.goldenMorning!!.start,
            AlertKind.SUNRISE to day.sunrise!!,
            AlertKind.GOLDEN_EVENING to day.goldenEvening!!.start,
            AlertKind.SUNSET to day.sunset!!,
            AlertKind.BLUE_EVENING to day.blueEvening!!.start,
            AlertKind.CIVIL_DUSK to day.civilDusk!!,
            AlertKind.NAUTICAL_DUSK to day.nauticalDusk!!,
            AlertKind.ASTRONOMICAL_DUSK to day.astronomicalDusk!!,
        )
        assertEquals(AlertKind.entries.size, expectedByKind.size)

        for ((kind, rawEvent) in expectedByKind) {
            val expectedEvent = rawEvent.truncatedTo(ChronoUnit.MINUTES)
            val expectedTrigger = expectedEvent.minus(Duration.ofMinutes(30))
            val result = AlertSchedule.nextTrigger(kind, madrid, madridZone, startOfDay)
            assertNotNull("expected trigger for $kind", result)
            assertEquals("event instant for $kind", expectedEvent, result!!.eventInstant)
            assertEquals("trigger instant for $kind", expectedTrigger, result.triggerInstant)
        }
    }

    @Test
    fun `Madrid DST spring-forward day 2026-03-29 schedules sunrise 08 03 trigger 07 33 and sunset 20 36 trigger 20 06`() {
        val now = at("2026-03-29T00:30:00")
        val sunrise = AlertSchedule.nextTrigger(AlertKind.SUNRISE, madrid, madridZone, now)!!
        assertEquals(at("2026-03-29T08:03:00"), sunrise.eventInstant)
        assertEquals(at("2026-03-29T07:33:00"), sunrise.triggerInstant)
        assertEquals(Duration.ofMinutes(30), Duration.between(sunrise.triggerInstant, sunrise.eventInstant))

        val sunset = AlertSchedule.nextTrigger(AlertKind.SUNSET, madrid, madridZone, now)!!
        assertEquals(at("2026-03-29T20:36:00"), sunset.eventInstant)
        assertEquals(at("2026-03-29T20:06:00"), sunset.triggerInstant)
        assertEquals(Duration.ofMinutes(30), Duration.between(sunset.triggerInstant, sunset.eventInstant))
    }

    @Test
    fun `30-minute boundary pins both sides strictly in the future`() {
        // Sunrise on 2026-06-21 is 06:44:00 (truncated), trigger is 06:14:00.
        // 1 second before trigger (30m 01s before event): trigger is strictly in the future -> today.
        val justBeforeBoundary = AlertSchedule.nextTrigger(
            kind = AlertKind.SUNRISE,
            place = madrid,
            zone = madridZone,
            now = at("2026-06-21T06:13:59"),
        )!!
        assertEquals(LocalDate.of(2026, 6, 21), justBeforeBoundary.eventInstant.atZone(madridZone).toLocalDate())
        assertEquals(at("2026-06-21T06:44:00"), justBeforeBoundary.eventInstant)
        assertEquals(at("2026-06-21T06:14:00"), justBeforeBoundary.triggerInstant)

        // Exactly at trigger (30m 00s before event): trigger == now is not strictly in the future -> tomorrow.
        // 2026-06-21 sunrise is 06:44:47; on 2026-06-22 (past solstice) it is 06:45:03 -> truncated 06:45.
        val exactBoundary = AlertSchedule.nextTrigger(
            kind = AlertKind.SUNRISE,
            place = madrid,
            zone = madridZone,
            now = at("2026-06-21T06:14:00"),
        )!!
        assertEquals(LocalDate.of(2026, 6, 22), exactBoundary.eventInstant.atZone(madridZone).toLocalDate())
        assertEquals(at("2026-06-22T06:45:00"), exactBoundary.eventInstant)
        assertEquals(at("2026-06-22T06:15:00"), exactBoundary.triggerInstant)

        // Less than 30 min away but event still ahead (14 min before sunrise) -> skips today, picks tomorrow.
        val lessThan30Min = AlertSchedule.nextTrigger(
            kind = AlertKind.SUNRISE,
            place = madrid,
            zone = madridZone,
            now = at("2026-06-21T06:30:00"),
        )!!
        assertEquals(LocalDate.of(2026, 6, 22), lessThan30Min.eventInstant.atZone(madridZone).toLocalDate())
        assertEquals(at("2026-06-22T06:45:00"), lessThan30Min.eventInstant)
        assertEquals(at("2026-06-22T06:15:00"), lessThan30Min.triggerInstant)
    }

    @Test
    fun `Tromso sunrise during June midnight sun skips polar days to the first sunrise in July`() {
        val now = at("2026-06-21T12:00:00", tromsoZone)
        val trigger = AlertSchedule.nextTrigger(
            kind = AlertKind.SUNRISE,
            place = tromso,
            zone = tromsoZone,
            now = now,
        )
        assertNotNull("Tromso has a sunrise later in summer within 370 days", trigger)
        trigger!!
        val eventDate = trigger.eventInstant.atZone(tromsoZone).toLocalDate()
        assertEquals(LocalDate.of(2026, 7, 26), eventDate)
        // Confirm the day before really had no sunrise and this day does.
        assertNull(Ephemeris.sunDay(tromso, eventDate.minusDays(1), tromsoZone).sunrise)
        assertEquals(
            Ephemeris.sunDay(tromso, eventDate, tromsoZone).sunrise!!.truncatedTo(ChronoUnit.MINUTES),
            trigger.eventInstant,
        )
        assertEquals(Duration.ofMinutes(30), Duration.between(trigger.triggerInstant, trigger.eventInstant))
        assertTrue(trigger.triggerInstant.isAfter(now))
    }

    @Test
    fun `bounded horizon returns null when polar period exceeds horizon`() {
        val now = at("2026-06-21T12:00:00", tromsoZone)
        val result = AlertSchedule.nextTrigger(
            kind = AlertKind.SUNRISE,
            place = tromso,
            zone = tromsoZone,
            now = now,
            horizonDays = 10,
        )
        assertNull(result)
    }

    @Test
    fun `extreme polar latitude returns null within default 370-day horizon when range never completes in one day`() {
        val nearNorthPole = Place(latitude = 89.5, longitude = 0.0)
        val result = AlertSchedule.nextTrigger(
            kind = AlertKind.GOLDEN_MORNING,
            place = nearNorthPole,
            zone = ZoneId.of("UTC"),
            now = Instant.parse("2026-06-21T12:00:00Z"),
        )
        assertNull(result)
    }

    @Test
    fun `SkyEventKind maps to AlertKind for solar alertable events and null for noon and moon`() {
        assertEquals(AlertKind.ASTRONOMICAL_DAWN, AlertKind.fromSkyEventKind(SkyEventKind.ASTRONOMICAL_DAWN))
        assertEquals(AlertKind.NAUTICAL_DAWN, AlertKind.fromSkyEventKind(SkyEventKind.NAUTICAL_DAWN))
        assertEquals(AlertKind.BLUE_MORNING, AlertKind.fromSkyEventKind(SkyEventKind.BLUE_MORNING))
        assertEquals(AlertKind.GOLDEN_MORNING, AlertKind.fromSkyEventKind(SkyEventKind.GOLDEN_MORNING))
        assertEquals(AlertKind.SUNRISE, AlertKind.fromSkyEventKind(SkyEventKind.SUNRISE))
        assertEquals(AlertKind.GOLDEN_EVENING, AlertKind.fromSkyEventKind(SkyEventKind.GOLDEN_EVENING))
        assertEquals(AlertKind.SUNSET, AlertKind.fromSkyEventKind(SkyEventKind.SUNSET))
        assertEquals(AlertKind.BLUE_EVENING, AlertKind.fromSkyEventKind(SkyEventKind.BLUE_EVENING))
        assertEquals(AlertKind.NAUTICAL_DUSK, AlertKind.fromSkyEventKind(SkyEventKind.NAUTICAL_DUSK))
        assertEquals(AlertKind.ASTRONOMICAL_DUSK, AlertKind.fromSkyEventKind(SkyEventKind.ASTRONOMICAL_DUSK))
        assertNull(AlertKind.fromSkyEventKind(SkyEventKind.SOLAR_NOON))
        assertNull(AlertKind.fromSkyEventKind(SkyEventKind.MOONRISE))
        assertNull(AlertKind.fromSkyEventKind(SkyEventKind.MOONSET))
    }

    @Test
    fun `negative lead or negative horizon is rejected`() {
        val now = at("2026-06-21T05:00:00")
        assertThrows(IllegalArgumentException::class.java) {
            AlertSchedule.nextTrigger(AlertKind.SUNRISE, madrid, madridZone, now, lead = Duration.ofMinutes(-1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AlertSchedule.nextTrigger(AlertKind.SUNRISE, madrid, madridZone, now, horizonDays = -1)
        }
    }
}
