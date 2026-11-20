package io.github.ivan_olivera_dev.cenit.ui.today

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ivan_olivera_dev.cenit.R
import io.github.ivan_olivera_dev.cenit.alerts.AlertNotifier
import io.github.ivan_olivera_dev.cenit.alerts.AlertPermissions
import io.github.ivan_olivera_dev.cenit.core.Cenit
import io.github.ivan_olivera_dev.cenit.core.Ephemeris
import io.github.ivan_olivera_dev.cenit.core.MoonDay
import io.github.ivan_olivera_dev.cenit.core.MoonPhaseName
import io.github.ivan_olivera_dev.cenit.core.PathPoint
import io.github.ivan_olivera_dev.cenit.core.Place
import io.github.ivan_olivera_dev.cenit.core.SunDay
import io.github.ivan_olivera_dev.cenit.core.alerts.AlertKind
import io.github.ivan_olivera_dev.cenit.core.format.CenitFormat
import io.github.ivan_olivera_dev.cenit.core.format.ClockFormat
import io.github.ivan_olivera_dev.cenit.core.sky.DayRibbon
import io.github.ivan_olivera_dev.cenit.core.sky.DayRibbonSegment
import io.github.ivan_olivera_dev.cenit.core.sky.SkyEvent
import io.github.ivan_olivera_dev.cenit.core.sky.SkyEventKind
import io.github.ivan_olivera_dev.cenit.core.sky.SkyEvents
import io.github.ivan_olivera_dev.cenit.core.sky.SkyFormatting
import io.github.ivan_olivera_dev.cenit.core.sky.SkyPhase
import io.github.ivan_olivera_dev.cenit.core.sky.UpcomingSkyEvent
import io.github.ivan_olivera_dev.cenit.core.sky.shadow.ShadowDirection
import io.github.ivan_olivera_dev.cenit.settings.LocalAppSettings
import io.github.ivan_olivera_dev.cenit.ui.components.CenitDatePicker
import io.github.ivan_olivera_dev.cenit.ui.components.CenitIcon
import io.github.ivan_olivera_dev.cenit.ui.components.CenitIconKind
import io.github.ivan_olivera_dev.cenit.ui.components.DateStrip
import io.github.ivan_olivera_dev.cenit.ui.components.DayRibbonBar
import io.github.ivan_olivera_dev.cenit.ui.components.DomeHorizonTick
import io.github.ivan_olivera_dev.cenit.ui.components.DomeReferencePaths
import io.github.ivan_olivera_dev.cenit.ui.components.PhaseGlyph
import io.github.ivan_olivera_dev.cenit.ui.components.SkyDome
import io.github.ivan_olivera_dev.cenit.ui.components.TimeScrubber
import io.github.ivan_olivera_dev.cenit.ui.components.cenitFocusRing
import io.github.ivan_olivera_dev.cenit.ui.planner.PlannerState
import io.github.ivan_olivera_dev.cenit.ui.theme.CenitPalette
import io.github.ivan_olivera_dev.cenit.ui.theme.LocalCenitPalette
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private data class TodayDayBundle(
    val sunDay: SunDay,
    val moonDay: MoonDay,
    val sunPath: List<PathPoint>,
    val moonPath: List<PathPoint>,
    val ribbonSegments: List<DayRibbonSegment>,
    val events: List<SkyEvent>,
    val sunriseTick: DomeHorizonTick?,
    val sunsetTick: DomeHorizonTick?,
)

private enum class TodayAlertNotice {
    PERMISSION_DENIED,
    NO_OCCURRENCE,
}

/**
 * Hoy screen of Artboard 02 (Cúpula): flat-compass sky dome, date strip, focal time with sun
 * position and shadow ratio, 24 h day ribbon, time scrubber, moon phase summary, and next events.
 * Fits without scrolling on a typical phone.
 */
