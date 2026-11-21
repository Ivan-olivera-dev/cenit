package io.github.ivan_olivera_dev.cenit.alerts

import android.content.Context

/**
 * Pure JVM rules for exact-alarm capability transitions and one-time notice display.
 */
object ExactAlarmPolicy {
    /**
     * Returns `true` only when a previously observed exact-alarm capability has actually flipped
     * (granted or revoked). Avoids spurious reschedules on ordinary app resumes.
     */
    fun hasCapabilityChanged(lastKnown: Boolean?, current: Boolean): Boolean =
        lastKnown != null && lastKnown != current

    /**
     * Returns `true` when Hoy should display the one-time exact-alarm notice after enabling an alert.
     */
    fun shouldShowHoyNotice(
        requiresExactAlarmPermission: Boolean,
        canScheduleExactAlarms: Boolean,
        alreadyShown: Boolean,
    ): Boolean = requiresExactAlarmPermission && !canScheduleExactAlarms && !alreadyShown
}

/**
 * Tracks the last observed exact-alarm capability and the one-time Hoy notice flag in local
 * SharedPreferences (excluded from backup).
 */
object ExactAlarmSync {
    private const val PREFS_NAME = "cenit_alert_prefs"
    private const val KEY_LAST_CAN_SCHEDULE_EXACT = "last_can_schedule_exact"
    private const val KEY_EXACT_NOTICE_SHOWN = "exact_alarm_notice_shown"

    fun recordCapability(context: Context, canScheduleExact: Boolean) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_LAST_CAN_SCHEDULE_EXACT, canScheduleExact).apply()
    }

    /**
     * Called on app resume. Reschedules all persisted subscriptions only when
     * `canScheduleExactAlarms()` changed since the last check (for example, after the user
     * granted or revoked the permission in Android settings).
     */
    fun rescheduleIfPermissionChanged(context: Context) {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastKnown = if (prefs.contains(KEY_LAST_CAN_SCHEDULE_EXACT)) {
            prefs.getBoolean(KEY_LAST_CAN_SCHEDULE_EXACT, false)
        } else {
            null
        }
        val current = AlertPermissions.canScheduleExactAlarms(appContext)
        prefs.edit().putBoolean(KEY_LAST_CAN_SCHEDULE_EXACT, current).apply()

        if (!ExactAlarmPolicy.hasCapabilityChanged(lastKnown, current)) return
        val subscriptions = AlertSubscriptionsStore.fromContext(appContext).load()
        if (subscriptions.isEmpty()) return
        AlarmScheduler(appContext).rescheduleAll(subscriptions)
    }

    /**
     * Returns `true` once if the user just turned an alert on while exact alarms are unavailable
     * on API 31+, marking the notice as shown so it does not repeat.
     */
    fun consumeHoyExactAlarmNotice(context: Context): Boolean {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val alreadyShown = prefs.getBoolean(KEY_EXACT_NOTICE_SHOWN, false)
        val shouldShow = ExactAlarmPolicy.shouldShowHoyNotice(
            requiresExactAlarmPermission = AlertPermissions.requiresExactAlarmPermission(),
            canScheduleExactAlarms = AlertPermissions.canScheduleExactAlarms(appContext),
            alreadyShown = alreadyShown,
        )
        if (shouldShow) {
            prefs.edit().putBoolean(KEY_EXACT_NOTICE_SHOWN, true).apply()
        }
        return shouldShow
    }
}
