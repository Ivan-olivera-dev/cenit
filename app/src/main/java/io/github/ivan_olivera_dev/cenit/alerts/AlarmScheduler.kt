package io.github.ivan_olivera_dev.cenit.alerts

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.ivan_olivera_dev.cenit.core.alerts.AlertTrigger
import java.time.Instant

/**
 * Schedules and cancels windowed offline alarms using [AlarmManager.setWindow] (`RTC_WAKEUP`,
 * 10-minute window ending 30 minutes before the event). Requires no exact-alarm permission (`SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM`).
 */
class AlarmScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    fun schedule(
        subscription: AlertSubscription,
        now: Instant = Instant.now(),
    ): AlertTrigger? = applyDecision(AlertReschedulePlanner.planOne(subscription, now))

    fun rescheduleAfterFire(
        subscription: AlertSubscription,
        firedTriggerInstant: Instant?,
        now: Instant = Instant.now(),
    ): AlertTrigger? = applyDecision(
        AlertReschedulePlanner.planAfterFire(
            subscription = subscription,
            firedTriggerInstant = firedTriggerInstant,
            now = now,
        ),
    )

    fun rescheduleAll(
        subscriptions: List<AlertSubscription>,
        now: Instant = Instant.now(),
    ) {
        val decisions = AlertReschedulePlanner.planAll(subscriptions, now)
        for (decision in decisions) {
            applyDecision(decision)
        }
    }

    fun cancel(subscriptionId: String) {
        val requestCode = AlertRequestCode.forSubscriptionId(subscriptionId)
        val intent = baseAlarmIntent()
        val existing = PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager?.cancel(existing)
        existing.cancel()
    }

    private fun applyDecision(decision: ScheduleDecision): AlertTrigger? = when (decision) {
        is ScheduleDecision.Schedule -> {
            val manager = alarmManager ?: return null
            val pendingIntent = buildPendingIntent(
                subscriptionId = decision.subscription.id,
                eventInstant = decision.trigger.eventInstant,
                triggerInstant = decision.trigger.triggerInstant,
            )
            manager.setWindow(
                AlarmManager.RTC_WAKEUP,
                decision.windowStart.toEpochMilli(),
                decision.windowLengthMillis,
                pendingIntent,
            )
            decision.trigger
        }
        is ScheduleDecision.Cancel -> {
            cancel(decision.subscription.id)
            null
        }
    }

    private fun buildPendingIntent(
        subscriptionId: String,
        eventInstant: Instant,
        triggerInstant: Instant,
    ): PendingIntent {
        val intent = baseAlarmIntent().apply {
            putExtra(EXTRA_SUBSCRIPTION_ID, subscriptionId)
            putExtra(EXTRA_EVENT_EPOCH_MILLIS, eventInstant.toEpochMilli())
            putExtra(EXTRA_TRIGGER_EPOCH_MILLIS, triggerInstant.toEpochMilli())
        }
        return PendingIntent.getBroadcast(
            appContext,
            AlertRequestCode.forSubscriptionId(subscriptionId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun baseAlarmIntent(): Intent =
        Intent(appContext, AlertReceiver::class.java).setAction(ACTION_FIRE_ALERT)

    companion object {
        const val ACTION_FIRE_ALERT = "io.github.ivan_olivera_dev.cenit.alerts.ACTION_FIRE_ALERT"
        const val EXTRA_SUBSCRIPTION_ID = "subscription_id"
        const val EXTRA_EVENT_EPOCH_MILLIS = "event_epoch_millis"
        const val EXTRA_TRIGGER_EPOCH_MILLIS = "trigger_epoch_millis"
    }
}
