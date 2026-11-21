package io.github.ivan_olivera_dev.cenit.alerts

import io.github.ivan_olivera_dev.cenit.core.format.CenitFormat
import io.github.ivan_olivera_dev.cenit.core.format.ClockFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

data class FormattedAlertNotification(
    val title: String,
    val body: String,
    val summary: String,
)

/**
 * Pure JVM formatter for local alert notifications ("Hora dorada en 30 minutos · 17:12 · Madrid").
 *
 * Event times are truncated to the minute per Cenit's display rule (L14) and respect the user's
 * [ClockFormat] preference.
 */
object AlertNotificationFormatter {
    /**
     * Ceiling of the minutes from [now] to the event's displayed minute (event time truncated to
     * the minute, L14), never negative. For example, 06:52:30 before a 07:22 event yields 30,
     * 06:52:00 yields 30, 06:53:01 yields 29, and any instant at or after 07:22:00 yields 0.
     */
    fun minutesUntil(now: Instant, eventInstant: Instant): Long {
        val shownEvent = eventInstant.truncatedTo(ChronoUnit.MINUTES)
        val remaining = Duration.between(now, shownEvent)
        if (remaining.isNegative || remaining.isZero) return 0L
        val wholeMinutes = remaining.seconds / 60L
        val hasFraction = (remaining.seconds % 60L) != 0L || remaining.nano != 0
        return if (hasFraction) wholeMinutes + 1L else wholeMinutes
    }

    fun format(
        eventLabel: String,
        leadMinutes: Long,
        eventInstant: Instant,
        zone: ZoneId,
        placeName: String,
        titleTemplate: String,
        bodyWithPlaceTemplate: String,
        summaryTemplate: String,
        locale: Locale = Locale.ROOT,
        clock: ClockFormat = ClockFormat.H24,
    ): FormattedAlertNotification {
        val timeText = CenitFormat.formatTime(eventInstant, zone, clock, locale)
        val title = String.format(locale, titleTemplate, eventLabel, leadMinutes)
        val trimmedPlace = placeName.trim()
        val body = if (trimmedPlace.isNotEmpty()) {
            String.format(locale, bodyWithPlaceTemplate, timeText, trimmedPlace)
        } else {
            timeText
        }
        val summary = String.format(locale, summaryTemplate, title, body)
        return FormattedAlertNotification(
            title = title,
            body = body,
            summary = summary,
        )
    }
}
