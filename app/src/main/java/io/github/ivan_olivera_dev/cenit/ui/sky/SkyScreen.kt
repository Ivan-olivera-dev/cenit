package io.github.ivan_olivera_dev.cenit.ui.sky

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ivan_olivera_dev.cenit.R
import io.github.ivan_olivera_dev.cenit.core.Cenit
import io.github.ivan_olivera_dev.cenit.core.Ephemeris
import io.github.ivan_olivera_dev.cenit.core.LunarEvent
import io.github.ivan_olivera_dev.cenit.core.LunarQuarter
import io.github.ivan_olivera_dev.cenit.core.MoonPhase
import io.github.ivan_olivera_dev.cenit.core.MoonPhaseName
import io.github.ivan_olivera_dev.cenit.core.PolarCondition
import io.github.ivan_olivera_dev.cenit.core.SkyPosition
import io.github.ivan_olivera_dev.cenit.core.SunDay
import io.github.ivan_olivera_dev.cenit.core.eclipses.Eclipses
import io.github.ivan_olivera_dev.cenit.core.format.CenitFormat
import io.github.ivan_olivera_dev.cenit.core.format.ClockFormat
import io.github.ivan_olivera_dev.cenit.core.sky.SkyFormatting
import io.github.ivan_olivera_dev.cenit.core.sky.SkyPhase
import io.github.ivan_olivera_dev.cenit.core.sky.chart.ElevationChartData
import io.github.ivan_olivera_dev.cenit.core.sky.chart.MoonTimeItem
import io.github.ivan_olivera_dev.cenit.core.sky.chart.MoonTimeKind
import io.github.ivan_olivera_dev.cenit.core.sky.chart.SkyDaySchedule
import io.github.ivan_olivera_dev.cenit.core.sky.chart.SkyTimesList
import io.github.ivan_olivera_dev.cenit.core.sky.chart.SunTimeItem
import io.github.ivan_olivera_dev.cenit.core.sky.chart.SunTimeKind
import io.github.ivan_olivera_dev.cenit.settings.LocalAppSettings
import io.github.ivan_olivera_dev.cenit.ui.components.CenitDatePicker
import io.github.ivan_olivera_dev.cenit.ui.components.CenitIcon
import io.github.ivan_olivera_dev.cenit.ui.components.CenitIconKind
import io.github.ivan_olivera_dev.cenit.ui.components.CenitSegmentedControl
import io.github.ivan_olivera_dev.cenit.ui.components.PhaseGlyph
import io.github.ivan_olivera_dev.cenit.ui.components.cenitFocusRing
import io.github.ivan_olivera_dev.cenit.ui.planner.PlannerState
import io.github.ivan_olivera_dev.cenit.ui.theme.CenitPalette
import io.github.ivan_olivera_dev.cenit.ui.theme.LocalCenitPalette
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Filter sections for Cielo's complete times list (`segmented` token).
 */
enum class SkyTimesFilter {
    ALL,
    SUN,
    MOON,
}

private data class SkyDayBundle(
    val sunDay: SunDay,
    val chartData: ElevationChartData,
    val schedule: SkyDaySchedule,
)

/**
 * Synthetic [MoonPhase] representing a principal [LunarQuarter] for its [PhaseGlyph].
 */
fun quarterPreviewPhase(quarter: LunarQuarter): MoonPhase = when (quarter) {
    LunarQuarter.NEW -> MoonPhase(angle = 0.0, illumination = 0.0)
    LunarQuarter.FIRST_QUARTER -> MoonPhase(angle = 90.0, illumination = 0.5)
    LunarQuarter.FULL -> MoonPhase(angle = 180.0, illumination = 1.0)
    LunarQuarter.LAST_QUARTER -> MoonPhase(angle = 270.0, illumination = 0.5)
}

/**
 * Applies a normalized horizontal chart scrub fraction to [state].
 */
fun scrubChartFraction(state: PlannerState, fraction: Float): PlannerState =
    state.selectFraction(fraction)

