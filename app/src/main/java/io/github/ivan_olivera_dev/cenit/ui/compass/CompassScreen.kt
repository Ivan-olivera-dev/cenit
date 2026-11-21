package io.github.ivan_olivera_dev.cenit.ui.compass

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ivan_olivera_dev.cenit.R
import io.github.ivan_olivera_dev.cenit.core.Ephemeris
import io.github.ivan_olivera_dev.cenit.core.MoonDay
import io.github.ivan_olivera_dev.cenit.core.PathPoint
import io.github.ivan_olivera_dev.cenit.core.SunDay
import io.github.ivan_olivera_dev.cenit.core.compass.CompassBody
import io.github.ivan_olivera_dev.cenit.core.compass.CompassMath
import io.github.ivan_olivera_dev.cenit.core.compass.CompassTarget
import io.github.ivan_olivera_dev.cenit.core.compass.CompassTargetKind
import io.github.ivan_olivera_dev.cenit.core.compass.CompassTargets
import io.github.ivan_olivera_dev.cenit.core.compass.RelativeDirection
import io.github.ivan_olivera_dev.cenit.core.format.CenitFormat
import io.github.ivan_olivera_dev.cenit.core.format.ClockFormat
import io.github.ivan_olivera_dev.cenit.core.sky.SkyFormatting
import io.github.ivan_olivera_dev.cenit.settings.LocalAppSettings
import io.github.ivan_olivera_dev.cenit.ui.components.CenitIcon
import io.github.ivan_olivera_dev.cenit.ui.components.CenitIconKind
import io.github.ivan_olivera_dev.cenit.ui.components.DomeHorizonTick
import io.github.ivan_olivera_dev.cenit.ui.components.DomeReferencePaths
import io.github.ivan_olivera_dev.cenit.ui.components.cenitFocusRing
import io.github.ivan_olivera_dev.cenit.ui.planner.PlannerState
import io.github.ivan_olivera_dev.cenit.ui.theme.CenitPalette
import io.github.ivan_olivera_dev.cenit.ui.theme.LocalCenitPalette
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private data class CompassDayBundle(
    val sunDay: SunDay,
    val moonDay: MoonDay,
    val sunPath: List<PathPoint>,
    val moonPath: List<PathPoint>,
    val sunriseTick: DomeHorizonTick?,
    val sunsetTick: DomeHorizonTick?,
    val moonriseTick: DomeHorizonTick?,
    val moonsetTick: DomeHorizonTick?,
)

/**
 * Brújula screen of Artboard 02 (Cúpula): live true-north heading readout (declination corrected),
 * flat-compass sky dome rotating with the phone's heading inside a 5° compass rose with rise/set
 * azimuth rays, target azimuth rows with relative bearings, calibration notice on low accuracy,
 * and a clear north-up state when the device has no compass sensor.
 */
@Composable
fun CompassScreen(
    state: PlannerState,
    onOpenCamera: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val sensorState = rememberCompassSensorState(
        place = state.activePlace.place,
        instant = state.selectedInstant,
    )
    CompassScreenContent(
        state = state,
        sensorState = sensorState,
        onOpenCamera = onOpenCamera,
        modifier = modifier,
    )
}

