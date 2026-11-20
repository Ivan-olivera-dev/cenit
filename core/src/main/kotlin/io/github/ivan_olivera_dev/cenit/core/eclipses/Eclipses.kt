package io.github.ivan_olivera_dev.cenit.core.eclipses

import io.github.cosinekitty.astronomy.EclipseKind as AstroEclipseKind
import io.github.cosinekitty.astronomy.LocalSolarEclipseInfo
import io.github.cosinekitty.astronomy.LunarEclipseInfo
import io.github.cosinekitty.astronomy.Observer
import io.github.cosinekitty.astronomy.Time
import io.github.cosinekitty.astronomy.nextGlobalSolarEclipse
import io.github.cosinekitty.astronomy.nextLocalSolarEclipse
import io.github.cosinekitty.astronomy.nextLunarEclipse
import io.github.cosinekitty.astronomy.searchGlobalSolarEclipse
import io.github.cosinekitty.astronomy.searchLocalSolarEclipse
import io.github.cosinekitty.astronomy.searchLunarEclipse
import io.github.ivan_olivera_dev.cenit.core.Ephemeris
import io.github.ivan_olivera_dev.cenit.core.Place
import io.github.ivan_olivera_dev.cenit.core.TimeRange
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Upcoming solar and lunar eclipse search backed by Astronomy Engine.
 */
object Eclipses {
    private val SEARCH_LOOKBACK: Duration = Duration.ofDays(1)
    private val GLOBAL_LOCAL_MATCH_WINDOW: Duration = Duration.ofHours(24)
    private val GLOBAL_MARGIN: Duration = Duration.ofHours(6)
    private const val MILLIS_PER_MINUTE = 60_000.0

    /**
     * Returns up to [count] upcoming solar and lunar eclipses for [place] with peak instant
     * in `[from, from + horizonYears]`, merged in ascending peak-time order.
     *
     * When [visibleOnly] is `true` (the default), only eclipses observable from [place] are returned:
     * - Solar: local eclipses where the Sun is above the horizon at peak or during partial phase.
     * - Lunar: lunar eclipses where the Moon's apparent centre is above the horizon at peak.
     *
     * When [visibleOnly] is `false`, non-visible global solar and lunar eclipses within the horizon
     * are also included with `visible = false`.
     */
    fun upcoming(
        place: Place,
        from: Instant,
        count: Int = 6,
        horizonYears: Int = 10,
        visibleOnly: Boolean = true,
    ): List<CenitEclipse> {
        require(count >= 0) { "count must be non-negative, was $count" }
        require(horizonYears > 0) { "horizonYears must be positive, was $horizonYears" }
        if (count == 0) return emptyList()

        val horizonEnd = from.atOffset(ZoneOffset.UTC).plusYears(horizonYears.toLong()).toInstant()
        val lunar = searchLunar(place, from, horizonEnd, count, visibleOnly)
        val solarCutoff = if (lunar.size == count) minOf(horizonEnd, lunar.last().peak) else horizonEnd
        val solar = if (visibleOnly) {
            searchLocalSolarVisible(place, from, solarCutoff, count)
        } else {
            searchAllSolar(place, from, solarCutoff, count)
        }

        return (solar + lunar)
            .sortedWith(compareBy<CenitEclipse> { it.peak }.thenBy { it.body })
            .take(count)
    }

