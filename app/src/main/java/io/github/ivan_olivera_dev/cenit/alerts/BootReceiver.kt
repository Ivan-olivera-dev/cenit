package io.github.ivan_olivera_dev.cenit.alerts

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Reschedules all persisted alert subscriptions after device reboot, package update,
 * time zone change, or manual clock change.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!AlertReschedulePlanner.shouldRescheduleForAction(intent?.action)) return
        val subscriptions = AlertSubscriptionsStore.fromContext(context).load()
        if (subscriptions.isEmpty()) return
        AlarmScheduler(context).rescheduleAll(subscriptions)
    }
}