/**
 * Cielo screen of Artboard 02 (Cúpula): fixed interactive elevation chart and live values readout
 * pinned above a scrollable complete times list covering every solar twilight/golden/blue event,
 * polar day/night notice when applicable, lunar rise/set with azimuths, moon phase and
 * illumination, and the next four lunar quarters.
 */
@Composable
fun SkyScreen(
    state: PlannerState,
    onStateChange: (PlannerState) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    val palette = LocalCenitPalette.current
    val settings = LocalAppSettings.current
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) {
        configuration.locales[0] ?: Locale.getDefault()
    }
    val winds = stringArrayResource(R.array.compass_winds)

    val place = state.activePlace.place
    val zone = state.zone
    val selectedDate = state.selectedDate
    val today = remember(zone, selectedDate) { LocalDate.now(zone) }

    val dayBundle = remember(place, selectedDate, zone) {
        val sunDay = Ephemeris.sunDay(place, selectedDate, zone)
        val moonDay = Ephemeris.moonDay(place, selectedDate, zone)
        val chartData = ElevationChartData.build(place, selectedDate, zone, sunDay = sunDay)
        val schedule = SkyTimesList.build(place, selectedDate, zone, sunDay = sunDay, moonDay = moonDay)
        SkyDayBundle(
            sunDay = sunDay,
            chartData = chartData,
            schedule = schedule,
        )
    }

    val selectedInstant = state.selectedInstant
    val sunPosition = remember(place, selectedInstant) { Ephemeris.sunPosition(place, selectedInstant) }
    val moonPosition = remember(place, selectedInstant) { Ephemeris.moonPosition(place, selectedInstant) }
    val currentPhase = remember(place, selectedInstant) { SkyPhase.at(place, selectedInstant) }
    val currentMoonPhase = remember(selectedInstant) { Ephemeris.moonPhase(selectedInstant) }
    val currentMoonIllumPct = (currentMoonPhase.illumination * 100.0).roundToInt().coerceIn(0, 100)

    var filter by rememberSaveable { mutableStateOf(SkyTimesFilter.ALL) }

    val eclipseCacheKey = remember(place, today, zone.id) {
        EclipseCacheKey(place = place, anchorDate = today, zoneId = zone.id)
    }
    var upcomingEclipses by remember(eclipseCacheKey) {
        mutableStateOf(DefaultSkyEclipseCache.get(eclipseCacheKey))
    }
    LaunchedEffect(eclipseCacheKey) {
        if (upcomingEclipses == null) {
            upcomingEclipses = withContext(Dispatchers.Default) {
                DefaultSkyEclipseCache.getOrCompute(eclipseCacheKey) {
                    Eclipses.upcoming(
                        place = place,
                        from = today.atStartOfDay(zone).toInstant(),
                        count = SKY_ECLIPSES_FETCH_COUNT,
                        horizonYears = SKY_ECLIPSES_HORIZON_YEARS,
                        visibleOnly = true,
                    )
                }
            }
        }
    }
    val visibleEclipses = remember(upcomingEclipses, filter) {
        upcomingEclipses?.let { filterEclipsesForTab(it, filter, limit = SKY_ECLIPSES_MAX_SHOWN) }
    }

    val shortDateFormatter = remember(locale) {
        DateTimeFormatter.ofPattern("EEE d MMM", locale)
    }
    val fullDateFormatter = remember(locale) {
        DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)
    }
    val quarterDateFormatter = remember(locale) {
        DateTimeFormatter.ofPattern("d MMM", locale)
    }

    val shortDateText = remember(selectedDate, shortDateFormatter) {
        shortDateFormatter.format(selectedDate).replace(",", "")
    }
    val fullDateText = remember(selectedDate, fullDateFormatter) {
        fullDateFormatter.format(selectedDate)
    }
    val dateChipText = if (selectedDate == today) {
        stringResource(R.string.sky_today_chip)
    } else {
        shortDateText
    }
    val formattedTime = CenitFormat.formatLocalTime(state.selectedTime, settings.clock, locale)

    val chartContentDesc = stringResource(
        R.string.sky_cd_elevation_chart,
        shortDateText,
        CenitFormat.formatAngleValue(dayBundle.sunDay.noonElevation, settings.degreeDecimals, locale),
        CenitFormat.formatTime(dayBundle.sunDay.solarNoon, zone, settings.clock, locale),
        formattedTime,
    )
    val timesListDesc = stringResource(R.string.sky_cd_times_list, shortDateText)

    val selectedXFraction = dayBundle.chartData.xFraction(selectedInstant).toFloat()
    val sunInsertIndex = remember(dayBundle.schedule.sunItems, selectedInstant) {
        SkyTimesList.selectedTimeInsertIndex(dayBundle.schedule.sunItems, selectedInstant)
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
                .padding(top = 8.dp),
        ) {
            // Fixed top block: title + date stepper, elevation chart, values readout, segmented filter
            SkyHeaderRow(
                dateChipText = dateChipText,
                fullDateText = fullDateText,
                onPrevDay = { onStateChange(state.shiftDays(-1L)) },
                onNextDay = { onStateChange(state.shiftDays(1L)) },
                onResetNow = { onStateChange(state.resetToNow(Clock.systemDefaultZone())) },
                onPickDate = { showDatePicker = true },
            )
            if (showDatePicker) {
                CenitDatePicker(
                    initialDate = state.selectedDate,
                    onDismiss = { showDatePicker = false },
                    onConfirm = { date ->
                        showDatePicker = false
                        onStateChange(state.selectDate(date))
                    },
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            ElevationChartCanvas(
                chartData = dayBundle.chartData,
                selectedXFraction = selectedXFraction,
                sunElevationDeg = sunPosition.elevation,
                moonElevationDeg = moonPosition.elevation,
                formattedTime = formattedTime,
                contentDescription = chartContentDesc,
                onScrubFraction = { fraction -> onStateChange(scrubChartFraction(state, fraction)) },
                onStepMinutes = { delta -> onStateChange(state.stepMinutes(delta)) },
            )

            Spacer(modifier = Modifier.height(10.dp))

            SkyValuesReadout(
                formattedTime = formattedTime,
                phaseLabel = skyPhaseLabel(currentPhase),
                sunPosition = sunPosition,
                moonPosition = moonPosition,
                moonIlluminationPct = currentMoonIllumPct,
                degreeDecimals = settings.degreeDecimals,
                winds = winds,
                locale = locale,
            )

            Spacer(modifier = Modifier.height(10.dp))

            SkySegmentedControl(
                selectedFilter = filter,
                onSelectFilter = { filter = it },
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Scrollable complete times list while the elevation chart stays fixed above
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .semantics { contentDescription = timesListDesc },
                contentPadding = PaddingValues(top = 6.dp, bottom = 20.dp),
            ) {
                if (filter == SkyTimesFilter.ALL || filter == SkyTimesFilter.SUN) {
                    item(key = "sun_header") {
                        SunSectionHeader(daylightDuration = dayBundle.schedule.daylightDuration)
                    }

                    if (dayBundle.schedule.polar != PolarCondition.NONE) {
                        item(key = "polar_notice") {
                            PolarConditionNotice(polar = dayBundle.schedule.polar)
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    val sunItems = dayBundle.schedule.sunItems
                    itemsIndexed(
                        items = sunItems,
                        key = { _, item -> "sun_${item.kind.name}" },
                    ) { index, item ->
                        if (index == sunInsertIndex) {
                            SelectedMomentRow(formattedTime = formattedTime, palette = palette)
                        }
                        SunTimeEventRow(
                            item = item,
                            isPast = SkyTimesList.isPast(item, selectedInstant),
                            zone = zone,
                            clock = settings.clock,
                            degreeDecimals = settings.degreeDecimals,
                            winds = winds,
                            locale = locale,
                            palette = palette,
                            showDivider = index < sunItems.lastIndex || sunInsertIndex == sunItems.size,
                        )
                    }

                    if (sunInsertIndex == sunItems.size) {
                        item(key = "selected_moment_end") {
                            SelectedMomentRow(formattedTime = formattedTime, palette = palette)
                        }
                    }
                }

                if (filter == SkyTimesFilter.ALL) {
                    item(key = "section_gap") {
                        Spacer(modifier = Modifier.height(18.dp))
                    }
                }

                if (filter == SkyTimesFilter.ALL || filter == SkyTimesFilter.MOON) {
                    item(key = "moon_header") {
                        SectionEyebrow(text = stringResource(R.string.sky_section_moon))
                    }

                    item(key = "moon_phase_summary") {
                        MoonPhaseDetailRow(
                            phase = dayBundle.schedule.moonPhase,
                            phaseName = moonPhaseLabel(dayBundle.schedule.moonPhaseName),
                            illuminationPercent = dayBundle.schedule.moonIlluminationPercent,
                            palette = palette,
                        )
                    }

                    item(key = "moon_rise_set") {
                        MoonRiseSetBlock(
                            moonItems = dayBundle.schedule.moonItems,
                            zone = zone,
                            clock = settings.clock,
                            winds = winds,
                            locale = locale,
                            palette = palette,
                        )
                    }

                    item(key = "quarters_header") {
                        Spacer(modifier = Modifier.height(14.dp))
                        SectionEyebrow(text = stringResource(R.string.sky_section_quarters))
                    }

                    itemsIndexed(
                        items = dayBundle.schedule.nextQuarters,
                        key = { idx, ev -> "quarter_${idx}_${ev.quarter.name}" },
                    ) { index, event ->
                        LunarQuarterRow(
                            event = event,
                            zone = zone,
                            clock = settings.clock,
                            locale = locale,
                            dateFormatter = quarterDateFormatter,
                            palette = palette,
                            showDivider = index < dayBundle.schedule.nextQuarters.lastIndex,
                        )
                    }
                }

                item(key = "eclipses_gap") {
                    Spacer(modifier = Modifier.height(18.dp))
                }

                item(key = "eclipses_header") {
                    SectionEyebrow(text = stringResource(R.string.eclipses_section_title))
                }

                val eclipsesToShow = visibleEclipses
                if (eclipsesToShow == null) {
                    item(key = "eclipses_loading") {
                        EclipseQuietLine(
                            text = stringResource(R.string.eclipses_loading),
                            palette = palette,
                        )
                    }
                } else if (eclipsesToShow.isEmpty()) {
                    item(key = "eclipses_empty") {
                        val emptyResId = when (filter) {
                            SkyTimesFilter.ALL -> R.string.eclipses_none_all
                            SkyTimesFilter.SUN -> R.string.eclipses_none_sun
                            SkyTimesFilter.MOON -> R.string.eclipses_none_moon
                        }
                        EclipseQuietLine(
                            text = stringResource(emptyResId),
                            palette = palette,
                        )
                    }
                } else {
                    itemsIndexed(
                        items = eclipsesToShow,
                        key = { _, eclipse -> "eclipse_${eclipse.body.name}_${eclipse.peak.toEpochMilli()}" },
                    ) { index, eclipse ->
                        EclipseEventRow(
                            eclipse = eclipse,
                            zone = zone,
                            locale = locale,
                            palette = palette,
                            showDivider = index < eclipsesToShow.lastIndex,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SkyHeaderRow(
    dateChipText: String,
    fullDateText: String,
    onPrevDay: () -> Unit,
    onNextDay: () -> Unit,
    onResetNow: () -> Unit,
    onPickDate: () -> Unit,
) {
    val palette = LocalCenitPalette.current
    val pickDesc = stringResource(R.string.cd_other_date)
    val stepperDesc = stringResource(R.string.sky_cd_date_stepper)
    val prevDesc = stringResource(R.string.cd_previous_day)
    val nextDesc = stringResource(R.string.cd_next_day)
    val chipDesc = stringResource(R.string.sky_cd_date_chip, fullDateText)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.tab_sky),
            style = MaterialTheme.typography.headlineLarge,
            color = palette.textPrimary,
            maxLines = 1,
        )

        Row(
            modifier = Modifier.semantics { contentDescription = stepperDesc },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .cenitFocusRing(palette.focusRing, cornerRadius = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button, onClick = onPickDate)
                    .semantics { contentDescription = pickDesc },
                contentAlignment = Alignment.Center,
            ) {
                CenitIcon(kind = CenitIconKind.CALENDAR, tint = palette.textPrimary, size = 20.dp)
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .cenitFocusRing(palette.focusRing, cornerRadius = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button, onClick = onPrevDay)
                    .semantics { contentDescription = prevDesc },
                contentAlignment = Alignment.Center,
            ) {
                CenitIcon(kind = CenitIconKind.PREV, tint = palette.textPrimary, size = 20.dp)
            }

            Box(
                modifier = Modifier
                    .heightIn(min = 44.dp)
                    .cenitFocusRing(palette.focusRing, cornerRadius = 999.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(palette.surfaceSubtle)
                    .clickable(role = Role.Button, onClick = onResetNow)
                    .padding(horizontal = 14.dp)
                    .semantics { contentDescription = chipDesc },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = dateChipText,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textPrimary,
                    maxLines = 1,
                )
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
                CenitIcon(kind = CenitIconKind.NEXT, tint = palette.textPrimary, size = 20.dp)
            }
        }
    }
}

@Composable
private fun SkyValuesReadout(
    formattedTime: String,
    phaseLabel: String,
    sunPosition: SkyPosition,
    moonPosition: SkyPosition,
    moonIlluminationPct: Int,
    degreeDecimals: Boolean,
    winds: Array<String>,
    locale: Locale,
) {
    val palette = LocalCenitPalette.current
    val shadowText = sunPosition.shadowRatio?.let { ratio ->
        stringResource(R.string.shadow_ratio, SkyFormatting.formatDecimal(ratio, 1, locale))
    } ?: stringResource(R.string.shadow_none)

    val sunAzText = stringResource(
        R.string.sky_readout_az_wind,
        SkyFormatting.formatDecimal(sunPosition.azimuth, 0, locale),
        winds[SkyFormatting.compassIndex(sunPosition.azimuth)],
    )
    val sunElevText = if (sunPosition.elevation < Cenit.RISE_SET_ALTITUDE_DEG) {
        stringResource(R.string.sky_readout_below)
    } else {
        stringResource(
            R.string.sky_readout_elev,
            CenitFormat.formatAngleValue(sunPosition.elevation, degreeDecimals, locale),
        )
    }

    val moonAzText = stringResource(
        R.string.sky_readout_az_wind,
        SkyFormatting.formatDecimal(moonPosition.azimuth, 0, locale),
        winds[SkyFormatting.compassIndex(moonPosition.azimuth)],
    )
    val moonElevText = if (moonPosition.elevation < Cenit.RISE_SET_ALTITUDE_DEG) {
        stringResource(R.string.sky_readout_moon_below, moonIlluminationPct)
    } else {
        stringResource(
            R.string.sky_readout_moon_above,
            CenitFormat.formatAngleValue(moonPosition.elevation, degreeDecimals, locale),
            moonIlluminationPct,
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                color = palette.textPrimary,
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.sky_selected_summary, phaseLabel, shadowText),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ReadoutCard(
                label = stringResource(R.string.sky_readout_sun),
                primary = sunAzText,
                secondary = sunElevText,
                isSun = true,
                palette = palette,
                modifier = Modifier.weight(1f),
            )
            ReadoutCard(
                label = stringResource(R.string.sky_readout_moon),
                primary = moonAzText,
                secondary = moonElevText,
                isSun = false,
                palette = palette,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ReadoutCard(
    label: String,
    primary: String,
    secondary: String,
    isSun: Boolean,
    palette: CenitPalette,
    modifier: Modifier = Modifier,
) {
    val cardShape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(cardShape)
            .background(palette.surfaceColor)
            .border(1.dp, palette.borderColor, cardShape)
            .padding(horizontal = 12.dp, vertical = 9.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (isSun) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(palette.sun),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .border(1.5.dp, palette.moon, CircleShape),
                )
            }
            Text(
                text = label.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelMedium,
                color = palette.textSecondary,
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = primary,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 18.sp,
                lineHeight = 22.sp,
                fontFeatureSettings = "tnum",
            ),
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = secondary,
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Segmented control (`segmented` token): `44 dp` high, `10 dp` radius, `1 dp controlBorder`,
 * selected segment filled `surfaceSubtle` with `textPrimary` label and `2 dp sun` underline.
 */
@Composable
private fun SkySegmentedControl(
    selectedFilter: SkyTimesFilter,
    onSelectFilter: (SkyTimesFilter) -> Unit,
) {
    val options = listOf(
        SkyTimesFilter.ALL to stringResource(R.string.sky_filter_all),
        SkyTimesFilter.SUN to stringResource(R.string.sky_filter_sun),
        SkyTimesFilter.MOON to stringResource(R.string.sky_filter_moon),
    )
    CenitSegmentedControl(
        options = options,
        selectedOption = selectedFilter,
        onSelect = onSelectFilter,
        groupContentDescription = stringResource(R.string.sky_cd_filter),
    )
}

@Composable
private fun SectionEyebrow(
    text: String,
    trailing: String? = null,
) {
    val palette = LocalCenitPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = text.uppercase(Locale.getDefault()),
            style = MaterialTheme.typography.labelMedium,
            color = palette.textSecondary,
            maxLines = 1,
        )
        if (!trailing.isNullOrEmpty()) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = palette.textSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SunSectionHeader(daylightDuration: Duration?) {
    val durationText = daylightDuration?.let { duration ->
        val totalMinutes = duration.toMinutes().coerceAtLeast(0L)
        val hours = totalMinutes / 60L
        val mins = totalMinutes % 60L
        if (hours > 0L) {
            stringResource(R.string.sky_daylight_hours_min, hours, mins)
        } else {
            stringResource(R.string.sky_daylight_min_only, mins)
        }
    }
    SectionEyebrow(
        text = stringResource(R.string.sky_section_sun),
        trailing = durationText,
    )
}

@Composable
private fun PolarConditionNotice(polar: PolarCondition) {
    val palette = LocalCenitPalette.current
    val message = when (polar) {
        PolarCondition.MIDNIGHT_SUN -> stringResource(R.string.sky_polar_midnight_sun)
        PolarCondition.POLAR_NIGHT -> stringResource(R.string.sky_polar_night)
        PolarCondition.NONE -> return
    }
    val noticeShape = RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(noticeShape)
            .background(palette.surfaceColor)
            .drawBehind {
                drawRect(
                    color = palette.warning,
                    topLeft = Offset.Zero,
                    size = Size(3.dp.toPx(), size.height),
                )
            }
            .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textPrimary,
        )
    }
}

@Composable
private fun SelectedMomentRow(
    formattedTime: String,
    palette: CenitPalette,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(vertical = 8.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = formattedTime,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum",
                ),
                color = palette.textPrimary,
                modifier = Modifier.widthIn(min = 102.dp),
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.sky_selected_moment),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = palette.textPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HorizontalDivider(thickness = 1.dp, color = palette.textPrimary)
    }
}

@Composable
private fun SunTimeEventRow(
    item: SunTimeItem,
    isPast: Boolean,
    zone: ZoneId,
    clock: ClockFormat,
    degreeDecimals: Boolean,
    winds: Array<String>,
    locale: Locale,
    palette: CenitPalette,
    showDivider: Boolean,
) {
    val timeText = item.end?.let { end ->
        CenitFormat.formatTimeRange(item.instant, end, zone, clock, locale)
    } ?: CenitFormat.formatTime(item.instant, zone, clock, locale)

    val trailingText = when {
        item.azimuthDeg != null -> stringResource(
            R.string.event_azimuth,
            SkyFormatting.formatDecimal(item.azimuthDeg!!, 0, locale),
            winds[SkyFormatting.compassIndex(item.azimuthDeg!!)],
        )
        item.elevationDeg != null -> stringResource(
            R.string.event_elevation,
            CenitFormat.formatAngleValue(item.elevationDeg!!, degreeDecimals, locale),
        )
        else -> ""
    }

    val textColor = if (isPast) palette.textSecondary else palette.textPrimary

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(vertical = 8.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = if (clock == ClockFormat.H12) 14.sp else 16.sp,
                    lineHeight = if (clock == ClockFormat.H12) 19.sp else 22.sp,
                    fontFeatureSettings = "tnum",
                ),
                color = textColor,
                modifier = Modifier.widthIn(min = 102.dp, max = if (clock == ClockFormat.H12) 136.dp else 108.dp),
                maxLines = if (clock == ClockFormat.H12) 2 else 1,
            )

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                SunKindTag(kind = item.kind, palette = palette)
                Text(
                    text = sunTimeLabel(item.kind),
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    maxLines = 2,
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
        if (showDivider) {
            HorizontalDivider(thickness = 1.dp, color = palette.borderColor)
        }
    }
}

@Composable
private fun SunKindTag(kind: SunTimeKind, palette: CenitPalette) {
    when (kind) {
        SunTimeKind.GOLDEN_MORNING, SunTimeKind.GOLDEN_EVENING -> Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(palette.sun),
        )
        SunTimeKind.BLUE_MORNING, SunTimeKind.BLUE_EVENING -> Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(palette.blueHour),
        )
        SunTimeKind.SUNRISE, SunTimeKind.SOLAR_NOON, SunTimeKind.SUNSET -> Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(palette.sun),
        )
        SunTimeKind.CIVIL_DAWN,
        SunTimeKind.CIVIL_DUSK -> Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(palette.skyCivil)
                .border(1.dp, palette.controlBorder, CircleShape),
        )
        SunTimeKind.NAUTICAL_DAWN,
        SunTimeKind.NAUTICAL_DUSK -> Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(palette.skyNautical)
                .border(1.dp, palette.controlBorder, CircleShape),
        )
        SunTimeKind.ASTRONOMICAL_DAWN,
        SunTimeKind.ASTRONOMICAL_DUSK -> Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(palette.skyAstro)
                .border(1.dp, palette.controlBorder, CircleShape),
        )
    }
}

