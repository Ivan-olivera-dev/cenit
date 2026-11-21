package io.github.ivan_olivera_dev.cenit.ui.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.ivan_olivera_dev.cenit.R
import io.github.ivan_olivera_dev.cenit.alerts.AlertPermissions
import io.github.ivan_olivera_dev.cenit.core.format.ClockFormat
import io.github.ivan_olivera_dev.cenit.settings.AppLanguage
import io.github.ivan_olivera_dev.cenit.settings.AppSettings
import io.github.ivan_olivera_dev.cenit.settings.AppTheme
import io.github.ivan_olivera_dev.cenit.ui.components.CenitIcon
import io.github.ivan_olivera_dev.cenit.ui.components.CenitIconKind
import io.github.ivan_olivera_dev.cenit.ui.components.CenitSegmentedControl
import io.github.ivan_olivera_dev.cenit.ui.components.cenitFocusRing
import io.github.ivan_olivera_dev.cenit.ui.theme.LocalCenitPalette
import java.util.Locale

/**
 * Full-screen Settings screen opened from the gear button on Hoy (T16, S8, L34).
 *
 * Uses only [io.github.ivan_olivera_dev.cenit.ui.theme.CenitPalette] tokens, uppercase
 * `--type-label` section eyebrows, `segmented` controls, and hairline editorial rows.
 */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalCenitPalette.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) {
        configuration.locales[0] ?: Locale.getDefault()
    }
    val versionName = remember(context) { resolveAppVersionName(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var canScheduleExactAlarms by remember(context) {
        mutableStateOf(AlertPermissions.canScheduleExactAlarms(context))
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canScheduleExactAlarms = AlertPermissions.canScheduleExactAlarms(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val backDesc = stringResource(R.string.settings_cd_back)
    val languageSectionLabel = stringResource(R.string.settings_section_language)
    val themeSectionLabel = stringResource(R.string.settings_section_theme)
    val formatSectionLabel = stringResource(R.string.settings_section_format)
    val clockLabel = stringResource(R.string.settings_clock_label)
    val degreesLabel = stringResource(R.string.settings_degrees_label)
    val alertsSectionLabel = stringResource(R.string.settings_section_alerts)
    val aboutSectionLabel = stringResource(R.string.settings_section_about)

    val languageOptions = listOf(
        AppLanguage.SYSTEM to stringResource(R.string.settings_language_system),
        AppLanguage.ES to stringResource(R.string.settings_language_es),
        AppLanguage.EN to stringResource(R.string.settings_language_en),
    )
    val themeOptions = listOf(
        AppTheme.SYSTEM to stringResource(R.string.settings_theme_system),
        AppTheme.LIGHT to stringResource(R.string.settings_theme_light),
        AppTheme.DARK to stringResource(R.string.settings_theme_dark),
    )
    val clockOptions = listOf(
        ClockFormat.H24 to stringResource(R.string.settings_clock_24h),
        ClockFormat.H12 to stringResource(R.string.settings_clock_12h),
    )
    val degreeOptions = listOf(
        true to stringResource(R.string.settings_degrees_with_decimals),
        false to stringResource(R.string.settings_degrees_without_decimals),
    )
    val aboutRows = listOf(
        stringResource(R.string.settings_about_version, versionName),
        stringResource(R.string.settings_about_license),
        stringResource(R.string.settings_about_astronomy),
        stringResource(R.string.settings_about_geonames),
        stringResource(R.string.settings_about_offline),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bgColor)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 460.dp)
                .padding(horizontal = 22.dp),
        ) {
            // Top bar: 44 dp back button + headline title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .cenitFocusRing(palette.focusRing, cornerRadius = 10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button, onClick = onBack)
                        .semantics { contentDescription = backDesc },
                    contentAlignment = Alignment.Center,
                ) {
                    CenitIcon(
                        kind = CenitIconKind.BACK,
                        tint = palette.textPrimary,
                        size = 22.dp,
                    )
                }
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            HorizontalDivider(thickness = 1.dp, color = palette.borderColor)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 20.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                // 1. Language
                SettingsSection(eyebrow = languageSectionLabel, locale = locale) {
                    CenitSegmentedControl(
                        options = languageOptions,
                        selectedOption = settings.language,
                        onSelect = { chosen ->
                            if (chosen != settings.language) {
                                onSettingsChange(settings.copy(language = chosen))
                            }
                        },
                        groupContentDescription = languageSectionLabel,
                    )
                }

                // 2. Theme
                SettingsSection(eyebrow = themeSectionLabel, locale = locale) {
                    CenitSegmentedControl(
                        options = themeOptions,
                        selectedOption = settings.theme,
                        onSelect = { chosen ->
                            if (chosen != settings.theme) {
                                onSettingsChange(settings.copy(theme = chosen))
                            }
                        },
                        groupContentDescription = themeSectionLabel,
                    )
                }

                // 3. Format (Clock 24 h / 12 h + Degrees with / without decimals)
                SettingsSection(eyebrow = formatSectionLabel, locale = locale) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = clockLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary,
                            )
                            CenitSegmentedControl(
                                options = clockOptions,
                                selectedOption = settings.clock,
                                onSelect = { chosen ->
                                    if (chosen != settings.clock) {
                                        onSettingsChange(settings.copy(clock = chosen))
                                    }
                                },
                                groupContentDescription = clockLabel,
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = degreesLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary,
                            )
                            CenitSegmentedControl(
                                options = degreeOptions,
                                selectedOption = settings.degreeDecimals,
                                onSelect = { chosen ->
                                    if (chosen != settings.degreeDecimals) {
                                        onSettingsChange(settings.copy(degreeDecimals = chosen))
                                    }
                                },
                                groupContentDescription = degreesLabel,
                            )
                        }
                    }
                }

                // 4. Alerts (optional exact-alarm permission)
                SettingsSection(eyebrow = alertsSectionLabel, locale = locale) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 32.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.settings_alerts_exact_title),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.textPrimary,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = stringResource(
                                        if (canScheduleExactAlarms) {
                                            R.string.settings_alerts_exact_on
                                        } else {
                                            R.string.settings_alerts_exact_off
                                        },
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (canScheduleExactAlarms) {
                                        palette.sun
                                    } else {
                                        palette.textSecondary
                                    },
                                )
                            }
                            Text(
                                text = stringResource(R.string.alert_exact_alarm_explanation),
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textSecondary,
                            )
                        }

                        if (!canScheduleExactAlarms && AlertPermissions.requiresExactAlarmPermission()) {
                            val buttonShape = RoundedCornerShape(14.dp)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 46.dp)
                                    .cenitFocusRing(palette.focusRing, cornerRadius = 14.dp)
                                    .clip(buttonShape)
                                    .border(1.dp, palette.controlBorder, buttonShape)
                                    .clickable(role = Role.Button) {
                                        AlertPermissions.openExactAlarmSettings(context)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.alert_exact_alarm_enable),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.textPrimary,
                                )
                            }
                        }
                    }
                }

                // 5. About (editorial hairline rows, no repository link)
                SettingsSection(eyebrow = aboutSectionLabel, locale = locale) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        aboutRows.forEachIndexed { index, rowText ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Text(
                                    text = rowText,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFeatureSettings = "tnum",
                                    ),
                                    color = palette.textPrimary,
                                )
                            }
                            if (index < aboutRows.lastIndex) {
                                HorizontalDivider(thickness = 1.dp, color = palette.borderColor)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    eyebrow: String,
    locale: Locale,
    content: @Composable () -> Unit,
) {
    val palette = LocalCenitPalette.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = eyebrow.uppercase(locale),
            style = MaterialTheme.typography.labelMedium,
            color = palette.textSecondary,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(10.dp))
        content()
    }
}

@Suppress("DEPRECATION")
private fun resolveAppVersionName(context: Context): String {
    return runCatching {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        packageInfo.versionName?.takeIf { it.isNotBlank() }
    }.getOrNull() ?: "0.1.0"
}
