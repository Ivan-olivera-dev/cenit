package io.github.ivan_olivera_dev.cenit.core.sky.chart

import io.github.ivan_olivera_dev.cenit.core.Ephemeris
import io.github.ivan_olivera_dev.cenit.core.PathPoint
import io.github.ivan_olivera_dev.cenit.core.Place
import io.github.ivan_olivera_dev.cenit.core.SunDay
import io.github.ivan_olivera_dev.cenit.core.sky.SkyPhase
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil
import kotlin.math.roundToLong

/**
 * Horizontal sky-phase bands drawn behind the elevation chart curves (`elevation-chart` token).
 */
enum class ChartSkyBand {
    /** Sun above the horizon (`0° … yMax`). */
    DAY,

    /** Civil twilight (`0° … −6°`). */
    CIVIL,

    /** Nautical twilight (`−6° … −12°`). */
    NAUTICAL,

    /** Astronomical twilight (`−12° … −18°`). */
    ASTRONOMICAL,

    /** Night below astronomical twilight (`−18° … −20°`). */
    NIGHT,
}

/**
 * Vertical slice of the elevation chart plot belonging to one [ChartSkyBand],
 * expressed in normalized plot fractions `[0.0, 1.0]` where `0.0` is the top (`yMax`)
 * and `1.0` is the bottom (`yMin`).
 */
data class ChartBandRect(
    val band: ChartSkyBand,
    val upperElevationDeg: Double,
    val lowerElevationDeg: Double,
    val topFraction: Double,
    val bottomFraction: Double,
) {
    val heightFraction: Double get() = bottomFraction - topFraction
}

/**
 * Sampled celestial body point projected onto normalized chart coordinates.
 *
 * [xFraction] is in `[0.0, 1.0]` across the local day window. [yFraction] is unclamped
 * so paths dipping below `−20°` interpolate accurately to the bottom clip edge.
 */
data class ChartPlotPoint(
    val instant: Instant,
    val xFraction: Double,
    val yFraction: Double,
    val elevationDeg: Double,
    val azimuthDeg: Double,
)

/**
 * Horizontal hour tick positioned in normalized day coordinates (`[0.0, 1.0]`).
 */
data class ChartHourTick(
    val label: String,
    val xFraction: Double,
)

/**
 * Vertical elevation scale for the elevation chart (`yMinDeg = −20°` to `yMaxDeg`).
 */
