package io.github.ivan_olivera_dev.cenit.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.ivan_olivera_dev.cenit.core.alerts.AlertSchedule
import java.time.Instant

/**
 * Receives windowed alert alarms, posts the local notification, and schedules the next occurrence.
 */
class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != AlarmScheduler.ACTION_FIRE_ALERT) return
        val subscriptionId = intent.getStringExtra(AlarmScheduler.EXTRA_SUBSCRIPTION_ID) ?: return

        val store = AlertSubscriptionsStore.fromContext(context)
        val subscription = store.findById(subscriptionId) ?: return

        val now = Instant.now()
        val eventMillis = intent.getLongExtra(AlarmScheduler.EXTRA_EVENT_EPOCH_MILLIS, Long.MIN_VALUE)
        val triggerMillis = intent.getLongExtra(AlarmScheduler.EXTRA_TRIGGER_EPOCH_MILLIS, Long.MIN_VALUE)
        val eventInstant = if (eventMillis != Long.MIN_VALUE) {
            Instant.ofEpochMilli(eventMillis)
        } else {
            now.plus(AlertSchedule.DEFAULT_LEAD)
        }
        val firedTriggerInstant = if (triggerMillis != Long.MIN_VALUE) {
            Instant.ofEpochMilli(triggerMillis)
        } else {
            null
        }

        AlertNotifier.post(context, subscription, eventInstant)
        AlarmScheduler(context).rescheduleAfterFire(
            subscription = subscription,
            firedTriggerInstant = firedTriggerInstant,
            now = now,
        )
    }
}
