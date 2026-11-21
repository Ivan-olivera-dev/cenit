package io.github.ivan_olivera_dev.cenit.ui.camera

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ivan_olivera_dev.cenit.R
import io.github.ivan_olivera_dev.cenit.core.Cenit
import io.github.ivan_olivera_dev.cenit.core.MoonPhase
import io.github.ivan_olivera_dev.cenit.core.SkyPosition
import io.github.ivan_olivera_dev.cenit.core.ar.HeadingTapeModel
import io.github.ivan_olivera_dev.cenit.core.compass.CompassMath
import io.github.ivan_olivera_dev.cenit.core.format.CenitFormat
import io.github.ivan_olivera_dev.cenit.core.format.ClockFormat
import io.github.ivan_olivera_dev.cenit.ui.components.CenitIcon
import io.github.ivan_olivera_dev.cenit.ui.components.CenitIconKind
import io.github.ivan_olivera_dev.cenit.ui.components.cenitFocusRing
import io.github.ivan_olivera_dev.cenit.ui.theme.DarkCenitPalette
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

// All HUD colours come strictly from DarkCenitPalette (design/system.md) for contrast over live sky.
internal val VisorHudBg: Color = DarkCenitPalette.bgColor
internal val VisorHudSurface: Color = DarkCenitPalette.surfaceColor
internal val VisorHudSubtle: Color = DarkCenitPalette.surfaceSubtle
internal val VisorHudFg: Color = DarkCenitPalette.textPrimary
internal val VisorHudMuted: Color = DarkCenitPalette.textSecondary
internal val VisorHudHairline: Color = DarkCenitPalette.borderColor
internal val VisorHudBorder: Color = DarkCenitPalette.controlBorder
internal val VisorHudSun: Color = DarkCenitPalette.sun
internal val VisorHudMoon: Color = DarkCenitPalette.moon
internal val VisorHudWarning: Color = DarkCenitPalette.warning
internal val VisorHudFocus: Color = DarkCenitPalette.focusRing
internal val VisorHudScrim: Color = DarkCenitPalette.bgColor.copy(alpha = 0.84f)
internal val VisorHudScrimStrong: Color = DarkCenitPalette.bgColor.copy(alpha = 0.92f)

/**
 * Top HUD row beside [CameraScreen]'s top-left back button (`01 · Visor` artboard):
 * active place pill and moon-phase pill.
 */
