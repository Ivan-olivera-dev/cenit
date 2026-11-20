package io.github.ivan_olivera_dev.cenit.settings

import io.github.ivan_olivera_dev.cenit.core.format.ClockFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AppSettingsTest {

    // --- Defaults ---

    @Test
    fun `default settings match pre-settings app behavior`() {
        val defaults = AppSettings()
        assertEquals(AppLanguage.SYSTEM, defaults.language)
        assertEquals(AppTheme.SYSTEM, defaults.theme)
        assertEquals(ClockFormat.H24, defaults.clock)
        assertTrue(defaults.degreeDecimals)
    }

    // --- Theme resolution ---

    @Test
    fun `theme resolution follows system or overrides explicitly`() {
        assertTrue(resolveDarkTheme(AppTheme.SYSTEM, systemInDarkTheme = true))
        assertFalse(resolveDarkTheme(AppTheme.SYSTEM, systemInDarkTheme = false))
        assertFalse(resolveDarkTheme(AppTheme.LIGHT, systemInDarkTheme = true))
        assertFalse(resolveDarkTheme(AppTheme.LIGHT, systemInDarkTheme = false))
        assertTrue(resolveDarkTheme(AppTheme.DARK, systemInDarkTheme = true))
        assertTrue(resolveDarkTheme(AppTheme.DARK, systemInDarkTheme = false))
    }

    // --- Locale resolution (setting + device locale -> Locale) ---

    @Test
    fun `locale resolution preserves device locale for SYSTEM and forces es or en otherwise`() {
        val esEs = Locale.forLanguageTag("es-ES")
        val enUs = Locale.US
        val frFr = Locale.FRANCE

        // SYSTEM follows device locale untouched
        assertEquals(esEs, LocaleResolution.resolveAppLocale(AppLanguage.SYSTEM, esEs))
        assertEquals(enUs, LocaleResolution.resolveAppLocale(AppLanguage.SYSTEM, enUs))
        assertEquals(frFr, LocaleResolution.resolveAppLocale(AppLanguage.SYSTEM, frFr))

        // ES forces Spanish regardless of device locale
        val forcedEs = LocaleResolution.resolveAppLocale(AppLanguage.ES, enUs)
        assertEquals("es", forcedEs.language)
        assertEquals("es", LocaleResolution.resolveAppLocale(AppLanguage.ES, frFr).language)

        // EN forces English regardless of device locale
        val forcedEn = LocaleResolution.resolveAppLocale(AppLanguage.EN, esEs)
        assertEquals("en", forcedEn.language)
        assertEquals("en", LocaleResolution.resolveAppLocale(AppLanguage.EN, frFr).language)
    }

    @Test
    fun `language tag mapping handles empty list, regional tags, and unknown tags`() {
        assertNull(AppLanguage.SYSTEM.toLanguageTagOrNull())
        assertEquals("es", AppLanguage.ES.toLanguageTagOrNull())
        assertEquals("en", AppLanguage.EN.toLanguageTagOrNull())

        assertEquals(AppLanguage.SYSTEM, LocaleResolution.fromLanguageTags(emptyList()))
        assertEquals(AppLanguage.ES, LocaleResolution.fromLanguageTags(listOf("es")))
        assertEquals(AppLanguage.ES, LocaleResolution.fromLanguageTags(listOf("es-ES", "en-US")))
        assertEquals(AppLanguage.ES, LocaleResolution.fromLanguageTags(listOf("ES-MX")))
        assertEquals(AppLanguage.EN, LocaleResolution.fromLanguageTags(listOf("en")))
        assertEquals(AppLanguage.EN, LocaleResolution.fromLanguageTags(listOf("en-GB")))
        assertEquals(AppLanguage.SYSTEM, LocaleResolution.fromLanguageTags(listOf("fr-FR")))
        assertEquals(AppLanguage.SYSTEM, LocaleResolution.fromLanguageTags(listOf("")))
    }

    // --- Persistence round-trip & corrupt value resilience ---

    @Test
    fun `settings store round-trips every preference and recovers from unknown stored values`() {
        val backing = InMemorySettingsStorage()
        val store = SettingsStore(backing)

        // Empty storage returns defaults
        assertEquals(AppSettings(), store.load())

        val custom = AppSettings(
            language = AppLanguage.EN,
            theme = AppTheme.LIGHT,
            clock = ClockFormat.H12,
            degreeDecimals = false,
        )
        store.save(custom)
        assertEquals(custom, store.load())

        // Corrupt / unknown string values fall back to safe defaults without throwing
        backing.putString(SettingsStore.KEY_LANGUAGE, "BOGUS")
        backing.putString(SettingsStore.KEY_THEME, "ULTRA_DARK")
        backing.putString(SettingsStore.KEY_CLOCK, "H99")
        val recovered = store.load()
        assertEquals(AppLanguage.SYSTEM, recovered.language)
        assertEquals(AppTheme.SYSTEM, recovered.theme)
        assertEquals(ClockFormat.H24, recovered.clock)
        assertFalse(recovered.degreeDecimals)
    }

    private class InMemorySettingsStorage : SettingsStorage {
        private val strings = LinkedHashMap<String, String>()
        private val booleans = LinkedHashMap<String, Boolean>()

        override fun getString(key: String, defaultValue: String?): String? =
            strings[key] ?: defaultValue

        override fun putString(key: String, value: String) {
            strings[key] = value
        }

        override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
            booleans[key] ?: defaultValue

        override fun putBoolean(key: String, value: Boolean) {
            booleans[key] = value
        }
    }
}
