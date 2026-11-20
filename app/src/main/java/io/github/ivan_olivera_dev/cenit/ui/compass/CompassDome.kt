package io.github.ivan_olivera_dev.cenit.ui.compass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ivan_olivera_dev.cenit.core.PathPoint
import io.github.ivan_olivera_dev.cenit.core.SkyPosition
import io.github.ivan_olivera_dev.cenit.core.compass.CompassMath
import io.github.ivan_olivera_dev.cenit.core.sky.SkyDomeProjection
import io.github.ivan_olivera_dev.cenit.ui.components.DomeHorizonTick
import io.github.ivan_olivera_dev.cenit.ui.components.DomeReferencePaths
import io.github.ivan_olivera_dev.cenit.ui.components.SkyDome
import io.github.ivan_olivera_dev.cenit.ui.theme.LocalCenitPalette
import java.time.ZoneId
import kotlin.math.abs

/**
 * Brújula rotating dome (`sky-dome` + `compass-rose` tokens, Artboard 02):
 * wraps the read-only [SkyDome] rotated by [headingDeg] inside a 5°-tick compass bezel,
 * adds sun and moon rise/set azimuth rays (plus moon rise/set horizon ticks and time labels),
 * and draws the fixed top heading index marker when a live compass sensor is active.
 */
