package io.github.ivan_olivera_dev.cenit.ui.sky

import io.github.ivan_olivera_dev.cenit.core.Place
import io.github.ivan_olivera_dev.cenit.core.TimeRange
import io.github.ivan_olivera_dev.cenit.core.eclipses.CenitEclipse
import io.github.ivan_olivera_dev.cenit.core.eclipses.EclipseBody
import io.github.ivan_olivera_dev.cenit.core.eclipses.EclipseKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class SkyEclipsesTest {
    private val madrid = Place(latitude = 40.4165, longitude = -3.7026)
    private val tarifa = Place(latitude = 36.0143, longitude = -5.6044)
    private val madridZone = ZoneId.of("Europe/Madrid")

    private fun sampleEclipse(
        body: EclipseBody,
        kind: EclipseKind,
        peakIso: String,
        obscuration: Double? = null,
        partialStartIso: String? = null,
        partialEndIso: String? = null,
    ): CenitEclipse = CenitEclipse(
        body = body,
        kind = kind,
        peak = Instant.parse(peakIso),
        obscuration = obscuration,
        visible = true,
        peakElevationDeg = 30.0,
        partialRange = if (partialStartIso != null && partialEndIso != null) {
            TimeRange(Instant.parse(partialStartIso), Instant.parse(partialEndIso))
        } else {
            null
        },
    )

    @Test
    fun `filterEclipsesForTab caps at 4 and filters by body for SUN and MOON`() {
        val mixed = listOf(
            sampleEclipse(EclipseBody.MOON, EclipseKind.PENUMBRAL, "2027-02-20T23:12:44Z", 0.0),
            sampleEclipse(EclipseBody.SUN, EclipseKind.PARTIAL, "2027-08-02T08:51:08Z", 0.862),
            sampleEclipse(EclipseBody.MOON, EclipseKind.PARTIAL, "2028-01-12T04:13:00Z", 0.027),
            sampleEclipse(EclipseBody.SUN, EclipseKind.PARTIAL, "2028-01-26T16:55:33Z", 0.823),
            sampleEclipse(EclipseBody.MOON, EclipseKind.TOTAL, "2029-06-26T03:22:06Z", 1.0),
            sampleEclipse(EclipseBody.MOON, EclipseKind.TOTAL, "2029-12-20T22:41:54Z", 1.0),
            sampleEclipse(EclipseBody.MOON, EclipseKind.PARTIAL, "2030-06-15T18:33:14Z", 0.47),
        )

        val all = filterEclipsesForTab(mixed, SkyTimesFilter.ALL)
        assertEquals(4, all.size)
        assertEquals(mixed.take(4), all)

        val sun = filterEclipsesForTab(mixed, SkyTimesFilter.SUN)
        assertEquals(2, sun.size)
        assertEquals(listOf(mixed[1], mixed[3]), sun)

        val moon = filterEclipsesForTab(mixed, SkyTimesFilter.MOON)
        assertEquals(4, moon.size)
        assertEquals(listOf(mixed[0], mixed[2], mixed[4], mixed[5]), moon)
    }

    @Test
    fun `eclipseObscurationPercent formats partial and annular only`() {
        assertEquals(86, eclipseObscurationPercent(sampleEclipse(EclipseBody.SUN, EclipseKind.PARTIAL, "2027-08-02T08:51:08Z", 0.8623)))
        assertEquals(85, eclipseObscurationPercent(sampleEclipse(EclipseBody.SUN, EclipseKind.ANNULAR, "2028-01-26T15:07:42Z", 0.8479)))
        assertEquals(3, eclipseObscurationPercent(sampleEclipse(EclipseBody.MOON, EclipseKind.PARTIAL, "2028-01-12T04:13:00Z", 0.0274)))
        assertNull(eclipseObscurationPercent(sampleEclipse(EclipseBody.SUN, EclipseKind.TOTAL, "2027-08-02T08:47:19Z", 1.0)))
        assertNull(eclipseObscurationPercent(sampleEclipse(EclipseBody.MOON, EclipseKind.TOTAL, "2029-06-26T03:22:06Z", 1.0)))
        assertNull(eclipseObscurationPercent(sampleEclipse(EclipseBody.MOON, EclipseKind.PENUMBRAL, "2027-02-20T23:12:44Z", 0.0)))
    }

    @Test
    fun `formatEclipseDate uses local zone date and strips month abbreviation dots`() {
        // 2027-02-20 23:12 UTC is 2027-02-21 00:12 CET in Europe/Madrid
        val feb = Instant.parse("2027-02-20T23:12:44Z")
        val esFeb = formatEclipseDate(feb, madridZone, Locale("es", "ES"))
        assertEquals("21 feb 2027", esFeb.lowercase(Locale("es", "ES")))

        val aug = Instant.parse("2027-08-02T08:51:08Z")
        val esAug = formatEclipseDate(aug, madridZone, Locale("es", "ES"))
        assertEquals("2 ago 2027", esAug.lowercase(Locale("es", "ES")))

        val enAug = formatEclipseDate(aug, madridZone, Locale.US)
        assertEquals("2 Aug 2027", enAug)
    }

    @Test
    fun `formatEclipseTimeRange truncates seconds in active zone`() {
        // 07:45:59Z -> 09:45 CEST, 10:02:59Z -> 12:02 CEST (truncated, never rounded up)
        val range = TimeRange(
            start = Instant.parse("2027-08-02T07:45:59Z"),
            end = Instant.parse("2027-08-02T10:02:59Z"),
        )
        assertEquals("09:45" to "12:02", formatEclipseTimeRange(range, madridZone))
    }

    @Test
    fun `SkyEclipseCache caches by place and evicts oldest beyond capacity`() {
        val cache = SkyEclipseCache(maxEntries = 2)
        val day = LocalDate.of(2026, 11, 20)
        val keyMadrid = EclipseCacheKey(madrid, day, "Europe/Madrid")
        val keyTarifa = EclipseCacheKey(tarifa, day, "Europe/Madrid")
        val keyOther = EclipseCacheKey(Place(0.0, 0.0), day, "UTC")

        var computations = 0
        val sample = listOf(sampleEclipse(EclipseBody.SUN, EclipseKind.PARTIAL, "2027-08-02T08:51:08Z", 0.862))

        val first = cache.getOrCompute(keyMadrid) {
            computations++
            sample
        }
        val second = cache.getOrCompute(keyMadrid) {
            computations++
            emptyList()
        }
        assertEquals(1, computations)
        assertEquals(first, second)

        cache.getOrCompute(keyTarifa) {
            computations++
            emptyList()
        }
        // Adding a 3rd entry evicts the least-recently-used (Madrid)
        cache.getOrCompute(keyOther) {
            computations++
            emptyList()
        }
        assertNull(cache.get(keyMadrid))
        assertEquals(emptyList<CenitEclipse>(), cache.get(keyTarifa))
        assertEquals(emptyList<CenitEclipse>(), cache.get(keyOther))
    }
}
