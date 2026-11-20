package io.github.ivan_olivera_dev.cenit.core

import org.junit.Assert.assertEquals
import org.junit.Test

class CenitTest {
    @Test
    fun `rise and set use the standard horizon altitude`() {
        assertEquals(-0.833, Cenit.RISE_SET_ALTITUDE_DEG, 0.0)
    }
}
