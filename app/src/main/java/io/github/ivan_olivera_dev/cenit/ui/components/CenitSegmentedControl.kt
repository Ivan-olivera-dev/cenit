package io.github.ivan_olivera_dev.cenit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.ivan_olivera_dev.cenit.ui.theme.LocalCenitPalette

/**
 * Editorial segmented control (`segmented` token of `design/system.md`):
 * `44 dp` high, `10 dp` radius, `1 dp controlBorder`, selected segment filled with `surfaceSubtle`,
 * `textPrimary` label, and a `2 dp sun` underline.
 */
@Composable
fun <T> CenitSegmentedControl(
    options: List<Pair<T, String>>,
    selectedOption: T,
    onSelect: (T) -> Unit,
    groupContentDescription: String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalCenitPalette.current
    val shape = RoundedCornerShape(10.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .border(1.dp, palette.controlBorder, shape)
            .semantics { contentDescription = groupContentDescription },
    ) {
        options.forEach { (option, label) ->
            val isSelected = option == selectedOption
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .cenitFocusRing(palette.focusRing, cornerRadius = 8.dp)
                    .then(if (isSelected) Modifier.background(palette.surfaceSubtle) else Modifier)
                    .drawBehind {
                        if (isSelected) {
                            val inset = 14.dp.toPx().coerceAtMost(size.width / 4f)
                            val lineHeight = 2.dp.toPx()
                            drawRect(
                                color = palette.sun,
                                topLeft = Offset(inset, size.height - lineHeight),
                                size = Size((size.width - inset * 2f).coerceAtLeast(0f), lineHeight),
                            )
                        }
                    }
                    .clickable(role = Role.Tab) { onSelect(option) }
                    .padding(horizontal = 6.dp)
                    .semantics { selected = isSelected },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    ),
                    color = if (isSelected) palette.textPrimary else palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
