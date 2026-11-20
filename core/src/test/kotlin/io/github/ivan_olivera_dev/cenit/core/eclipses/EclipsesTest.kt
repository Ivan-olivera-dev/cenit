package io.github.ivan_olivera_dev.cenit.core.eclipses

import io.github.ivan_olivera_dev.cenit.core.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs

class EclipsesTest {
    private val madrid = Place(latitude = 40.4165, longitude = -3.7026)
    private val tarifa = Place(latitude = 36.0143, longitude = -5.6044)
    private val from2026 = Instant.parse("2026-11-20T00:00:00Z")

    /**
     * Converts a NASA GSFC L30 Dynamical Time (TD) timestamp to approximate UT (UT ≈ TD − 70 s).
     */
    private fun tdToApproxUt(isoTd: String): Instant =
        Instant.parse(isoTd).minusSeconds(70L)

    private fun assertWithinMinutes(expected: Instant, actual: Instant, maxMinutes: Long, label: String) {
        val deltaSeconds = abs(Duration.between(expected, actual).seconds)
        assertTrue(
            "$label: expected within ±$maxMinutes min of $expected, got $actual (delta=${deltaSeconds}s)",
            deltaSeconds <= maxMinutes * 60L,
        )
    }

    @Test
    fun `global solar eclipses match L30 NASA reference values 2027 to 2029`() {
        val globals = Eclipses.globalSolar(from = from2026, count = 5, horizonYears = 5)
        assertEquals(5, globals.size)

        // L30: 2027-02-06 16:00:47 TD annular
        assertEquals(EclipseKind.ANNULAR, globals[0].kind)
        assertWithinMinutes(tdToApproxUt("2027-02-06T16:00:47Z"), globals[0].peak, 3, "2027-02-06 solar")

        // L30: 2027-08-02 10:07:49 TD total
        assertEquals(EclipseKind.TOTAL, globals[1].kind)
        assertWithinMinutes(tdToApproxUt("2027-08-02T10:07:49Z"), globals[1].peak, 3, "2027-08-02 solar")
        assertEquals(1.0, globals[1].obscuration!!, 1e-9)

        // L30: 2028-01-26 15:08:58 TD annular
        assertEquals(EclipseKind.ANNULAR, globals[2].kind)
        assertWithinMinutes(tdToApproxUt("2028-01-26T15:08:58Z"), globals[2].peak, 3, "2028-01-26 solar")

        // L30: 2028-07-22 02:56:39 TD total (Australia)
        assertEquals(EclipseKind.TOTAL, globals[3].kind)
        assertWithinMinutes(tdToApproxUt("2028-07-22T02:56:39Z"), globals[3].peak, 3, "2028-07-22 solar")

        // L30: 2029-01-14 17:13:47 TD partial
        assertEquals(EclipseKind.PARTIAL, globals[4].kind)
        assertWithinMinutes(tdToApproxUt("2029-01-14T17:13:47Z"), globals[4].peak, 3, "2029-01-14 solar")
        assertNull(globals[4].obscuration)
    }

    @Test
    fun `lunar eclipses match L30 NASA reference kinds, times, and phase ranges`() {
        val all = Eclipses.upcoming(
            place = madrid,
            from = from2026,
            count = 20,
            horizonYears = 4,
            visibleOnly = false,
        )
        val lunar = all.filter { it.body == EclipseBody.MOON }

        // L30: 2027-02-20 23:14:06 TD penumbral
        val penumbral2027 = lunar.first { it.peak.atOffset(ZoneOffset.UTC).toLocalDate() == LocalDate.of(2027, 2, 20) }
        assertEquals(EclipseKind.PENUMBRAL, penumbral2027.kind)
        assertWithinMinutes(tdToApproxUt("2027-02-20T23:14:06Z"), penumbral2027.peak, 3, "2027-02-20 lunar")
        assertNotNull(penumbral2027.penumbralRange)
        assertNull(penumbral2027.partialRange)
        assertNull(penumbral2027.totalRange)

        // L30: 2028-01-12 04:14:13 TD partial
        val partial2028 = lunar.first { it.peak.atOffset(ZoneOffset.UTC).toLocalDate() == LocalDate.of(2028, 1, 12) }
        assertEquals(EclipseKind.PARTIAL, partial2028.kind)
        assertWithinMinutes(tdToApproxUt("2028-01-12T04:14:13Z"), partial2028.peak, 3, "2028-01-12 lunar")
        assertNotNull(partial2028.penumbralRange)
        assertNotNull(partial2028.partialRange)
        assertNull(partial2028.totalRange)
        assertTrue(partial2028.obscuration!! > 0.0 && partial2028.obscuration!! < 1.0)
        assertTrue(partial2028.partialRange!!.start.isBefore(partial2028.peak))
        assertTrue(partial2028.partialRange!!.end.isAfter(partial2028.peak))

        // L30: 2028-12-31 16:53:15 TD total
        val total2028 = lunar.first { it.peak.atOffset(ZoneOffset.UTC).toLocalDate() == LocalDate.of(2028, 12, 31) }
        assertEquals(EclipseKind.TOTAL, total2028.kind)
        assertWithinMinutes(tdToApproxUt("2028-12-31T16:53:15Z"), total2028.peak, 3, "2028-12-31 lunar")
        assertEquals(1.0, total2028.obscuration!!, 1e-9)
        val pen = total2028.penumbralRange!!
        val part = total2028.partialRange!!
        val tot = total2028.totalRange!!
        assertTrue(pen.start.isBefore(part.start))
        assertTrue(part.start.isBefore(tot.start))
        assertTrue(tot.start.isBefore(total2028.peak))
        assertTrue(total2028.peak.isBefore(tot.end))
        assertTrue(tot.end.isBefore(part.end))
        assertTrue(part.end.isBefore(pen.end))
    }

