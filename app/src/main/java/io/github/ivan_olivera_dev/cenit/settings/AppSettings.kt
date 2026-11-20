package io.github.ivan_olivera_dev.cenit.settings

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import io.github.ivan_olivera_dev.cenit.core.format.ClockFormat
import java.util.Locale

/**
 * In-app language preference.
 *
 * [SYSTEM] follows the device locale (or Android 13+ system setting when empty).
 * [ES] and [EN] force Spanish or English respectively.
 */
enum class AppLanguage {
    SYSTEM,
    ES,
    EN;

    /** BCP-47 language tag for explicit choices, or `null` for [SYSTEM]. */
    fun toLanguageTagOrNull(): String? = when (this) {
        SYSTEM -> null
        ES -> "es"
        EN -> "en"
    }
}

/**
 * Theme preference: follow the system dark mode or force light/dark.
 */
enum class AppTheme {
    SYSTEM,
    LIGHT,
    DARK,
}

/**
 * User preferences persisted locally in SharedPreferences (excluded from backups).
 *
 * Defaults (`SYSTEM`, `SYSTEM`, `H24`, `true`) preserve Cenit's pre-settings behaviour.
 */
@Immutable
data class AppSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val theme: AppTheme = AppTheme.SYSTEM,
    val clock: ClockFormat = ClockFormat.H24,
    val degreeDecimals: Boolean = true,
)

/**
 * Resolves whether the dark palette and dark system bars should be active.
 */
fun resolveDarkTheme(theme: AppTheme, systemInDarkTheme: Boolean): Boolean = when (theme) {
    AppTheme.SYSTEM -> systemInDarkTheme
    AppTheme.LIGHT -> false
    AppTheme.DARK -> true
}

/**
 * Pure JVM locale-resolution logic for [AppLanguage] + device [Locale].
 */
object LocaleResolution {
    private val LOCALE_ES: Locale = Locale.forLanguageTag("es")
    private val LOCALE_EN: Locale = Locale.forLanguageTag("en")

    /**
     * Resolves the effective [Locale] given the user's [language] setting and the [deviceLocale].
     */
    fun resolveAppLocale(language: AppLanguage, deviceLocale: Locale): Locale = when (language) {
        AppLanguage.SYSTEM -> deviceLocale
        AppLanguage.ES -> LOCALE_ES
        AppLanguage.EN -> LOCALE_EN
    }

    /**
     * Maps a list of BCP-47 language tags (for example from Android 13+ `LocaleManager`)
     * back to the matching [AppLanguage].
     */
    fun fromLanguageTags(tags: List<String>): AppLanguage {
        val primary = tags.firstOrNull()?.trim().orEmpty()
        if (primary.isEmpty()) return AppLanguage.SYSTEM
        val lang = Locale.forLanguageTag(primary).language.lowercase(Locale.ROOT)
        return when (lang) {
            "es" -> AppLanguage.ES
            "en" -> AppLanguage.EN
            else -> AppLanguage.SYSTEM
        }
    }
}

val LocalAppSettings = compositionLocalOf { AppSettings() }