@Composable
internal fun VisorTopBar(
    placeName: String,
    moonPhase: MoonPhase,
    moonPhaseName: String,
    modifier: Modifier = Modifier,
) {
    val illuminationPercent = (moonPhase.illumination * 100.0).roundToInt().coerceIn(0, 100)
    val placeDesc = stringResource(R.string.visor_cd_place_pill, placeName)
    val moonDesc = stringResource(R.string.cd_phase_glyph, moonPhaseName, illuminationPercent)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .weight(1f, fill = false)
                .heightIn(min = 36.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(VisorHudScrim)
                .border(1.dp, VisorHudBorder, RoundedCornerShape(999.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .semantics { contentDescription = placeDesc },
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = placeName,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = VisorHudFg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Row(
            modifier = Modifier
                .heightIn(min = 36.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(VisorHudScrim)
                .border(1.dp, VisorHudBorder, RoundedCornerShape(999.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .semantics(mergeDescendants = true) { contentDescription = moonDesc },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HudMoonPhaseGlyph(phase = moonPhase, size = 14.dp)
            Text(
                text = stringResource(R.string.visor_moon_pill, illuminationPercent),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontFeatureSettings = "tnum",
                ),
                color = VisorHudFg,
                maxLines = 1,
            )
        }
    }
}

/**
 * Translucent top heading tape (`01 · Visor` artboard): pointing azimuth + 16-point wind badge,
 * 5° ticks, 15° labels, localized 45°/90° cardinals, and live sun/moon bearing dots.
 */
@Composable
internal fun VisorHeadingTape(
    headingDeg: Double,
    winds: Array<String>,
    sunPosition: SkyPosition,
    moonPosition: SkyPosition,
    layers: VisorLayers,
    locale: Locale,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val wholeDeg = CompassMath.roundToWholeDegree(headingDeg)
    val headingText = CenitFormat.formatAngle(wholeDeg.toDouble(), withDecimals = false, locale = locale)
    val windText = winds[CompassMath.windIndex(headingDeg)]
    val badgeText = "$headingText $windText"
    val tapeDesc = stringResource(R.string.visor_cd_heading_tape, headingText, windText)

    val paints = remember(density) {
        val labelPx = with(density) { 10.5.sp.toPx() }
        val badgePx = with(density) { 10.5.sp.toPx() }
        val cardinalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = labelPx
            color = VisorHudFg.toArgb()
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            fontFeatureSettings = "tnum"
        }
        val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = labelPx
            color = VisorHudMuted.toArgb()
            textAlign = Paint.Align.CENTER
            typeface = Typeface.SANS_SERIF
            fontFeatureSettings = "tnum"
        }
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = badgePx
            color = VisorHudFg.toArgb()
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            fontFeatureSettings = "tnum"
        }
        Triple(cardinalPaint, mutedPaint, badgePaint)
    }
    val lubberPath = remember { Path() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .semantics {
                contentDescription = tapeDesc
                this.stateDescription = badgeText
            },
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val cornerR = 12.dp.toPx()
        val stroke1 = 1.dp.toPx()
        val insetPx = 8.dp.toPx()
        val tickTop = 6.dp.toPx()
        val tickMajorBottom = 14.dp.toPx()
        val tickMinorBottom = 10.dp.toPx()
        val labelY = 27.dp.toPx()

        drawRoundRect(
            color = VisorHudScrim,
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = CornerRadius(cornerR, cornerR),
        )
        drawRoundRect(
            color = VisorHudBorder,
            topLeft = Offset(stroke1 / 2f, stroke1 / 2f),
            size = Size(w - stroke1, h - stroke1),
            cornerRadius = CornerRadius(cornerR, cornerR),
            style = Stroke(width = stroke1),
        )

        val (cardinalPaint, mutedPaint, badgePaint) = paints
        val ticks = HeadingTapeModel.ticksForHeading(headingDeg)
        val badgeTextWidth = badgePaint.measureText(badgeText)
        val badgeW = (badgeTextWidth + 16.dp.toPx()).coerceAtLeast(56.dp.toPx())
        val badgeHalfW = badgeW / 2f
        val centerX = w / 2f

        for (i in ticks.indices) {
            val tick = ticks[i]
            val x = (tick.normalizedPosition * w).toFloat()
            if (x < insetPx || x > w - insetPx) continue

            drawLine(
                color = if (tick.isMajor) VisorHudFg else VisorHudBorder,
                start = Offset(x, tickTop),
                end = Offset(x, if (tick.isMajor) tickMajorBottom else tickMinorBottom),
                strokeWidth = stroke1,
            )

            if (tick.isMajor) {
                val label = if (tick.windIndex != null) {
                    winds[tick.windIndex!!]
                } else {
                    tick.azimuthDeg.toString()
                }
                val paint = if (tick.isQuadrant) cardinalPaint else mutedPaint
                drawContext.canvas.nativeCanvas.drawText(label, x, labelY, paint)
            }
        }

        // Sun & moon bearing dots along top track
        val dotY = 9.dp.toPx()
        val dotR = 3.2.dp.toPx()
        val dotStroke = 1.5.dp.toPx()
        if (layers.sun && sunPosition.elevation > -1.0) {
            HeadingTapeModel.markerPosition(headingDeg, sunPosition.azimuth)?.let { pos ->
                val sx = (pos * w).toFloat()
                if (sx in insetPx..(w - insetPx) && abs(sx - centerX) > badgeHalfW + dotR) {
                    drawCircle(color = VisorHudSun, radius = dotR, center = Offset(sx, dotY))
                }
            }
        }
        if (layers.moon && moonPosition.elevation > -1.0) {
            HeadingTapeModel.markerPosition(headingDeg, moonPosition.azimuth)?.let { pos ->
                val mx = (pos * w).toFloat()
                if (mx in insetPx..(w - insetPx) && abs(mx - centerX) > badgeHalfW + dotR) {
                    drawCircle(color = VisorHudBg, radius = dotR, center = Offset(mx, dotY))
                    drawCircle(
                        color = VisorHudMoon,
                        radius = dotR,
                        center = Offset(mx, dotY),
                        style = Stroke(width = dotStroke),
                    )
                }
            }
        }

        // Bottom center lubber triangle
        val triHalf = 5.dp.toPx()
        val triTop = 30.dp.toPx()
        lubberPath.rewind()
        lubberPath.moveTo(centerX - triHalf, h - stroke1)
        lubberPath.lineTo(centerX + triHalf, h - stroke1)
        lubberPath.lineTo(centerX, triTop)
        lubberPath.close()
        drawPath(path = lubberPath, color = VisorHudSun)

        // Center pointing azimuth + wind pill
        val badgeH = 16.dp.toPx()
        val badgeTop = 1.dp.toPx()
        val badgeLeft = centerX - badgeHalfW
        drawRoundRect(
            color = VisorHudBg,
            topLeft = Offset(badgeLeft, badgeTop),
            size = Size(badgeW, badgeH),
            cornerRadius = CornerRadius(badgeH / 2f, badgeH / 2f),
        )
        drawRoundRect(
            color = VisorHudSun,
            topLeft = Offset(badgeLeft, badgeTop),
            size = Size(badgeW, badgeH),
            cornerRadius = CornerRadius(badgeH / 2f, badgeH / 2f),
            style = Stroke(width = stroke1),
        )
        val fm = badgePaint.fontMetrics
        val badgeTextY = badgeTop + badgeH / 2f - (fm.ascent + fm.descent) / 2f
        drawContext.canvas.nativeCanvas.drawText(badgeText, centerX, badgeTextY, badgePaint)
    }
}

/**
 * Calibration notice banner over the live preview using the exact same copy as Brújula
 * (`R.string.compass_calibration_notice`) and the artboard's 3 dp warning rule + figure-8 glyph.
 */
@Composable
internal fun VisorCalibrationNotice(
    modifier: Modifier = Modifier,
) {
    val noticeText = stringResource(R.string.compass_calibration_notice)
    val dimmedSub = stringResource(R.string.visor_calibration_dimmed)
    val shape = RoundedCornerShape(
        topStart = 0.dp,
        bottomStart = 0.dp,
        topEnd = 10.dp,
        bottomEnd = 10.dp,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(VisorHudScrimStrong)
            .border(1.dp, VisorHudHairline, shape)
            .drawBehind {
                val ruleWidth = 3.dp.toPx()
                val startX = if (layoutDirection == LayoutDirection.Rtl) size.width - ruleWidth else 0f
                drawRect(
                    color = VisorHudWarning,
                    topLeft = Offset(startX, 0f),
                    size = Size(ruleWidth, size.height),
                )
            }
            .padding(start = 13.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FigureEightGlyph(tint = VisorHudSun, size = 22.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = noticeText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = VisorHudFg,
            )
            Text(
                text = dimmedSub,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                ),
                color = VisorHudMuted,
            )
        }
    }
}

