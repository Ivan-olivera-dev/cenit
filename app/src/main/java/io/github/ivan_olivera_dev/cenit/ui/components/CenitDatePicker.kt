package io.github.ivan_olivera_dev.cenit.ui.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.ivan_olivera_dev.cenit.R
import io.github.ivan_olivera_dev.cenit.ui.theme.LocalCenitPalette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Converts a calendar [LocalDate] into the UTC-midnight epoch milliseconds expected by
 * Material 3's [androidx.compose.material3.DatePickerState], independent of the device or
 * active place time zone.
 */
fun localDateToPickerUtcMillis(date: LocalDate): Long =
    date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/**
 * Converts a Material 3 [androidx.compose.material3.DatePickerState.selectedDateMillis] value
 * (UTC midnight) back to the exact calendar [LocalDate], independent of any local time zone.
 */
fun pickerUtcMillisToLocalDate(utcMillis: Long): LocalDate =
    Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()

/**
 * Reusable Material 3 date picker dialog styled with [io.github.ivan_olivera_dev.cenit.ui.theme.CenitPalette].
 *
 * Uses pure UTC-midnight conversion so [initialDate] and the confirmed [LocalDate] never shift by
 * a day in time zones with positive or negative UTC offsets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CenitDatePicker(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val palette = LocalCenitPalette.current
    val initialUtcMillis = localDateToPickerUtcMillis(initialDate)
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialUtcMillis,
        initialDisplayedMonthMillis = initialUtcMillis,
    )

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = palette.textPrimary,
        unfocusedTextColor = palette.textPrimary,
        errorTextColor = palette.textPrimary,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
        cursorColor = palette.sun,
        errorCursorColor = palette.danger,
        focusedBorderColor = palette.sun,
        unfocusedBorderColor = palette.controlBorder,
        errorBorderColor = palette.danger,
        focusedLabelColor = palette.sun,
        unfocusedLabelColor = palette.textSecondary,
        errorLabelColor = palette.danger,
        focusedPlaceholderColor = palette.textSecondary,
        unfocusedPlaceholderColor = palette.textSecondary,
        focusedSupportingTextColor = palette.textSecondary,
        unfocusedSupportingTextColor = palette.textSecondary,
        errorSupportingTextColor = palette.danger,
    )

    val pickerColors = DatePickerDefaults.colors(
        containerColor = palette.surfaceColor,
        titleContentColor = palette.textSecondary,
        headlineContentColor = palette.textPrimary,
        weekdayContentColor = palette.textSecondary,
        subheadContentColor = palette.textSecondary,
        navigationContentColor = palette.textPrimary,
        yearContentColor = palette.textPrimary,
        disabledYearContentColor = palette.textSecondary,
        currentYearContentColor = palette.sun,
        selectedYearContentColor = palette.bgColor,
        disabledSelectedYearContentColor = palette.bgColor,
        selectedYearContainerColor = palette.sun,
        disabledSelectedYearContainerColor = palette.surfaceSubtle,
        dayContentColor = palette.textPrimary,
        disabledDayContentColor = palette.textSecondary,
        selectedDayContentColor = palette.bgColor,
        disabledSelectedDayContentColor = palette.bgColor,
        selectedDayContainerColor = palette.sun,
        disabledSelectedDayContainerColor = palette.surfaceSubtle,
        todayContentColor = palette.sun,
        todayDateBorderColor = palette.sun,
        dayInSelectionRangeContainerColor = palette.surfaceSubtle,
        dayInSelectionRangeContentColor = palette.textPrimary,
        dividerColor = palette.borderColor,
        dateTextFieldColors = textFieldColors,
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp,
        colors = pickerColors,
        confirmButton = {
            val selectedMillis = datePickerState.selectedDateMillis
            TextButton(
                onClick = {
                    if (selectedMillis != null) {
                        onConfirm(pickerUtcMillisToLocalDate(selectedMillis))
                    }
                },
                enabled = selectedMillis != null,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .cenitFocusRing(palette.focusRing, cornerRadius = 10.dp),
            ) {
                Text(
                    text = stringResource(R.string.date_picker_confirm),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selectedMillis != null) palette.sun else palette.textSecondary,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .cenitFocusRing(palette.focusRing, cornerRadius = 10.dp),
            ) {
                Text(
                    text = stringResource(R.string.date_picker_cancel),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                )
            }
        },
    ) {
        DatePicker(
            state = datePickerState,
            colors = pickerColors,
        )
    }
}
