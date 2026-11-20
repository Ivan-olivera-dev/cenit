package io.github.ivan_olivera_dev.cenit.core.sky

import io.github.ivan_olivera_dev.cenit.core.Ephemeris
import io.github.ivan_olivera_dev.cenit.core.Place
import io.github.ivan_olivera_dev.cenit.core.SunDay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.TreeSet

/**
 * Contiguous slice of a local calendar day belonging to a single [SkyPhase].
 *
 * [startFraction] and [endFraction] lie in `[0.0, 1.0]` relative to the local day window
 * `[local midnight, next local midnight)`, handling 23 h and 25 h DST days proportionally.
 */
data class DayRibbonSegment(
    val phase: SkyPhase,
    val start: Instant,
    val end: Instant,
    val startFraction: Double,
    val endFraction: Double,
)

/**
 * Partitions a local calendar day into contiguous [DayRibbonSegment]s using the Sun's
 * geometric elevation (unrefracted centre) and exact [SunDay] boundaries.
 */
object DayRibbon {
    private val COARSE_STEP: Duration = Duration.ofMinutes(15)
    private const val BISECT_TOLERANCE_MILLIS: Long = 1_000L

    /**
     * Computes contiguous day-ribbon segments for [date] in [zone] at [place].
     *
     * Exact transition instants from [sunDay] are preserved directly. For polar or high-latitude
     * days where a phase threshold is crossed even though a paired [SunDay] range is `null`
     * (for example midnight sun dipping below `+6°`), a coarse geometric-elevation scan detects
     * the transition and bisects it to within one second.
     */
    fun segments(
        place: Place,
        date: LocalDate,
        zone: ZoneId,
        sunDay: SunDay = Ephemeris.sunDay(place, date, zone),
    ): List<DayRibbonSegment> {
        val windowStart = date.atStartOfDay(zone).toInstant()
        val windowEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
        val totalMillis = Duration.between(windowStart, windowEnd).toMillis().toDouble()

        val boundaries = TreeSet<Instant>()
        boundaries.add(windowStart)
        boundaries.add(windowEnd)

        fun addIfInside(instant: Instant?) {
            if (instant != null && instant.isAfter(windowStart) && instant.isBefore(windowEnd)) {
                boundaries.add(instant)
            }
        }

        addIfInside(sunDay.astronomicalDawn)
        addIfInside(sunDay.nauticalDawn)
        addIfInside(sunDay.civilDawn)
        addIfInside(sunDay.blueMorning?.start)
        addIfInside(sunDay.blueMorning?.end)
        addIfInside(sunDay.goldenMorning?.start)
        addIfInside(sunDay.goldenMorning?.end)
        addIfInside(sunDay.goldenEvening?.start)
        addIfInside(sunDay.goldenEvening?.end)
        addIfInside(sunDay.blueEvening?.start)
        addIfInside(sunDay.blueEvening?.end)
        addIfInside(sunDay.civilDusk)
        addIfInside(sunDay.nauticalDusk)
        addIfInside(sunDay.astronomicalDusk)

        // Ensure polar/high-latitude transitions not captured in paired SunDay ranges are found.
        findMissingTransitions(place, windowStart, windowEnd, boundaries)

        val sorted = boundaries.toList()
        val merged = mutableListOf<Triple<SkyPhase, Instant, Instant>>()
        for (i in 0 until sorted.lastIndex) {
            val a = sorted[i]
            val b = sorted[i + 1]
            if (!b.isAfter(a)) continue
            val mid = a.plusMillis(Duration.between(a, b).toMillis() / 2)
            val phase = SkyPhase.at(place, mid)
            val prev = merged.lastOrNull()
            if (prev != null && prev.first == phase) {
                merged[merged.lastIndex] = Triple(phase, prev.second, b)
            } else {
                merged.add(Triple(phase, a, b))
            }
        }

        return merged.mapIndexed { index, (phase, start, end) ->
            val startFraction = if (index == 0) {
                0.0
            } else {
                Duration.between(windowStart, start).toMillis() / totalMillis
            }
            val endFraction = if (index == merged.lastIndex) {
                1.0
            } else {
                Duration.between(windowStart, end).toMillis() / totalMillis
            }
            DayRibbonSegment(
                phase = phase,
                start = start,
                end = end,
                startFraction = startFraction,
                endFraction = endFraction,
            )
        }
    }

    private fun findMissingTransitions(
        place: Place,
        windowStart: Instant,
        windowEnd: Instant,
        boundaries: TreeSet<Instant>,
    ) {
        val grid = TreeSet<Instant>(boundaries)
        var cursor = windowStart
        while (cursor.isBefore(windowEnd)) {
            grid.add(cursor)
            cursor = cursor.plus(COARSE_STEP)
        }
        grid.add(windowEnd)

        val points = grid.toList()
        for (i in 0 until points.lastIndex) {
            val a = points[i]
            val b = points[i + 1]
            val spanMillis = Duration.between(a, b).toMillis()
            if (spanMillis <= BISECT_TOLERANCE_MILLIS * 2) continue
            // Sample slightly inside each endpoint so exact-boundary points do not trigger false splits.
            val inset = minOf(BISECT_TOLERANCE_MILLIS, spanMillis / 4)
            val innerA = a.plusMillis(inset)
            val innerB = b.minusMillis(inset)
            if (!innerB.isAfter(innerA)) continue
            val phaseA = SkyPhase.at(place, innerA)
            val phaseB = SkyPhase.at(place, innerB)
            if (phaseA != phaseB) {
                bisectInterval(place, innerA, phaseA, innerB, phaseB, boundaries)
            }
        }
    }

    private fun bisectInterval(
        place: Place,
        a: Instant,
        phaseA: SkyPhase,
        b: Instant,
        phaseB: SkyPhase,
        boundaries: TreeSet<Instant>,
    ) {
        if (phaseA == phaseB) return
        val spanMillis = Duration.between(a, b).toMillis()
        if (spanMillis <= BISECT_TOLERANCE_MILLIS) {
            boundaries.add(a.plusMillis(spanMillis / 2))
            return
        }
        val mid = a.plusMillis(spanMillis / 2)
        val phaseMid = SkyPhase.at(place, mid)
        if (phaseMid != phaseA) {
            bisectInterval(place, a, phaseA, mid, phaseMid, boundaries)
        }
        if (phaseMid != phaseB) {
            bisectInterval(place, mid, phaseMid, b, phaseB, boundaries)
        }
    }
}