@Composable
fun TodayScreen(
    state: PlannerState,
    today: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onShiftDays: (Long) -> Unit,
    onResetNow: () -> Unit,
    onScrubFraction: (Float) -> Unit,
    onStepMinutes: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    subscribedAlertKinds: Set<AlertKind> = emptySet(),
    onSubscribeAlert: (AlertKind) -> Boolean = { false },
    onUnsubscribeAlert: (AlertKind) -> Unit = {},
) {
    val palette = LocalCenitPalette.current
    val settings = LocalAppSettings.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) {
        configuration.locales[0] ?: Locale.getDefault()
    }
    val winds = stringArrayResource(R.array.compass_winds)

    val place = state.activePlace.place
    val zone = state.zone
    val selectedDate = state.selectedDate

    val dayBundle = remember(place, selectedDate, zone, settings.clock, locale) {
        val sunDay = Ephemeris.sunDay(place, selectedDate, zone)
        val moonDay = Ephemeris.moonDay(place, selectedDate, zone)
        val sunPath = Ephemeris.sunPath(place, selectedDate, zone, Duration.ofMinutes(10))
        val moonPath = Ephemeris.moonPath(place, selectedDate, zone, Duration.ofMinutes(15))
        val ribbonSegments = DayRibbon.segments(place, selectedDate, zone, sunDay)
        val events = SkyEvents.forDay(place, sunDay, moonDay)
        val sunriseTick = sunDay.sunrise?.let {
            DomeHorizonTick(
                azimuthDeg = Ephemeris.sunPosition(place, it).azimuth,
                label = CenitFormat.formatTime(it, zone, settings.clock, locale),
            )
        }
        val sunsetTick = sunDay.sunset?.let {
            DomeHorizonTick(
                azimuthDeg = Ephemeris.sunPosition(place, it).azimuth,
                label = CenitFormat.formatTime(it, zone, settings.clock, locale),
            )
        }
        TodayDayBundle(
            sunDay = sunDay,
            moonDay = moonDay,
            sunPath = sunPath,
            moonPath = moonPath,
            ribbonSegments = ribbonSegments,
            events = events,
            sunriseTick = sunriseTick,
            sunsetTick = sunsetTick,
        )
    }

    val referencePaths = remember(place, selectedDate.year, zone) {
        val seasons = Ephemeris.seasons(selectedDate.year)
        fun refDayPath(date: LocalDate) = Ephemeris.sunPath(place, date, zone, Duration.ofMinutes(15))
        DomeReferencePaths(
            juneSolstice = refDayPath(seasons.juneSolstice.atZone(zone).toLocalDate()),
            decemberSolstice = refDayPath(seasons.decemberSolstice.atZone(zone).toLocalDate()),
            equinox = refDayPath(seasons.marchEquinox.atZone(zone).toLocalDate()),
        )
    }

    val selectedInstant = state.selectedInstant
    val sunPosition = remember(place, selectedInstant) { Ephemeris.sunPosition(place, selectedInstant) }
    val moonPosition = remember(place, selectedInstant) { Ephemeris.moonPosition(place, selectedInstant) }
    val currentPhase = remember(place, selectedInstant) { SkyPhase.at(place, selectedInstant) }
    val currentMoonPhase = remember(selectedInstant) { Ephemeris.moonPhase(selectedInstant) }
    val upcomingEvents = remember(dayBundle.events, place, selectedDate, selectedInstant, zone) {
        SkyEvents.nextKeyEventsOrTomorrow(
            todayEvents = dayBundle.events,
            after = selectedInstant,
            limit = Int.MAX_VALUE,
            tomorrowEvents = {
                val tomorrow = selectedDate.plusDays(1)
                SkyEvents.forDay(
                    place,
                    Ephemeris.sunDay(place, tomorrow, zone),
                    Ephemeris.moonDay(place, tomorrow, zone),
                )
            },
        )
    }
    val weekDays = remember(selectedDate) { state.weekDays() }
    val monthYearFormatter = remember(locale) {
        DateTimeFormatter.ofPattern("LLLL yyyy", locale)
    }
    val monthYearLabel = remember(selectedDate, monthYearFormatter, locale) {
        val raw = monthYearFormatter.format(selectedDate)
        raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    val formattedTime = CenitFormat.formatLocalTime(state.selectedTime, settings.clock, locale)
    val coordsText = formatCoordinates(place, locale)

    val domeDescription = if (dayBundle.sunriseTick != null && dayBundle.sunsetTick != null) {
        stringResource(
            R.string.cd_sky_dome_normal,
            SkyFormatting.formatDecimal(dayBundle.sunriseTick.azimuthDeg, 0, locale),
            dayBundle.sunriseTick.label,
            SkyFormatting.formatDecimal(dayBundle.sunsetTick.azimuthDeg, 0, locale),
            dayBundle.sunsetTick.label,
        )
    } else {
        stringResource(R.string.cd_sky_dome_polar)
    }

    val sunPhaseLabel = skyPhaseLabel(currentPhase)
    val sunAzText = SkyFormatting.formatDecimal(sunPosition.azimuth, 0, locale)
    val sunWind = winds[SkyFormatting.compassIndex(sunPosition.azimuth)]
    val sunStatusLine = if (sunPosition.elevation < Cenit.RISE_SET_ALTITUDE_DEG) {
        stringResource(R.string.sun_position_below, sunPhaseLabel, sunAzText, sunWind)
    } else {
        stringResource(
            R.string.sun_position_above,
            sunPhaseLabel,
            sunAzText,
            sunWind,
            CenitFormat.formatAngleValue(sunPosition.elevation, settings.degreeDecimals, locale),
        )
    }

    val shadowInfo = remember(sunPosition) { ShadowDirection.fromSunPosition(sunPosition) }
    val shadowLine: String
    val shadowDesc: String?
    if (shadowInfo != null) {
        val shadowRatioText = SkyFormatting.formatDecimal(shadowInfo.ratio, 1, locale)
        val displayAzimuth = if (shadowInfo.azimuthDeg >= 359.5) 0.0 else shadowInfo.azimuthDeg
        val shadowAzText = SkyFormatting.formatDecimal(displayAzimuth, 0, locale)
        val shadowWind = winds[shadowInfo.compassIndex]
        shadowLine = stringResource(
            R.string.shadow_ratio_direction,
            shadowRatioText,
            shadowAzText,
            shadowWind,
        )
        shadowDesc = stringResource(
            R.string.cd_shadow_ratio_direction,
            shadowRatioText,
            shadowAzText,
            shadowWind,
        )
    } else {
        shadowLine = stringResource(R.string.shadow_none)
        shadowDesc = null
    }

    val nextAlertEvent = remember(upcomingEvents) {
        upcomingEvents.firstOrNull { AlertKind.fromSkyEventKind(it.event.kind) != null }
    }
    val alertTargetKind = remember(nextAlertEvent, upcomingEvents, place, selectedDate, selectedInstant, zone) {
        if (nextAlertEvent != null) {
            AlertKind.fromSkyEventKind(nextAlertEvent.event.kind)
        } else if (upcomingEvents.any { it.isNextDay }) {
            null
        } else {
            val tomorrow = selectedDate.plusDays(1)
            val tomorrowSun = Ephemeris.sunDay(place, tomorrow, zone)
            val tomorrowMoon = Ephemeris.moonDay(place, tomorrow, zone)
            SkyEvents.nextKeyEvents(
                SkyEvents.forDay(place, tomorrowSun, tomorrowMoon),
                after = selectedInstant,
            ).firstNotNullOfOrNull { AlertKind.fromSkyEventKind(it.kind) }
        }
    }
    val isAlertSubscribed = alertTargetKind != null && alertTargetKind in subscribedAlertKinds

    var pendingAlertKind by rememberSaveable { mutableStateOf<AlertKind?>(null) }
    var alertNotice by remember { mutableStateOf<TodayAlertNotice?>(null) }

    LaunchedEffect(state.activePlace, alertTargetKind) {
        alertNotice = null
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pending = pendingAlertKind
        pendingAlertKind = null
        if (granted && pending != null) {
            val scheduled = onSubscribeAlert(pending)
            alertNotice = if (scheduled) null else TodayAlertNotice.NO_OCCURRENCE
        } else if (!granted) {
            alertNotice = TodayAlertNotice.PERMISSION_DENIED
        }
    }

    fun onAlertToggleClick() {
        val kind = alertTargetKind ?: return
        alertNotice = null
        if (isAlertSubscribed) {
            onUnsubscribeAlert(kind)
        } else if (AlertPermissions.hasNotificationPermission(context)) {
            val scheduled = onSubscribeAlert(kind)
            if (!scheduled) {
                alertNotice = TodayAlertNotice.NO_OCCURRENCE
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingAlertKind = kind
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val alertDesc = if (alertTargetKind != null) {
        val targetName = stringResource(AlertNotifier.labelResId(alertTargetKind)).lowercase(locale)
        if (isAlertSubscribed) {
            stringResource(R.string.alert_cd_enabled, targetName)
        } else {
            stringResource(R.string.alert_cd_notify, targetName)
        }
    } else {
        stringResource(R.string.alert_cd_unavailable)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bgColor),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 460.dp)
                .padding(horizontal = 22.dp)
                .padding(top = 8.dp, bottom = 4.dp),
        ) {
            // 1. Place headline + "Ahora" reset + Settings gear button
            PlaceHeaderRow(
                placeName = state.activePlace.name,
                coordsText = coordsText,
                onResetNow = onResetNow,
                onOpenSettings = onOpenSettings,
            )

            // 2. Month context + previous/next day arrows + "Otra fecha" picker trigger + 7-day strip
            DateStripHeaderRow(
                monthYearLabel = monthYearLabel,
                onPrevDay = { onShiftDays(-1L) },
                onNextDay = { onShiftDays(1L) },
                onOpenDatePicker = { showDatePicker = true },
            )

            DateStrip(
                weekDays = weekDays,
                selectedDate = selectedDate,
                today = today,
                locale = locale,
                onSelectDate = onSelectDate,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 3. Flat-compass sky dome (fits strictly inside both width and remaining height)
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val domeSize = minOf(maxWidth, maxHeight)
                SkyDome(
                    sunPath = dayBundle.sunPath,
                    moonPath = dayBundle.moonPath,
                    referencePaths = referencePaths,
                    sunPosition = sunPosition,
                    moonPosition = moonPosition,
                    sunriseTick = dayBundle.sunriseTick,
                    sunsetTick = dayBundle.sunsetTick,
                    zone = zone,
                    contentDescription = domeDescription,
                    modifier = Modifier.size(domeSize),
                )
            }

            // 4. Dome reference legend
            DomeLegendRow()

            Spacer(modifier = Modifier.height(4.dp))

            // 5. Focal time + sun position + shadow ratio + inert alert toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = formattedTime,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontSize = if (settings.clock == ClockFormat.H12) 28.sp else 36.sp,
                        lineHeight = if (settings.clock == ClockFormat.H12) 32.sp else 40.sp,
                        fontFeatureSettings = "tnum",
                    ),
                    color = palette.textPrimary,
                    maxLines = 1,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                ) {
                    Text(
                        text = sunStatusLine,
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = palette.textSecondary,
                        textAlign = TextAlign.End,
                        // Two lines: "Noche · sol 270° O, bajo el horizonte" does not fit one on a 411 dp phone.
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = shadowLine,
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = palette.textSecondary,
                        textAlign = TextAlign.End,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (shadowDesc != null) {
                            Modifier.semantics { contentDescription = shadowDesc }
                        } else {
                            Modifier
                        },
                    )
                }

                // Alert toggle (design/system.md alert-toggle: 44×44 dp; 1 dp controlBorder;
                // pressed: fill --sun, icon --bg-color).
                val toggleShape = RoundedCornerShape(10.dp)
                val toggleBg = if (isAlertSubscribed) palette.sun else Color.Transparent
                val toggleBorder = if (isAlertSubscribed) palette.sun else palette.controlBorder
                val toggleIconTint = when {
                    isAlertSubscribed -> palette.bgColor
                    alertTargetKind != null -> palette.textPrimary
                    else -> palette.textSecondary
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .cenitFocusRing(palette.focusRing, cornerRadius = 10.dp)
                        .clip(toggleShape)
                        .background(toggleBg)
                        .border(1.dp, toggleBorder, toggleShape)
                        .clickable(
                            enabled = alertTargetKind != null,
                            role = Role.Button,
                            onClick = ::onAlertToggleClick,
                        )
                        .semantics {
                            contentDescription = alertDesc
                            selected = isAlertSubscribed
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    CenitIcon(
                        kind = CenitIconKind.BELL,
                        tint = toggleIconTint,
                        size = 22.dp,
                    )
                }
            }

            if (alertNotice != null) {
                Spacer(modifier = Modifier.height(4.dp))
                val noticeText = when (alertNotice) {
                    TodayAlertNotice.PERMISSION_DENIED ->
                        stringResource(R.string.alert_permission_denied_notice)
                    TodayAlertNotice.NO_OCCURRENCE ->
                        stringResource(R.string.alert_no_occurrence_notice)
                    null -> ""
                }
                TodayAlertNoticeBanner(
                    text = noticeText,
                    palette = palette,
                    onDismiss = { alertNotice = null },
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 6. 24-hour day ribbon + golden hours in text
            DayRibbonBar(
                segments = dayBundle.ribbonSegments,
                selectedFraction = state.selectedFraction,
                zone = zone,
            )

            // 7. Time scrubber
            TimeScrubber(
                selectedFraction = state.selectedFraction,
                formattedTime = formattedTime,
                onFractionChange = onScrubFraction,
                onStepMinutes = onStepMinutes,
            )

            Spacer(modifier = Modifier.height(4.dp))

            // 8. Moon phase glyph + name + % illumination + moon position
            HorizontalDivider(thickness = 1.dp, color = palette.borderColor)
            MoonSummaryRow(
                moonPhaseName = moonPhaseLabel(dayBundle.moonDay.phaseName),
                illuminationPct = (currentMoonPhase.illumination * 100.0).roundToInt().coerceIn(0, 100),
                moonPhase = currentMoonPhase,
                moonPosition = moonPosition,
                degreeDecimals = settings.degreeDecimals,
                winds = winds,
                locale = locale,
            )

            // 9. Next events rows (up to 3)
            HorizontalDivider(thickness = 1.dp, color = palette.borderColor)
            NextEventsBlock(
                events = upcomingEvents.take(3),
                zone = zone,
                clock = settings.clock,
                degreeDecimals = settings.degreeDecimals,
                winds = winds,
                locale = locale,
            )
        }
    }

    if (showDatePicker) {
        CenitDatePicker(
            initialDate = selectedDate,
            onDismiss = { showDatePicker = false },
            onConfirm = { pickedDate ->
                showDatePicker = false
                onSelectDate(pickedDate)
            },
        )
    }
}

