package io.github.ivan_olivera_dev.cenit.ui.places

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.ivan_olivera_dev.cenit.R
import io.github.ivan_olivera_dev.cenit.core.Place
import io.github.ivan_olivera_dev.cenit.core.places.CoordinateError
import io.github.ivan_olivera_dev.cenit.core.places.CoordinateParser
import io.github.ivan_olivera_dev.cenit.core.places.CoordinatesValidationResult
import io.github.ivan_olivera_dev.cenit.data.SavedPlace
import io.github.ivan_olivera_dev.cenit.ui.components.cenitFocusRing
import io.github.ivan_olivera_dev.cenit.ui.planner.displayName
import io.github.ivan_olivera_dev.cenit.ui.theme.LocalCenitPalette

/** Scrim token from design/system.md (`rgba(0,0,0,.6)`). */
private val CenitScrim = Color.Black.copy(alpha = 0.6f)

@Composable
internal fun CenitModalPanel(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val palette = LocalCenitPalette.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CenitScrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .padding(horizontal = 22.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(palette.surfaceColor)
                    .border(1.dp, palette.borderColor, RoundedCornerShape(20.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(20.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
internal fun CenitPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalCenitPalette.current
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .cenitFocusRing(palette.focusRing, cornerRadius = 16.dp)
            .clip(shape)
            .background(palette.textPrimary)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = palette.bgColor,
        )
    }
}

@Composable
internal fun CenitSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val palette = LocalCenitPalette.current
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .cenitFocusRing(palette.focusRing, cornerRadius = 14.dp)
            .clip(shape)
            .border(1.dp, palette.controlBorder, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            leadingIcon()
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textPrimary,
        )
    }
}

@Composable
internal fun CenitDangerTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalCenitPalette.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .cenitFocusRing(palette.focusRing, cornerRadius = 12.dp)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = palette.danger,
        )
    }
}

@Composable
internal fun CenitTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    errorText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val palette = LocalCenitPalette.current
    val shape = RoundedCornerShape(14.dp)
    val borderColor = if (errorText != null) palette.danger else palette.controlBorder

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .cenitFocusRing(palette.focusRing, cornerRadius = 14.dp)
                .clip(shape)
                .border(1.dp, borderColor, shape)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = palette.textPrimary,
                    fontFeatureSettings = "tnum",
                ),
                cursorBrush = SolidColor(palette.textPrimary),
                keyboardOptions = keyboardOptions,
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.textSecondary,
                        )
                    }
                    inner()
                },
            )
        }
        if (errorText != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = palette.danger,
            )
        }
    }
}

@Composable
internal fun ManualCoordinatesDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, place: Place) -> Unit,
) {
    val palette = LocalCenitPalette.current
    val defaultName = stringResource(R.string.places_manual_default_name)

    var nameInput by remember { mutableStateOf("") }
    var latInput by remember { mutableStateOf("") }
    var lonInput by remember { mutableStateOf("") }
    var latError by remember { mutableStateOf<CoordinateError?>(null) }
    var lonError by remember { mutableStateOf<CoordinateError?>(null) }

    val latErrorText = when (latError) {
        CoordinateError.EMPTY -> stringResource(R.string.places_error_lat_empty)
        CoordinateError.INVALID_FORMAT -> stringResource(R.string.places_error_lat_format)
        CoordinateError.OUT_OF_RANGE -> stringResource(R.string.places_error_lat_range)
        null -> null
    }
    val lonErrorText = when (lonError) {
        CoordinateError.EMPTY -> stringResource(R.string.places_error_lon_empty)
        CoordinateError.INVALID_FORMAT -> stringResource(R.string.places_error_lon_format)
        CoordinateError.OUT_OF_RANGE -> stringResource(R.string.places_error_lon_range)
        null -> null
    }

    CenitModalPanel(onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.places_manual_title),
            style = MaterialTheme.typography.headlineLarge,
            color = palette.textPrimary,
        )
        Spacer(modifier = Modifier.height(16.dp))

        CenitTextField(
            label = stringResource(R.string.places_manual_name_label),
            value = nameInput,
            onValueChange = { nameInput = it },
            placeholder = stringResource(R.string.places_manual_name_placeholder),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Spacer(modifier = Modifier.height(12.dp))

        CenitTextField(
            label = stringResource(R.string.places_manual_lat_label),
            value = latInput,
            onValueChange = {
                latInput = it
                latError = null
            },
            placeholder = stringResource(R.string.places_manual_lat_placeholder),
            errorText = latErrorText,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
            ),
        )
        Spacer(modifier = Modifier.height(12.dp))

        CenitTextField(
            label = stringResource(R.string.places_manual_lon_label),
            value = lonInput,
            onValueChange = {
                lonInput = it
                lonError = null
            },
            placeholder = stringResource(R.string.places_manual_lon_placeholder),
            errorText = lonErrorText,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
        )
        Spacer(modifier = Modifier.height(20.dp))

        CenitPrimaryButton(
            text = stringResource(R.string.places_manual_use_action),
            onClick = {
                when (val result = CoordinateParser.validate(latInput, lonInput)) {
                    is CoordinatesValidationResult.Valid -> {
                        val finalName = nameInput.trim().ifEmpty { defaultName }
                        onConfirm(finalName, result.place)
                    }
                    is CoordinatesValidationResult.Invalid -> {
                        latError = result.latitudeError
                        lonError = result.longitudeError
                    }
                }
            },
        )
        Spacer(modifier = Modifier.height(10.dp))
        CenitSecondaryButton(
            text = stringResource(R.string.places_cancel),
            onClick = onDismiss,
        )
    }
}

