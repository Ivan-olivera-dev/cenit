package io.github.ivan_olivera_dev.cenit.core.places

import io.github.ivan_olivera_dev.cenit.core.Place
import kotlin.math.abs

/**
 * Exact validation error reasons for manual coordinate entry.
 */
enum class CoordinateError {
    EMPTY,
    INVALID_FORMAT,
    OUT_OF_RANGE,
}

sealed interface CoordinateParseResult {
    data class Valid(val value: Double) : CoordinateParseResult
    data class Invalid(val error: CoordinateError) : CoordinateParseResult
}

sealed interface CoordinatesValidationResult {
    data class Valid(val place: Place) : CoordinatesValidationResult
    data class Invalid(
        val latitudeError: CoordinateError?,
        val longitudeError: CoordinateError?,
    ) : CoordinatesValidationResult
}

/**
 * Strict parser and validator for manual latitude (`-90..90`) and longitude (`-180..180`) input.
 * Accepts dot or comma decimal separators and ASCII or Unicode minus (`−`); rejects garbage.
 */
object CoordinateParser {
    private val DECIMAL_REGEX = Regex("""^[+\-\u2212]?(\d+([.,]\d+)?|[.,]\d+)$""")

    fun parseLatitude(raw: String): CoordinateParseResult =
        parseCoordinate(raw, min = -90.0, max = 90.0)

    fun parseLongitude(raw: String): CoordinateParseResult =
        parseCoordinate(raw, min = -180.0, max = 180.0)

    fun validate(rawLatitude: String, rawLongitude: String): CoordinatesValidationResult {
        val latResult = parseLatitude(rawLatitude)
        val lonResult = parseLongitude(rawLongitude)
        return if (latResult is CoordinateParseResult.Valid && lonResult is CoordinateParseResult.Valid) {
            CoordinatesValidationResult.Valid(
                Place(latitude = latResult.value, longitude = lonResult.value),
            )
        } else {
            CoordinatesValidationResult.Invalid(
                latitudeError = (latResult as? CoordinateParseResult.Invalid)?.error,
                longitudeError = (lonResult as? CoordinateParseResult.Invalid)?.error,
            )
        }
    }

    private fun parseCoordinate(raw: String, min: Double, max: Double): CoordinateParseResult {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return CoordinateParseResult.Invalid(CoordinateError.EMPTY)
        if (!DECIMAL_REGEX.matches(trimmed)) {
            return CoordinateParseResult.Invalid(CoordinateError.INVALID_FORMAT)
        }
        val normalized = trimmed
            .replace('\u2212', '-')
            .replace(',', '.')
        val value = normalized.toDoubleOrNull()
            ?: return CoordinateParseResult.Invalid(CoordinateError.INVALID_FORMAT)
        if (!value.isFinite()) {
            return CoordinateParseResult.Invalid(CoordinateError.INVALID_FORMAT)
        }
        if (value < min || value > max) {
            return CoordinateParseResult.Invalid(CoordinateError.OUT_OF_RANGE)
        }
        val clean = if (abs(value) == 0.0) 0.0 else value
        return CoordinateParseResult.Valid(clean)
    }
}