@Composable
private fun DateStripHeaderRow(
    monthYearLabel: String,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onOpenDatePicker: () -> Unit,
) {
    val palette = LocalCenitPalette.current
    val prevDesc = stringResource(R.string.cd_previous_day)
    val nextDesc = stringResource(R.string.cd_next_day)
    val otherDateLabel = stringResource(R.string.action_other_date)
    val otherDateDesc = stringResource(R.string.cd_other_date)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = monthYearLabel,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(44.dp)
                .cenitFocusRing(palette.focusRing, cornerRadius = 10.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = onPrevDay)
                .semantics { contentDescription = prevDesc },
            contentAlignment = Alignment.Center,
        ) {
            CenitIcon(kind = CenitIconKind.PREV, tint = palette.textPrimary, size = 18.dp)
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .cenitFocusRing(palette.focusRing, cornerRadius = 10.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = onNextDay)
                .semantics { contentDescription = nextDesc },
            contentAlignment = Alignment.Center,
        ) {
            CenitIcon(kind = CenitIconKind.NEXT, tint = palette.textPrimary, size = 18.dp)
        }
        Box(
            modifier = Modifier
                .heightIn(min = 44.dp)
                .cenitFocusRing(palette.focusRing, cornerRadius = 10.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = onOpenDatePicker)
                .padding(start = 8.dp, end = 4.dp)
                .semantics { contentDescription = otherDateDesc },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = otherDateLabel,
                style = MaterialTheme.typography.bodySmall,
                color = palette.sun,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TodayAlertNoticeBanner(
    text: String,
    palette: CenitPalette,
    onDismiss: () -> Unit,
) {
    val shape = RoundedCornerShape(
        topStart = 0.dp,
        bottomStart = 0.dp,
        topEnd = 12.dp,
        bottomEnd = 12.dp,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.surfaceColor)
            .drawBehind {
                val ruleWidth = 3.dp.toPx()
                val startX = if (layoutDirection == LayoutDirection.Rtl) size.width - ruleWidth else 0f
                drawRect(
                    color = palette.warning,
                    topLeft = Offset(startX, 0f),
                    size = Size(ruleWidth, size.height),
                )
            }
            .clickable(role = Role.Button, onClick = onDismiss)
            .padding(start = 15.dp, end = 12.dp, top = 7.dp, bottom = 7.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlaceHeaderRow(
    placeName: String,
    coordsText: String,
    onResetNow: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val palette = LocalCenitPalette.current
    val nowLabel = stringResource(R.string.action_now)
    val nowDesc = stringResource(R.string.cd_back_to_now)
    val settingsDesc = stringResource(R.string.settings_cd_open)
    val gearShape = RoundedCornerShape(10.dp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = placeName,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Normal,
                    fontSize = 22.sp,
                    lineHeight = 26.sp,
                    letterSpacing = (-0.3).sp,
                ),
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = coordsText,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .cenitFocusRing(palette.focusRing, cornerRadius = 999.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(palette.surfaceSubtle)
                    .clickable(role = Role.Button, onClick = onResetNow)
                    .padding(horizontal = 14.dp)
                    .semantics { contentDescription = nowDesc },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = nowLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textPrimary,
                    maxLines = 1,
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .cenitFocusRing(palette.focusRing, cornerRadius = 10.dp)
                    .clip(gearShape)
                    .border(1.dp, palette.controlBorder, gearShape)
                    .clickable(role = Role.Button, onClick = onOpenSettings)
                    .semantics { contentDescription = settingsDesc },
                contentAlignment = Alignment.Center,
            ) {
                CenitIcon(kind = CenitIconKind.SETTINGS, tint = palette.textPrimary, size = 20.dp)
            }
        }
    }
}

@Composable
private fun DomeLegendRow() {
    val palette = LocalCenitPalette.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendSample(
            label = stringResource(R.string.dome_legend_solstices),
            color = palette.textSecondary,
            dashPattern = null,
            strokeWidthDp = 1f,
        )
        LegendSample(
            label = stringResource(R.string.dome_legend_equinox),
            color = palette.textSecondary,
            dashPattern = floatArrayOf(2f, 3f),
            strokeWidthDp = 1f,
        )
        LegendSample(
            label = stringResource(R.string.dome_legend_night),
            color = palette.textSecondary,
            dashPattern = floatArrayOf(1f, 3f),
            strokeWidthDp = 1.5f,
        )
    }
}

