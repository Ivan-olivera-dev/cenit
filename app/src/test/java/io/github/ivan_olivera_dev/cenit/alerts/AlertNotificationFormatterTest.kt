package io.github.ivan_olivera_dev.cenit.alerts

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class AlertNotificationFormatterTest {

    @Test
    fun `formats Spanish notification with minute-truncated event time and place`() {
        val madridZone = ZoneId.of("Europe/Madrid")
        // 17:12:59 must truncate to 17:12 (L14 rule), never round to 17:13.
        val eventInstant = LocalDateTime.parse("2026-11-20T17:12:59").atZone(madridZone).toInstant()

        val formatted = AlertNotificationFormatter.format(
            eventLabel = "Hora dorada",
            leadMinutes = 30,
            eventInstant = eventInstant,
            zone = madridZone,
            placeName = "Madrid",
            titleTemplate = "%1\$s en %2\$d minutos",
            bodyWithPlaceTemplate = "%1\$s · %2\$s",
            summaryTemplate = "%1\$s · %2\$s",
        )

        assertEquals("Hora dorada en 30 minutos", formatted.title)
        assertEquals("17:12 · Madrid", formatted.body)
        assertEquals("Hora dorada en 30 minutos · 17:12 · Madrid", formatted.summary)
    }

    @Test
    fun `formats English notification and respects subscription timezone`() {
        val tokyoZone = ZoneId.of("Asia/Tokyo")
        val eventInstant = LocalDateTime.parse("2026-06-21T04:25:41").atZone(tokyoZone).toInstant()

        val formatted = AlertNotificationFormatter.format(
            eventLabel = "Sunrise",
            leadMinutes = 30,
            eventInstant = eventInstant,
            zone = tokyoZone,
            placeName = "Tokyo",
            titleTemplate = "%1\$s in %2\$d minutes",
            bodyWithPlaceTemplate = "%1\$s · %2\$s",
            summaryTemplate = "%1\$s · %2\$s",
        )

        assertEquals("Sunrise in 30 minutes", formatted.title)
        assertEquals("04:25 · Tokyo", formatted.body)
        assertEquals("Sunrise in 30 minutes · 04:25 · Tokyo", formatted.summary)
    }

    @Test
    fun `blank place name omits bullet separator`() {
        val utc = ZoneId.of("UTC")
        val eventInstant = LocalDateTime.parse("2026-06-21T06:44:30").atZone(utc).toInstant()

        val formatted = AlertNotificationFormatter.format(
            eventLabel = "Sunrise",
            leadMinutes = 30,
            eventInstant = eventInstant,
            zone = utc,
            placeName = "   ",
            titleTemplate = "%1\$s in %2\$d minutes",
            bodyWithPlaceTemplate = "%1\$s · %2\$s",
            summaryTemplate = "%1\$s · %2\$s",
        )

        assertEquals("Sunrise in 30 minutes", formatted.title)
        assertEquals("06:44", formatted.body)
        assertEquals("Sunrise in 30 minutes · 06:44", formatted.summary)
    }
}
