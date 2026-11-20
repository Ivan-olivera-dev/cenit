package io.github.ivan_olivera_dev.cenit.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.ivan_olivera_dev.cenit.R
import io.github.ivan_olivera_dev.cenit.core.alerts.AlertKind
import io.github.ivan_olivera_dev.cenit.settings.AppLocaleController
import io.github.ivan_olivera_dev.cenit.settings.SettingsStore
import io.github.ivan_olivera_dev.cenit.ui.MainActivity
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/**
 * Lazily creates the Cenit alerts notification channel and posts local alert notifications.
 */
object AlertNotifier {
    const val CHANNEL_ID = "cenit_alerts"

    @StringRes
    fun labelResId(kind: AlertKind): Int = when (kind) {
        AlertKind.ASTRONOMICAL_DAWN -> R.string.alert_kind_astro_dawn
        AlertKind.NAUTICAL_DAWN -> R.string.alert_kind_nautical_dawn
        AlertKind.CIVIL_DAWN -> R.string.alert_kind_civil_dawn
        AlertKind.BLUE_MORNING -> R.string.alert_kind_blue_morning
        AlertKind.GOLDEN_MORNING -> R.string.alert_kind_golden_morning
        AlertKind.SUNRISE -> R.string.alert_kind_sunrise
        AlertKind.GOLDEN_EVENING -> R.string.alert_kind_golden_evening
        AlertKind.SUNSET -> R.string.alert_kind_sunset
        AlertKind.BLUE_EVENING -> R.string.alert_kind_blue_evening
        AlertKind.CIVIL_DUSK -> R.string.alert_kind_civil_dusk
        AlertKind.NAUTICAL_DUSK -> R.string.alert_kind_nautical_dusk
        AlertKind.ASTRONOMICAL_DUSK -> R.string.alert_kind_astro_dusk
    }

    fun post(
        context: Context,
        subscription: AlertSubscription,
        eventInstant: Instant,
    ) {
        val appContext = context.applicationContext
        if (!AlertPermissions.hasNotificationPermission(appContext)) return

        val localizedContext = AppLocaleController.localizedContext(appContext)
        val settings = SettingsStore.fromContext(appContext).load()

        ensureChannel(localizedContext)

        val zone = subscription.resolveZoneOrNull() ?: ZoneId.systemDefault()
        val locale = localizedContext.resources.configuration.locales[0] ?: Locale.getDefault()
        val formatted = AlertNotificationFormatter.format(
            eventLabel = localizedContext.getString(labelResId(subscription.kind)),
            leadMinutes = AlertNotificationFormatter.minutesUntil(Instant.now(), eventInstant),
            eventInstant = eventInstant,
            zone = zone,
            placeName = subscription.placeName,
            titleTemplate = localizedContext.getString(R.string.alert_notif_title),
            bodyWithPlaceTemplate = localizedContext.getString(R.string.alert_notif_body_with_place),
            summaryTemplate = localizedContext.getString(R.string.alert_notif_summary),
            locale = locale,
            clock = settings.clock,
        )

        val openAppIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            appContext,
            AlertRequestCode.forSubscriptionId(subscription.id),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_cenit)
            .setContentTitle(formatted.title)
            .setContentText(formatted.body)
            .setTicker(formatted.summary)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(appContext).notify(
                AlertRequestCode.forSubscriptionId(subscription.id),
                notification,
            )
        }
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.alert_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.alert_channel_description)
        }
        manager.createNotificationChannel(channel)
    }
}