@Composable
private fun LegendSample(
    label: String,
    color: Color,
    dashPattern: FloatArray?,
    strokeWidthDp: Float,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Canvas(modifier = Modifier.width(16.dp).height(6.dp)) {
            val effect = dashPattern?.let {
                PathEffect.dashPathEffect(FloatArray(it.size) { i -> it[i].dp.toPx() })
            }
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = strokeWidthDp.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = effect,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.sp,
            ),
            color = color,
            maxLines = 1,
        )
    }
}

@Composable
private fun MoonSummaryRow(
    moonPhaseName: String,
    illuminationPct: Int,
    moonPhase: io.github.ivan_olivera_dev.cenit.core.MoonPhase,
    moonPosition: io.github.ivan_olivera_dev.cenit.core.SkyPosition,
    degreeDecimals: Boolean,
    winds: Array<String>,
    locale: Locale,
) {
    val palette = LocalCenitPalette.current
    val glyphDesc = stringResource(R.string.cd_phase_glyph, moonPhaseName, illuminationPct)
    val summaryText = stringResource(R.string.moon_summary, moonPhaseName, illuminationPct)
    val moonPosText = if (moonPosition.elevation < Cenit.RISE_SET_ALTITUDE_DEG) {
        stringResource(R.string.moon_position_below)
    } else {
        stringResource(
            R.string.moon_position_above,
            SkyFormatting.formatDecimal(moonPosition.azimuth, 0, locale),
            winds[SkyFormatting.compassIndex(moonPosition.azimuth)],
            CenitFormat.formatAngleValue(moonPosition.elevation, degreeDecimals, locale),
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 34.dp)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PhaseGlyph(
            phase = moonPhase,
            contentDescription = glyphDesc,
            size = 24.dp,
        )
        Text(
            text = summaryText,
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = palette.textPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = moonPosText,
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = palette.textSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun NextEventsBlock(
    events: List<UpcomingSkyEvent>,
    zone: ZoneId,
    clock: ClockFormat,
    degreeDecimals: Boolean,
    winds: Array<String>,
    locale: Locale,
) {
    val palette = LocalCenitPalette.current
    val sectionDesc = stringResource(R.string.cd_next_events)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = sectionDesc },
    ) {
        if (events.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 38.dp)
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = stringResource(R.string.events_none_left),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                )
            }
        } else {
            events.forEachIndexed { index, upcoming ->
                EventRow(
                    event = upcoming.event,
                    isNextDay = upcoming.isNextDay,
                    zone = zone,
                    clock = clock,
                    degreeDecimals = degreeDecimals,
                    winds = winds,
                    locale = locale,
                    palette = palette,
                )
                if (index < events.lastIndex) {
                    HorizontalDivider(thickness = 1.dp, color = palette.borderColor)
                }
            }
        }
    }
}

