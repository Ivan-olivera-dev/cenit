package io.github.ivan_olivera_dev.cenit.core.sky

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Pure formatting helpers shared across the app.
 *
 * Times are always truncated to the minute (never rounded up), matching EphemerisTest and L14.
 */
object SkyFormatting {
    private val HHMM_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    private const val UNICODE_MINUS = '\u2212'

    /**
     * Formats [instant] in [zone] as `HH:mm`, truncated to the minute (seconds and nanoseconds dropped).
     */
    fun formatTimeTruncated(instant: Instant, zone: ZoneId): String =
        instant.atZone(zone).truncatedTo(ChronoUnit.MINUTES).format(HHMM_FORMATTER)

    /**
     * Formats [time] as `HH:mm`, truncated to the minute.
     */
    fun formatLocalTimeTruncated(time: LocalTime): String =
        time.truncatedTo(ChronoUnit.MINUTES).format(HHMM_FORMATTER)

    /**
     * Formats [value] with [decimals] fractional digits using [locale]'s decimal separator
     * and the typographic minus sign (`−`) for negative values, never emitting negative zero.
     */
    fun formatDecimal(value: Double, decimals: Int, locale: Locale): String {
        require(decimals >= 0) { "decimals must be non-negative, was $decimals" }
        val scale = 10.0.pow(decimals)
        val rounded = round(value * scale) / scale
        val normalized = if (abs(rounded) == 0.0) 0.0 else rounded

        val symbols = DecimalFormatSymbols.getInstance(locale).apply {
            minusSign = UNICODE_MINUS
        }
        val pattern = if (decimals == 0) "0" else "0." + "0".repeat(decimals)
        return DecimalFormat(pattern, symbols).format(normalized)
    }

    /**
     * Maps a compass bearing in degrees to a 16-point wind index in `0..15`
     * (`0 = N`, `4 = E`, `8 = S`, `12 = W`).
     */
    fun compassIndex(azimuthDeg: Double): Int {
        val normalized = ((azimuthDeg % 360.0) + 360.0) % 360.0
        return (normalized / 22.5).roundToInt() % 16
    }
}
