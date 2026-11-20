package io.github.ivan_olivera_dev.cenit.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws the design-system focus ring (`2 dp solid focusRing, offset 2 dp`) whenever the element
 * holds keyboard/D-pad focus, distinct from selection styling.
 */
fun Modifier.cenitFocusRing(
    color: Color,
    cornerRadius: Dp = 10.dp,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    this
        .onFocusChanged { focused = it.isFocused }
        .drawWithContent {
            drawContent()
            if (focused) {
                val strokePx = 2.dp.toPx()
                val offsetPx = 2.dp.toPx()
                val radiusPx = (cornerRadius + 2.dp).toPx()
                drawRoundRect(
                    color = color,
                    topLeft = Offset(-offsetPx, -offsetPx),
                    size = Size(size.width + offsetPx * 2f, size.height + offsetPx * 2f),
                    cornerRadius = CornerRadius(radiusPx, radiusPx),
                    style = Stroke(width = strokePx),
                )
            }
        }
}