@Composable
internal fun EditSavedPlaceDialog(
    place: SavedPlace,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onRequestDelete: () -> Unit,
) {
    val palette = LocalCenitPalette.current
    val displayName = place.displayName()
    var nameInput by remember(place.id, displayName) { mutableStateOf(displayName) }
    var emptyError by remember(place.id) { mutableStateOf(false) }

    CenitModalPanel(onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.places_edit_title),
            style = MaterialTheme.typography.headlineLarge,
            color = palette.textPrimary,
        )
        Spacer(modifier = Modifier.height(16.dp))

        CenitTextField(
            label = stringResource(R.string.places_name_label),
            value = nameInput,
            onValueChange = {
                nameInput = it
                emptyError = false
            },
            placeholder = displayName,
            errorText = if (emptyError) stringResource(R.string.places_name_error_empty) else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        Spacer(modifier = Modifier.height(20.dp))

        CenitPrimaryButton(
            text = stringResource(R.string.places_save_changes),
            onClick = {
                val trimmed = nameInput.trim()
                if (trimmed.isEmpty()) {
                    emptyError = true
                } else {
                    onRename(trimmed)
                }
            },
        )
        Spacer(modifier = Modifier.height(10.dp))
        CenitSecondaryButton(
            text = stringResource(R.string.places_cancel),
            onClick = onDismiss,
        )
        Spacer(modifier = Modifier.height(8.dp))
        CenitDangerTextButton(
            text = stringResource(R.string.places_delete_action),
            onClick = onRequestDelete,
        )
    }
}

@Composable
internal fun DeleteConfirmationDialog(
    placeName: String,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    val palette = LocalCenitPalette.current
    CenitModalPanel(onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.places_delete_confirm_title),
            style = MaterialTheme.typography.headlineLarge,
            color = palette.textPrimary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.places_delete_confirm_body, placeName),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary,
        )
        Spacer(modifier = Modifier.height(20.dp))
        CenitDangerTextButton(
            text = stringResource(R.string.places_delete_confirm_button),
            onClick = onConfirmDelete,
        )
        Spacer(modifier = Modifier.height(8.dp))
        CenitSecondaryButton(
            text = stringResource(R.string.places_cancel),
            onClick = onDismiss,
        )
    }
}

@Composable
internal fun LocationRationaleDialog(
    onDismiss: () -> Unit,
    onAllow: () -> Unit,
) {
    val palette = LocalCenitPalette.current
    CenitModalPanel(onDismiss = onDismiss) {
        Text(
            text = stringResource(R.string.places_gps_rationale_title),
            style = MaterialTheme.typography.headlineLarge,
            color = palette.textPrimary,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.places_gps_rationale_body),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary,
        )
        Spacer(modifier = Modifier.height(20.dp))
        CenitPrimaryButton(
            text = stringResource(R.string.places_gps_rationale_allow),
            onClick = onAllow,
        )
        Spacer(modifier = Modifier.height(10.dp))
        CenitSecondaryButton(
            text = stringResource(R.string.places_gps_rationale_later),
            onClick = onDismiss,
        )
    }
}
