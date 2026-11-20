package io.github.ivan_olivera_dev.cenit.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The palettes must match the accepted tokens of design/system.md. */
class CenitPaletteTest {
    @Test
    fun `dark palette matches design system tokens`() {
        val p = DarkCenitPalette
        assertEquals(Color(0xFF111318), p.bgColor)
        assertEquals(Color(0xFF171A21), p.surfaceColor)
        assertEquals(Color(0xFF1F232C), p.surfaceSubtle)
        assertEquals(Color(0xFFE9E7E2), p.textPrimary)
        assertEquals(Color(0xFFA6ABB5), p.textSecondary)
        assertEquals(Color(0xFF272B35), p.borderColor)
        assertEquals(Color(0xFF6C7382), p.controlBorder)
        assertEquals(Color(0xFFD6C07E), p.sun)
        assertEquals(Color(0xFFAFBDE3), p.moon)
        assertEquals(Color(0xFF7F93D0), p.blueHour)
        assertEquals(Color(0xFF272C38), p.skyDay)
        assertEquals(Color(0xFF212532), p.skyCivil)
        assertEquals(Color(0xFF1B1F29), p.skyNautical)
        assertEquals(Color(0xFF161920), p.skyAstro)
        assertEquals(Color(0xFF8DB38F), p.success)
        assertEquals(Color(0xFFD6C07E), p.warning)
        assertEquals(Color(0xFFE39A93), p.danger)
        assertEquals(Color(0xFFAFBDE3), p.focusRing)
        assertTrue(p.isDark)
    }

    @Test
    fun `light palette matches design system tokens`() {
        val p = LightCenitPalette
        assertEquals(Color(0xFFFAF9F6), p.bgColor)
        assertEquals(Color(0xFFF3F1EC), p.surfaceColor)
        assertEquals(Color(0xFFECE9E2), p.surfaceSubtle)
        assertEquals(Color(0xFF2C2C2C), p.textPrimary)
        assertEquals(Color(0xFF5C5A54), p.textSecondary)
        assertEquals(Color(0xFFE6E3DC), p.borderColor)
        assertEquals(Color(0xFF857F74), p.controlBorder)
        assertEquals(Color(0xFF7A6220), p.sun)
        assertEquals(Color(0xFF3D5896), p.moon)
        assertEquals(Color(0xFF4F67A6), p.blueHour)
        assertEquals(Color(0xFFFAF9F6), p.skyDay)
        assertEquals(Color(0xFFEEEBE4), p.skyCivil)
        assertEquals(Color(0xFFE5E1D8), p.skyNautical)
        assertEquals(Color(0xFFDCD7CB), p.skyAstro)
        assertEquals(Color(0xFF4A784E), p.success)
        assertEquals(Color(0xFF7A6220), p.warning)
        assertEquals(Color(0xFFA2443C), p.danger)
        assertEquals(Color(0xFF3D5896), p.focusRing)
        assertFalse(p.isDark)
    }
}
