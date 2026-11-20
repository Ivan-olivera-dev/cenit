package io.github.ivan_olivera_dev.cenit.settings

import android.content.Context
import android.content.SharedPreferences
import io.github.ivan_olivera_dev.cenit.core.format.ClockFormat

/**
 * Minimal key-value abstraction over [SharedPreferences] so [SettingsStore] can be tested on
 * the JVM without Android stubs or extra dependencies.
 */
interface SettingsStorage {
    fun getString(key: String, defaultValue: String?): String?
    fun putString(key: String, value: String)
    fun getBoolean(key: String, defaultValue: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
}

private class SharedPreferencesSettingsStorage(
    private val prefs: SharedPreferences,
) : SettingsStorage {
    override fun getString(key: String, defaultValue: String?): String? =
        prefs.getString(key, defaultValue)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        prefs.getBoolean(key, defaultValue)

    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }
}

/**
 * Local SharedPreferences persistence for [AppSettings].
 *
 * SharedPreferences are excluded from cloud backup and device transfer in
 * `res/xml/data_extraction_rules.xml` (`allowBackup="false"`).
 */
class SettingsStore(private val storage: SettingsStorage) {

    fun load(): AppSettings {
        val defaults = AppSettings()
        val language = storage.getString(KEY_LANGUAGE, null)
            ?.let { raw -> runCatching { AppLanguage.valueOf(raw) }.getOrNull() }
            ?: defaults.language
        val theme = storage.getString(KEY_THEME, null)
            ?.let { raw -> runCatching { AppTheme.valueOf(raw) }.getOrNull() }
            ?: defaults.theme
        val clock = storage.getString(KEY_CLOCK, null)
            ?.let { raw -> runCatching { ClockFormat.valueOf(raw) }.getOrNull() }
            ?: defaults.clock
        val degreeDecimals = storage.getBoolean(KEY_DEGREE_DECIMALS, defaults.degreeDecimals)
        return AppSettings(
            language = language,
            theme = theme,
            clock = clock,
            degreeDecimals = degreeDecimals,
        )
    }

    fun save(settings: AppSettings) {
        storage.putString(KEY_LANGUAGE, settings.language.name)
        storage.putString(KEY_THEME, settings.theme.name)
        storage.putString(KEY_CLOCK, settings.clock.name)
        storage.putBoolean(KEY_DEGREE_DECIMALS, settings.degreeDecimals)
    }

    companion object {
        const val PREFS_NAME = "cenit_settings"
        const val KEY_LANGUAGE = "language"
        const val KEY_THEME = "theme"
        const val KEY_CLOCK = "clock"
        const val KEY_DEGREE_DECIMALS = "degree_decimals"

        /**
         * Builds a [SettingsStore] from [context]. Safe to call from `Activity.attachBaseContext`
         * where `applicationContext` may still be null.
         */
        fun fromContext(context: Context): SettingsStore {
            val target = context.applicationContext ?: context
            val prefs = target.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return SettingsStore(SharedPreferencesSettingsStorage(prefs))
        }
    }
}
