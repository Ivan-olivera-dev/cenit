package io.github.ivan_olivera_dev.cenit.alerts

import io.github.ivan_olivera_dev.cenit.core.sky.SkyFormatting
import java.time.Instant
import java.time.ZoneId
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