@Composable
private fun MoonPhaseDetailRow(
    phase: MoonPhase,
    phaseName: String,
    illuminationPercent: Int,
    palette: CenitPalette,
) {
    val glyphDesc = stringResource(R.string.cd_phase_glyph, phaseName, illuminationPercent)
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(vertical = 8.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PhaseGlyph(
                phase = phase,
                contentDescription = glyphDesc,
                size = 36.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = phaseName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.sky_moon_illumination, illuminationPercent),
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = palette.textSecondary,
                    maxLines = 1,
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = palette.borderColor)
    }
}

@Composable
private fun MoonRiseSetBlock(
    moonItems: List<MoonTimeItem>,
    zone: ZoneId,
    clock: ClockFormat,
    winds: Array<String>,
    locale: Locale,
    palette: CenitPalette,
) {
    val hasRise = moonItems.any { it.kind == MoonTimeKind.MOONRISE }
    val hasSet = moonItems.any { it.kind == MoonTimeKind.MOONSET }

    val rows: List<Pair<MoonTimeKind, MoonTimeItem?>> = buildList {
        moonItems.forEach { add(it.kind to it) }
        if (!hasRise) add(MoonTimeKind.MOONRISE to null)
        if (!hasSet) add(MoonTimeKind.MOONSET to null)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        rows.forEachIndexed { index, (kind, item) ->
            val label = stringResource(
                if (kind == MoonTimeKind.MOONRISE) R.string.event_moonrise else R.string.event_moonset,
            )
            val timeText = item?.let { CenitFormat.formatTime(it.instant, zone, clock, locale) }
                ?: stringResource(R.string.sky_moon_dash)
            val trailing = if (item != null) {
                stringResource(
                    R.string.event_azimuth,
                    SkyFormatting.formatDecimal(item.azimuthDeg, 0, locale),
                    winds[SkyFormatting.compassIndex(item.azimuthDeg)],
                )
            } else {
                stringResource(
                    if (kind == MoonTimeKind.MOONRISE) R.string.sky_moon_no_rise else R.string.sky_moon_no_set,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(vertical = 8.dp)
                    .semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = if (clock == ClockFormat.H12) 16.sp else 20.sp,
                        fontFeatureSettings = "tnum",
                    ),
                    color = if (item != null) palette.textPrimary else palette.textSecondary,
                    modifier = Modifier.widthIn(min = 64.dp),
                    maxLines = 1,
                )
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .border(1.5.dp, palette.moon, CircleShape),
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = trailing,
                    style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                    color = palette.textSecondary,
                    maxLines = 1,
                )
            }

            if (index < rows.lastIndex) {
                HorizontalDivider(thickness = 1.dp, color = palette.borderColor)
            }
        }
    }
}