@Composable
private fun EventRow(
    event: SkyEvent,
    isNextDay: Boolean,
    zone: ZoneId,
    clock: ClockFormat,
    degreeDecimals: Boolean,
    winds: Array<String>,
    locale: Locale,
    palette: CenitPalette,
) {
    val timeText = CenitFormat.formatTime(event.instant, zone, clock, locale)
    val labelText = eventLabel(event.kind)
    val tomorrowLabel = if (isNextDay) stringResource(R.string.event_tomorrow) else null
    val az = event.azimuth
    val end = event.end
    val elev = event.elevation
    val trailingText = when {
        az != null -> stringResource(
            R.string.event_azimuth,
            SkyFormatting.formatDecimal(az, 0, locale),
            winds[SkyFormatting.compassIndex(az)],
        )
        end != null -> stringResource(
            R.string.event_until,
            CenitFormat.formatTime(end, zone, clock, locale),
        )
        elev != null -> stringResource(
            R.string.event_elevation,
            CenitFormat.formatAngleValue(elev, degreeDecimals, locale),
        )
        else -> ""
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 38.dp)
            .padding(vertical = if (isNextDay) 4.dp else 5.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.widthIn(min = 58.dp)) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = if (clock == ClockFormat.H12) 16.sp else 20.sp,
                    fontFeatureSettings = "tnum",
                    lineHeight = if (isNextDay) 20.sp else 24.sp,
                ),
                color = palette.textPrimary,
                maxLines = 1,
            )
            if (tomorrowLabel != null) {
                Text(
                    text = tomorrowLabel,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.sp,
                    ),
                    color = palette.textSecondary,
                    maxLines = 1,
                )
            }
        }

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            EventTag(kind = event.kind, palette = palette)
            Text(
                text = labelText,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (trailingText.isNotEmpty()) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = palette.textSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun EventTag(kind: SkyEventKind, palette: CenitPalette) {
    when (kind) {
        SkyEventKind.GOLDEN_MORNING, SkyEventKind.GOLDEN_EVENING -> Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(palette.sun),
        )
        SkyEventKind.BLUE_MORNING, SkyEventKind.BLUE_EVENING -> Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(palette.blueHour),
        )
        SkyEventKind.MOONRISE, SkyEventKind.MOONSET -> Box(
            modifier = Modifier
                .size(8.dp)
                .border(1.5.dp, palette.moon, CircleShape),
        )
        SkyEventKind.ASTRONOMICAL_DUSK,
        SkyEventKind.ASTRONOMICAL_DAWN,
        SkyEventKind.NAUTICAL_DAWN,
        SkyEventKind.NAUTICAL_DUSK -> Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(palette.skyAstro)
                .border(1.dp, palette.controlBorder, CircleShape),
        )
        SkyEventKind.SUNRISE, SkyEventKind.SOLAR_NOON, SkyEventKind.SUNSET -> Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(palette.sun),
        )
    }
}

