package io.github.ivan_olivera_dev.cenit.data

import android.content.Context
import io.github.ivan_olivera_dev.cenit.core.places.CityIndex
import io.github.ivan_olivera_dev.cenit.core.places.CityTsvParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

/**
 * Loads and caches the bundled offline GeoNames city index from `assets/geonames/cities.tsv`.
 */
object CityRepository {
    private const val ASSET_PATH = "geonames/cities.tsv"

    @Volatile
    private var cachedIndex: CityIndex? = null

    suspend fun loadIndex(context: Context): CityIndex {
        cachedIndex?.let { return it }
        val appContext = context.applicationContext
        return withContext(Dispatchers.IO) {
            cachedIndex?.let { return@withContext it }
            val index = runCatching {
                appContext.assets.open(ASSET_PATH).use { stream ->
                    BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                        CityIndex.fromEntries(CityTsvParser.parse(reader))
                    }
                }
            }.getOrElse { CityIndex.empty() }
            cachedIndex = index
            index
        }
    }

    fun localizedCountryName(countryCode: String, locale: Locale): String {
        val trimmed = countryCode.trim()
        if (trimmed.isEmpty()) return ""
        val display = Locale.Builder().setRegion(trimmed).build().getDisplayCountry(locale)
        return display.ifBlank { trimmed }
    }

    /**
     * Resolves the ISO 3166-1 alpha-2 country code to prefer in city search: the device [locale]'s
     * two-letter region when present, or lazily [fallbackCountryCode] (e.g. the active place's
     * country) when the locale carries no two-letter country code (such as `es` or `es-419`).
     */
    fun resolvePreferredCountryCode(
        locale: Locale,
        fallbackCountryCode: () -> String? = { null },
    ): String? {
        normalizeIsoCountryCode(locale.country)?.let { return it }
        return normalizeIsoCountryCode(fallbackCountryCode())
    }

    private fun normalizeIsoCountryCode(raw: String?): String? {
        val code = raw?.trim()?.uppercase(Locale.ROOT) ?: return null
        return if (code.length == 2 && code[0] in 'A'..'Z' && code[1] in 'A'..'Z') code else null
    }
}
