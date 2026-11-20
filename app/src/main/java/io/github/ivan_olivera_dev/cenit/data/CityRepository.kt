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
}