    /**
     * Returns up to [count] upcoming global solar eclipses with peak instant in
     * `[from, from + horizonYears]`.
     */
    fun globalSolar(
        from: Instant,
        count: Int = 6,
        horizonYears: Int = 10,
    ): List<GlobalSolarEclipse> {
        require(count >= 0) { "count must be non-negative, was $count" }
        require(horizonYears > 0) { "horizonYears must be positive, was $horizonYears" }
        if (count == 0) return emptyList()

        val horizonEnd = from.atOffset(ZoneOffset.UTC).plusYears(horizonYears.toLong()).toInstant()
        val results = ArrayList<GlobalSolarEclipse>(count)
        var info = searchGlobalSolarEclipse(from.minus(SEARCH_LOOKBACK).toAstroTime())
        while (results.size < count) {
            val peak = info.peak.toInstant()
            if (peak.isAfter(horizonEnd)) break
            if (!peak.isBefore(from)) {
                results.add(
                    GlobalSolarEclipse(
                        kind = info.kind.toCenitKind(),
                        peak = peak,
                        obscuration = info.obscuration.takeIf { it.isFinite() },
                        latitude = info.latitude.takeIf { it.isFinite() },
                        longitude = info.longitude.takeIf { it.isFinite() },
                    ),
                )
            }
            info = nextGlobalSolarEclipse(info.peak)
        }
        return results
    }

    private fun searchLunar(
        place: Place,
        from: Instant,
        horizonEnd: Instant,
        count: Int,
        visibleOnly: Boolean,
    ): List<CenitEclipse> {
        val results = ArrayList<CenitEclipse>(count)
        var info = searchLunarEclipse(from.minus(SEARCH_LOOKBACK).toAstroTime())
        while (results.size < count) {
            val peak = info.peak.toInstant()
            if (peak.isAfter(horizonEnd)) break
            if (!peak.isBefore(from)) {
                val eclipse = info.toCenitEclipse(place)
                if (!visibleOnly || eclipse.visible) {
                    results.add(eclipse)
                }
            }
            info = nextLunarEclipse(info.peak)
        }
        return results
    }

    private fun searchLocalSolarVisible(
        place: Place,
        from: Instant,
        horizonEnd: Instant,
        count: Int,
    ): List<CenitEclipse> {
        val observer = place.toObserver()
        val results = ArrayList<CenitEclipse>(count)
        var info = searchLocalSolarEclipse(from.minus(SEARCH_LOOKBACK).toAstroTime(), observer)
        while (results.size < count) {
            val peak = info.peak.time.toInstant()
            if (peak.isAfter(horizonEnd)) break
            if (!peak.isBefore(from)) {
                val eclipse = info.toCenitEclipse()
                if (eclipse.visible) {
                    results.add(eclipse)
                }
            }
            info = nextLocalSolarEclipse(info.peak.time, observer)
        }
        return results
    }

    private fun searchAllSolar(
        place: Place,
        from: Instant,
        horizonEnd: Instant,
        count: Int,
    ): List<CenitEclipse> {
        val observer = place.toObserver()
        val localWindowEnd = horizonEnd.plus(SEARCH_LOOKBACK)
        val localCandidates = mutableListOf<CenitEclipse>()
        var localInfo = searchLocalSolarEclipse(from.minus(SEARCH_LOOKBACK).toAstroTime(), observer)
        while (true) {
            val localPeak = localInfo.peak.time.toInstant()
            if (localPeak.isAfter(localWindowEnd)) break
            val localEclipse = localInfo.toCenitEclipse()
            if (localEclipse.visible) {
                localCandidates.add(localEclipse)
            }
            localInfo = nextLocalSolarEclipse(localInfo.peak.time, observer)
        }

        val results = ArrayList<CenitEclipse>(count)
        val globalSearchEnd = horizonEnd.plus(GLOBAL_MARGIN)
        var globalInfo = searchGlobalSolarEclipse(from.minus(SEARCH_LOOKBACK).toAstroTime())
        while (results.size < count) {
            val globalPeak = globalInfo.peak.toInstant()
            if (globalPeak.isAfter(globalSearchEnd)) break
            val matchedLocal = localCandidates.firstOrNull { local ->
                abs(Duration.between(globalPeak, local.peak).toMillis()) < GLOBAL_LOCAL_MATCH_WINDOW.toMillis()
            }
            val candidate = matchedLocal ?: CenitEclipse(
                body = EclipseBody.SUN,
                kind = globalInfo.kind.toCenitKind(),
                peak = globalPeak,
                obscuration = globalInfo.obscuration.takeIf { it.isFinite() },
                visible = false,
                peakElevationDeg = Ephemeris.sunPosition(place, globalPeak).elevation,
            )
            if (!candidate.peak.isBefore(from) && !candidate.peak.isAfter(horizonEnd)) {
                results.add(candidate)
            }
            globalInfo = nextGlobalSolarEclipse(globalInfo.peak)
        }
        return results
    }

