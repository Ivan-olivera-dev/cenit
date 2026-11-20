package io.github.ivan_olivera_dev.cenit.core.places

import io.github.ivan_olivera_dev.cenit.core.Place
import java.time.ZoneId

/**
 * One city row from Cenit's bundled offline GeoNames extract.
 */
data class CityEntry(
    val name: String,
    val asciiName: String,
    val admin1: String,
    val countryCode: String,
    val latitude: Double,
    val longitude: Double,
    val population: Long,
    val timezoneId: String,
) {
    fun toPlace(): Place = Place(latitude = latitude, longitude = longitude)

    fun zoneIdOrNull(): ZoneId? = runCatching { ZoneId.of(timezoneId) }.getOrNull()
}

/**
 * A ranked offline city search hit paired with a disambiguated region label (`admin1 · country`).
 */
data class CitySearchResult(
    val city: CityEntry,
    val regionLabel: String,
)
