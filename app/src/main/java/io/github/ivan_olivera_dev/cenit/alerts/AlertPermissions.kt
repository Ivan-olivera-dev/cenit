package io.github.ivan_olivera_dev.cenit.alerts

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Runtime notification and optional exact-alarm permission helpers.
 *
 * - On API 33+ (`POST_NOTIFICATIONS`), permission is requested lazily only when the user enables
 *   an alert on Hoy, never at app startup.
 * - On API 31+ (`SCHEDULE_EXACT_ALARM`), exact alarms are optional: when granted, alerts use
 *   `setExactAndAllowWhileIdle`; when off, Cenit falls back to windowed alarms.
 */
object AlertPermissions {
    fun requiresRuntimePermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun hasNotificationPermission(context: Context): Boolean {
        if (!requiresRuntimePermission()) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requiresExactAlarmPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (!requiresExactAlarmPermission()) return true
        val alarmManager = context.applicationContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    fun openExactAlarmSettings(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val packageIntent = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.fromParts("package", context.packageName, null),
        ).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(packageIntent)
            true
        }.getOrElse {
            val fallbackIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching {
                context.startActivity(fallbackIntent)
                true
            }.getOrDefault(false)
        }
    }
}
