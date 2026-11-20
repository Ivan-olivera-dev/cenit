package io.github.ivan_olivera_dev.cenit.core.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

class CenitFormatTest {
    private val madridZone = ZoneId.of("Europe/Madrid")
    private val esLocale = Locale.forLanguageTag("es-ES")
    private val enLocale = Locale.US

    private fun at(isoLocal: String, zone: ZoneId = madridZone): Instant =
        LocalDateTime.parse(isoLocal).atZone(zone).toInstant()

    // --- Single time: H24 and H12, minute-truncated (L14) ---

    @Test
    fun `H24 time formatting truncates seconds and never rounds up`() {
        assertEquals("07:22", CenitFormat.formatTime(at("2026-11-21T07:22:00"), madridZone, ClockFormat.H24, esLocale))
        assertEquals("07:22", CenitFormat.formatTime(at("2026-11-21T07:22:59").plusMillis(999), madridZone, ClockFormat.H24, esLocale))
        assertEquals("00:00", CenitFormat.formatTime(at("2026-11-21T00:00:59"), madridZone, ClockFormat.H24, enLocale))
        assertEquals("12:00", CenitFormat.formatTime(at("2026-11-21T12:00:59"), madridZone, ClockFormat.H24, enLocale))
        assertEquals("23:59", CenitFormat.formatTime(at("2026-11-21T23:59:59"), madridZone, ClockFormat.H24, enLocale))
        assertEquals("07:22", CenitFormat.formatLocalTime(LocalTime.of(7, 22, 59, 999_000_000), ClockFormat.H24, esLocale))
    }

    @Test
    fun `H12 time formatting truncates to the minute and pins exact English strings`() {
        assertEquals("12:00 AM", CenitFormat.formatTime(at("2026-11-21T00:00:59"), madridZone, ClockFormat.H12, enLocale))
        assertEquals("12:05 AM", CenitFormat.formatTime(at("2026-11-21T00:05:45"), madridZone, ClockFormat.H12, enLocale))
        assertEquals("7:22 AM", CenitFormat.formatTime(at("2026-11-21T07:22:59"), madridZone, ClockFormat.H12, enLocale))
        assertEquals("12:00 PM", CenitFormat.formatTime(at("2026-11-21T12:00:59"), madridZone, ClockFormat.H12, enLocale))
        assertEquals("12:30 PM", CenitFormat.formatTime(at("2026-11-21T12:30:40"), madridZone, ClockFormat.H12, enLocale))
        assertEquals("4:20 PM", CenitFormat.formatTime(at("2026-11-21T16:20:59"), madridZone, ClockFormat.H12, enLocale))
        assertEquals("11:59 PM", CenitFormat.formatTime(at("2026-11-21T23:59:59"), madridZone, ClockFormat.H12, enLocale))
        assertEquals("7:22 AM", CenitFormat.formatLocalTime(LocalTime.of(7, 22, 59), ClockFormat.H12, Locale.ENGLISH))
    }

    @Test
    fun `H12 time formatting truncates to the minute and pins exact Spanish strings`() {
        // JDK CLDR for Spanish uses non-breaking space (U+00A0) inside "a.\u00A0m." / "p.\u00A0m."
        val am = "a.\u00A0m."
        val pm = "p.\u00A0m."
        assertEquals("12:00 $am", CenitFormat.formatTime(at("2026-11-21T00:00:59"), madridZone, ClockFormat.H12, esLocale))
        assertEquals("7:22 $am", CenitFormat.formatTime(at("2026-11-21T07:22:59"), madridZone, ClockFormat.H12, esLocale))
        assertEquals("12:00 $pm", CenitFormat.formatTime(at("2026-11-21T12:00:59"), madridZone, ClockFormat.H12, esLocale))
        assertEquals("4:20 $pm", CenitFormat.formatTime(at("2026-11-21T16:20:59"), madridZone, ClockFormat.H12, esLocale))
        assertEquals("11:59 $pm", CenitFormat.formatTime(at("2026-11-21T23:59:59"), madridZone, ClockFormat.H12, esLocale))
        assertEquals("7:22 $am", CenitFormat.formatLocalTime(LocalTime.of(7, 22, 59), ClockFormat.H12, Locale.forLanguageTag("es")))
    }

    // --- Time ranges ---

