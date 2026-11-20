package io.github.ivan_olivera_dev.cenit.alerts

import io.github.ivan_olivera_dev.cenit.core.alerts.AlertSchedule
import io.github.ivan_olivera_dev.cenit.core.alerts.AlertTrigger
import java.time.Duration
import java.time.Instant

enum class CancelReason {
    NO_UPCOMING_OCCURRENCE,
    INVALID_ZONE,
}

sealed interface ScheduleDecision {
    val subscription: AlertSubscription

    data class Schedule(
        override val subscription: AlertSubscription,
        val trigger: AlertTrigger,
        val windowLengthMillis: Long,
    ) : ScheduleDecision {
        /** Start of the `setWindow` window; the window ends exactly at [AlertTrigger.triggerInstant]. */
        val windowStart: java.time.Instant get() = trigger.triggerInstant.minusMillis(windowLengthMillis)
    }

    data class Cancel(
        override val subscription: AlertSubscription,
        val reason: CancelReason,
    ) : ScheduleDecision
}

/**
 * Pure JVM decision logic for scheduling, rescheduling, and cancelling alert alarms.
 */
object AlertReschedulePlanner {
    /**
     * Window length for `AlarmManager.setWindow` (no exact-alarm permission). Android 14+ widens
     * shorter windows to 10 minutes, so the window is 10 minutes and ends at the trigger: the alert
     * arrives between 40 and 30 minutes before the event, never later (L26).
     */
    const val WINDOW_LENGTH_MILLIS: Long = 10L * 60L * 1000L

    private val RESCHEDULE_ACTIONS = setOf(
        "android.intent.action.BOOT_COMPLETED",
        "android.intent.action.MY_PACKAGE_REPLACED",
        "android.intent.action.TIMEZONE_CHANGED",
        "android.intent.action.TIME_SET",
    )

    fun shouldRescheduleForAction(action: String?): Boolean =
        action != null && action in RESCHEDULE_ACTIONS

    fun planOne(
        subscription: AlertSubscription,
        now: Instant,
        lead: Duration = AlertSchedule.DEFAULT_LEAD,
    ): ScheduleDecision {
        val zone = subscription.resolveZoneOrNull()
            ?: return ScheduleDecision.Cancel(subscription, CancelReason.INVALID_ZONE)
        val trigger = AlertSchedule.nextTrigger(
            kind = subscription.kind,
            place = subscription.toPlace(),
            zone = zone,
            now = now,
            lead = lead,
        ) ?: return ScheduleDecision.Cancel(subscription, CancelReason.NO_UPCOMING_OCCURRENCE)

        return ScheduleDecision.Schedule(
            subscription = subscription,
            trigger = trigger,
            windowLengthMillis = WINDOW_LENGTH_MILLIS,
        )
    }

    fun planAll(
        subscriptions: List<AlertSubscription>,
        now: Instant,
        lead: Duration = AlertSchedule.DEFAULT_LEAD,
    ): List<ScheduleDecision> = subscriptions.map { planOne(it, now, lead) }

    /**
     * Plans the next occurrence after an alarm has fired. Uses `max(now, firedTriggerInstant)`
     * so small clock adjustments can never re-schedule the occurrence that just fired.
     */
    fun planAfterFire(
        subscription: AlertSubscription,
        firedTriggerInstant: Instant?,
        now: Instant,
        lead: Duration = AlertSchedule.DEFAULT_LEAD,
    ): ScheduleDecision {
        val effectiveNow = if (firedTriggerInstant != null && firedTriggerInstant.isAfter(now)) {
            firedTriggerInstant
        } else {
            now
        }
        return planOne(subscription, effectiveNow, lead)
    }
}

/**
 * Stable, non-negative 31-bit PendingIntent / Notification request code derived from a
 * subscription id via FNV-1a 32-bit hashing.
 */
object AlertRequestCode {
    private const val FNV_OFFSET_BASIS: Long = 0x811c9dc5L
    private const val FNV_PRIME: Long = 0x01000193L

    fun forSubscriptionId(subscriptionId: String): Int {
        var hash = FNV_OFFSET_BASIS
        for (byte in subscriptionId.toByteArray(Charsets.UTF_8)) {
            hash = hash xor (byte.toLong() and 0xffL)
            hash = (hash * FNV_PRIME) and 0xffffffffL
        }
        return (hash and 0x7fffffffL).toInt()
    }
}
