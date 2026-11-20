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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ivan_olivera_dev.cenit.R
import io.github.ivan_olivera_dev.cenit.core.PathPoint
import io.github.ivan_olivera_dev.cenit.core.SkyPosition
import io.github.ivan_olivera_dev.cenit.core.compass.CompassLabelPlacement
import io.github.ivan_olivera_dev.cenit.core.compass.CompassRingLabel
import io.github.ivan_olivera_dev.cenit.core.compass.CompassRingObstacle
import io.github.ivan_olivera_dev.cenit.core.sky.SkyDomeProjection
import io.github.ivan_olivera_dev.cenit.ui.components.DomeHorizonTick
import io.github.ivan_olivera_dev.cenit.ui.components.DomeReferencePaths
import io.github.ivan_olivera_dev.cenit.ui.components.SkyDome
import io.github.ivan_olivera_dev.cenit.ui.theme.LocalCenitPalette
import java.time.ZoneId

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
    val northCardinalStyle = labelStyle.copy(fontWeight = FontWeight.SemiBold)
    val cardinalLabels = listOf(
        stringResource(R.string.cardinal_n),
        stringResource(R.string.cardinal_e),
        stringResource(R.string.cardinal_s),
        stringResource(R.string.cardinal_w),
    )

    val bezelInset = 18.dp

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        // Core flat-compass sky dome, rotated by live true-north heading (read-only reuse).
        // Pass null sunrise/sunset ticks so CompassDome draws all four rise/set ticks and
        // places every time label jointly to avoid collisions with the cardinals or each other.
        SkyDome(
            sunPath = sunPath,
            moonPath = moonPath,
            referencePaths = referencePaths,
            sunPosition = sunPosition,
            moonPosition = moonPosition,
            sunriseTick = null,
            sunsetTick = null,
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

            // 2. Sun & moon rise/set azimuth rays and horizon ticks at exact event azimuths
            val rayStartR = 5.dp.toPx()
            val primaryLabelR = (horizonR - 25.dp.toPx()).coerceAtLeast(rayStartR + 12.dp.toPx())
            val cardinalR = (horizonR - 11.dp.toPx()).coerceAtLeast(rayStartR + 1.dp.toPx())
            val outerLabelR = (domeOuterR + 4.dp.toPx()).coerceAtMost(bezelR - 2.dp.toPx())
            val rayEndR = (primaryLabelR - 11.dp.toPx()).coerceAtLeast(rayStartR)
            val tickInnerR = (horizonR - 9.dp.toPx()).coerceAtLeast(rayStartR)

            val sunTicks = listOfNotNull(
                sunriseTick?.let { "sunrise" to it },
                sunsetTick?.let { "sunset" to it },
            )
            sunTicks.forEach { (_, tick) ->
                drawLine(
                    color = palette.sun,
                    start = onRing(rayStartR, tick.azimuthDeg),
                    end = onRing(rayEndR, tick.azimuthDeg),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = palette.sun,
                    start = onRing(horizonR, tick.azimuthDeg),
                    end = onRing(tickInnerR, tick.azimuthDeg),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            val moonDash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
            val moonTicks = listOfNotNull(
                moonriseTick?.let { "moonrise" to it },
                moonsetTick?.let { "moonset" to it },
            )
            moonTicks.forEach { (_, tick) ->
                drawLine(
                    color = palette.moon,
                    start = onRing(rayStartR, tick.azimuthDeg),
                    end = onRing(rayEndR, tick.azimuthDeg),
                    strokeWidth = 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = moonDash,
                )
                drawLine(
                    color = palette.moon,
                    start = onRing(horizonR, tick.azimuthDeg),
                    end = onRing(tickInnerR, tick.azimuthDeg),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            // 3. Collision-free placement for all sun & moon rise/set time labels
            val allTicks = sunTicks + moonTicks
            if (allTicks.isNotEmpty() && primaryLabelR > 0f && cardinalR > 0f) {
                val measuredTicks = allTicks.map { (id, tick) ->
                    Triple(id, tick, textMeasurer.measure(text = tick.label, style = labelStyle))
                }
                val cardinalMeasurements = cardinalLabels.mapIndexed { idx, text ->
                    textMeasurer.measure(
                        text = text,
                        style = if (idx == 0) northCardinalStyle else labelStyle,
                    )
                }
                val cardinalWidthPx = cardinalMeasurements.maxOf { it.size.width }.toDouble()
                val cardinalHeightPx = cardinalMeasurements.maxOf { it.size.height }.toDouble()
                val labelPaddingPx = 2.dp.toPx().toDouble()

                val ringLabels = measuredTicks.map { (id, tick, layout) ->
                    val pairWidthDeg = CompassLabelPlacement.requiredPairAngularWidthDeg(
                        labelWidth = layout.size.width.toDouble(),
                        labelHeight = layout.size.height.toDouble(),
                        ringRadius = primaryLabelR.toDouble(),
                        padding = labelPaddingPx,
                    ).coerceAtLeast(1.0)
                    CompassRingLabel(
                        id = id,
                        anchorAzimuthDeg = tick.azimuthDeg,
                        angularWidthDeg = pairWidthDeg,
                    )
                }

                val maxLabelWidthPx = measuredTicks.maxOf { it.third.size.width }.toDouble()
                val maxLabelHeightPx = measuredTicks.maxOf { it.third.size.height }.toDouble()
                val minPairWidthDeg = ringLabels.minOf { it.angularWidthDeg }
                val cardinalClearanceDeg = CompassLabelPlacement.requiredCardinalClearanceDeg(
                    labelWidth = maxLabelWidthPx,
                    labelHeight = maxLabelHeightPx,
                    labelRingRadius = primaryLabelR.toDouble(),
                    cardinalWidth = cardinalWidthPx,
                    cardinalHeight = cardinalHeightPx,
                    cardinalRingRadius = cardinalR.toDouble(),
                    padding = labelPaddingPx,
                )
                val obstacleWidthDeg = CompassLabelPlacement.obstacleAngularWidthDeg(
                    pairAngularWidthDeg = minPairWidthDeg,
                    cardinalClearanceDeg = cardinalClearanceDeg,
                )
                val cardinalObstacles = listOf(0.0, 90.0, 180.0, 270.0).map { az ->
                    CompassRingObstacle(azimuthDeg = az, angularWidthDeg = obstacleWidthDeg)
                }

                val placedLabels = CompassLabelPlacement.place(
                    labels = ringLabels,
                    obstacles = cardinalObstacles,
                )
                val layoutById = measuredTicks.associate { (id, _, layout) -> id to layout }

                placedLabels.forEach { placed ->
                    val layout = layoutById.getValue(placed.id)
                    val radius = if (placed.radialTier == 0) primaryLabelR else outerLabelR
                    drawCenteredLayout(
                        layout = layout,
                        center = onRing(radius, placed.placedAzimuthDeg),
                    )
                }
            }

            // 4. Fixed top heading index marker (lubber triangle + tick at 12 o'clock)
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

private fun DrawScope.drawCenteredLayout(
    layout: TextLayoutResult,
    center: Offset,
) {
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(
            x = center.x - layout.size.width / 2f,
            y = center.y - layout.size.height / 2f,
        ),
    )
}