@Composable
private fun LunarQuarterRow(
    event: LunarEvent,
    zone: ZoneId,
    clock: ClockFormat,
    locale: Locale,
    dateFormatter: DateTimeFormatter,
    palette: CenitPalette,
    showDivider: Boolean,
) {
    val label = lunarQuarterLabel(event.quarter)
    val previewPhase = quarterPreviewPhase(event.quarter)
    val pct = (previewPhase.illumination * 100.0).roundToInt()
    val glyphDesc = stringResource(R.string.cd_phase_glyph, label, pct)
    val localZoned = event.instant.atZone(zone)
    val dateStr = dateFormatter.format(localZoned.toLocalDate())
    val timeStr = CenitFormat.formatTime(event.instant, zone, clock, locale)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(vertical = 8.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PhaseGlyph(
                phase = previewPhase,
                contentDescription = glyphDesc,
                size = 24.dp,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.sky_quarter_date_time, dateStr, timeStr),
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = palette.textSecondary,
                maxLines = 1,
            )
        }
        if (showDivider) {
            HorizontalDivider(thickness = 1.dp, color = palette.borderColor)
        }
    }
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
private fun sunTimeLabel(kind: SunTimeKind): String = stringResource(
    when (kind) {
        SunTimeKind.ASTRONOMICAL_DAWN -> R.string.event_astro_dawn
        SunTimeKind.NAUTICAL_DAWN -> R.string.event_nautical_dawn
        SunTimeKind.CIVIL_DAWN -> R.string.sky_event_civil_dawn
        SunTimeKind.BLUE_MORNING, SunTimeKind.BLUE_EVENING -> R.string.event_blue_hour
        SunTimeKind.GOLDEN_MORNING, SunTimeKind.GOLDEN_EVENING -> R.string.event_golden_hour
        SunTimeKind.SUNRISE -> R.string.event_sunrise
        SunTimeKind.SOLAR_NOON -> R.string.event_solar_noon
        SunTimeKind.SUNSET -> R.string.event_sunset
        SunTimeKind.CIVIL_DUSK -> R.string.sky_event_civil_dusk
        SunTimeKind.NAUTICAL_DUSK -> R.string.event_nautical_dusk
        SunTimeKind.ASTRONOMICAL_DUSK -> R.string.sky_event_astro_dusk
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
private fun lunarQuarterLabel(quarter: LunarQuarter): String = stringResource(
    when (quarter) {
        LunarQuarter.NEW -> R.string.moon_phase_new
        LunarQuarter.FIRST_QUARTER -> R.string.moon_phase_first_quarter
        LunarQuarter.FULL -> R.string.moon_phase_full
        LunarQuarter.LAST_QUARTER -> R.string.moon_phase_last_quarter
    },
)
