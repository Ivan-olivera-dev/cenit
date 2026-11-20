package io.github.ivan_olivera_dev.cenit.ui.places

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Minimal stroke icons matching tools/design-kit/lib.js (`24×24` viewBox, `1.5` stroke,
 * round caps and joins) used by the Places screen.
 */
internal enum class PlacesIconKind {
    SEARCH,
    GPS,
    CHECK,
    EDIT,
    CLOSE,
}

@Composable
internal fun PlacesIcon(
    kind: PlacesIconKind,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
) {
    Canvas(modifier = modifier.size(size)) {
        val sx = this.size.width / 24f
        val sy = this.size.height / 24f
        val stroke = Stroke(
            width = 1.5f * sx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        when (kind) {
            PlacesIconKind.SEARCH -> drawSearch(tint, sx, sy, stroke)
            PlacesIconKind.GPS -> drawGps(tint, sx, sy, stroke)
            PlacesIconKind.CHECK -> drawCheck(tint, sx, sy, stroke)
            PlacesIconKind.EDIT -> drawEdit(tint, sx, sy, stroke)
            PlacesIconKind.CLOSE -> drawClose(tint, sx, sy, stroke)
        }
    }
}

private fun DrawScope.drawSearch(color: Color, sx: Float, sy: Float, stroke: Stroke) {
    drawCircle(color, radius = 6.5f * sx, center = Offset(11f * sx, 11f * sy), style = stroke)
    drawLine(color, Offset(16f * sx, 16f * sy), Offset(20.5f * sx, 20.5f * sy), stroke.width, StrokeCap.Round)
}

private fun DrawScope.drawGps(color: Color, sx: Float, sy: Float, stroke: Stroke) {
    drawCircle(color, radius = 3.2f * sx, center = Offset(12f * sx, 12f * sy), style = stroke)
    drawCircle(color, radius = 7f * sx, center = Offset(12f * sx, 12f * sy), style = stroke)
    drawLine(color, Offset(12f * sx, 2.5f * sy), Offset(12f * sx, 5.5f * sy), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(12f * sx, 18.5f * sy), Offset(12f * sx, 21.5f * sy), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(2.5f * sx, 12f * sy), Offset(5.5f * sx, 12f * sy), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(18.5f * sx, 12f * sy), Offset(21.5f * sx, 12f * sy), stroke.width, StrokeCap.Round)
}

private fun DrawScope.drawCheck(color: Color, sx: Float, sy: Float, stroke: Stroke) {
    val path = Path().apply {
        moveTo(5f * sx, 12.5f * sy)
        lineTo(9.5f * sx, 17f * sy)
        lineTo(19f * sx, 7.5f * sy)
    }
    drawPath(path, color, style = stroke)
}

private fun DrawScope.drawEdit(color: Color, sx: Float, sy: Float, stroke: Stroke) {
    val path = Path().apply {
        moveTo(4f * sx, 20f * sy)
        lineTo(8f * sx, 20f * sy)
        lineTo(19f * sx, 9f * sy)
        lineTo(15f * sx, 5f * sy)
        lineTo(4f * sx, 16f * sy)
        close()
    }
    drawPath(path, color, style = stroke)
}

private fun DrawScope.drawClose(color: Color, sx: Float, sy: Float, stroke: Stroke) {
    drawLine(color, Offset(6f * sx, 6f * sy), Offset(18f * sx, 18f * sy), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(18f * sx, 6f * sy), Offset(6f * sx, 18f * sy), stroke.width, StrokeCap.Round)
}
