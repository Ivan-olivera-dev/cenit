package io.github.ivan_olivera_dev.cenit.alerts

import io.github.ivan_olivera_dev.cenit.core.sky.SkyFormatting
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
 * Event times are truncated to the minute per Cenit's display rule (L14).
 */
object AlertNotificationFormatter {
    /**
     * Whole minutes from [now] to the event's displayed minute (event time truncated to the
     * minute, L14), never negative. The alarm fires anywhere from 40 to 30 minutes before the
     * event, so the notification states the real time left instead of a fixed lead.
     */
    fun minutesUntil(now: Instant, eventInstant: Instant): Long {
        val shownEvent = eventInstant.truncatedTo(ChronoUnit.MINUTES)
        return Duration.between(now, shownEvent).toMinutes().coerceAtLeast(0L)
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
    ): FormattedAlertNotification {
        val timeText = SkyFormatting.formatTimeTruncated(eventInstant, zone)
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
