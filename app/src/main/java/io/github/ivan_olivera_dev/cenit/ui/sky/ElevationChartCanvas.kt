package io.github.ivan_olivera_dev.cenit.ui.sky

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ivan_olivera_dev.cenit.R
import io.github.ivan_olivera_dev.cenit.core.sky.chart.ChartPlotPoint
import io.github.ivan_olivera_dev.cenit.core.sky.chart.ChartSkyBand
import io.github.ivan_olivera_dev.cenit.core.sky.chart.ElevationChartData
import io.github.ivan_olivera_dev.cenit.ui.components.cenitFocusRing
import io.github.ivan_olivera_dev.cenit.ui.theme.CenitPalette
import io.github.ivan_olivera_dev.cenit.ui.theme.LocalCenitPalette

/**
 * Interactive elevation chart (`elevation-chart` token): plate radius `20 dp`, y from `−20°` to
 * the day's max, sky-phase bands (`skyDay`/`skyCivil`/`skyNautical`/`skyAstro` + night), golden
 * and blue hour highlights, solid `2 dp` sun path, dashed `2 dp` moon path, `1 dp` `controlBorder`
 * horizon line, and a `1 dp` `textPrimary` selected-time line with pill label and markers.
 *
 * Horizontal tap/drag or keyboard arrows scrub the selected time.
 */