    @Test
    fun `madrid sees 2027-08-02 as deep partial solar eclipse and excludes Australia 2028-07-22 when visibleOnly`() {
        val visible = Eclipses.upcoming(
            place = madrid,
            from = from2026,
            count = 15,
            horizonYears = 5,
            visibleOnly = true,
        )
        assertTrue(visible.all { it.visible })

        val aug2027 = visible.firstOrNull {
            it.body == EclipseBody.SUN && it.peak.atOffset(ZoneOffset.UTC).toLocalDate() == LocalDate.of(2027, 8, 2)
        }
        assertNotNull("Madrid should see the 2027-08-02 solar eclipse", aug2027)
        assertEquals(EclipseKind.PARTIAL, aug2027!!.kind)
        assertTrue("Madrid obscuration should be > 0.8 and < 1.0, was ${aug2027.obscuration}",
            aug2027.obscuration!! > 0.8 && aug2027.obscuration!! < 1.0)
        assertNotNull(aug2027.partialRange)
        assertNull(aug2027.totalRange)
        assertTrue(aug2027.partialRange!!.start.isBefore(aug2027.peak))
        assertTrue(aug2027.partialRange!!.end.isAfter(aug2027.peak))
        assertTrue(aug2027.peakElevationDeg > 0.0)

        // Australia 2028-07-22 must NOT appear when visibleOnly = true
        val jul2028Visible = visible.firstOrNull {
            it.body == EclipseBody.SUN && it.peak.atOffset(ZoneOffset.UTC).toLocalDate() == LocalDate.of(2028, 7, 22)
        }
        assertNull("2028-07-22 solar eclipse (Australia) must be excluded in Madrid when visibleOnly = true", jul2028Visible)

        // When visibleOnly = false, 2028-07-22 DOES appear with visible = false
        val all = Eclipses.upcoming(
            place = madrid,
            from = from2026,
            count = 20,
            horizonYears = 5,
            visibleOnly = false,
        )
        val jul2028Global = all.firstOrNull {
            it.body == EclipseBody.SUN && it.peak.atOffset(ZoneOffset.UTC).toLocalDate() == LocalDate.of(2028, 7, 22)
        }
        assertNotNull("2028-07-22 solar eclipse should appear when visibleOnly = false", jul2028Global)
        assertFalse(jul2028Global!!.visible)
        assertEquals(EclipseKind.TOTAL, jul2028Global.kind)
        assertNull(jul2028Global.partialRange)
    }

