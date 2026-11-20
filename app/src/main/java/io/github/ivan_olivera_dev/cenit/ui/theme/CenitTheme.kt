package io.github.ivan_olivera_dev.cenit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.ivan_olivera_dev.cenit.settings.AppTheme
import io.github.ivan_olivera_dev.cenit.settings.resolveDarkTheme

/**
 * Colour tokens of design/system.md, one field per token. Light is Scrolless
 * paper, dark is Sereno ink; sun and moon are the two accents.
 */
@Immutable
data class CenitPalette(
    val bgColor: Color,
    val surfaceColor: Color,
    val surfaceSubtle: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    /** Decorative hairline only. */
    val borderColor: Color,
    /** Outlines of interactive controls (at least 3:1). */
    val controlBorder: Color,
    val sun: Color,
    val moon: Color,
    val blueHour: Color,
    /** Chart band: sun above the horizon. */
    val skyDay: Color,
    /** Chart band: civil twilight, 0° to −6°. */
    val skyCivil: Color,
    /** Chart band: nautical twilight, −6° to −12°. */
    val skyNautical: Color,
    /** Chart band: astronomical twilight, −12° to −18°. */
    val skyAstro: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val focusRing: Color,
    val isDark: Boolean
)

val DarkCenitPalette = CenitPalette(
    bgColor = Color(0xFF111318),
    surfaceColor = Color(0xFF171A21),
    surfaceSubtle = Color(0xFF1F232C),
    textPrimary = Color(0xFFE9E7E2),
    textSecondary = Color(0xFFA6ABB5),
    borderColor = Color(0xFF272B35),
    controlBorder = Color(0xFF6C7382),
    sun = Color(0xFFD6C07E),
    moon = Color(0xFFAFBDE3),
    blueHour = Color(0xFF7F93D0),
    skyDay = Color(0xFF272C38),
    skyCivil = Color(0xFF212532),
    skyNautical = Color(0xFF1B1F29),
    skyAstro = Color(0xFF161920),
    success = Color(0xFF8DB38F),
    warning = Color(0xFFD6C07E),
    danger = Color(0xFFE39A93),
    focusRing = Color(0xFFAFBDE3),
    isDark = true
)

val LightCenitPalette = CenitPalette(
    bgColor = Color(0xFFFAF9F6),
    surfaceColor = Color(0xFFF3F1EC),
    surfaceSubtle = Color(0xFFECE9E2),
    textPrimary = Color(0xFF2C2C2C),
    textSecondary = Color(0xFF5C5A54),
    borderColor = Color(0xFFE6E3DC),
    controlBorder = Color(0xFF857F74),
    sun = Color(0xFF7A6220),
    moon = Color(0xFF3D5896),
    blueHour = Color(0xFF4F67A6),
    skyDay = Color(0xFFFAF9F6),
    skyCivil = Color(0xFFEEEBE4),
    skyNautical = Color(0xFFE5E1D8),
    skyAstro = Color(0xFFDCD7CB),
    success = Color(0xFF4A784E),
    warning = Color(0xFF7A6220),
    danger = Color(0xFFA2443C),
    focusRing = Color(0xFF3D5896),
    isDark = false
)

val LocalCenitPalette = staticCompositionLocalOf { DarkCenitPalette }

/** Type scale of design/system.md: system sans everywhere, serif for the focal numbers. */
val CenitTypography = Typography(
    // --type-focal: the one focal time per screen.
    displayLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Light,
        fontSize = 52.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.03).em
    ),
    // --type-display: compass heading, secondary focal number.
    displayMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Light,
        fontSize = 36.sp,
        lineHeight = 40.sp,
        letterSpacing = (-1.2).sp
    ),
    // --type-headline: screen title.
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-1).sp
    ),
    // --type-title: event times in rows.
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.4).sp
    ),
    // --type-body-large: button labels, place names.
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.3).sp
    ),
    // --type-body
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    // --type-small: captions, azimuths, chart labels.
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    // --type-label: uppercase eyebrows (apply uppercase at the call site).
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.sp
    )
)

@Composable
fun CenitTheme(
    theme: AppTheme,
    content: @Composable () -> Unit,
) {
    CenitTheme(
        darkTheme = resolveDarkTheme(theme, isSystemInDarkTheme()),
        content = content,
    )
}

@Composable
fun CenitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkCenitPalette else LightCenitPalette
    val colorScheme = if (darkTheme) {
        darkColorScheme(
            background = palette.bgColor,
            surface = palette.surfaceColor,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            primary = palette.textPrimary,
            onPrimary = palette.bgColor,
            error = palette.danger
        )
    } else {
        lightColorScheme(
            background = palette.bgColor,
            surface = palette.surfaceColor,
            onBackground = palette.textPrimary,
            onSurface = palette.textPrimary,
            primary = palette.textPrimary,
            onPrimary = palette.bgColor,
            error = palette.danger
        )
    }

    CompositionLocalProvider(LocalCenitPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CenitTypography,
            content = content
        )
    }
}