/**
 * Ground-pointing hint pill shown when camera elevation drops below −28°.
 */
@Composable
internal fun VisorGroundNotice(
    upDegrees: Int,
    modifier: Modifier = Modifier,
) {
    val text = stringResource(R.string.visor_ground_hint, upDegrees)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(VisorHudScrimStrong)
            .border(1.2.dp, VisorHudSun, RoundedCornerShape(999.dp))
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum",
            ),
            color = VisorHudFg,
            maxLines = 1,
        )
    }
}

/**
 * Portrait bottom dock (`01 · Visor` artboard):
 * focal time, date chip (opens `CenitDatePicker`), "Ahora" reset, sky phase & sun readout,
 * time slider with `TimeScrubber` semantics, and Sol / Luna / Solsticios layer toggles.
 */
@Composable
internal fun VisorBottomDock(
    formattedTime: String,
    dateChipLabel: String,
    dateChipFullLabel: String,
    phaseLabel: String,
    sunPosition: SkyPosition,
    selectedFraction: Float,
    layers: VisorLayers,
    winds: Array<String>,
    clock: ClockFormat,
    withDecimals: Boolean,
    locale: Locale,
    onOpenDatePicker: () -> Unit,
    onResetNow: () -> Unit,
    onFractionChange: (Float) -> Unit,
    onStepMinutes: (Int) -> Unit,
    onToggleSun: () -> Unit,
    onToggleMoon: () -> Unit,
    onToggleSolstices: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sunAzText = CenitFormat.formatAngle(
        CompassMath.roundToWholeDegree(sunPosition.azimuth).toDouble(),
        withDecimals = false,
        locale = locale,
    )
    val sunWind = winds[CompassMath.windIndex(sunPosition.azimuth)]
    val metaText = if (sunPosition.elevation <= Cenit.RISE_SET_ALTITUDE_DEG) {
        stringResource(R.string.visor_dock_sun_below, phaseLabel, sunAzText, sunWind)
    } else {
        val sunElevText = CenitFormat.formatAngle(sunPosition.elevation, withDecimals, locale)
        stringResource(R.string.visor_dock_sun_above, phaseLabel, sunAzText, sunWind, sunElevText)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(VisorHudScrimStrong)
            .drawBehind {
                drawLine(
                    color = VisorHudHairline,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontSize = 30.sp,
                    lineHeight = 34.sp,
                    letterSpacing = (-0.8).sp,
                    fontFeatureSettings = "tnum",
                ),
                color = VisorHudFg,
                maxLines = 1,
            )

            Spacer(modifier = Modifier.weight(1f))

            VisorDateChip(
                label = dateChipLabel,
                fullDateLabel = dateChipFullLabel,
                onClick = onOpenDatePicker,
            )

            VisorNowButton(onClick = onResetNow)
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = metaText,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontFeatureSettings = "tnum",
            ),
            color = VisorHudMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        VisorTimeScrubber(
            selectedFraction = selectedFraction,
            formattedTime = formattedTime,
            clock = clock,
            locale = locale,
            onFractionChange = onFractionChange,
            onStepMinutes = onStepMinutes,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VisorLayerToggleButton(
                label = stringResource(R.string.visor_layer_sun),
                kind = VisorLayerKind.SUN,
                selected = layers.sun,
                onToggle = onToggleSun,
                modifier = Modifier.weight(1f),
            )
            VisorLayerToggleButton(
                label = stringResource(R.string.visor_layer_moon),
                kind = VisorLayerKind.MOON,
                selected = layers.moon,
                onToggle = onToggleMoon,
                modifier = Modifier.weight(1f),
            )
            VisorLayerToggleButton(
                label = stringResource(R.string.visor_layer_solstices),
                kind = VisorLayerKind.SOLSTICES,
                selected = layers.solstices,
                onToggle = onToggleSolstices,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Landscape side dock (`01 · Visor` landscape frame): compact right rail preserving full vertical
 * viewfinder height when the activity rotates to landscape.
 */
@Composable
internal fun VisorLandscapeDock(
    placeName: String,
    formattedTime: String,
    dateChipLabel: String,
    dateChipFullLabel: String,
    phaseLabel: String,
    sunPosition: SkyPosition,
    selectedFraction: Float,
    layers: VisorLayers,
    winds: Array<String>,
    clock: ClockFormat,
    withDecimals: Boolean,
    locale: Locale,
    onOpenDatePicker: () -> Unit,
    onResetNow: () -> Unit,
    onFractionChange: (Float) -> Unit,
    onStepMinutes: (Int) -> Unit,
    onToggleSun: () -> Unit,
    onToggleMoon: () -> Unit,
    onToggleSolstices: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sunAzText = CenitFormat.formatAngle(
        CompassMath.roundToWholeDegree(sunPosition.azimuth).toDouble(),
        withDecimals = false,
        locale = locale,
    )
    val sunWind = winds[CompassMath.windIndex(sunPosition.azimuth)]
    val metaText = if (sunPosition.elevation <= Cenit.RISE_SET_ALTITUDE_DEG) {
        stringResource(R.string.visor_dock_sun_below, phaseLabel, sunAzText, sunWind)
    } else {
        val sunElevText = CenitFormat.formatAngle(sunPosition.elevation, withDecimals, locale)
        stringResource(R.string.visor_dock_sun_above, phaseLabel, sunAzText, sunWind, sunElevText)
    }

    Column(
        modifier = modifier
            .width(196.dp)
            .fillMaxHeight()
            .background(VisorHudScrimStrong)
            .drawBehind {
                drawLine(
                    color = VisorHudHairline,
                    start = Offset(0f, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = placeName.uppercase(locale),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp, lineHeight = 14.sp),
                color = VisorHudMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontSize = 26.sp,
                    lineHeight = 30.sp,
                    letterSpacing = (-0.6).sp,
                    fontFeatureSettings = "tnum",
                ),
                color = VisorHudFg,
                maxLines = 1,
            )
            Text(
                text = metaText,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    fontFeatureSettings = "tnum",
                ),
                color = VisorHudMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                VisorDateChip(
                    label = dateChipLabel,
                    fullDateLabel = dateChipFullLabel,
                    onClick = onOpenDatePicker,
                    modifier = Modifier.weight(1f),
                )
                VisorNowButton(onClick = onResetNow)
            }

            VisorTimeScrubber(
                selectedFraction = selectedFraction,
                formattedTime = formattedTime,
                clock = clock,
                locale = locale,
                onFractionChange = onFractionChange,
                onStepMinutes = onStepMinutes,
                compact = true,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            VisorLayerToggleButton(
                label = stringResource(R.string.visor_layer_sun),
                kind = VisorLayerKind.SUN,
                selected = layers.sun,
                onToggle = onToggleSun,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
            VisorLayerToggleButton(
                label = stringResource(R.string.visor_layer_moon),
                kind = VisorLayerKind.MOON,
                selected = layers.moon,
                onToggle = onToggleMoon,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
            VisorLayerToggleButton(
                label = stringResource(R.string.visor_layer_solstices),
                kind = VisorLayerKind.SOLSTICES,
                selected = layers.solstices,
                onToggle = onToggleSolstices,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun VisorDateChip(
    label: String,
    fullDateLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val desc = stringResource(R.string.visor_cd_date_chip, fullDateLabel)
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .cenitFocusRing(VisorHudFocus, cornerRadius = 20.dp)
            .clip(shape)
            .background(VisorHudSubtle)
            .border(1.dp, VisorHudBorder, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = desc },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CenitIcon(kind = CenitIconKind.CALENDAR, tint = VisorHudFg, size = 14.dp)
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
            color = VisorHudFg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun VisorNowButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val desc = stringResource(R.string.cd_back_to_now)
    val shape = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .cenitFocusRing(VisorHudFocus, cornerRadius = 20.dp)
            .clip(shape)
            .background(VisorHudScrim)
            .border(1.dp, VisorHudBorder, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.action_now),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
            color = VisorHudFg,
            maxLines = 1,
        )
    }
}

/**
 * Dark-HUD time slider preserving every interaction and accessibility semantic of [TimeScrubber]:
 * 44 dp touch row, drag/tap, keyboard arrows (±5 min) and PageUp/PageDown (±1 h),
 * `ProgressBarRangeInfo`, `setProgress`, and ±5 min / ±1 h TalkBack custom actions.
 */
@Composable
internal fun VisorTimeScrubber(
    selectedFraction: Float,
    formattedTime: String,
    clock: ClockFormat,
    locale: Locale,
    onFractionChange: (Float) -> Unit,
    onStepMinutes: (Int) -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scrubberDesc = stringResource(R.string.cd_time_scrubber)
    val forward5 = stringResource(R.string.a11y_step_forward_5m)
    val back5 = stringResource(R.string.a11y_step_back_5m)
    val forward1h = stringResource(R.string.a11y_step_forward_1h)
    val back1h = stringResource(R.string.a11y_step_back_1h)

    val latestFractionChange by rememberUpdatedState(onFractionChange)
    val latestStep by rememberUpdatedState(onStepMinutes)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (compact) 36.dp else 44.dp)
                .cenitFocusRing(VisorHudFocus, cornerRadius = 10.dp)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> {
                            latestStep(-5)
                            true
                        }
                        Key.DirectionRight -> {
                            latestStep(5)
                            true
                        }
                        Key.PageUp -> {
                            latestStep(60)
                            true
                        }
                        Key.PageDown -> {
                            latestStep(-60)
                            true
                        }
                        else -> false
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val thumbRadiusPx = 10.dp.toPx()
                        val usableWidth = (size.width - thumbRadiusPx * 2f).coerceAtLeast(1f)
                        fun fractionFor(x: Float): Float =
                            ((x - thumbRadiusPx) / usableWidth).coerceIn(0f, 1f)

                        latestFractionChange(fractionFor(down.position.x))
                        horizontalDrag(down.id) { change ->
                            change.consume()
                            latestFractionChange(fractionFor(change.position.x))
                        }
                    }
                }
                .semantics {
                    contentDescription = scrubberDesc
                    stateDescription = formattedTime
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = selectedFraction.coerceIn(0f, 1f),
                        range = 0f..1f,
                    )
                    setProgress { target ->
                        latestFractionChange(target.coerceIn(0f, 1f))
                        true
                    }
                    customActions = listOf(
                        CustomAccessibilityAction(forward5) {
                            latestStep(5)
                            true
                        },
                        CustomAccessibilityAction(back5) {
                            latestStep(-5)
                            true
                        },
                        CustomAccessibilityAction(forward1h) {
                            latestStep(60)
                            true
                        },
                        CustomAccessibilityAction(back1h) {
                            latestStep(-60)
                            true
                        },
                    )
                },
        ) {
            val thumbRadius = if (compact) 8.5.dp.toPx() else 10.dp.toPx()
            val trackY = size.height / 2f
            val trackStart = thumbRadius
            val trackEnd = size.width - thumbRadius
            val trackSpan = (trackEnd - trackStart).coerceAtLeast(1f)

            val tickStroke = 1.dp.toPx()
            for (i in 0..8) {
                val x = trackStart + (i / 8f) * trackSpan
                val isMajor = i % 2 == 0
                val halfLen = if (isMajor) 5.dp.toPx() else 3.dp.toPx()
                drawLine(
                    color = if (isMajor) VisorHudBorder else VisorHudHairline,
                    start = Offset(x, trackY - halfLen),
                    end = Offset(x, trackY + halfLen),
                    strokeWidth = tickStroke,
                )
            }

            drawLine(
                color = VisorHudBorder,
                start = Offset(trackStart, trackY),
                end = Offset(trackEnd, trackY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )

            val thumbX = trackStart + selectedFraction.coerceIn(0f, 1f) * trackSpan
            val center = Offset(thumbX, trackY)
            drawCircle(color = VisorHudFg, radius = thumbRadius, center = center)
            drawCircle(
                color = VisorHudBg,
                radius = thumbRadius - 1.dp.toPx(),
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
            drawCircle(
                color = VisorHudBorder,
                radius = thumbRadius,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
        }

        if (!compact) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf(0, 6, 12, 18, 24).forEach { hour ->
                    Text(
                        text = CenitFormat.formatAxisHour(hour, clock, locale),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            letterSpacing = 0.sp,
                            fontFeatureSettings = "tnum",
                        ),
                        color = VisorHudMuted,
                    )
                }
            }
        }
    }
}