    @Test
    fun `madrid sees the 2028-12-31 total lunar eclipse as an eclipsed moonrise`() {
        val all = Eclipses.upcoming(
            place = madrid,
            from = from2026,
            count = 20,
            horizonYears = 4,
            visibleOnly = false,
        )
        val dec2028 = all.first {
            it.body == EclipseBody.MOON && it.peak.atOffset(ZoneOffset.UTC).toLocalDate() == LocalDate.of(2028, 12, 31)
        }
        // At 16:52 UT in Madrid (40.4165°N, -3.7026°W), the Moon is still ~0.37° below the horizon,
        // but it rises about a minute later, in the middle of totality: an eclipsed moonrise, so the
        // eclipse is visible (visible = the Moon is up at some point of the eclipse, like the solar rule).
        assertTrue(dec2028.visible)
        assertTrue(
            "Expected Moon just below horizon in Madrid at 2028-12-31 peak, elevation=${dec2028.peakElevationDeg}°",
            dec2028.peakElevationDeg < 0.0 && dec2028.peakElevationDeg > -1.0,
        )
        val elevAtTotalEnd = io.github.ivan_olivera_dev.cenit.core.Ephemeris.moonPosition(madrid, dec2028.totalRange!!.end).elevation
        assertTrue("Moon rises during totality in Madrid (elev at totalEnd=$elevAtTotalEnd°)", elevAtTotalEnd > 0.0)
    }

    @Test
    fun `short horizon returns only visible eclipses inside the window`() {
        val oneYear = Eclipses.upcoming(
            place = madrid,
            from = Instant.parse("2026-11-20T00:00:00Z"),
            count = 4,
            horizonYears = 1,
            visibleOnly = true,
        )
        val cutoff = Instant.parse("2027-11-20T00:00:00Z")
        assertTrue(oneYear.all { !it.peak.isAfter(cutoff) && it.visible })
        // Within 1 year from 2026-11-20, Madrid sees 2027-02-20 penumbral lunar + 2027-08-02 partial solar.
        // Not 2027-08-17 penumbral: the Moon is 0.5° up only at the start of the penumbral phase,
        // when no shading is visible, and sets before the peak.
        assertEquals(2, oneYear.size)
    }

    @Test
    fun `tarifa lies in the 2027-08-02 totality path and sees local kind TOTAL`() {
        val visible = Eclipses.upcoming(
            place = tarifa,
            from = from2026,
            count = 10,
            horizonYears = 5,
            visibleOnly = true,
        )
        val aug2027 = visible.firstOrNull {
            it.body == EclipseBody.SUN && it.peak.atOffset(ZoneOffset.UTC).toLocalDate() == LocalDate.of(2027, 8, 2)
        }
        assertNotNull("Tarifa should see the 2027-08-02 solar eclipse", aug2027)
        assertEquals(EclipseKind.TOTAL, aug2027!!.kind)
        assertEquals(1.0, aug2027.obscuration!!, 1e-9)
        assertNotNull(aug2027.partialRange)
        assertNotNull(aug2027.totalRange)
        assertTrue(aug2027.partialRange!!.start.isBefore(aug2027.totalRange!!.start))
        assertTrue(aug2027.totalRange!!.start.isBefore(aug2027.peak))
        assertTrue(aug2027.peak.isBefore(aug2027.totalRange!!.end))
        assertTrue(aug2027.totalRange!!.end.isBefore(aug2027.partialRange!!.end))
    }

    @Test
    fun `upcoming merges solar and lunar eclipses in ascending time order within horizon and count`() {
        val results = Eclipses.upcoming(
            place = madrid,
            from = from2026,
            count = 6,
            horizonYears = 10,
            visibleOnly = true,
        )
        assertEquals(6, results.size)
        for (i in 1 until results.size) {
            assertFalse(
                "Eclipses must be sorted by peak ascending: ${results[i - 1].peak} vs ${results[i].peak}",
                results[i].peak.isBefore(results[i - 1].peak),
            )
        }
        val horizonEnd = from2026.atOffset(ZoneOffset.UTC).plusYears(10).toInstant()
        assertTrue(results.all { !it.peak.isBefore(from2026) && !it.peak.isAfter(horizonEnd) })
        assertTrue("Expected both solar and lunar eclipses in top 6 for Madrid",
            results.any { it.body == EclipseBody.SUN } && results.any { it.body == EclipseBody.MOON })
    }

    @Test
    fun `upcoming validates count and horizonYears and handles zero count`() {
        assertTrue(Eclipses.upcoming(madrid, from2026, count = 0).isEmpty())
        assertTrue(Eclipses.globalSolar(from2026, count = 0).isEmpty())

        val badCount = runCatching { Eclipses.upcoming(madrid, from2026, count = -1) }.exceptionOrNull()
        assertTrue(badCount is IllegalArgumentException)

        val badHorizon = runCatching { Eclipses.upcoming(madrid, from2026, horizonYears = 0) }.exceptionOrNull()
        assertTrue(badHorizon is IllegalArgumentException)
    }
}