@Composable
internal fun CompassScreenContent(
    state: PlannerState,
    sensorState: CompassSensorState,
    onOpenCamera: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = LocalCenitPalette.current
    val clock = LocalAppSettings.current.clock
    val configuration = LocalConfiguration.current
    val locale = remember(configuration) {
        configuration.locales[0] ?: Locale.getDefault()
    }
    val winds = stringArrayResource(R.array.compass_winds)

    val place = state.activePlace.place
    val zone = state.zone
    val selectedDate = state.selectedDate
    val selectedInstant = state.selectedInstant

    val dayBundle = remember(place, selectedDate, zone, clock, locale) {
        val sunDay = Ephemeris.sunDay(place, selectedDate, zone)
        val moonDay = Ephemeris.moonDay(place, selectedDate, zone)
        val sunPath = Ephemeris.sunPath(place, selectedDate, zone, Duration.ofMinutes(10))
        val moonPath = Ephemeris.moonPath(place, selectedDate, zone, Duration.ofMinutes(15))
        val sunriseTick = sunDay.sunrise?.let {
            DomeHorizonTick(
                azimuthDeg = Ephemeris.sunPosition(place, it).azimuth,
                label = CenitFormat.formatTime(it, zone, clock, locale),
            )
        }
        val sunsetTick = sunDay.sunset?.let {
            DomeHorizonTick(
                azimuthDeg = Ephemeris.sunPosition(place, it).azimuth,
                label = CenitFormat.formatTime(it, zone, clock, locale),
            )
        }
        val moonriseTick = moonDay.moonrise?.let {
            DomeHorizonTick(
                azimuthDeg = Ephemeris.moonPosition(place, it).azimuth,
                label = CenitFormat.formatTime(it, zone, clock, locale),
            )
        }
        val moonsetTick = moonDay.moonset?.let {
            DomeHorizonTick(
                azimuthDeg = Ephemeris.moonPosition(place, it).azimuth,
                label = CenitFormat.formatTime(it, zone, clock, locale),
            )
        }
        CompassDayBundle(
            sunDay = sunDay,
            moonDay = moonDay,
            sunPath = sunPath,
            moonPath = moonPath,
            sunriseTick = sunriseTick,
            sunsetTick = sunsetTick,
            moonriseTick = moonriseTick,
            moonsetTick = moonsetTick,
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

    val sunPosition = remember(place, selectedInstant) { Ephemeris.sunPosition(place, selectedInstant) }
    val moonPosition = remember(place, selectedInstant) { Ephemeris.moonPosition(place, selectedInstant) }
    val targets = remember(place, dayBundle, sunPosition, moonPosition) {
        CompassTargets.forMoment(
            place = place,
            sunDay = dayBundle.sunDay,
            moonDay = dayBundle.moonDay,
            sunPosition = sunPosition,
            moonPosition = moonPosition,
            maxTargets = 5,
        )
    }

    val screenDesc = stringResource(R.string.compass_cd_screen)
    val domeDescription = buildDomeContentDescription(
        sensorState = sensorState,
        dayBundle = dayBundle,
        winds = winds,
        locale = locale,
    )

    val isLiveAvailable = sensorState.availability == CompassAvailability.AVAILABLE
    val liveHeading = if (isLiveAvailable) sensorState.headingDeg else null

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bgColor)
            .semantics { contentDescription = screenDesc },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 460.dp)
                .padding(horizontal = 22.dp)
                .padding(top = 12.dp, bottom = 10.dp),
        ) {
            // 1. Screen title + full-screen AR camera entry button
            val cameraDesc = stringResource(R.string.camera_cd_open)
            val cameraShape = RoundedCornerShape(10.dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.compass_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = palette.textPrimary,
                    maxLines = 1,
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .cenitFocusRing(palette.focusRing, cornerRadius = 10.dp)
                        .clip(cameraShape)
                        .border(1.dp, palette.controlBorder, cameraShape)
                        .clickable(role = Role.Button, onClick = onOpenCamera)
                        .semantics { contentDescription = cameraDesc },
                    contentAlignment = Alignment.Center,
                ) {
                    CenitIcon(
                        kind = CenitIconKind.CAMERA,
                        tint = palette.textPrimary,
                        size = 20.dp,
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Heading readout (serif display + 16-point wind + true north subtitle)
            CompassHeadingHeader(
                sensorState = sensorState,
                winds = winds,
                locale = locale,
                palette = palette,
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 3. Rotating sky dome with 5° compass rose bezel and rise/set azimuth rays
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val domeSize = minOf(maxWidth, maxHeight)
                CompassDome(
                    sunPath = dayBundle.sunPath,
                    moonPath = dayBundle.moonPath,
                    referencePaths = referencePaths,
                    sunPosition = sunPosition,
                    moonPosition = moonPosition,
                    sunriseTick = dayBundle.sunriseTick,
                    sunsetTick = dayBundle.sunsetTick,
                    moonriseTick = dayBundle.moonriseTick,
                    moonsetTick = dayBundle.moonsetTick,
                    zone = zone,
                    headingDeg = sensorState.displayHeadingDeg,
                    showLivePointer = isLiveAvailable,
                    contentDescription = domeDescription,
                    modifier = Modifier.size(domeSize),
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 4. Sun & moon azimuth targets with relative bearing
            CompassTargetsList(
                targets = targets,
                liveHeadingDeg = liveHeading,
                zone = zone,
                clock = clock,
                winds = winds,
                locale = locale,
                palette = palette,
            )

            // 5. Notice: no sensor OR low/unreliable accuracy calibration prompt
            val noticeText = when {
                !isLiveAvailable -> stringResource(R.string.compass_no_sensor_notice)
                sensorState.needsCalibration -> stringResource(R.string.compass_calibration_notice)
                else -> null
            }
            if (noticeText != null) {
                Spacer(modifier = Modifier.height(8.dp))
                CompassNotice(
                    text = noticeText,
                    palette = palette,
                )
            }
        }
    }
}

@Composable
private fun CompassHeadingHeader(
    sensorState: CompassSensorState,
    winds: Array<String>,
    locale: Locale,
    palette: CenitPalette,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
    ) {
        when (sensorState.availability) {
            CompassAvailability.AVAILABLE -> {
                val heading = sensorState.headingDeg
                val primaryText = if (heading != null) {
                    stringResource(
                        R.string.compass_heading_degrees,
                        formatRoundedDegrees(heading, locale),
                    )
                } else {
                    stringResource(R.string.compass_heading_pending)
                }
                val windText = heading?.let { winds[CompassMath.windIndex(it)] } ?: ""

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = primaryText,
                        style = MaterialTheme.typography.displayMedium.copy(fontFeatureSettings = "tnum"),
                        color = palette.textPrimary,
                        maxLines = 1,
                    )
                    if (windText.isNotEmpty()) {
                        Text(
                            text = windText,
                            style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 0.sp),
                            color = palette.textSecondary,
                            modifier = Modifier.padding(bottom = 4.dp),
                            maxLines = 1,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.compass_true_north_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            CompassAvailability.UNAVAILABLE -> {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.cardinal_n),
                        style = MaterialTheme.typography.displayMedium,
                        color = palette.textPrimary,
                        maxLines = 1,
                    )
                    Text(
                        text = stringResource(R.string.compass_north_up_label),
                        style = MaterialTheme.typography.titleMedium.copy(letterSpacing = 0.sp),
                        color = palette.textSecondary,
                        modifier = Modifier.padding(bottom = 4.dp),
                        maxLines = 1,
                    )
                }
                Text(
                    text = stringResource(R.string.compass_no_sensor_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CompassTargetsList(
    targets: List<CompassTarget>,
    liveHeadingDeg: Double?,
    zone: ZoneId,
    clock: ClockFormat,
    winds: Array<String>,
    locale: Locale,
    palette: CenitPalette,
) {
    val listDesc = stringResource(R.string.compass_cd_targets)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = listDesc },
    ) {
        targets.forEachIndexed { index, target ->
            CompassTargetRow(
                target = target,
                liveHeadingDeg = liveHeadingDeg,
                zone = zone,
                clock = clock,
                winds = winds,
                locale = locale,
                palette = palette,
            )
            if (index < targets.lastIndex) {
                HorizontalDivider(thickness = 1.dp, color = palette.borderColor)
            }
        }
    }
}

@Composable
private fun CompassTargetRow(
    target: CompassTarget,
    liveHeadingDeg: Double?,
    zone: ZoneId,
    clock: ClockFormat,
    winds: Array<String>,
    locale: Locale,
    palette: CenitPalette,
) {
    val azimuthText = stringResource(
        R.string.compass_heading_degrees,
        formatRoundedDegrees(target.azimuthDeg, locale),
    )
    val timeText = target.instant?.let { CenitFormat.formatTime(it, zone, clock, locale) } ?: ""
    val labelText = when (target.kind) {
        CompassTargetKind.SUNSET -> stringResource(R.string.compass_target_sunset, timeText)
        CompassTargetKind.SUN_NOW -> stringResource(R.string.compass_target_sun_now)
        CompassTargetKind.SUNRISE -> stringResource(R.string.compass_target_sunrise, timeText)
        CompassTargetKind.MOONRISE -> stringResource(R.string.compass_target_moonrise, timeText)
        CompassTargetKind.MOON_NOW -> stringResource(R.string.compass_target_moon_now)
        CompassTargetKind.MOONSET -> stringResource(R.string.compass_target_moonset, timeText)
    }

    val trailingText = if (liveHeadingDeg != null) {
        val rel = CompassMath.relativeBearing(
            headingDeg = liveHeadingDeg,
            targetAzimuthDeg = target.azimuthDeg,
        )
        when (rel.direction) {
            RelativeDirection.AHEAD -> stringResource(R.string.compass_relative_ahead)
            RelativeDirection.LEFT -> stringResource(
                R.string.compass_relative_left,
                SkyFormatting.formatDecimal(rel.absDegrees, 0, locale),
            )
            RelativeDirection.RIGHT -> stringResource(
                R.string.compass_relative_right,
                SkyFormatting.formatDecimal(rel.absDegrees, 0, locale),
            )
        }
    } else {
        winds[CompassMath.windIndex(target.azimuthDeg)]
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 38.dp)
            .padding(vertical = 5.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = azimuthText,
            style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"),
            color = palette.textPrimary,
            modifier = Modifier.width(52.dp),
            maxLines = 1,
        )

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            CompassBodyTag(body = target.body, palette = palette)
            Text(
                text = labelText,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = trailingText,
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = palette.textSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun CompassBodyTag(
    body: CompassBody,
    palette: CenitPalette,
) {
    when (body) {
        CompassBody.SUN -> Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(palette.sun),
        )
        CompassBody.MOON -> Box(
            modifier = Modifier
                .size(8.dp)
                .border(1.5.dp, palette.moon, CircleShape),
        )
    }
}

/**
 * Editorial `notice` component from `design/system.md`: `--surface-color` fill, `3 dp` start
 * warning rule (`--warning`), rounded trailing corners (`12 dp`), and `--text-primary` text.
 */
@Composable
private fun CompassNotice(
    text: String,
    palette: CenitPalette,
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
            .padding(start = 17.dp, end = 14.dp, top = 10.dp, bottom = 10.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = palette.textPrimary,
        )
    }
}

@Composable
private fun buildDomeContentDescription(
    sensorState: CompassSensorState,
    dayBundle: CompassDayBundle,
    winds: Array<String>,
    locale: Locale,
): String {
    val eventsText = when {
        dayBundle.sunriseTick != null && dayBundle.sunsetTick != null && dayBundle.moonriseTick != null ->
            stringResource(
                R.string.compass_cd_events_sun_moon,
                formatRoundedDegrees(dayBundle.sunriseTick.azimuthDeg, locale),
                dayBundle.sunriseTick.label,
                formatRoundedDegrees(dayBundle.sunsetTick.azimuthDeg, locale),
                dayBundle.sunsetTick.label,
                formatRoundedDegrees(dayBundle.moonriseTick.azimuthDeg, locale),
                dayBundle.moonriseTick.label,
            )
        dayBundle.sunriseTick != null && dayBundle.sunsetTick != null ->
            stringResource(
                R.string.compass_cd_events_sun_only,
                formatRoundedDegrees(dayBundle.sunriseTick.azimuthDeg, locale),
                dayBundle.sunriseTick.label,
                formatRoundedDegrees(dayBundle.sunsetTick.azimuthDeg, locale),
                dayBundle.sunsetTick.label,
            )
        else -> stringResource(R.string.compass_cd_events_polar)
    }

    return when (sensorState.availability) {
        CompassAvailability.UNAVAILABLE -> stringResource(R.string.compass_cd_dome_north_up, eventsText)
        CompassAvailability.AVAILABLE -> {
            val heading = sensorState.headingDeg
            if (heading != null) {
                stringResource(
                    R.string.compass_cd_dome_live,
                    formatRoundedDegrees(heading, locale),
                    winds[CompassMath.windIndex(heading)],
                    eventsText,
                )
            } else {
                stringResource(R.string.compass_cd_dome_pending, eventsText)
            }
        }
    }
}

/**
 * Formats an azimuth/heading rounded to the nearest whole degree, folding `359.5° → 360°` into `0°`
 * so the readout never displays `360°`.
 */
private fun formatRoundedDegrees(degrees: Double, locale: Locale): String =
    SkyFormatting.formatDecimal(CompassMath.roundToWholeDegree(degrees).toDouble(), 0, locale)