@Composable
internal fun CompassDome(
    sunPath: List<PathPoint>,
    moonPath: List<PathPoint>,
    referencePaths: DomeReferencePaths,
    sunPosition: SkyPosition,
    moonPosition: SkyPosition,
    sunriseTick: DomeHorizonTick?,
    sunsetTick: DomeHorizonTick?,
    moonriseTick: DomeHorizonTick?,
    moonsetTick: DomeHorizonTick?,
    zone: ZoneId,
    headingDeg: Double,
    showLivePointer: Boolean,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalCenitPalette.current
    val textMeasurer = rememberTextMeasurer()

    val labelStyle = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Normal,
        fontFeatureSettings = "tnum",
        color = palette.textPrimary,
    )

    val bezelInset = 18.dp

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        // Core flat-compass sky dome, rotated by live true-north heading (read-only reuse).
        SkyDome(
            sunPath = sunPath,
            moonPath = moonPath,
            referencePaths = referencePaths,
            sunPosition = sunPosition,
            moonPosition = moonPosition,
            sunriseTick = sunriseTick,
            sunsetTick = sunsetTick,
            zone = zone,
            contentDescription = contentDescription,
            headingDeg = headingDeg,
            modifier = Modifier
                .fillMaxSize()
                .padding(bezelInset),
        )

        // Compass rose 5° bezel ticks, rise/set azimuth rays, moon ticks, and fixed top pointer.
        Canvas(modifier = Modifier.fillMaxSize()) {
            val diameter = size.minDimension
            val cx = size.width / 2f
            val cy = size.height / 2f
            val bezelInsetPx = bezelInset.toPx()
            val bezelR = (diameter / 2f) - 4.dp.toPx()
            val domeOuterR = ((diameter - 2f * bezelInsetPx) / 2f) - 4.dp.toPx()
            if (bezelR <= 0f || domeOuterR <= 0f) return@Canvas

            val horizonR = (domeOuterR * SkyDomeProjection.HORIZON_RADIUS).toFloat()

            fun onRing(radiusPx: Float, azimuthDeg: Double): Offset {
                val pt = SkyDomeProjection.pointOnRing(1.0, azimuthDeg, headingDeg)
                return Offset(cx + (pt.x * radiusPx).toFloat(), cy + (pt.y * radiusPx).toFloat())
            }

            // 1. Compass rose outer ring & ticks every 5° (major 30°, cardinal 90°)
            drawCircle(
                color = palette.borderColor,
                radius = bezelR,
                center = Offset(cx, cy),
                style = Stroke(width = 1.dp.toPx()),
            )
            for (angle in 0 until 360 step 5) {
                val isCardinal = angle % 90 == 0
                val isMajor = angle % 30 == 0
                val tickLength = when {
                    isCardinal -> 11.dp.toPx()
                    isMajor -> 8.dp.toPx()
                    else -> 4.5.dp.toPx()
                }
                val strokeWidth = when {
                    isCardinal -> 2.dp.toPx()
                    isMajor -> 1.5.dp.toPx()
                    else -> 1.dp.toPx()
                }
                val color = if (isCardinal) palette.textPrimary else palette.controlBorder
                drawLine(
                    color = color,
                    start = onRing(bezelR, angle.toDouble()),
                    end = onRing(bezelR - tickLength, angle.toDouble()),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }

            // 2. Sun & moon rise/set azimuth rays from zenith toward the horizon
            val rayStartR = 5.dp.toPx()
            val rayEndR = (horizonR - 36.dp.toPx()).coerceAtLeast(rayStartR)

            listOfNotNull(sunriseTick, sunsetTick).forEach { tick ->
                drawLine(
                    color = palette.sun,
                    start = onRing(rayStartR, tick.azimuthDeg),
                    end = onRing(rayEndR, tick.azimuthDeg),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            val moonDash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
            val sunTickAzimuths = listOfNotNull(sunriseTick?.azimuthDeg, sunsetTick?.azimuthDeg)
            val moonTicks = listOfNotNull(moonriseTick, moonsetTick)
            val placedMoonLabelAzimuths = mutableListOf<Double>()

            moonTicks.forEach { tick ->
                // Offset label inward if close to a sun tick or another moon tick to avoid overlap
                val closeToExisting = (sunTickAzimuths + placedMoonLabelAzimuths).any { other ->
                    abs(CompassMath.shortestArcDelta(tick.azimuthDeg, other)) < 20.0
                }
                val labelR = (horizonR - if (closeToExisting) 40.dp.toPx() else 25.dp.toPx())
                    .coerceAtLeast(rayStartR + 12.dp.toPx())
                val moonRayEndR = (labelR - 11.dp.toPx()).coerceAtLeast(rayStartR)

                drawLine(
                    color = palette.moon,
                    start = onRing(rayStartR, tick.azimuthDeg),
                    end = onRing(moonRayEndR, tick.azimuthDeg),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = moonDash,
                )

                // Moon horizon tick (SkyDome renders sun ticks; CompassDome adds moon ticks)
                drawLine(
                    color = palette.moon,
                    start = onRing(horizonR, tick.azimuthDeg),
                    end = onRing(horizonR - 9.dp.toPx(), tick.azimuthDeg),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )

                drawCenteredText(
                    textMeasurer = textMeasurer,
                    text = tick.label,
                    center = onRing(labelR, tick.azimuthDeg),
                    style = labelStyle,
                )
                placedMoonLabelAzimuths.add(tick.azimuthDeg)
            }

            // 3. Fixed top heading index marker (lubber triangle + tick at 12 o'clock)
            if (showLivePointer) {
                val topY = cy - bezelR
                val pointerHalfWidth = 6.dp.toPx()
                val pointerHeight = 9.dp.toPx()
                val triangle = Path().apply {
                    moveTo(cx - pointerHalfWidth, (topY - 3.dp.toPx()).coerceAtLeast(0f))
                    lineTo(cx + pointerHalfWidth, (topY - 3.dp.toPx()).coerceAtLeast(0f))
                    lineTo(cx, topY + pointerHeight - 3.dp.toPx())
                    close()
                }
                drawPath(path = triangle, color = palette.textPrimary)

                val lubberStartY = cy - domeOuterR
                drawLine(
                    color = palette.textPrimary,
                    start = Offset(cx, lubberStartY),
                    end = Offset(cx, lubberStartY + 11.dp.toPx()),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun DrawScope.drawCenteredText(
    textMeasurer: TextMeasurer,
    text: String,
    center: Offset,
    style: TextStyle,
) {
    val result = textMeasurer.measure(text = text, style = style)
    drawText(
        textLayoutResult = result,
        topLeft = Offset(
            x = center.x - result.size.width / 2f,
            y = center.y - result.size.height / 2f,
        ),
    )
}
