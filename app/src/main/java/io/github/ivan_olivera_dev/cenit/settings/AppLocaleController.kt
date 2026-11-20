package io.github.ivan_olivera_dev.cenit.settings

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * Applies [AppLanguage] across API 29–35 without AppCompat or extra dependencies:
 * - API 33+: delegates to `LocaleManager.setApplicationLocales` (empty list for [AppLanguage.SYSTEM]);
 *   Android automatically recreates the activity and localizes app resources & notifications.
 * - API 29–32: wraps [Activity.attachBaseContext] and notification contexts via
 *   `Context.createConfigurationContext`, and calls `Activity.recreate()` when changed.
 */
object AppLocaleController {

    /**
     * Reads the active [AppLanguage]: on API 33+ prefers the platform `LocaleManager` so changes
     * made in Android's system per-app language screen stay in sync with Cenit's Settings screen.
     */
    fun readEffectiveLanguage(context: Context, storedLanguage: AppLanguage): AppLanguage {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = context.getSystemService(LocaleManager::class.java)
            if (manager != null) {
                val locales = manager.applicationLocales
                val tags = List(locales.size()) { index -> locales[index].toLanguageTag() }
                return LocaleResolution.fromLanguageTags(tags)
            }
        }
        return storedLanguage
    }

    /**
     * Wraps [base] in `MainActivity.attachBaseContext` on API 29–32 when an explicit language
     * override is persisted. On API 33+, returns [base] unchanged because `LocaleManager` manages
     * the activity locale natively.
     */
    fun wrapBaseContext(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return base
        }
        val language = SettingsStore.fromContext(base).load().language
        return applyConfigurationLocale(base, language)
    }

    /**
     * Returns a [Context] configured with the user's selected language for formatting resources
     * outside `MainActivity` (such as [io.github.ivan_olivera_dev.cenit.alerts.AlertNotifier]).
     */
    fun localizedContext(context: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return context
        }
        val language = SettingsStore.fromContext(context).load().language
        return applyConfigurationLocale(context, language)
    }

    /**
     * Applies [language] immediately from an active [Activity].
     */
    fun applyLanguage(activity: Activity, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = activity.getSystemService(LocaleManager::class.java)
            if (manager != null) {
                val tag = language.toLanguageTagOrNull()
                manager.applicationLocales = if (tag == null) {
                    LocaleList.getEmptyLocaleList()
                } else {
                    LocaleList.forLanguageTags(tag)
                }
                return
            }
        }
        activity.recreate()
    }

    private fun applyConfigurationLocale(context: Context, language: AppLanguage): Context {
        if (language == AppLanguage.SYSTEM) return context
        val currentConfig = context.resources.configuration
        val deviceLocale = currentConfig.locales[0] ?: Locale.getDefault()
        val resolved = LocaleResolution.resolveAppLocale(language, deviceLocale)
        val config = Configuration(currentConfig).apply {
            setLocale(resolved)
            setLocales(LocaleList(resolved))
        }
        return context.createConfigurationContext(config)
    }
}