@Composable
private fun formatCoordinates(place: Place, locale: Locale): String {
    val latStr = SkyFormatting.formatDecimal(abs(place.latitude), 2, locale)
    val lonStr = SkyFormatting.formatDecimal(abs(place.longitude), 2, locale)
    val ns = stringResource(if (place.latitude >= 0.0) R.string.hemisphere_north else R.string.hemisphere_south)
    val ew = stringResource(if (place.longitude >= 0.0) R.string.hemisphere_east else R.string.hemisphere_west)
    return stringResource(R.string.coords_format, latStr, ns, lonStr, ew)
}

@Composable
private fun skyPhaseLabel(phase: SkyPhase): String = stringResource(
    when (phase) {
        SkyPhase.DAY -> R.string.phase_day
        SkyPhase.GOLDEN -> R.string.phase_golden
        SkyPhase.BLUE -> R.string.phase_blue
        SkyPhase.NAUTICAL -> R.string.phase_nautical
        SkyPhase.ASTRONOMICAL -> R.string.phase_astronomical
        SkyPhase.NIGHT -> R.string.phase_night
    },
)

@Composable
private fun moonPhaseLabel(name: MoonPhaseName): String = stringResource(
    when (name) {
        MoonPhaseName.NEW -> R.string.moon_phase_new
        MoonPhaseName.WAXING_CRESCENT -> R.string.moon_phase_waxing_crescent
        MoonPhaseName.FIRST_QUARTER -> R.string.moon_phase_first_quarter
        MoonPhaseName.WAXING_GIBBOUS -> R.string.moon_phase_waxing_gibbous
        MoonPhaseName.FULL -> R.string.moon_phase_full
        MoonPhaseName.WANING_GIBBOUS -> R.string.moon_phase_waning_gibbous
        MoonPhaseName.LAST_QUARTER -> R.string.moon_phase_last_quarter
        MoonPhaseName.WANING_CRESCENT -> R.string.moon_phase_waning_crescent
    },
)

@Composable
private fun eventLabel(kind: SkyEventKind): String = stringResource(
    when (kind) {
        SkyEventKind.ASTRONOMICAL_DAWN -> R.string.event_astro_dawn
        SkyEventKind.NAUTICAL_DAWN -> R.string.event_nautical_dawn
        SkyEventKind.BLUE_MORNING, SkyEventKind.BLUE_EVENING -> R.string.event_blue_hour
        SkyEventKind.GOLDEN_MORNING, SkyEventKind.GOLDEN_EVENING -> R.string.event_golden_hour
        SkyEventKind.SUNRISE -> R.string.event_sunrise
        SkyEventKind.SOLAR_NOON -> R.string.event_solar_noon
        SkyEventKind.SUNSET -> R.string.event_sunset
        SkyEventKind.NAUTICAL_DUSK -> R.string.event_nautical_dusk
        SkyEventKind.ASTRONOMICAL_DUSK -> R.string.event_night
        SkyEventKind.MOONRISE -> R.string.event_moonrise
        SkyEventKind.MOONSET -> R.string.event_moonset
    },
)
