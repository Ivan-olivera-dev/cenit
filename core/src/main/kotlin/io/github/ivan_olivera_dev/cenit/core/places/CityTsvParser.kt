package io.github.ivan_olivera_dev.cenit.core.places

import java.io.BufferedReader
import java.io.Reader

/**
 * Parses Cenit's compact UTF-8 GeoNames TSV (`name`, `ascii_name`, `admin1_name`,
 * `country_code`, `lat`, `lon`, `population`, `timezone`).
 */
object CityTsvParser {
    private const val EXPECTED_COLUMNS = 8

    fun parse(reader: Reader): List<CityEntry> {
        val buffered = reader as? BufferedReader ?: BufferedReader(reader)
        return parseLines(buffered.lineSequence())
    }

    fun parseLines(lines: Sequence<String>): List<CityEntry> {
        val out = ArrayList<CityEntry>()
        for (rawLine in lines) {
            if (rawLine.isBlank() || rawLine.startsWith('#')) continue
            val parts = rawLine.split('\t')
            if (parts.size < EXPECTED_COLUMNS) continue

            val name = parts[0].trim()
            if (name.isEmpty()) continue
            val asciiName = parts[1].trim().ifEmpty { name }
            val admin1 = parts[2].trim()
            val countryCode = parts[3].trim()
            val lat = parts[4].trim().toDoubleOrNull() ?: continue
            val lon = parts[5].trim().toDoubleOrNull() ?: continue
            val population = parts[6].trim().toLongOrNull() ?: continue
            val timezoneId = parts[7].trim()

            if (!lat.isFinite() || lat !in -90.0..90.0) continue
            if (!lon.isFinite() || lon !in -180.0..180.0) continue
            if (population < 0L || countryCode.isEmpty() || timezoneId.isEmpty()) continue

            out.add(
                CityEntry(
                    name = name,
                    asciiName = asciiName,
                    admin1 = admin1,
                    countryCode = countryCode,
                    latitude = lat,
                    longitude = lon,
                    population = population,
                    timezoneId = timezoneId,
                ),
            )
        }
        return out
    }
}
