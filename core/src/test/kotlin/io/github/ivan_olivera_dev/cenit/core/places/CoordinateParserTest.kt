package io.github.ivan_olivera_dev.cenit.core.places

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateParserTest {

    @Test
    fun `parses valid dot and comma decimals including Unicode minus and boundaries`() {
        val dot = CoordinateParser.validate("40.4165", "-3.7026")
        assertTrue(dot is CoordinatesValidationResult.Valid)
        val dotPlace = (dot as CoordinatesValidationResult.Valid).place
        assertEquals(40.4165, dotPlace.latitude, 1e-6)
        assertEquals(-3.7026, dotPlace.longitude, 1e-6)

        val comma = CoordinateParser.validate(" 39,2817 ", " −0,2763 ")
        assertTrue(comma is CoordinatesValidationResult.Valid)
        val commaPlace = (comma as CoordinatesValidationResult.Valid).place
        assertEquals(39.2817, commaPlace.latitude, 1e-6)
        assertEquals(-0.2763, commaPlace.longitude, 1e-6)

        val minBounds = CoordinateParser.validate("-90", "-180")
        assertTrue(minBounds is CoordinatesValidationResult.Valid)
        assertEquals(-90.0, (minBounds as CoordinatesValidationResult.Valid).place.latitude, 1e-6)
        assertEquals(-180.0, minBounds.place.longitude, 1e-6)

        val maxBounds = CoordinateParser.validate("+90.0", "+180,0")
        assertTrue(maxBounds is CoordinatesValidationResult.Valid)
        assertEquals(90.0, (maxBounds as CoordinatesValidationResult.Valid).place.latitude, 1e-6)
        assertEquals(180.0, maxBounds.place.longitude, 1e-6)

        val negZero = CoordinateParser.parseLatitude("-0.0")
        assertEquals(CoordinateParseResult.Valid(0.0), negZero)
    }

    @Test
    fun `rejects empty and blank inputs with EMPTY error`() {
        assertEquals(CoordinateParseResult.Invalid(CoordinateError.EMPTY), CoordinateParser.parseLatitude(""))
        assertEquals(CoordinateParseResult.Invalid(CoordinateError.EMPTY), CoordinateParser.parseLongitude("   "))

        val result = CoordinateParser.validate("", "   ")
        assertEquals(
            CoordinatesValidationResult.Invalid(
                latitudeError = CoordinateError.EMPTY,
                longitudeError = CoordinateError.EMPTY,
            ),
            result,
        )
    }

    @Test
    fun `rejects out-of-range latitude and longitude with OUT_OF_RANGE error`() {
        assertEquals(CoordinateParseResult.Invalid(CoordinateError.OUT_OF_RANGE), CoordinateParser.parseLatitude("90.0001"))
        assertEquals(CoordinateParseResult.Invalid(CoordinateError.OUT_OF_RANGE), CoordinateParser.parseLatitude("-90.0001"))
        assertEquals(CoordinateParseResult.Invalid(CoordinateError.OUT_OF_RANGE), CoordinateParser.parseLongitude("180.0001"))
        assertEquals(CoordinateParseResult.Invalid(CoordinateError.OUT_OF_RANGE), CoordinateParser.parseLongitude("-180,5"))

        val mixed = CoordinateParser.validate("91", "-3.7")
        assertTrue(mixed is CoordinatesValidationResult.Invalid)
        val invalid = mixed as CoordinatesValidationResult.Invalid
        assertEquals(CoordinateError.OUT_OF_RANGE, invalid.latitudeError)
        assertNull(invalid.longitudeError)
    }

    @Test
    fun `rejects garbage and malformed inputs with INVALID_FORMAT error`() {
        val badValues = listOf(
            "abc",
            "40.1.2",
            "40,1.2",
            "40.4 N",
            "NaN",
            "Infinity",
            "-Infinity",
            "1e2",
            ".",
            ",",
            "-",
            "+",
            "40.",
            "40,",
            "40 12",
        )
        for (raw in badValues) {
            assertEquals(
                "Expected INVALID_FORMAT for latitude '$raw'",
                CoordinateParseResult.Invalid(CoordinateError.INVALID_FORMAT),
                CoordinateParser.parseLatitude(raw),
            )
            assertEquals(
                "Expected INVALID_FORMAT for longitude '$raw'",
                CoordinateParseResult.Invalid(CoordinateError.INVALID_FORMAT),
                CoordinateParser.parseLongitude(raw),
            )
        }
    }
}
