package io.github.ivan_olivera_dev.cenit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ivan_olivera_dev.cenit.R
import io.github.ivan_olivera_dev.cenit.ui.theme.LocalCenitPalette
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * Seven-day date strip (`date-strip` token): 7 cells with `radius 12 dp`, `gap 6 dp`,
 * today outlined `1.5 dp sun`, selected filled `surfaceSubtle`, touch target ≥ 44 dp.
 */
@Composable
fun DateStrip(
    weekDays: List<LocalDate>,
    selectedDate: LocalDate,
    today: LocalDate,
    locale: Locale,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalCenitPalette.current
    val stripDesc = stringResource(R.string.cd_date_strip)
    val todaySuffix = stringResource(R.string.cd_date_today_suffix)
    val fullDateFormatter = remember(locale) {
        DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = stripDesc },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        weekDays.forEach { day ->
            val isToday = day == today
            val isSelected = day == selectedDate
            val weekdayLetter = remember(day, locale) {
                day.dayOfWeek.getDisplayName(JavaTextStyle.NARROW, locale).uppercase(locale)
            }
            val dayDesc = remember(day, isToday, todaySuffix, fullDateFormatter) {
                fullDateFormatter.format(day) + if (isToday) todaySuffix else ""
            }
            val shape = RoundedCornerShape(12.dp)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .cenitFocusRing(palette.focusRing, cornerRadius = 12.dp)
                    .clip(shape)
                    .then(
                        if (isSelected) Modifier.background(palette.surfaceSubtle) else Modifier,
                    )
                    .then(
                        if (isToday) Modifier.border(1.5.dp, palette.sun, shape) else Modifier,
                    )
                    .clickable(role = Role.Button) { onSelectDate(day) }
                    .semantics(mergeDescendants = true) {
                        contentDescription = dayDesc
                        selected = isSelected
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = weekdayLetter,
                    style = MaterialTheme.typography.labelMedium.copy(
                        letterSpacing = 0.sp,
                        fontWeight = FontWeight.Normal,
                    ),
                    color = palette.textSecondary,
                    maxLines = 1,
                )
                Text(
                    text = day.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        lineHeight = 22.sp,
                        fontFeatureSettings = "tnum",
                    ),
                    color = palette.textPrimary,
                    maxLines = 1,
                )
            }
        }
    }
}
