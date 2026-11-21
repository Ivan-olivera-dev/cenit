package io.github.ivan_olivera_dev.cenit.alerts

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import io.github.ivan_olivera_dev.cenit.core.alerts.AlertTrigger
import java.time.Instant

/**
 * Schedules and cancels offline alert alarms.
 *
 * When [AlertPermissions.canScheduleExactAlarms] is `true` (always on API < 31, or when the user
 * grants the optional `SCHEDULE_EXACT_ALARM` permission on API 31+), alarms are scheduled with
 * [AlarmManager.setExactAndAllowWhileIdle] (`RTC_WAKEUP`, 30 minutes before the event) so they
 * fire on time during Doze. Otherwise, Cenit falls back to [AlarmManager.setWindow] (`RTC_WAKEUP`,
 * 10-minute window ending 30 minutes before the event).
 */
class AlarmScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    private fun currentMode(): AlarmScheduleMode {
        val canExact = AlertPermissions.canScheduleExactAlarms(appContext)
        ExactAlarmSync.recordCapability(appContext, canExact)
        return if (canExact) AlarmScheduleMode.EXACT else AlarmScheduleMode.WINDOWED
    }

    fun schedule(
        subscription: AlertSubscription,
        now: Instant = Instant.now(),
    ): AlertTrigger? {
        val mode = currentMode()
        return applyDecision(
            decision = AlertReschedulePlanner.planOne(subscription, now, mode = mode),
            fallbackDecision = {
                AlertReschedulePlanner.planOne(subscription, now, mode = AlarmScheduleMode.WINDOWED)
            },
        )
    }

    fun rescheduleAfterFire(
        subscription: AlertSubscription,
        firedTriggerInstant: Instant?,
        now: Instant = Instant.now(),
    ): AlertTrigger? {
        val mode = currentMode()
        return applyDecision(
            decision = AlertReschedulePlanner.planAfterFire(
                subscription = subscription,
                firedTriggerInstant = firedTriggerInstant,
                now = now,
                mode = mode,
            ),
            fallbackDecision = {
                AlertReschedulePlanner.planAfterFire(
                    subscription = subscription,
                    firedTriggerInstant = firedTriggerInstant,
                    now = now,
                    mode = AlarmScheduleMode.WINDOWED,
                )
            },
        )
    }

    fun rescheduleAll(
        subscriptions: List<AlertSubscription>,
        now: Instant = Instant.now(),
    ) {
        val mode = currentMode()
        val decisions = AlertReschedulePlanner.planAll(subscriptions, now, mode = mode)
        for (decision in decisions) {
            applyDecision(
                decision = decision,
                fallbackDecision = {
                    AlertReschedulePlanner.planOne(
                        subscription = decision.subscription,
                        now = now,
                        mode = AlarmScheduleMode.WINDOWED,
                    )
                },
            )
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

    private fun applyDecision(
        decision: ScheduleDecision,
        fallbackDecision: () -> ScheduleDecision,
    ): AlertTrigger? = when (decision) {
        is ScheduleDecision.Schedule -> {
            val manager = alarmManager ?: return null
            val pendingIntent = buildPendingIntent(
                subscriptionId = decision.subscription.id,
                eventInstant = decision.trigger.eventInstant,
                triggerInstant = decision.trigger.triggerInstant,
            )
            when (decision.mode) {
                AlarmScheduleMode.EXACT -> {
                    try {
                        manager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            decision.trigger.triggerInstant.toEpochMilli(),
                            pendingIntent,
                        )
                        decision.trigger
                    } catch (_: SecurityException) {
                        ExactAlarmSync.recordCapability(appContext, false)
                        applyWindowedFallback(manager, fallbackDecision())
                    }
                }
                AlarmScheduleMode.WINDOWED -> {
                    manager.setWindow(
                        AlarmManager.RTC_WAKEUP,
                        decision.windowStart.toEpochMilli(),
                        decision.windowLengthMillis,
                        pendingIntent,
                    )
                    decision.trigger
                }
            }
        }
        is ScheduleDecision.Cancel -> {
            cancel(decision.subscription.id)
            null
        }
    }

    private fun applyWindowedFallback(
        manager: AlarmManager,
        fallback: ScheduleDecision,
    ): AlertTrigger? = when (fallback) {
        is ScheduleDecision.Schedule -> {
            val pendingIntent = buildPendingIntent(
                subscriptionId = fallback.subscription.id,
                eventInstant = fallback.trigger.eventInstant,
                triggerInstant = fallback.trigger.triggerInstant,
            )
            manager.setWindow(
                AlarmManager.RTC_WAKEUP,
                fallback.windowStart.toEpochMilli(),
                fallback.windowLengthMillis,
                pendingIntent,
            )
            fallback.trigger
        }
        is ScheduleDecision.Cancel -> {
            cancel(fallback.subscription.id)
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