data class ElevationChartScale(
    val yMinDeg: Double = MIN_ELEVATION_DEG,
    val yMaxDeg: Double,
) {
    init {
        require(yMaxDeg > yMinDeg) { "yMaxDeg ($yMaxDeg) must exceed yMinDeg ($yMinDeg)" }
    }

    /** Normalized Y fraction of the `0°` horizon line. */
    val horizonYFraction: Double get() = yFraction(0.0)

    /** Maps an elevation in degrees to a normalized Y fraction (`yMax -> 0.0`, `yMin -> 1.0`). */
    fun yFraction(elevationDeg: Double): Double =
        (yMaxDeg - elevationDeg) / (yMaxDeg - yMinDeg)

    /** Maps an elevation in degrees to a normalized Y fraction clamped to `[0.0, 1.0]`. */
    fun yFractionClamped(elevationDeg: Double): Double =
        yFraction(elevationDeg).coerceIn(0.0, 1.0)

    /**
     * Produces the 5 contiguous horizontal sky-phase band rectangles partitioning `[0.0, 1.0]`.
     */
    fun bands(): List<ChartBandRect> = listOf(
        bandRect(ChartSkyBand.DAY, yMaxDeg, 0.0),
        bandRect(ChartSkyBand.CIVIL, 0.0, CIVIL_BOTTOM_DEG),
        bandRect(ChartSkyBand.NAUTICAL, CIVIL_BOTTOM_DEG, NAUTICAL_BOTTOM_DEG),
        bandRect(ChartSkyBand.ASTRONOMICAL, NAUTICAL_BOTTOM_DEG, ASTRO_BOTTOM_DEG),
        bandRect(ChartSkyBand.NIGHT, ASTRO_BOTTOM_DEG, yMinDeg),
    )

    private fun bandRect(
        band: ChartSkyBand,
        upperDeg: Double,
        lowerDeg: Double,
    ): ChartBandRect = ChartBandRect(
        band = band,
        upperElevationDeg = upperDeg,
        lowerElevationDeg = lowerDeg,
        topFraction = yFractionClamped(upperDeg),
        bottomFraction = yFractionClamped(lowerDeg),
    )

    companion object {
        const val MIN_ELEVATION_DEG: Double = -20.0
        const val MIN_MAX_ELEVATION_DEG: Double = 30.0
        const val MAX_ELEVATION_DEG: Double = 90.0
        private const val TOP_PADDING_DEG: Double = 6.0
        private const val STEP_DEG: Double = 10.0
        private const val CIVIL_BOTTOM_DEG: Double = -6.0
        private const val NAUTICAL_BOTTOM_DEG: Double = -12.0
        private const val ASTRO_BOTTOM_DEG: Double = -18.0

        /**
         * Computes the chart ceiling elevation (`°`) from the highest point across [sunPath]
         * and [moonPath], padded by `6°`, rounded up to a `10°` step, and bounded in `[30°, 90°]`.
         */
        fun computeYMaxDeg(
            sunPath: List<PathPoint>,
            moonPath: List<PathPoint>,
        ): Double {
            val peak = (sunPath.asSequence() + moonPath.asSequence())
                .map { it.position.elevation }
                .filter { it.isFinite() }
                .maxOrNull()
            return computeYMaxFromPeak(peak)
        }

        /**
         * Computes the 5 horizontal sky-phase band rectangles directly from a [SunDay]
         * (using [SunDay.noonElevation] and optional [moonPeakElevationDeg] to set the ceiling).
         */
        fun bandsFromSunDay(
            sunDay: SunDay,
            moonPeakElevationDeg: Double? = null,
        ): List<ChartBandRect> {
            val peak = if (moonPeakElevationDeg != null && moonPeakElevationDeg.isFinite()) {
                maxOf(sunDay.noonElevation, moonPeakElevationDeg)
            } else {
                sunDay.noonElevation
            }
            return ElevationChartScale(yMaxDeg = computeYMaxFromPeak(peak)).bands()
        }

        private fun computeYMaxFromPeak(peakDeg: Double?): Double {
            if (peakDeg == null || !peakDeg.isFinite()) return MIN_MAX_ELEVATION_DEG
            val padded = ceil((peakDeg + TOP_PADDING_DEG) / STEP_DEG) * STEP_DEG
            return padded.coerceIn(MIN_MAX_ELEVATION_DEG, MAX_ELEVATION_DEG)
        }
    }
}

/**
 * Complete pure geometry for rendering and scrubbing the elevation chart on a local calendar day.
 */
