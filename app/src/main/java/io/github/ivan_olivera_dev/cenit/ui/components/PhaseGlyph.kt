package io.github.ivan_olivera_dev.cenit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.ivan_olivera_dev.cenit.core.MoonPhase
import io.github.ivan_olivera_dev.cenit.ui.theme.LocalCenitPalette
import kotlin.math.abs

/**
 * Moon phase glyph (`phase-glyph` token): 24–44 dp circle with lit part in `moon`,
 * dark part in `surfaceSubtle`, and 1 dp `controlBorder` outline.
 */
@Composable
fun PhaseGlyph(
    phase: MoonPhase,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    val palette = LocalCenitPalette.current
    Canvas(
        modifier = modifier
            .size(size)
            .semantics {
                this.contentDescription = contentDescription
                this.role = Role.Image
            },
    ) {
        val strokePx = 1.dp.toPx()
        val r = (this.size.minDimension / 2f) - strokePx
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f

        drawCircle(
            color = palette.surfaceSubtle,
            radius = r,
            center = Offset(cx, cy),
        )

        val k = phase.illumination.coerceIn(0.0, 1.0)
        val waxing = phase.angle < 180.0
        when {
            k >= 0.995 -> drawCircle(color = palette.moon, radius = r, center = Offset(cx, cy))
            k > 0.005 -> {
                val outerRect = Rect(cx - r, cy - r, cx + r, cy + r)
                val rx = (abs(1.0 - 2.0 * k) * r).toFloat()
                val outerSweep = if (waxing) 180f else -180f
                val innerSweep = if ((k > 0.5) == waxing) 180f else -180f
                val litPath = Path().apply {
                    arcTo(
                        rect = outerRect,
                        startAngleDegrees = 270f,
                        sweepAngleDegrees = outerSweep,
                        forceMoveTo = true,
                    )
                    if (rx < 0.5f) {
                        lineTo(cx, cy - r)
                    } else {
                        val innerRect = Rect(cx - rx, cy - r, cx + rx, cy + r)
                        arcTo(
                            rect = innerRect,
                            startAngleDegrees = 90f,
                            sweepAngleDegrees = innerSweep,
                            forceMoveTo = false,
                        )
                    }
                    close()
                }
                drawPath(path = litPath, color = palette.moon)
            }
        }

        drawCircle(
            color = palette.controlBorder,
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = strokePx),
        )
    }
}
