package io.github.ivan_olivera_dev.cenit.core.format

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Clock display format preference: 24-hour (`"07:22"`) or 12-hour (`"7:22 AM"` / `"7:22 a. m."`).
 */
enum class ClockFormat {
    H24,
    H12,
}

/**
 * Pure Kotlin formatting helpers shared across all screens, charts, and alert notifications.
 *
 * Times are always truncated to the minute (never rounded up), matching L14.
 * Sky position angles use [RoundingMode.HALF_UP], the typographic minus sign (`−`, U+2212),
 * and normalize negative zero (`−0,4°` without decimals -> `"0°"`).
 */
object CenitFormat {
    private val H24_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    private const val UNICODE_MINUS = '\u2212'
    private const val EN_DASH = '\u2013'
    private const val DEGREE_SIGN = '\u00B0'

    /**
     * Formats [instant] in [zone] using [clock] and [locale], truncated to the minute (L14).
     */
    fun formatTime(
        instant: Instant,
        zone: ZoneId,
        clock: ClockFormat = ClockFormat.H24,
        locale: Locale = Locale.ROOT,
    ): String = formatLocalTime(instant.atZone(zone).toLocalTime(), clock, locale)

    /**
     * Formats [time] using [clock] and [locale], truncated to the minute (L14).
     */
    fun formatLocalTime(
        time: LocalTime,
        clock: ClockFormat = ClockFormat.H24,
        locale: Locale = Locale.ROOT,
    ): String {
        val truncated = time.truncatedTo(ChronoUnit.MINUTES)
        return when (clock) {
            ClockFormat.H24 -> truncated.format(H24_FORMATTER)
            ClockFormat.H12 -> truncated.format(DateTimeFormatter.ofPattern("h:mm a", locale))
        }
    }

    /**
     * Formats a time range `[start, end]` in [zone] into its `(startText, endText)` parts,
     * both truncated to the minute (L14).
     *
     * In [ClockFormat.H12], when both endpoints fall in the same AM/PM period, the redundant
     * period marker is omitted on the start endpoint (e.g. `("7:32", "8:31 AM")`). When the
     * range crosses noon or midnight, both endpoints include their period marker.
     */
    fun formatTimeRangeParts(
        start: Instant,
        end: Instant,
        zone: ZoneId,
        clock: ClockFormat = ClockFormat.H24,
        locale: Locale = Locale.ROOT,
    ): Pair<String, String> = formatLocalTimeRangeParts(
        start = start.atZone(zone).toLocalTime(),
        end = end.atZone(zone).toLocalTime(),
        clock = clock,
        locale = locale,
    )

    /**
     * Formats a local time range `[start, end]` into its `(startText, endText)` parts,
     * both truncated to the minute (L14).
     */
    fun formatLocalTimeRangeParts(
        start: LocalTime,
        end: LocalTime,
        clock: ClockFormat = ClockFormat.H24,
        locale: Locale = Locale.ROOT,
    ): Pair<String, String> {
        val s = start.truncatedTo(ChronoUnit.MINUTES)
        val e = end.truncatedTo(ChronoUnit.MINUTES)
        return when (clock) {
            ClockFormat.H24 -> s.format(H24_FORMATTER) to e.format(H24_FORMATTER)
            ClockFormat.H12 -> {
                val samePeriod = (s.hour < 12) == (e.hour < 12)
                if (samePeriod) {
                    val startText = s.format(DateTimeFormatter.ofPattern("h:mm", locale))
                    val endText = e.format(DateTimeFormatter.ofPattern("h:mm a", locale))
                    startText to endText
                } else {
                    formatLocalTime(s, ClockFormat.H12, locale) to
                        formatLocalTime(e, ClockFormat.H12, locale)
                }
            }
        }
    }

    /**
     * Formats a time range `[start, end]` in [zone] joined with an en-dash (`–`).
     */
    fun formatTimeRange(
        start: Instant,
        end: Instant,
        zone: ZoneId,
        clock: ClockFormat = ClockFormat.H24,
        locale: Locale = Locale.ROOT,
    ): String {
        val (s, e) = formatTimeRangeParts(start, end, zone, clock, locale)
        return "$s$EN_DASH$e"
    }

    /**
     * Formats a local time range `[start, end]` joined with an en-dash (`–`).
     */
    fun formatLocalTimeRange(
        start: LocalTime,
        end: LocalTime,
        clock: ClockFormat = ClockFormat.H24,
        locale: Locale = Locale.ROOT,
    ): String {
        val (s, e) = formatLocalTimeRangeParts(start, end, clock, locale)
        return "$s$EN_DASH$e"
    }

    /**
     * Formats an axis tick hour (`0`, `6`, `12`, `18`, `24`) for the elevation chart and scrubber.
     * - [ClockFormat.H24]: `"00"`, `"06"`, `"12"`, `"18"`, `"24"`
     * - [ClockFormat.H12]: localized `"12 AM"`, `"6 AM"`, `"12 PM"`, `"6 PM"`, `"12 AM"`
     */
    fun formatAxisHour(
        hour: Int,
        clock: ClockFormat = ClockFormat.H24,
        locale: Locale = Locale.ROOT,
    ): String = when (clock) {
        ClockFormat.H24 -> String.format(Locale.ROOT, "%02d", hour)
        ClockFormat.H12 -> {
            val wrapped = ((hour % 24) + 24) % 24
            LocalTime.of(wrapped, 0).format(DateTimeFormatter.ofPattern("h a", locale))
        }
    }

    /**
     * Formats a compact hour dot label on the flat-compass sky dome (`0..23`).
     * - [ClockFormat.H24]: `"0"`, `"6"`, `"12"`, `"15"`, `"18"`, `"21"`
     * - [ClockFormat.H12]: `"12"`, `"6"`, `"12"`, `"3"`, `"6"`, `"9"`
     */
    fun formatDomeHour(
        hour: Int,
        clock: ClockFormat = ClockFormat.H24,
    ): String = when (clock) {
        ClockFormat.H24 -> hour.toString()
        ClockFormat.H12 -> {
            val mod12 = ((hour % 12) + 12) % 12
            if (mod12 == 0) "12" else mod12.toString()
        }
    }

    /**
     * Formats a sky angle in degrees without the trailing `°` symbol (for string resources that
     * already include `°`), using [RoundingMode.HALF_UP], [locale]'s decimal separator, the
     * typographic minus sign (`−`), and normalizing `-0` to `0`.
     */
    fun formatAngleValue(
        degrees: Double,
        withDecimals: Boolean,
        locale: Locale,
    ): String {
        if (!degrees.isFinite()) return "\u2014"
        val scale = if (withDecimals) 1 else 0
        val rounded = BigDecimal.valueOf(degrees).setScale(scale, RoundingMode.HALF_UP)
        val normalized = if (rounded.compareTo(BigDecimal.ZERO) == 0) {
            BigDecimal.ZERO.setScale(scale)
        } else {
            rounded
        }
        val symbols = DecimalFormatSymbols.getInstance(locale).apply {
            minusSign = UNICODE_MINUS
        }
        val pattern = if (scale == 0) "0" else "0.0"
        return DecimalFormat(pattern, symbols).format(normalized)
    }

    /**
     * Formats a sky angle in degrees with the trailing `°` symbol (e.g. `"13,4°"`, `"13°"`, `"0°"`).
     */
    fun formatAngle(
        degrees: Double,
        withDecimals: Boolean,
        locale: Locale,
    ): String = "${formatAngleValue(degrees, withDecimals, locale)}$DEGREE_SIGN"
}
