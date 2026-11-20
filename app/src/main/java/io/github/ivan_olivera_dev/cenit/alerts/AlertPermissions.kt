package io.github.ivan_olivera_dev.cenit.alerts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Runtime notification permission helpers.
 *
 * On API 33+ (`POST_NOTIFICATIONS`), permission is requested lazily only when the user enables
 * an alert on Hoy, never at app startup.
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
}
