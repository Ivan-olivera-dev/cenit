package io.github.ivan_olivera_dev.cenit.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Minimal stroke icons matching tools/design-kit/lib.js (`24×24` viewBox, `1.5` stroke,
 * round caps and joins). No external icon pack dependency required.
 */
enum class CenitIconKind {
    HOY,
    CIELO,
    BRUJULA,
    LUGARES,
    PREV,
    NEXT,
    BELL,
    CALENDAR,
    SETTINGS,
    BACK,
    CAMERA,
}

@Composable
fun CenitIcon(
    kind: CenitIconKind,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
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
            CenitIconKind.HOY -> drawHoy(tint, sx, sy, stroke)
            CenitIconKind.CIELO -> drawCielo(tint, sx, sy, stroke)
            CenitIconKind.BRUJULA -> drawBrujula(tint, sx, sy, stroke)
            CenitIconKind.LUGARES -> drawLugares(tint, sx, sy, stroke)
            CenitIconKind.PREV -> drawChevron(tint, sx, sy, stroke, left = true)
            CenitIconKind.NEXT -> drawChevron(tint, sx, sy, stroke, left = false)
            CenitIconKind.BELL -> drawBell(tint, sx, sy, stroke)
            CenitIconKind.CALENDAR -> drawCalendar(tint, sx, sy, stroke)
            CenitIconKind.SETTINGS -> drawSettings(tint, sx, sy, stroke)
            CenitIconKind.BACK -> drawBack(tint, sx, sy, stroke)
            CenitIconKind.CAMERA -> drawCamera(tint, sx, sy, stroke)
        }
    }
}

private fun DrawScope.drawHoy(color: Color, sx: Float, sy: Float, stroke: Stroke) {
    drawLine(color, Offset(3f * sx, 17f * sy), Offset(21f * sx, 17f * sy), stroke.width, StrokeCap.Round)
    drawArc(
        color = color,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(6.5f * sx, 11.5f * sy),
        size = Size(11f * sx, 11f * sy),
        style = stroke,
    )
    drawLine(color, Offset(12f * sx, 5f * sy), Offset(12f * sx, 7.5f * sy), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(4.6f * sx, 9.6f * sy), Offset(6.4f * sx, 11.4f * sy), stroke.width, StrokeCap.Round)
    drawLine(color, Offset(19.4f * sx, 9.6f * sy), Offset(17.6f * sx, 11.4f * sy), stroke.width, StrokeCap.Round)
}

private fun DrawScope.drawCielo(color: Color, sx: Float, sy: Float, stroke: Stroke) {
    drawLine(color, Offset(2.5f * sx, 18f * sy), Offset(21.5f * sx, 18f * sy), stroke.width, StrokeCap.Round)
    val arc = Path().apply {
        moveTo(4.5f * sx, 18f * sy)
        cubicTo(6f * sx, 9.5f * sy, 18f * sx, 9.5f * sy, 19.5f * sx, 18f * sy)
    }
    drawPath(arc, color, style = stroke)
    drawCircle(color, radius = 1.8f * sx, center = Offset(12f * sx, 11.6f * sy), style = stroke)
}

private fun DrawScope.drawBrujula(color: Color, sx: Float, sy: Float, stroke: Stroke) {
    drawCircle(color, radius = 9f * sx, center = Offset(12f * sx, 12f * sy), style = stroke)
    val needle = Path().apply {
        moveTo(14.8f * sx, 9.2f * sy)
        lineTo(12.9f * sx, 14.1f * sy)
        lineTo(9.2f * sx, 15.8f * sy)
        lineTo(11.1f * sx, 10.9f * sy)
        close()
    }
    drawPath(needle, color, style = stroke)
}

private fun DrawScope.drawLugares(color: Color, sx: Float, sy: Float, stroke: Stroke) {
    val pin = Path().apply {
        moveTo(12f * sx, 21f * sy)
        cubicTo(7.5f * sx, 16.8f * sy, 5.5f * sx, 13.5f * sy, 5.5f * sx, 10f * sy)
        cubicTo(5.5f * sx, 6.4f * sy, 8.4f * sx, 3.5f * sy, 12f * sx, 3.5f * sy)
        cubicTo(15.6f * sx, 3.5f * sy, 18.5f * sx, 6.4f * sy, 18.5f * sx, 10f * sy)
        cubicTo(18.5f * sx, 13.5f * sy, 16.5f * sx, 16.8f * sy, 12f * sx, 21f * sy)
        close()
    }
    drawPath(pin, color, style = stroke)
    drawCircle(color, radius = 2.3f * sx, center = Offset(12f * sx, 10f * sy), style = stroke)
}

