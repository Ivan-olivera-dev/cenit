package io.github.ivan_olivera_dev.cenit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ivan_olivera_dev.cenit.R
import io.github.ivan_olivera_dev.cenit.ui.theme.LocalCenitPalette

/**
 * Time scrubber (`time-scrubber` token): `44 dp` touch row, `2 dp` track `controlBorder`,
 * `20 dp` thumb `textPrimary` with `2 dp bgColor` ring, ticks every 3 h, drag/tap quantized
 * to 5 min, and accessibility + keyboard actions for ±5 min and ±1 h page steps.
 */
@Composable
fun TimeScrubber(
    selectedFraction: Float,
    formattedTime: String,
    onFractionChange: (Float) -> Unit,
    onStepMinutes: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalCenitPalette.current
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
                .height(44.dp)
                .cenitFocusRing(palette.focusRing, cornerRadius = 10.dp)
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
            val thumbRadius = 10.dp.toPx()
            val trackY = size.height / 2f
            val trackStart = thumbRadius
            val trackEnd = size.width - thumbRadius
            val trackSpan = (trackEnd - trackStart).coerceAtLeast(1f)

            // 3-hour ticks (0, 3, 6, 9, 12, 15, 18, 21, 24)
            val tickStroke = 1.dp.toPx()
            for (i in 0..8) {
                val x = trackStart + (i / 8f) * trackSpan
                val isMajor = i % 2 == 0
                val halfLen = if (isMajor) 5.dp.toPx() else 3.dp.toPx()
                drawLine(
                    color = if (isMajor) palette.controlBorder else palette.borderColor,
                    start = Offset(x, trackY - halfLen),
                    end = Offset(x, trackY + halfLen),
                    strokeWidth = tickStroke,
                )
            }

            // 2 dp track
            drawLine(
                color = palette.controlBorder,
                start = Offset(trackStart, trackY),
                end = Offset(trackEnd, trackY),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )

            // 20 dp thumb with 2 dp bgColor ring and 1 dp controlBorder outline
            val thumbX = trackStart + selectedFraction.coerceIn(0f, 1f) * trackSpan
            val center = Offset(thumbX, trackY)
            drawCircle(
                color = palette.textPrimary,
                radius = thumbRadius,
                center = center,
            )
            drawCircle(
                color = palette.bgColor,
                radius = thumbRadius - 1.dp.toPx(),
                center = center,
                style = Stroke(width = 2.dp.toPx()),
            )
            drawCircle(
                color = palette.controlBorder,
                radius = thumbRadius,
                center = center,
                style = Stroke(width = 1.dp.toPx()),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("00", "06", "12", "18", "24").forEach { tick ->
                Text(
                    text = tick,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        letterSpacing = 0.sp,
                        fontFeatureSettings = "tnum",
                    ),
                    color = palette.textSecondary,
                )
            }
        }
    }
}