    private fun LunarEclipseInfo.toCenitEclipse(place: Place): CenitEclipse {
        val peakInstant = peak.toInstant()
        val moonElevation = Ephemeris.moonPosition(place, peakInstant).elevation
        val penumbralRange = rangeFromSemiDuration(peakInstant, sdPenum)
        val partialRange = rangeFromSemiDuration(peakInstant, sdPartial)
        // Visible when the Moon is up at the peak or at some point of the umbral (partial or total)
        // phase: an eclipsed moonrise or moonset counts, like the solar rule. Penumbral shading is
        // only noticeable near the peak, so a penumbral eclipse needs the Moon up at the peak.
        // Within a few hours the Moon cannot rise and set again, so both ends are enough.
        val visible = moonElevation > 0.0 || listOfNotNull(partialRange?.start, partialRange?.end)
            .any { Ephemeris.moonPosition(place, it).elevation > 0.0 }
        return CenitEclipse(
            body = EclipseBody.MOON,
            kind = kind.toCenitKind(),
            peak = peakInstant,
            obscuration = obscuration.takeIf { it.isFinite() },
            visible = visible,
            peakElevationDeg = moonElevation,
            penumbralRange = penumbralRange,
            partialRange = partialRange,
            totalRange = rangeFromSemiDuration(peakInstant, sdTotal),
        )
    }

    private fun LocalSolarEclipseInfo.toCenitEclipse(): CenitEclipse {
        val peakInstant = peak.time.toInstant()
        val isVisible = peak.altitude > 0.0 ||
            partialBegin.altitude > 0.0 ||
            partialEnd.altitude > 0.0
        val partial = TimeRange(partialBegin.time.toInstant(), partialEnd.time.toInstant())
        val totBegin = totalBegin
        val totEnd = totalEnd
        val total = if (totBegin != null && totEnd != null) {
            TimeRange(totBegin.time.toInstant(), totEnd.time.toInstant())
        } else {
            null
        }
        return CenitEclipse(
            body = EclipseBody.SUN,
            kind = kind.toCenitKind(),
            peak = peakInstant,
            obscuration = obscuration.takeIf { it.isFinite() },
            visible = isVisible,
            peakElevationDeg = peak.altitude,
            penumbralRange = null,
            partialRange = partial,
            totalRange = total,
        )
    }

    private fun rangeFromSemiDuration(peak: Instant, semiDurationMinutes: Double): TimeRange? {
        if (!semiDurationMinutes.isFinite() || semiDurationMinutes <= 0.0) return null
        val halfMillis = (semiDurationMinutes * MILLIS_PER_MINUTE).roundToLong()
        return TimeRange(peak.minusMillis(halfMillis), peak.plusMillis(halfMillis))
    }

    private fun AstroEclipseKind.toCenitKind(): EclipseKind = when (this) {
        AstroEclipseKind.Penumbral -> EclipseKind.PENUMBRAL
        AstroEclipseKind.Partial -> EclipseKind.PARTIAL
        AstroEclipseKind.Annular -> EclipseKind.ANNULAR
        AstroEclipseKind.Total -> EclipseKind.TOTAL
    }

    private fun Place.toObserver(): Observer = Observer(latitude, longitude, elevationMeters)

    private fun Instant.toAstroTime(): Time = Time.fromMillisecondsSince1970(toEpochMilli())

    private fun Time.toInstant(): Instant = Instant.ofEpochMilli(toMillisecondsSince1970())
}