private fun DrawScope.drawChevron(color: Color, sx: Float, sy: Float, stroke: Stroke, left: Boolean) {
    val path = Path().apply {
        if (left) {
            moveTo(15f * sx, 5f * sy)
            lineTo(8f * sx, 12f * sy)
            lineTo(15f * sx, 19f * sy)
        } else {
            moveTo(9f * sx, 5f * sy)
            lineTo(16f * sx, 12f * sy)
            lineTo(9f * sx, 19f * sy)
        }
    }
    drawPath(path, color, style = stroke)
}

private fun DrawScope.drawBell(color: Color, sx: Float, sy: Float, stroke: Stroke) {
    val body = Path().apply {
        moveTo(6f * sx, 16.5f * sy)
        lineTo(6f * sx, 11f * sy)
        cubicTo(6f * sx, 7.7f * sy, 8.7f * sx, 5f * sy, 12f * sx, 5f * sy)
        cubicTo(15.3f * sx, 5f * sy, 18f * sx, 7.7f * sy, 18f * sx, 11f * sy)
        lineTo(18f * sx, 16.5f * sy)
        lineTo(19.5f * sx, 18.5f * sy)
        lineTo(4.5f * sx, 18.5f * sy)
        close()
    }
    drawPath(body, color, style = stroke)
    drawArc(
        color = color,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(10f * sx, 18.5f * sy),
        size = Size(4f * sx, 4f * sy),
        style = stroke,
    )
}

private fun DrawScope.drawCalendar(color: Color, sx: Float, sy: Float, stroke: Stroke) {
    fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
        drawLine(color, Offset(x1 * sx, y1 * sy), Offset(x2 * sx, y2 * sy), stroke.width, StrokeCap.Round)
    line(4f, 6f, 20f, 6f)
    line(20f, 6f, 20f, 20f)
    line(20f, 20f, 4f, 20f)
    line(4f, 20f, 4f, 6f)
    line(4f, 10f, 20f, 10f)
    line(8f, 3.5f, 8f, 7.5f)
    line(16f, 3.5f, 16f, 7.5f)
}

private fun DrawScope.drawSettings(color: Color, sx: Float, sy: Float, stroke: Stroke) {
    // Minimal 8-tooth gear matching the 24×24 viewBox, 1.5 stroke weight, round caps & joins.
    val cx = 12f
    val cy = 12f
    val innerR = 6.5f
    val outerR = 9.2f
    val path = Path()
    for (i in 0 until 8) {
        val baseDeg = i * 45.0
        val points = listOf(
            Math.toRadians(baseDeg - 15.0) to innerR,
            Math.toRadians(baseDeg - 9.0) to outerR,
            Math.toRadians(baseDeg + 9.0) to outerR,
            Math.toRadians(baseDeg + 15.0) to innerR,
        )
        points.forEachIndexed { idx, (rad, r) ->
            val x = (cx + r * cos(rad).toFloat()) * sx
            val y = (cy + r * sin(rad).toFloat()) * sy
            if (i == 0 && idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
    }
    path.close()
    drawPath(path, color, style = stroke)
    drawCircle(color, radius = 3f * sx, center = Offset(cx * sx, cy * sy), style = stroke)
}

private fun DrawScope.drawBack(color: Color, sx: Float, sy: Float, stroke: Stroke) {
    drawLine(color, Offset(19f * sx, 12f * sy), Offset(5f * sx, 12f * sy), stroke.width, StrokeCap.Round)
    val head = Path().apply {
        moveTo(11f * sx, 6f * sy)
        lineTo(5f * sx, 12f * sy)
        lineTo(11f * sx, 18f * sy)
    }
    drawPath(head, color, style = stroke)
}

private fun DrawScope.drawCamera(color: Color, sx: Float, sy: Float, stroke: Stroke) {
    // Matches tools/design-kit/ar.js ICON.camara: 24×24 viewBox, 1.5 stroke, round caps & joins.
    drawRoundRect(
        color = color,
        topLeft = Offset(3f * sx, 6.5f * sy),
        size = Size(18f * sx, 13f * sy),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5f * sx, 2.5f * sy),
        style = stroke,
    )
    val viewfinder = Path().apply {
        moveTo(8.5f * sx, 6.5f * sy)
        lineTo(10f * sx, 4f * sy)
        lineTo(14f * sx, 4f * sy)
        lineTo(15.5f * sx, 6.5f * sy)
    }
    drawPath(viewfinder, color, style = stroke)
    drawCircle(color, radius = 3.2f * sx, center = Offset(12f * sx, 13f * sy), style = stroke)
}
