package io.github.ivan_olivera_dev.cenit.core.sky

import io.github.ivan_olivera_dev.cenit.core.Ephemeris
import io.github.ivan_olivera_dev.cenit.core.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class SkyEventsTomorrowTest {
    private val madrid = Place(latitude = 40.4165, longitude = -3.7026)
    private val madridZone = ZoneId.of("Europe/Madrid")

    private val tromso = Place(latitude = 69.6492, longitude = 18.9553)
    private val tromsoZone = ZoneId.of("Europe/Oslo")

    private fun at(isoLocal: String, zone: ZoneId): Instant =
        LocalDateTime.parse(isoLocal).atZone(zone).toInstant()

    @Test
    fun `Madrid 2026-11-20 at 21_00 returns tomorrow first key events in order`() {
        val date = LocalDate.of(2026, 11, 20)
        val after = at("2026-11-20T21:00", madridZone)

        val upcoming = SkyEvents.nextKeyEventsOrTomorrow(
            place = madrid,
            selectedDate = date,
            zone = madridZone,
            after = after,
            limit = 3,
        )

        assertEquals(3, upcoming.size)
        assertTrue(upcoming.all { it.isNextDay })
        assertTrue(upcoming.all { it.event.instant.isAfter(after) })
        assertTrue(
            upcoming.all { it.event.instant.atZone(madridZone).toLocalDate() == LocalDate.of(2026, 11, 21) },
        )
        assertEquals(upcoming.sortedBy { it.event.instant }, upcoming)

        val tomorrowExpected = SkyEvents.nextKeyEvents(
            SkyEvents.forDay(
                madrid,
                Ephemeris.sunDay(madrid, date.plusDays(1), madridZone),
                Ephemeris.moonDay(madrid, date.plusDays(1), madridZone),
            ),
            after = after,
        ).take(3)
        assertEquals(tomorrowExpected, upcoming.map { it.event })
    }

    @Test
    fun `Madrid 2026-11-20 at 12_00 returns today remaining events without computing tomorrow`() {
        val date = LocalDate.of(2026, 11, 20)
        val after = at("2026-11-20T12:00", madridZone)
        val todayEvents = SkyEvents.forDay(
            madrid,
            Ephemeris.sunDay(madrid, date, madridZone),
            Ephemeris.moonDay(madrid, date, madridZone),
        )

        val upcoming = SkyEvents.nextKeyEventsOrTomorrow(
            todayEvents = todayEvents,
            after = after,
            limit = 3,
            tomorrowEvents = { fail("tomorrowEvents should not be computed when today has remaining events"); emptyList() },
        )

        assertEquals(3, upcoming.size)
        assertTrue(upcoming.all { !it.isNextDay })
        assertTrue(upcoming.all { it.event.instant.isAfter(after) })
        assertTrue(
            upcoming.all { it.event.instant.atZone(madridZone).toLocalDate() == date },
        )
        assertEquals(upcoming.sortedBy { it.event.instant }, upcoming)
    }

    @Test
    fun `polar Tromso 2026-12-21 with no sunrise returns twilight events and falls back to tomorrow cleanly`() {
        val date = LocalDate.of(2026, 12, 21)

        // Daytime on polar night: returns today's available events (twilights/solar noon/moon), no sunrise/sunset
        val morning = at("2026-12-21T06:00", tromsoZone)
        val dayUpcoming = SkyEvents.nextKeyEventsOrTomorrow(
            place = tromso,
            selectedDate = date,
            zone = tromsoZone,
            after = morning,
            limit = 3,
        )
        assertTrue(dayUpcoming.isNotEmpty())
        assertTrue(dayUpcoming.all { !it.isNextDay })
        assertFalse(dayUpcoming.any { it.event.kind == SkyEventKind.SUNRISE || it.event.kind == SkyEventKind.SUNSET })
        assertEquals(dayUpcoming.sortedBy { it.event.instant }, dayUpcoming)

        // Late night after all events of Dec 21: falls back to Dec 22 without crashing
        val lateNight = at("2026-12-21T23:55", tromsoZone)
        val nextDayUpcoming = SkyEvents.nextKeyEventsOrTomorrow(
            place = tromso,
            selectedDate = date,
            zone = tromsoZone,
            after = lateNight,
            limit = 3,
        )
        assertTrue(nextDayUpcoming.isNotEmpty())
        assertTrue(nextDayUpcoming.all { it.isNextDay })
        assertTrue(
            nextDayUpcoming.all { it.event.instant.atZone(tromsoZone).toLocalDate() == LocalDate.of(2026, 12, 22) },
        )
        assertFalse(nextDayUpcoming.any { it.event.kind == SkyEventKind.SUNRISE || it.event.kind == SkyEventKind.SUNSET })
        assertEquals(nextDayUpcoming.sortedBy { it.event.instant }, nextDayUpcoming)
    }

    @Test
    fun `DST spring transition day 2026-03-29 handles today and next-day fallback in Europe_Madrid`() {
        val dstDate = LocalDate.of(2026, 3, 29)

        val earlyMorning = at("2026-03-29T01:30", madridZone)
        val todayUpcoming = SkyEvents.nextKeyEventsOrTomorrow(
            place = madrid,
            selectedDate = dstDate,
            zone = madridZone,
            after = earlyMorning,
            limit = 3,
        )
        assertEquals(3, todayUpcoming.size)
        assertTrue(todayUpcoming.all { !it.isNextDay })
        assertTrue(todayUpcoming.all { it.event.instant.atZone(madridZone).toLocalDate() == dstDate })
        assertEquals(todayUpcoming.sortedBy { it.event.instant }, todayUpcoming)

        val lateNight = at("2026-03-29T23:00", madridZone)
        val tomorrowUpcoming = SkyEvents.nextKeyEventsOrTomorrow(
            place = madrid,
            selectedDate = dstDate,
            zone = madridZone,
            after = lateNight,
            limit = 3,
        )
        assertEquals(3, tomorrowUpcoming.size)
        assertTrue(tomorrowUpcoming.all { it.isNextDay })
        assertTrue(
            tomorrowUpcoming.all { it.event.instant.atZone(madridZone).toLocalDate() == LocalDate.of(2026, 3, 30) },
        )
        assertEquals(tomorrowUpcoming.sortedBy { it.event.instant }, tomorrowUpcoming)
    }

    @Test
    fun `empty today and tomorrow or zero limit return empty without crashing`() {
        val after = at("2026-11-20T21:00", madridZone)
        assertTrue(
            SkyEvents.nextKeyEventsOrTomorrow(
                todayEvents = emptyList(),
                after = after,
                limit = 3,
                tomorrowEvents = { emptyList() },
            ).isEmpty(),
        )
        assertTrue(
            SkyEvents.nextKeyEventsOrTomorrow(
                todayEvents = emptyList(),
                after = after,
                limit = 0,
                tomorrowEvents = { fail("limit 0 should not invoke tomorrowEvents"); emptyList() },
            ).isEmpty(),
        )
    }

    @Test
    fun `existing nextKeyEvents and nextKeyEvent API remains unchanged after last event of day`() {
        val date = LocalDate.of(2026, 11, 20)
        val after = at("2026-11-20T21:00", madridZone)
        val todayEvents = SkyEvents.forDay(
            madrid,
            Ephemeris.sunDay(madrid, date, madridZone),
            Ephemeris.moonDay(madrid, date, madridZone),
        )

        assertTrue(SkyEvents.nextKeyEvents(todayEvents, after).isEmpty())
        assertEquals(null, SkyEvents.nextKeyEvent(todayEvents, after))
    }
}