@Composable
fun ElevationChartCanvas(
    chartData: ElevationChartData,
    selectedXFraction: Float,
    sunElevationDeg: Double,
    moonElevationDeg: Double,
    formattedTime: String,
    contentDescription: String,
    onScrubFraction: (Float) -> Unit,
    onStepMinutes: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalCenitPalette.current
    val textMeasurer = rememberTextMeasurer()

    val bandLabels = mapOf(
        ChartSkyBand.DAY to stringResource(R.string.sky_band_day),
        ChartSkyBand.CIVIL to stringResource(R.string.sky_band_civil),
        ChartSkyBand.NAUTICAL to stringResource(R.string.sky_band_nautical),
        ChartSkyBand.ASTRONOMICAL to stringResource(R.string.sky_band_astro),
        ChartSkyBand.NIGHT to stringResource(R.string.sky_band_night),
    )

    val forward5 = stringResource(R.string.a11y_step_forward_5m)
    val back5 = stringResource(R.string.a11y_step_back_5m)
    val forward1h = stringResource(R.string.a11y_step_forward_1h)
    val back1h = stringResource(R.string.a11y_step_back_1h)

    val latestFractionChange by rememberUpdatedState(onScrubFraction)
    val latestStep by rememberUpdatedState(onStepMinutes)

    val plateShape = RoundedCornerShape(20.dp)
    val labelStyle = TextStyle(
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Normal,
        fontFeatureSettings = "tnum",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(168.dp)
            .cenitFocusRing(palette.focusRing, cornerRadius = 20.dp)
            .clip(plateShape)
            .background(palette.surfaceColor)
            .border(1.dp, palette.borderColor, plateShape)
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
                    val widthPx = size.width.toFloat().coerceAtLeast(1f)
                    fun fractionFor(x: Float): Float = (x / widthPx).coerceIn(0f, 1f)

                    latestFractionChange(fractionFor(down.position.x))
                    horizontalDrag(down.id) { change ->
                        change.consume()
                        latestFractionChange(fractionFor(change.position.x))
                    }
                }
            }
            .semantics {
                this.contentDescription = contentDescription
                this.stateDescription = formattedTime
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = selectedXFraction.coerceIn(0f, 1f),
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
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val padTop = 8.dp.toPx()
            val padBottom = 22.dp.toPx()
            val plotBottom = (h - padBottom).coerceAtLeast(padTop + 1f)
            val plotHeight = (plotBottom - padTop).coerceAtLeast(1f)

            fun xOf(fraction: Double): Float = (fraction * w).toFloat()
            fun yOf(fraction: Double): Float = padTop + (fraction * plotHeight).toFloat()

            clipRect(left = 0f, top = 0f, right = w, bottom = plotBottom) {
                // Top padding fills with skyDay so the top band extends seamlessly to the edge
                drawRect(
                    color = palette.skyDay,
                    topLeft = Offset.Zero,
                    size = Size(w, padTop),
                )

                // 1. Sky-phase horizontal bands
                chartData.bands.forEach { bandRect ->
                    val top = yOf(bandRect.topFraction)
                    val bottom = yOf(bandRect.bottomFraction)
                    drawRect(
                        color = bandColor(bandRect.band, palette),
                        topLeft = Offset(0f, top),
                        size = Size(w, (bottom - top).coerceAtLeast(0f)),
                    )
                }

                // 2. Golden & blue hour highlights along the sun path
                val highlightWidth = 6.dp.toPx()
                val highlightStroke = Stroke(
                    width = highlightWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                )
                chartData.goldenRuns.forEach { run ->
                    drawPlotPolyline(run, ::xOf, ::yOf, palette.sun, highlightStroke)
                }
                chartData.blueRuns.forEach { run ->
                    drawPlotPolyline(run, ::xOf, ::yOf, palette.blueHour, highlightStroke)
                }

                // 3. Horizon line (0°)
                val horizonY = yOf(chartData.scale.horizonYFraction)
                drawLine(
                    color = palette.controlBorder,
                    start = Offset(0f, horizonY),
                    end = Offset(w, horizonY),
                    strokeWidth = 1.dp.toPx(),
                )

                // 4. Moon path (2 dp dashed)
                val moonStroke = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                )
                drawPlotPolyline(chartData.moonPoints, ::xOf, ::yOf, palette.moon, moonStroke)

                // 5. Sun path (2 dp solid)
                val sunStroke = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                )
                drawPlotPolyline(chartData.sunPoints, ::xOf, ::yOf, palette.sun, sunStroke)

                // 6. Right-aligned band labels when band height is tall enough
                val minBandLabelHeight = 13.dp.toPx()
                val rightInset = 8.dp.toPx()
                val bandTextStyle = labelStyle.copy(color = palette.textSecondary)
                chartData.bands.forEach { bandRect ->
                    val label = bandLabels[bandRect.band].orEmpty()
                    if (label.isEmpty()) return@forEach
                    val top = yOf(bandRect.topFraction)
                    val bottom = yOf(bandRect.bottomFraction)
                    if (bottom - top >= minBandLabelHeight) {
                        val measured = textMeasurer.measure(text = label, style = bandTextStyle)
                        val textY = ((top + bottom) / 2f) - (measured.size.height / 2f)
                        drawText(
                            textLayoutResult = measured,
                            topLeft = Offset(
                                x = (w - rightInset - measured.size.width).coerceAtLeast(4.dp.toPx()),
                                y = textY.coerceIn(padTop, plotBottom - measured.size.height),
                            ),
                        )
                    }
                }

                // 7. Selected-time vertical line + markers + pill label
                val nx = xOf(selectedXFraction.toDouble().coerceIn(0.0, 1.0))
                drawLine(
                    color = palette.textPrimary,
                    start = Offset(nx, 0f),
                    end = Offset(nx, plotBottom),
                    strokeWidth = 1.dp.toPx(),
                )

                if (moonElevationDeg >= chartData.scale.yMinDeg) {
                    val moonY = yOf(chartData.scale.yFraction(moonElevationDeg))
                    drawCircle(color = palette.bgColor, radius = 4.5.dp.toPx(), center = Offset(nx, moonY))
                    drawCircle(
                        color = palette.moon,
                        radius = 4.5.dp.toPx(),
                        center = Offset(nx, moonY),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }

                if (sunElevationDeg >= chartData.scale.yMinDeg) {
                    val sunY = yOf(chartData.scale.yFraction(sunElevationDeg))
                    drawCircle(color = palette.sun, radius = 5.5.dp.toPx(), center = Offset(nx, sunY))
                    drawCircle(
                        color = palette.bgColor,
                        radius = 5.5.dp.toPx(),
                        center = Offset(nx, sunY),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }

                // Selected-time pill label anchored at top of the now line
                val pillTextStyle = labelStyle.copy(
                    color = palette.textPrimary,
                    fontWeight = FontWeight.Medium,
                )
                val pillText = textMeasurer.measure(text = formattedTime, style = pillTextStyle)
                val pillPadH = 7.dp.toPx()
                val pillPadV = 2.dp.toPx()
                val pillW = pillText.size.width + pillPadH * 2f
                val pillH = pillText.size.height + pillPadV * 2f
                val edgeMargin = 6.dp.toPx()
                val pillLeft = (nx - pillW / 2f).coerceIn(edgeMargin, (w - pillW - edgeMargin).coerceAtLeast(edgeMargin))
                val pillTop = 5.dp.toPx()
                val pillRadius = pillH / 2f

                drawRoundRect(
                    color = palette.bgColor,
                    topLeft = Offset(pillLeft, pillTop),
                    size = Size(pillW, pillH),
                    cornerRadius = CornerRadius(pillRadius, pillRadius),
                )
                drawRoundRect(
                    color = palette.controlBorder,
                    topLeft = Offset(pillLeft, pillTop),
                    size = Size(pillW, pillH),
                    cornerRadius = CornerRadius(pillRadius, pillRadius),
                    style = Stroke(width = 1.dp.toPx()),
                )
                drawText(
                    textLayoutResult = pillText,
                    topLeft = Offset(pillLeft + pillPadH, pillTop + pillPadV),
                )
            }

            // 8. Hour ticks along the bottom strip (DST-aware positions)
            val tickStyle = labelStyle.copy(color = palette.textSecondary)
            val tickInset = 6.dp.toPx()
            chartData.hourTicks.forEachIndexed { index, tick ->
                val measured = textMeasurer.measure(text = tick.label, style = tickStyle)
                val rawX = xOf(tick.xFraction)
                val textX = when (index) {
                    0 -> tickInset
                    chartData.hourTicks.lastIndex -> (w - tickInset - measured.size.width).coerceAtLeast(0f)
                    else -> (rawX - measured.size.width / 2f)
                        .coerceIn(tickInset, (w - tickInset - measured.size.width).coerceAtLeast(tickInset))
                }
                val textY = plotBottom + ((padBottom - measured.size.height) / 2f).coerceAtLeast(0f)
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(textX, textY),
                )
            }
        }
    }
}

private fun DrawScope.drawPlotPolyline(
    points: List<ChartPlotPoint>,
    xOf: (Double) -> Float,
    yOf: (Double) -> Float,
    color: Color,
    stroke: Stroke,
) {
    if (points.size < 2) return
    val path = Path().apply {
        val first = points.first()
        moveTo(xOf(first.xFraction), yOf(first.yFraction))
        for (i in 1 until points.size) {
            val pt = points[i]
            lineTo(xOf(pt.xFraction), yOf(pt.yFraction))
        }
    }
    drawPath(path = path, color = color, style = stroke)
}

private fun bandColor(band: ChartSkyBand, palette: CenitPalette): Color = when (band) {
    ChartSkyBand.DAY -> palette.skyDay
    ChartSkyBand.CIVIL -> palette.skyCivil
    ChartSkyBand.NAUTICAL -> palette.skyNautical
    ChartSkyBand.ASTRONOMICAL -> palette.skyAstro
    ChartSkyBand.NIGHT -> if (palette.isDark) palette.bgColor else palette.skyAstro
}
