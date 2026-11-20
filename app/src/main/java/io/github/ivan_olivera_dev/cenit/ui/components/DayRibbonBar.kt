package io.github.ivan_olivera_dev.cenit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ivan_olivera_dev.cenit.R
import io.github.ivan_olivera_dev.cenit.core.sky.DayRibbonSegment
import io.github.ivan_olivera_dev.cenit.core.sky.SkyFormatting
import io.github.ivan_olivera_dev.cenit.core.sky.SkyPhase
import io.github.ivan_olivera_dev.cenit.ui.theme.CenitPalette
import io.github.ivan_olivera_dev.cenit.ui.theme.LocalCenitPalette
import java.time.ZoneId

/**
 * 24-hour day light ribbon (`day-ribbon` token): `14 dp` pill bar coloured by [SkyPhase],
 * selected-time marker, and golden hours always named in text below (never colour alone).
 */
@Composable
fun DayRibbonBar(
    segments: List<DayRibbonSegment>,
    selectedFraction: Float,
    zone: ZoneId,
    modifier: Modifier = Modifier,
) {
    val palette = LocalCenitPalette.current
    val goldenRanges = segments
        .filter { it.phase == SkyPhase.GOLDEN }
        .map { seg ->
            stringResource(
                R.string.ribbon_golden_range,
                SkyFormatting.formatTimeTruncated(seg.start, zone),
                SkyFormatting.formatTimeTruncated(seg.end, zone),
            )
        }

    val goldenText = if (goldenRanges.isEmpty()) {
        stringResource(R.string.ribbon_golden_none)
    } else {
        stringResource(R.string.ribbon_golden_label, goldenRanges.joinToString(" · "))
    }
    val ribbonDesc = stringResource(R.string.cd_day_ribbon, goldenText)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .semantics {
                    contentDescription = ribbonDesc
                    role = Role.Image
                },
        ) {
            val barTop = 2.dp.toPx()
            val barHeight = 14.dp.toPx()
            val radius = barHeight / 2f
            val clip = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = barTop,
                        right = size.width,
                        bottom = barTop + barHeight,
                        cornerRadius = CornerRadius(radius, radius),
                    ),
                )
            }

            clipPath(clip) {
                segments.forEach { seg ->
                    val left = (seg.startFraction * size.width).toFloat()
                    val right = (seg.endFraction * size.width).toFloat()
                    drawRect(
                        color = phaseColor(seg.phase, palette),
                        topLeft = Offset(left, barTop),
                        size = Size((right - left).coerceAtLeast(1f), barHeight),
                    )
                }
            }

            val borderPx = 1.dp.toPx()
            drawRoundRect(
                color = palette.controlBorder,
                topLeft = Offset(borderPx / 2f, barTop + borderPx / 2f),
                size = Size(size.width - borderPx, barHeight - borderPx),
                cornerRadius = CornerRadius(radius, radius),
                style = Stroke(width = borderPx),
            )

            val markerW = 3.dp.toPx()
            val markerX = (selectedFraction.coerceIn(0f, 1f) * size.width)
                .coerceIn(markerW / 2f, size.width - markerW / 2f)
            drawRoundRect(
                color = palette.bgColor,
                topLeft = Offset(markerX - markerW / 2f - borderPx, 0f),
                size = Size(markerW + borderPx * 2f, size.height),
                cornerRadius = CornerRadius(markerW, markerW),
            )
            drawRoundRect(
                color = palette.textPrimary,
                topLeft = Offset(markerX - markerW / 2f, borderPx),
                size = Size(markerW, size.height - borderPx * 2f),
                cornerRadius = CornerRadius(markerW / 2f, markerW / 2f),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = goldenText,
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun phaseColor(phase: SkyPhase, palette: CenitPalette): Color = when (phase) {
    SkyPhase.NIGHT -> if (palette.isDark) palette.bgColor else palette.skyAstro
    SkyPhase.ASTRONOMICAL -> palette.skyAstro
    SkyPhase.NAUTICAL -> palette.skyNautical
    SkyPhase.BLUE -> palette.blueHour
    SkyPhase.GOLDEN -> palette.sun
    SkyPhase.DAY -> palette.skyDay
}
