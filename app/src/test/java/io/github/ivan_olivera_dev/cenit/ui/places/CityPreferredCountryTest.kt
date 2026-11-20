package io.github.ivan_olivera_dev.cenit.ui.places

import io.github.ivan_olivera_dev.cenit.data.CityRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class CityPreferredCountryTest {

    @Test
    fun `uses locale ISO alpha-2 region when present without calling fallback`() {
        var fallbackCalled = false
        val es = CityRepository.resolvePreferredCountryCode(
            locale = Locale.forLanguageTag("es-ES"),
            fallbackCountryCode = {
                fallbackCalled = true
                "VE"
            },
        )
        assertEquals("ES", es)
        assertEquals(false, fallbackCalled)

        val us = CityRepository.resolvePreferredCountryCode(
            locale = Locale.forLanguageTag("en-US"),
            fallbackCountryCode = { "ES" },
        )
        assertEquals("US", us)
    }

    @Test
    fun `falls back to active place country when locale region is absent or non-ISO`() {
        assertEquals(
            "ES",
            CityRepository.resolvePreferredCountryCode(
                locale = Locale.forLanguageTag("es"),
                fallbackCountryCode = { "es" },
            ),
        )
        // UN M.49 numeric region (Latin America, 419) is not a 2-letter GeoNames country code
        assertEquals(
            "VE",
            CityRepository.resolvePreferredCountryCode(
                locale = Locale.forLanguageTag("es-419"),
                fallbackCountryCode = { "VE" },
            ),
        )
        assertNull(
            CityRepository.resolvePreferredCountryCode(
                locale = Locale.forLanguageTag("es"),
                fallbackCountryCode = { null },
            ),
        )
    }
}