data class ElevationChartData(
    val dayStart: Instant,
    val dayEnd: Instant,
    val scale: ElevationChartScale,
    val bands: List<ChartBandRect>,
    val sunPoints: List<ChartPlotPoint>,
    val moonPoints: List<ChartPlotPoint>,
    val goldenRuns: List<List<ChartPlotPoint>>,
    val blueRuns: List<List<ChartPlotPoint>>,
    val hourTicks: List<ChartHourTick>,
) {
    private val dayMillis: Double =
        Duration.between(dayStart, dayEnd).toMillis().toDouble().coerceAtLeast(1.0)

    /** Normalized horizontal position `[0.0, 1.0]` of [instant] within this local day. */
    fun xFraction(instant: Instant): Double =
        (Duration.between(dayStart, instant).toMillis().toDouble() / dayMillis).coerceIn(0.0, 1.0)

    /** Maps a normalized horizontal scrub fraction `[0.0, 1.0]` back to an [Instant] in this day. */
    fun instantForXFraction(fraction: Double): Instant {
        val clamped = fraction.coerceIn(0.0, 1.0)
        return dayStart.plusMillis((clamped * dayMillis).roundToLong())
    }

    companion object {
        private val DEFAULT_STEP: Duration = Duration.ofMinutes(5)
        private val TICK_HOURS: List<Int> = listOf(0, 6, 12, 18)

        /**
         * Builds all scaled chart paths, sky-phase bands, golden/blue highlight runs, and
         * DST-aware hour ticks for [date] in [zone] at [place].
         */
        fun build(
            place: Place,
            date: LocalDate,
            zone: ZoneId,
            sunDay: SunDay = Ephemeris.sunDay(place, date, zone),
            sunPath: List<PathPoint> = Ephemeris.sunPath(place, date, zone, DEFAULT_STEP),
            moonPath: List<PathPoint> = Ephemeris.moonPath(place, date, zone, DEFAULT_STEP),
        ): ElevationChartData {
            val dayStart = date.atStartOfDay(zone).toInstant()
            val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
            val dayMillis = Duration.between(dayStart, dayEnd).toMillis().toDouble().coerceAtLeast(1.0)

            val yMaxFromPaths = ElevationChartScale.computeYMaxDeg(sunPath, moonPath)
            val yMaxFromNoon = ElevationChartScale.bandsFromSunDay(sunDay).first().upperElevationDeg
            val scale = ElevationChartScale(yMaxDeg = maxOf(yMaxFromPaths, yMaxFromNoon))
            val bands = scale.bands()

            fun projectPath(points: List<PathPoint>): List<ChartPlotPoint> = points.map { pt ->
                val x = (Duration.between(dayStart, pt.instant).toMillis().toDouble() / dayMillis)
                    .coerceIn(0.0, 1.0)
                ChartPlotPoint(
                    instant = pt.instant,
                    xFraction = x,
                    yFraction = scale.yFraction(pt.position.elevation),
                    elevationDeg = pt.position.elevation,
                    azimuthDeg = pt.position.azimuth,
                )
            }

            val sunPoints = projectPath(sunPath)
            val moonPoints = projectPath(moonPath)

            val phases = sunPoints.map { SkyPhase.at(place, it.instant) }
            val goldenRuns = extractRuns(sunPoints, phases, SkyPhase.GOLDEN)
            val blueRuns = extractRuns(sunPoints, phases, SkyPhase.BLUE)

            val hourTicks = buildList {
                TICK_HOURS.forEach { hour ->
                    val tickInstant = date.atTime(hour, 0).atZone(zone).toInstant()
                    val x = (Duration.between(dayStart, tickInstant).toMillis().toDouble() / dayMillis)
                        .coerceIn(0.0, 1.0)
                    add(ChartHourTick(label = "%02d".format(hour), xFraction = x))
                }
                add(ChartHourTick(label = "24", xFraction = 1.0))
            }

            return ElevationChartData(
                dayStart = dayStart,
                dayEnd = dayEnd,
                scale = scale,
                bands = bands,
                sunPoints = sunPoints,
                moonPoints = moonPoints,
                goldenRuns = goldenRuns,
                blueRuns = blueRuns,
                hourTicks = hourTicks,
            )
        }

        private fun extractRuns(
            points: List<ChartPlotPoint>,
            phases: List<SkyPhase>,
            target: SkyPhase,
        ): List<List<ChartPlotPoint>> {
            val runs = mutableListOf<List<ChartPlotPoint>>()
            var current = mutableListOf<ChartPlotPoint>()
            for (i in points.indices) {
                if (phases[i] == target) {
                    current.add(points[i])
                } else if (current.isNotEmpty()) {
                    runs.add(current)
                    current = mutableListOf()
                }
            }
            if (current.isNotEmpty()) {
                runs.add(current)
            }
            return runs
        }
    }
}