    @Test
    fun `time range formatting uses en-dash and collapses repeated AM or PM period in H12`() {
        val am = "a.\u00A0m."
        val pm = "p.\u00A0m."

        // H24
        assertEquals(
            "07:32–08:31",
            CenitFormat.formatTimeRange(
                at("2026-11-21T07:32:59"),
                at("2026-11-21T08:31:59"),
                madridZone,
                ClockFormat.H24,
                esLocale,
            ),
        )

        // H12 same period (morning): collapse AM/a. m. on start
        assertEquals(
            "7:32–8:31 AM",
            CenitFormat.formatTimeRange(
                at("2026-11-21T07:32:59"),
                at("2026-11-21T08:31:59"),
                madridZone,
                ClockFormat.H12,
                enLocale,
            ),
        )
        assertEquals(
            "7:32–8:31 $am",
            CenitFormat.formatTimeRange(
                at("2026-11-21T07:32:59"),
                at("2026-11-21T08:31:59"),
                madridZone,
                ClockFormat.H12,
                esLocale,
            ),
        )

        // H12 same period (evening): collapse PM/p. m. on start
        assertEquals(
            "5:12–6:10 PM",
            CenitFormat.formatTimeRange(
                at("2026-11-21T17:12:40"),
                at("2026-11-21T18:10:55"),
                madridZone,
                ClockFormat.H12,
                enLocale,
            ),
        )
        assertEquals(
            "5:12–6:10 $pm",
            CenitFormat.formatTimeRange(
                at("2026-11-21T17:12:40"),
                at("2026-11-21T18:10:55"),
                madridZone,
                ClockFormat.H12,
                esLocale,
            ),
        )

        // H12 crossing AM -> PM: keep both day periods
        assertEquals(
            "9:47 AM–12:08 PM",
            CenitFormat.formatTimeRange(
                at("2027-08-02T09:47:59"),
                at("2027-08-02T12:08:59"),
                madridZone,
                ClockFormat.H12,
                enLocale,
            ),
        )
        assertEquals(
            "9:47 $am–12:08 $pm",
            CenitFormat.formatTimeRange(
                at("2027-08-02T09:47:59"),
                at("2027-08-02T12:08:59"),
                madridZone,
                ClockFormat.H12,
                esLocale,
            ),
        )

        // Range parts match for template reuse (e.g. eclipse phase strings)
        assertEquals(
            "7:32" to "8:31 AM",
            CenitFormat.formatTimeRangeParts(
                at("2026-11-21T07:32:00"),
                at("2026-11-21T08:31:00"),
                madridZone,
                ClockFormat.H12,
                enLocale,
            ),
        )
        assertEquals(
            "9:47 AM" to "12:08 PM",
            CenitFormat.formatTimeRangeParts(
                at("2027-08-02T09:47:00"),
                at("2027-08-02T12:08:00"),
                madridZone,
                ClockFormat.H12,
                enLocale,
            ),
        )
    }

    // --- Chart axis hours and dome hour dots ---

    @Test
    fun `chart axis hours and dome hours adapt to H24 and H12`() {
        val am = "a.\u00A0m."
        val pm = "p.\u00A0m."

        assertEquals(
            listOf("00", "06", "12", "18", "24"),
            listOf(0, 6, 12, 18, 24).map { CenitFormat.formatAxisHour(it, ClockFormat.H24, esLocale) },
        )
        assertEquals(
            listOf("12 AM", "6 AM", "12 PM", "6 PM", "12 AM"),
            listOf(0, 6, 12, 18, 24).map { CenitFormat.formatAxisHour(it, ClockFormat.H12, enLocale) },
        )
        assertEquals(
            listOf("12 $am", "6 $am", "12 $pm", "6 $pm", "12 $am"),
            listOf(0, 6, 12, 18, 24).map { CenitFormat.formatAxisHour(it, ClockFormat.H12, esLocale) },
        )

        assertEquals(
            listOf("6", "9", "12", "15", "18", "21"),
            listOf(6, 9, 12, 15, 18, 21).map { CenitFormat.formatDomeHour(it, ClockFormat.H24) },
        )
        assertEquals(
            listOf("6", "9", "12", "3", "6", "9"),
            listOf(6, 9, 12, 15, 18, 21).map { CenitFormat.formatDomeHour(it, ClockFormat.H12) },
        )
        assertEquals("12", CenitFormat.formatDomeHour(0, ClockFormat.H12))
    }

    // --- Angles with/without tenths, rounding half up, negative zero ---

    @Test
    fun `angle formatting supports tenths or whole degrees with half-up rounding and no negative zero`() {
        // Spanish decimal comma
        assertEquals("13,4°", CenitFormat.formatAngle(13.4, withDecimals = true, esLocale))
        assertEquals("13°", CenitFormat.formatAngle(13.4, withDecimals = false, esLocale))

        // English decimal point
        assertEquals("13.4°", CenitFormat.formatAngle(13.4, withDecimals = true, enLocale))
        assertEquals("13°", CenitFormat.formatAngle(13.4, withDecimals = false, enLocale))

        // Rounding half up (away from zero, NOT half-to-even)
        assertEquals("13°", CenitFormat.formatAngle(12.5, withDecimals = false, esLocale))
        assertEquals("14°", CenitFormat.formatAngle(13.5, withDecimals = false, esLocale))
        assertEquals("12,3°", CenitFormat.formatAngle(12.25, withDecimals = true, esLocale))
        assertEquals("12.4°", CenitFormat.formatAngle(12.35, withDecimals = true, enLocale))
        assertEquals("−13°", CenitFormat.formatAngle(-12.5, withDecimals = false, esLocale))
        assertEquals("−1°", CenitFormat.formatAngle(-0.5, withDecimals = false, esLocale))

        // Negative values rounding to zero must show "0°" / "0,0°", never "−0°"
        assertEquals("0°", CenitFormat.formatAngle(-0.4, withDecimals = false, esLocale))
        assertEquals("0°", CenitFormat.formatAngle(-0.49, withDecimals = false, enLocale))
        assertEquals("0,0°", CenitFormat.formatAngle(-0.04, withDecimals = true, esLocale))
        assertEquals("0.0°", CenitFormat.formatAngle(-0.04, withDecimals = true, enLocale))
        assertEquals("0°", CenitFormat.formatAngle(-0.0, withDecimals = false, esLocale))

        // Negative angles use typographic minus U+2212
        assertEquals("−0,4°", CenitFormat.formatAngle(-0.4, withDecimals = true, esLocale))
        assertEquals("−4.0°", CenitFormat.formatAngle(-4.0, withDecimals = true, enLocale))

        // Value-only helper omits trailing degree sign
        assertEquals("13,4", CenitFormat.formatAngleValue(13.4, withDecimals = true, esLocale))
        assertEquals("13", CenitFormat.formatAngleValue(13.4, withDecimals = false, esLocale))
        assertEquals("0", CenitFormat.formatAngleValue(-0.4, withDecimals = false, esLocale))
    }
}