private enum class VisorLayerKind {
    SUN,
    MOON,
    SOLSTICES,
}

@Composable
private fun VisorLayerToggleButton(
    label: String,
    kind: VisorLayerKind,
    selected: Boolean,
    onToggle: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    val borderColor = when {
        !selected -> VisorHudBorder
        kind == VisorLayerKind.SUN -> VisorHudSun
        kind == VisorLayerKind.MOON -> VisorHudMoon
        else -> VisorHudFg
    }
    val textColor = when {
        !selected -> VisorHudMuted
        kind == VisorLayerKind.SUN -> VisorHudSun
        kind == VisorLayerKind.MOON -> VisorHudMoon
        else -> VisorHudFg
    }
    val bgColor = if (selected) VisorHudSubtle else Color.Transparent

    Row(
        modifier = modifier
            .heightIn(min = if (compact) 36.dp else 44.dp)
            .cenitFocusRing(VisorHudFocus, cornerRadius = 10.dp)
            .clip(shape)
            .background(bgColor)
            .border(1.dp, borderColor, shape)
            .toggleable(
                value = selected,
                role = Role.Switch,
                onValueChange = { onToggle() },
            )
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        when (kind) {
            VisorLayerKind.SUN -> Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(VisorHudSun),
            )
            VisorLayerKind.MOON -> Box(
                modifier = Modifier
                    .size(7.dp)
                    .border(1.5.dp, VisorHudMoon, CircleShape),
            )
            VisorLayerKind.SOLSTICES -> Canvas(modifier = Modifier.size(width = 11.dp, height = 4.dp)) {
                val y = size.height / 2f
                drawLine(
                    color = textColor,
                    start = Offset(0f, y),
                    end = Offset(size.width * 0.42f, y),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = textColor,
                    start = Offset(size.width * 0.62f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = if (compact) 12.sp else 13.sp,
                lineHeight = 16.sp,
            ),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Compact dark-HUD moon phase icon used in the top moon pill.
 */
@Composable
internal fun HudMoonPhaseGlyph(
    phase: MoonPhase,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(size)) {
        val strokePx = 1.dp.toPx()
        val r = (this.size.minDimension / 2f) - strokePx
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f

        drawCircle(color = VisorHudSubtle, radius = r, center = Offset(cx, cy))
        val litPath = buildMoonLitPath(cx, cy, r, phase, Path())
        if (litPath != null) {
            drawPath(path = litPath, color = VisorHudMoon)
        }
        drawCircle(
            color = VisorHudMoon,
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = strokePx),
        )
    }
}

/**
 * Populates [outPath] with the lit crescent/gibbous silhouette for [phase] inside circle `(cx, cy, r)`.
 * Returns `null` when the moon is effectively new (`illumination <= 0.005`).
 */
internal fun buildMoonLitPath(
    cx: Float,
    cy: Float,
    r: Float,
    phase: MoonPhase,
    outPath: Path,
): Path? {
    val k = phase.illumination.coerceIn(0.0, 1.0)
    if (k <= 0.005) return null
    outPath.rewind()
    if (k >= 0.995) {
        outPath.addOval(Rect(cx - r, cy - r, cx + r, cy + r))
        return outPath
    }
    val waxing = phase.angle < 180.0
    val outerRect = Rect(cx - r, cy - r, cx + r, cy + r)
    val rx = (abs(1.0 - 2.0 * k) * r).toFloat()
    val outerSweep = if (waxing) 180f else -180f
    val innerSweep = if ((k > 0.5) == waxing) 180f else -180f
    outPath.arcTo(
        rect = outerRect,
        startAngleDegrees = 270f,
        sweepAngleDegrees = outerSweep,
        forceMoveTo = true,
    )
    if (rx < 0.5f) {
        outPath.lineTo(cx, cy - r)
    } else {
        val innerRect = Rect(cx - rx, cy - r, cx + rx, cy + r)
        outPath.arcTo(
            rect = innerRect,
            startAngleDegrees = 90f,
            sweepAngleDegrees = innerSweep,
            forceMoveTo = false,
        )
    }
    outPath.close()
    return outPath
}

@Composable
private fun FigureEightGlyph(
    tint: Color,
    size: Dp,
) {
    Canvas(modifier = Modifier.size(size)) {
        val sx = this.size.width / 24f
        val sy = this.size.height / 24f
        val stroke = Stroke(width = 1.6f * sx, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(7.5f * sx, 15.5f * sy)
            cubicTo(5f * sx, 15.5f * sy, 3.5f * sx, 13.9f * sy, 3.5f * sx, 12f * sy)
            cubicTo(3.5f * sx, 10.1f * sy, 5f * sx, 8.5f * sy, 7.5f * sx, 8.5f * sy)
            cubicTo(10.5f * sx, 8.5f * sy, 13.5f * sx, 15.5f * sy, 16.5f * sx, 15.5f * sy)
            cubicTo(19f * sx, 15.5f * sy, 20.5f * sx, 13.9f * sy, 20.5f * sx, 12f * sy)
            cubicTo(20.5f * sx, 10.1f * sy, 19f * sx, 8.5f * sy, 16.5f * sx, 8.5f * sy)
            cubicTo(13.5f * sx, 8.5f * sy, 10.5f * sx, 15.5f * sy, 7.5f * sx, 15.5f * sy)
            close()
        }
        drawPath(path = path, color = tint, style = stroke)
    }
}
