package io.github.ivan_olivera_dev.cenit.ui.camera

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.hardware.GeomagneticField
import android.view.Surface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.ivan_olivera_dev.cenit.R
import io.github.ivan_olivera_dev.cenit.core.Cenit
import io.github.ivan_olivera_dev.cenit.core.Ephemeris
import io.github.ivan_olivera_dev.cenit.core.MoonDay
import io.github.ivan_olivera_dev.cenit.core.MoonPhase
import io.github.ivan_olivera_dev.cenit.core.MoonPhaseName
import io.github.ivan_olivera_dev.cenit.core.PathPoint
import io.github.ivan_olivera_dev.cenit.core.SkyPosition
import io.github.ivan_olivera_dev.cenit.core.ar.ArLabelPlacer
import io.github.ivan_olivera_dev.cenit.core.ar.ArPaths
import io.github.ivan_olivera_dev.cenit.core.ar.ArProjector
import io.github.ivan_olivera_dev.cenit.core.ar.CameraFov
import io.github.ivan_olivera_dev.cenit.core.ar.DeviceRotation
import io.github.ivan_olivera_dev.cenit.core.ar.Pointing
import io.github.ivan_olivera_dev.cenit.core.ar.RotationSmoother
import io.github.ivan_olivera_dev.cenit.core.ar.ViewportRect
import io.github.ivan_olivera_dev.cenit.core.compass.CompassMath
import io.github.ivan_olivera_dev.cenit.core.format.CenitFormat
import io.github.ivan_olivera_dev.cenit.core.sky.SkyPhase
import io.github.ivan_olivera_dev.cenit.settings.LocalAppSettings
import io.github.ivan_olivera_dev.cenit.ui.components.CenitDatePicker
import io.github.ivan_olivera_dev.cenit.ui.planner.PlannerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

private val VisorLayersSaver = listSaver<VisorLayers, Boolean>(
    save = { listOf(it.sun, it.moon, it.solstices) },
    restore = {
        VisorLayers(
            sun = it.getOrElse(0) { true },
            moon = it.getOrElse(1) { true },
            solstices = it.getOrElse(2) { false },
        )
    },
)

private data class VisorDayData(
    val paths: ArPaths,
    val moonDay: MoonDay,
)

private data class VisorStrings(
    val horizonLabel: String,
    val sunriseWord: String,
    val sunsetWord: String,
    val juneLabel: String,
    val equinoxLabel: String,
    val decemberLabel: String,
    val sunMarkerTime: String,
    val sunMarkerBelow: String,
    val moonMarkerLabel: String,
    val sunEdgeAbove: String,
    val sunEdgeBelow: String,
    val moonEdgeAbove: String,
    val sunrisePill: String?,
    val sunsetPill: String?,
    val hourLabels: List<String>,
    val winds: List<String>,
)

/**
 * Full-screen AR camera overlay implementing artboard `01 · Visor` (`design/cenit-stage-2/01-visor.html`).
 *
 * Orientation pipeline:
 * raw portrait `ArInputs.rotation` -> `DeviceRotation` -> magnetic declination (`GeomagneticField`
 * at the active place) -> active display rotation remap -> `RotationSmoother` (quaternion SLERP,
 * `alpha = 0.14`) -> `ArProjector` with live preview FOV/size.
 *
 * Performance: `ArPaths` (5-min steps) and `MoonDay` are precomputed off the main thread per
 * `(place, date, zone)`. Per sensor frame, only projection, crossing detection, and pre-allocated
 * Canvas drawing run.
 */
@Composable
fun VisorOverlay(
    inputs: ArInputs,
    state: PlannerState,
    onStateChange: (PlannerState) -> Unit,
    modifier: Modifier = Modifier,
    clock: Clock = Clock.systemDefaultZone(),
) {
    val settings = LocalAppSettings.current
    val configuration = LocalConfiguration.current
    val view = LocalView.current
    val density = LocalDensity.current
    val locale = remember(configuration) {
        configuration.locales[0] ?: Locale.getDefault()
    }
    val winds = stringArrayResource(R.array.compass_winds)

    var layers by rememberSaveable(stateSaver = VisorLayersSaver) {
        mutableStateOf(VisorLayers.DEFAULT)
    }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    val place = state.activePlace.place
    val zone = state.zone
    val selectedDate = state.selectedDate
    val selectedInstant = state.selectedInstant

    // Off-main-thread precomputation of all daily trajectories, hour markers, and rise/set points.
    val dayData by produceState<VisorDayData?>(
        initialValue = null,
        key1 = place,
        key2 = selectedDate,
        key3 = zone,
    ) {
        value = withContext(Dispatchers.Default) {
            val paths = ArPaths.compute(place, selectedDate, zone, stepMinutes = 5)
            val moonDay = Ephemeris.moonDay(place, selectedDate, zone)
            VisorDayData(paths = paths, moonDay = moonDay)
        }
    }

    // Selected-instant positions & sky phase (recomputed only when time slider / date / place changes).
    val sunPosition = remember(place, selectedInstant) {
        Ephemeris.sunPosition(place, selectedInstant, apparent = true)
    }
    val moonPosition = remember(place, selectedInstant) {
        Ephemeris.moonPosition(place, selectedInstant)
    }
    val moonPhase = remember(selectedInstant) {
        Ephemeris.moonPhase(selectedInstant)
    }
    val sunSkyPhase = remember(place, selectedInstant) {
        SkyPhase.at(place, selectedInstant)
    }
    val moonIllumPercent = (moonPhase.illumination * 100.0).roundToInt().coerceIn(0, 100)

    // True-north magnetic declination at the active place & date (matches CompassSensor).
    val declinationDeg = remember(place, selectedDate, zone) {
        val noonMillis = selectedDate.atStartOfDay(zone).plusHours(12).toInstant().toEpochMilli()
        GeomagneticField(
            place.latitude.toFloat(),
            place.longitude.toFloat(),
            place.elevationMeters.toFloat(),
            noonMillis,
        ).declination.toDouble()
    }

    // Active display rotation (0° portrait, 90°/270° landscape when auto-rotate is enabled).
    val displayRotationDeg = remember(configuration.orientation, view.display?.rotation) {
        when (view.display?.rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }

    // Quaternion low-pass smoother eliminating jitter ("paths bounce around").
    val smoother = remember { RotationSmoother(alpha = VISOR_SMOOTHING_ALPHA) }
    LaunchedEffect(declinationDeg, displayRotationDeg) {
        smoother.reset()
    }

    val smoothedRotation = remember(inputs.rotation, declinationDeg, displayRotationDeg) {
        val parsed = runCatching {
            DeviceRotation.fromFloatArray(inputs.rotation)
                .withDeclination(declinationDeg)
                .withDisplayRotation(displayRotationDeg)
        }.getOrNull()
        if (parsed != null) smoother.update(parsed) else smoother.current ?: DeviceRotation.IDENTITY
    }

    val projector = remember(
        inputs.horizontalFovDeg,
        inputs.verticalFovDeg,
        inputs.previewWidthPx,
        inputs.previewHeightPx,
    ) {
        if (inputs.previewWidthPx > 0 && inputs.previewHeightPx > 0) {
            runCatching {
                ArProjector(
                    fov = CameraFov(
                        horizontalDeg = inputs.horizontalFovDeg,
                        verticalDeg = inputs.verticalFovDeg,
                    ),
                    widthPx = inputs.previewWidthPx.toDouble(),
                    heightPx = inputs.previewHeightPx.toDouble(),
                )
            }.getOrNull()
        } else {
            null
        }
    }

    val pointing = remember(projector, smoothedRotation) {
        val activeProjector = projector ?: FALLBACK_POINTING_PROJECTOR
        activeProjector.pointing(smoothedRotation)
    }

    val calibrated = !needsCompassCalibration(inputs.accuracy)
    val pointingAtGround = isPointingAtGround(pointing.elevationDeg)

    val crossingResult = remember(
        smoothedRotation,
        pointing.elevationDeg,
        dayData,
        layers,
        moonIllumPercent,
        place,
    ) {
        evaluateVisorCrossing(
            rotation = smoothedRotation,
            pointingElevationDeg = pointing.elevationDeg,
            paths = dayData?.paths,
            layers = layers,
            moonIlluminationPercent = moonIllumPercent,
            skyPhaseAt = { instant -> SkyPhase.at(place, instant) },
        )
    }

    // Formatted labels cached outside Canvas onDraw so draw stays allocation-free.
    val formattedSelectedTime = remember(state.selectedTime, settings.clock, locale) {
        CenitFormat.formatLocalTime(state.selectedTime, settings.clock, locale)
    }
    val dateChipLabel = remember(selectedDate, locale) {
        selectedDate.format(DateTimeFormatter.ofPattern("d MMM", locale))
    }
    val dateChipFullLabel = remember(selectedDate, locale) {
        selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
    }
    val phaseLabel = stringResource(skyPhaseLabelRes(sunSkyPhase))
    val moonPhaseName = stringResource(moonPhaseNameRes(dayData?.moonDay?.phaseName ?: MoonPhaseName.WAXING_GIBBOUS))

    val sunriseWord = stringResource(R.string.event_sunrise)
    val sunsetWord = stringResource(R.string.event_sunset)
    val sunrisePill = dayData?.paths?.sunrise?.let { pt ->
        val t = CenitFormat.formatLocalTime(pt.localTime, settings.clock, locale)
        stringResource(R.string.visor_rise_set_label, sunriseWord, t)
    }
    val sunsetPill = dayData?.paths?.sunset?.let { pt ->
        val t = CenitFormat.formatLocalTime(pt.localTime, settings.clock, locale)
        stringResource(R.string.visor_rise_set_label, sunsetWord, t)
    }
    val hourLabels = remember(dayData, settings.clock, locale) {
        dayData?.paths?.hourMarkers?.map { marker ->
            formatArHourLabel(marker.localTime.hour, settings.clock, locale)
        } ?: emptyList()
    }

    val sunAzShort = CenitFormat.formatAngle(
        CompassMath.roundToWholeDegree(sunPosition.azimuth).toDouble(),
        withDecimals = false,
        locale = locale,
    )
    val moonAzShort = CenitFormat.formatAngle(
        CompassMath.roundToWholeDegree(moonPosition.azimuth).toDouble(),
        withDecimals = false,
        locale = locale,
    )
    val visorStrings = VisorStrings(
        horizonLabel = stringResource(R.string.visor_horizon_label),
        sunriseWord = sunriseWord,
        sunsetWord = sunsetWord,
        juneLabel = stringResource(R.string.dome_solstice_jun),
        equinoxLabel = stringResource(R.string.visor_solstice_equinox),
        decemberLabel = stringResource(R.string.dome_solstice_dec),
        sunMarkerTime = formattedSelectedTime,
        sunMarkerBelow = stringResource(R.string.visor_sun_marker_below, formattedSelectedTime),
        moonMarkerLabel = stringResource(R.string.visor_moon_marker, moonIllumPercent),
        sunEdgeAbove = stringResource(R.string.visor_edge_sun, sunAzShort),
        sunEdgeBelow = stringResource(R.string.visor_edge_sun_below, sunAzShort),
        moonEdgeAbove = stringResource(R.string.visor_edge_moon, moonAzShort),
        sunrisePill = sunrisePill,
        sunsetPill = sunsetPill,
        hourLabels = hourLabels,
        winds = remember(winds) { winds.toList() },
    )

    val crosshairAimText = stringResource(
        R.string.visor_crosshair_aim,
        CenitFormat.formatAngle(pointing.azimuthDeg, settings.degreeDecimals, locale),
        winds[CompassMath.windIndex(pointing.azimuthDeg)],
        formatCrosshairElevation(pointing.elevationDeg, settings.degreeDecimals, locale),
    )

    val crossingPillText: String? = when (val c = crossingResult) {
        is VisorCrossing.Sun -> {
            val t = CenitFormat.formatTime(c.instant, zone, settings.clock, locale)
            val ph = stringResource(skyPhaseLabelRes(c.phase)).lowercase(locale)
            stringResource(R.string.visor_crossing_sun, t, ph)
        }
        is VisorCrossing.SunBelowHorizon -> {
            val t = CenitFormat.formatTime(c.instant, zone, settings.clock, locale)
            stringResource(R.string.visor_crossing_sun_below, t)
        }
        is VisorCrossing.Moon -> {
            val t = CenitFormat.formatTime(c.instant, zone, settings.clock, locale)
            stringResource(R.string.visor_crossing_moon, t, c.illuminationPercent)
        }
        is VisorCrossing.Solstice -> {
            val t = CenitFormat.formatTime(c.instant, zone, settings.clock, locale)
            val resId = when (c.kind) {
                SolsticeReferenceKind.JUNE_SOLSTICE -> R.string.visor_crossing_solstice_jun
                SolsticeReferenceKind.MARCH_EQUINOX -> R.string.visor_crossing_solstice_eq
                SolsticeReferenceKind.DECEMBER_SOLSTICE -> R.string.visor_crossing_solstice_dec
            }
            stringResource(resId, t)
        }
        is VisorCrossing.PointingAtGround, VisorCrossing.None -> null
    }

    val canvasResources = remember(density) { ArCanvasResources(density) }
    val overlayDesc = stringResource(R.string.visor_cd_overlay)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isLandscape = maxWidth > maxHeight

        // Safe label viewport insets so Canvas labels & edge arrows stay clear of HUD bars.
        val safeTopPx = with(density) {
            val baseTop = if (isLandscape) 60.dp else 112.dp
            val noticeExtra = if (!calibrated || pointingAtGround) 56.dp else 0.dp
            (baseTop + noticeExtra).toPx()
        }
        val safeBottomPx = with(density) {
            (if (isLandscape) 16.dp else 210.dp).toPx()
        }
        val safeRightPx = with(density) {
            (if (isLandscape) 208.dp else 12.dp).toPx()
        }
        val safeLeftPx = with(density) { 12.dp.toPx() }

        // 1. Live AR Canvas (horizon, paths, hour dots, markers, crosshair, crossing pill, edge arrows)
        VisorArCanvas(
            projector = projector,
            rotation = smoothedRotation,
            pointing = pointing,
            paths = dayData?.paths,
            sunPosition = sunPosition,
            moonPosition = moonPosition,
            moonPhase = moonPhase,
            layers = layers,
            calibrated = calibrated,
            pointingAtGround = pointingAtGround,
            crossing = crossingResult,
            crosshairAimText = crosshairAimText,
            crossingPillText = crossingPillText,
            strings = visorStrings,
            safeLeftPx = safeLeftPx,
            safeTopPx = safeTopPx,
            safeRightPx = safeRightPx,
            safeBottomPx = safeBottomPx,
            resources = canvasResources,
            contentDescription = overlayDesc,
            modifier = Modifier.fillMaxSize(),
        )

        if (isLandscape) {
            // 2L. Landscape top heading tape (clears top-left back button & right side dock)
            VisorHeadingTape(
                headingDeg = pointing.azimuthDeg,
                winds = winds,
                sunPosition = sunPosition,
                moonPosition = moonPosition,
                layers = layers,
                locale = locale,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 64.dp, end = 208.dp, top = 8.dp),
            )

            // 3L. Landscape notices below tape
            if (!calibrated || pointingAtGround) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(start = 12.dp, end = 208.dp, top = 54.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (!calibrated) VisorCalibrationNotice()
                    if (pointingAtGround) {
                        VisorGroundNotice(upDegrees = abs(pointing.elevationDeg).roundToInt().coerceAtLeast(1))
                    }
                }
            }

            // 4L. Landscape right-hand side dock
            VisorLandscapeDock(
                placeName = state.activePlace.name,
                formattedTime = formattedSelectedTime,
                dateChipLabel = dateChipLabel,
                dateChipFullLabel = dateChipFullLabel,
                phaseLabel = phaseLabel,
                sunPosition = sunPosition,
                selectedFraction = state.selectedFraction,
                layers = layers,
                winds = winds,
                clock = settings.clock,
                withDecimals = settings.degreeDecimals,
                locale = locale,
                onOpenDatePicker = { showDatePicker = true },
                onResetNow = { onStateChange(state.resetToNow(clock)) },
                onFractionChange = { f -> onStateChange(state.selectFraction(f)) },
                onStepMinutes = { d -> onStateChange(state.stepMinutes(d)) },
                onToggleSun = { layers = layers.copy(sun = !layers.sun) },
                onToggleMoon = { layers = layers.copy(moon = !layers.moon) },
                onToggleSolstices = { layers = layers.copy(solstices = !layers.solstices) },
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        } else {
            // 2P. Portrait top HUD bar (beside CameraScreen's top-left 44 dp back button)
            VisorTopBar(
                placeName = state.activePlace.name,
                moonPhase = moonPhase,
                moonPhaseName = moonPhaseName,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 64.dp, end = 12.dp, top = 8.dp),
            )

            // 3P. Portrait heading tape right below the top bar
            VisorHeadingTape(
                headingDeg = pointing.azimuthDeg,
                winds = winds,
                sunPosition = sunPosition,
                moonPosition = moonPosition,
                layers = layers,
                locale = locale,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 58.dp),
            )

            // 4P. Calibration & ground-pointing notices below the heading tape
            if (!calibrated || pointingAtGround) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(start = 12.dp, end = 12.dp, top = 104.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (!calibrated) VisorCalibrationNotice()
                    if (pointingAtGround) {
                        VisorGroundNotice(upDegrees = abs(pointing.elevationDeg).roundToInt().coerceAtLeast(1))
                    }
                }
            }

            // 5P. Portrait bottom dock
            VisorBottomDock(
                formattedTime = formattedSelectedTime,
                dateChipLabel = dateChipLabel,
                dateChipFullLabel = dateChipFullLabel,
                phaseLabel = phaseLabel,
                sunPosition = sunPosition,
                selectedFraction = state.selectedFraction,
                layers = layers,
                winds = winds,
                clock = settings.clock,
                withDecimals = settings.degreeDecimals,
                locale = locale,
                onOpenDatePicker = { showDatePicker = true },
                onResetNow = { onStateChange(state.resetToNow(clock)) },
                onFractionChange = { f -> onStateChange(state.selectFraction(f)) },
                onStepMinutes = { d -> onStateChange(state.stepMinutes(d)) },
                onToggleSun = { layers = layers.copy(sun = !layers.sun) },
                onToggleMoon = { layers = layers.copy(moon = !layers.moon) },
                onToggleSolstices = { layers = layers.copy(solstices = !layers.solstices) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    if (showDatePicker) {
        CenitDatePicker(
            initialDate = state.selectedDate,
            onDismiss = { showDatePicker = false },
            onConfirm = { pickedDate ->
                showDatePicker = false
                onStateChange(state.selectDate(pickedDate))
            },
        )
    }
}

/**
 * Pre-allocated Paint, Stroke, Path, and collision placer objects reused across every 60 fps draw.
 */
private class ArCanvasResources(density: Density) {
    val pathScratch = Path()
    val moonLitScratch = Path()
    val pillRectScratch = RectF()
    val placer = ArLabelPlacer(ViewportRect(0.0, 0.0, 1.0, 1.0))
    val placedCenter = DoubleArray(2)

    val casingWidthPx: Float = with(density) { 3.5.dp.toPx() }
    val dp1: Float = with(density) { 1.dp.toPx() }
    val dp2: Float = with(density) { 2.dp.toPx() }
    val dp4: Float = with(density) { 4.dp.toPx() }
    val dp8: Float = with(density) { 8.dp.toPx() }
    val dp12: Float = with(density) { 12.dp.toPx() }
    val dp14: Float = with(density) { 14.dp.toPx() }
    val dp18: Float = with(density) { 18.dp.toPx() }
    val dp26: Float = with(density) { 26.dp.toPx() }
    val dp80: Float = with(density) { 80.dp.toPx() }

    private val horizonDash = PathEffect.dashPathEffect(floatArrayOf(with(density) { 5.dp.toPx() }, with(density) { 4.dp.toPx() }))
    private val sunNightDash = PathEffect.dashPathEffect(floatArrayOf(with(density) { 2.dp.toPx() }, with(density) { 4.dp.toPx() }))
    private val moonDash = PathEffect.dashPathEffect(floatArrayOf(with(density) { 6.dp.toPx() }, with(density) { 4.dp.toPx() }))
    private val solsticeDash = PathEffect.dashPathEffect(floatArrayOf(with(density) { 8.dp.toPx() }, with(density) { 4.dp.toPx() }))
    private val equinoxDash = PathEffect.dashPathEffect(floatArrayOf(with(density) { 2.dp.toPx() }, with(density) { 4.dp.toPx() }))
    private val markerBelowDash = PathEffect.dashPathEffect(floatArrayOf(with(density) { 3.dp.toPx() }, with(density) { 2.dp.toPx() }))

    val horizonCasing = Stroke(width = with(density) { 4.2.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = horizonDash)
    val horizonCore = Stroke(width = with(density) { 1.2.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = horizonDash)

    val sunAboveCasing = Stroke(width = with(density) { 5.6.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val sunAboveCore = Stroke(width = with(density) { 2.6.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round)

    val sunBelowCasing = Stroke(width = with(density) { 4.4.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = sunNightDash)
    val sunBelowCore = Stroke(width = with(density) { 1.4.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = sunNightDash)

    val moonCasing = Stroke(width = with(density) { 5.1.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = moonDash)
    val moonCore = Stroke(width = with(density) { 2.1.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = moonDash)

    val solsticeCasing = Stroke(width = with(density) { 4.1.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = solsticeDash)
    val solsticeCore = Stroke(width = with(density) { 1.1.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = solsticeDash)

    val equinoxCasing = Stroke(width = with(density) { 4.1.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = equinoxDash)
    val equinoxCore = Stroke(width = with(density) { 1.1.dp.toPx() }, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = equinoxDash)

    val crosshairCasing = Stroke(width = with(density) { 3.8.dp.toPx() }, cap = StrokeCap.Round)
    val crosshairCore = Stroke(width = with(density) { 1.5.dp.toPx() }, cap = StrokeCap.Round)

    val sunDotStroke = Stroke(width = with(density) { 1.8.dp.toPx() })
    val moonDotStroke = Stroke(width = with(density) { 1.5.dp.toPx() })
    val sunMarkerStroke = Stroke(width = with(density) { 2.2.dp.toPx() })
    val sunMarkerBelowStroke = Stroke(width = with(density) { 2.dp.toPx() }, pathEffect = markerBelowDash)
    val riseSetStroke = Stroke(width = with(density) { 2.dp.toPx() })

    val textSizePx: Float = with(density) { 11.5.sp.toPx() }
    val pillTextSizePx: Float = with(density) { 11.sp.toPx() }
    val pillHeightPx: Float = with(density) { 22.dp.toPx() }
    val edgePillHeightPx: Float = with(density) { 26.dp.toPx() }

    val textCasingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = casingWidthPx
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = VisorHudBg.toArgb()
        textSize = textSizePx
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        fontFeatureSettings = "tnum"
    }

    val textFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = VisorHudFg.toArgb()
        textSize = textSizePx
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        fontFeatureSettings = "tnum"
    }

    val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = VisorHudFg.toArgb()
        textSize = pillTextSizePx
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        fontFeatureSettings = "tnum"
    }

    val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = VisorHudBg.copy(alpha = 0.90f).toArgb()
    }

    val pillStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = with(density) { 1.1.dp.toPx() }
        color = VisorHudBorder.toArgb()
    }
}

@Composable
private fun VisorArCanvas(
    projector: ArProjector?,
    rotation: DeviceRotation,
    pointing: Pointing,
    paths: ArPaths?,
    sunPosition: SkyPosition,
    moonPosition: SkyPosition,
    moonPhase: MoonPhase,
    layers: VisorLayers,
    calibrated: Boolean,
    pointingAtGround: Boolean,
    crossing: VisorCrossing,
    crosshairAimText: String,
    crossingPillText: String?,
    strings: VisorStrings,
    safeLeftPx: Float,
    safeTopPx: Float,
    safeRightPx: Float,
    safeBottomPx: Float,
    resources: ArCanvasResources,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.semantics {
            this.contentDescription = contentDescription
            this.role = Role.Image
        },
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f || projector == null) return@Canvas

        val cx0 = w / 2f
        val cy0 = h / 2f
        val safeRightBound = (w - safeRightPx).coerceAtLeast(safeLeftPx + 1f)
        val safeBottomBound = (h - safeBottomPx).coerceAtLeast(safeTopPx + 1f)
        val pathAlpha = if (calibrated) 1f else 0.45f
        val labelRollDeg = if (abs(pointing.rollDeg) > 1.5) (-pointing.rollDeg).toFloat() else 0f

        val placer = resources.placer
        placer.reset(
            ViewportRect(
                left = safeLeftPx.toDouble(),
                top = safeTopPx.toDouble(),
                right = safeRightBound.toDouble(),
                bottom = safeBottomBound.toDouble(),
            ),
        )

        // Reserve crosshair + aim pill + crossing pill first (highest visual priority)
        val crossR = resources.dp18
        val crossOuter = resources.dp26
        placer.reserve(
            (cx0 - crossOuter - resources.dp4).toDouble(),
            (cy0 - crossOuter - resources.dp4).toDouble(),
            (cx0 + crossOuter + resources.dp4).toDouble(),
            (cy0 + crossOuter + resources.dp4).toDouble(),
        )
        val aimPillW = (resources.pillTextPaint.measureText(crosshairAimText) + resources.dp18).coerceAtLeast(resources.dp26 * 2f)
        val aimPillCy = cy0 - crossOuter - resources.pillHeightPx * 0.72f
        placer.reserve(
            (cx0 - aimPillW / 2f).toDouble(),
            (aimPillCy - resources.pillHeightPx / 2f).toDouble(),
            (cx0 + aimPillW / 2f).toDouble(),
            (aimPillCy + resources.pillHeightPx / 2f).toDouble(),
        )

        val crossingPillCy = cy0 + crossOuter + resources.pillHeightPx * 0.78f
        val crossingPillW = if (crossingPillText != null) {
            (resources.pillTextPaint.measureText(crossingPillText) + resources.dp18)
                .coerceAtMost((safeRightBound - safeLeftPx - resources.dp8).coerceAtLeast(80f))
        } else {
            0f
        }
        if (crossingPillText != null && crossingPillW > 0f) {
            placer.reserve(
                (cx0 - crossingPillW / 2f).toDouble(),
                (crossingPillCy - resources.pillHeightPx / 2f).toDouble(),
                (cx0 + crossingPillW / 2f).toDouble(),
                (crossingPillCy + resources.pillHeightPx / 2f).toDouble(),
            )
        }

        // Reserve current sun & moon marker footprints before placing path labels
        val sunProjected = if (layers.sun) projector.project(rotation, sunPosition) else null
        val sunInSafeView = sunProjected != null &&
            sunProjected.x >= safeLeftPx && sunProjected.x <= safeRightBound &&
            sunProjected.y >= safeTopPx && sunProjected.y <= safeBottomBound
        if (sunInSafeView) {
            placer.reserve(
                sunProjected.x - resources.dp14,
                sunProjected.y - resources.dp14,
                sunProjected.x + resources.dp14,
                sunProjected.y + resources.dp14,
            )
        }

        val moonProjected = if (layers.moon) projector.project(rotation, moonPosition) else null
        val moonInSafeView = moonProjected != null &&
            moonProjected.x >= safeLeftPx && moonProjected.x <= safeRightBound &&
            moonProjected.y >= safeTopPx && moonProjected.y <= safeBottomBound
        if (moonInSafeView && moonPosition.elevation > Cenit.RISE_SET_ALTITUDE_DEG) {
            placer.reserve(
                moonProjected.x - resources.dp14,
                moonProjected.y - resources.dp14,
                moonProjected.x + resources.dp14,
                moonProjected.y + resources.dp14,
            )
        }

        // 1. Horizon line (0°) + 15° azimuth ticks + 45° cardinal labels
        drawHorizonLayer(
            projector = projector,
            rotation = rotation,
            pointingAtGround = pointingAtGround,
            labelRollDeg = labelRollDeg,
            strings = strings,
            resources = resources,
            w = w,
            h = h,
        )

        // 2. Solstice & equinox reference paths (thin, dashed/dotted, labelled jun / eq / dic)
        if (layers.solstices && paths != null) {
            drawSolsticeLayer(
                projector = projector,
                rotation = rotation,
                paths = paths,
                pathAlpha = pathAlpha,
                pointingAtGround = pointingAtGround,
                labelRollDeg = labelRollDeg,
                strings = strings,
                resources = resources,
                w = w,
                h = h,
            )
        }

        // 3. Moon path (dashed) + 2h dots + moon marker with phase glyph
        if (layers.moon && paths != null) {
            drawMoonLayer(
                projector = projector,
                rotation = rotation,
                paths = paths,
                moonPosition = moonPosition,
                moonProjected = moonProjected,
                moonInSafeView = moonInSafeView,
                moonPhase = moonPhase,
                pathAlpha = pathAlpha,
                pointingAtGround = pointingAtGround,
                labelRollDeg = labelRollDeg,
                strings = strings,
                resources = resources,
                w = w,
                h = h,
            )
        }

        // 4. Sun path (solid above horizon, dotted below) + hour dots & labels + rise/set + sun marker
        if (layers.sun && paths != null) {
            drawSunLayer(
                projector = projector,
                rotation = rotation,
                paths = paths,
                sunPosition = sunPosition,
                sunProjected = sunProjected,
                sunInSafeView = sunInSafeView,
                pathAlpha = pathAlpha,
                pointingAtGround = pointingAtGround,
                labelRollDeg = labelRollDeg,
                strings = strings,
                resources = resources,
                w = w,
                h = h,
            )
        }

        // 5. Centre crosshair + aim readout pill + crossing pill
        val hitColor = when (crossing) {
            is VisorCrossing.Sun, is VisorCrossing.SunBelowHorizon -> VisorHudSun
            is VisorCrossing.Moon -> VisorHudMoon
            else -> VisorHudFg
        }
        drawCrosshairAndPills(
            cx = cx0,
            cy = cy0,
            crossR = crossR,
            crossOuter = crossOuter,
            hitColor = hitColor,
            aimText = crosshairAimText,
            aimPillW = aimPillW,
            aimPillCy = aimPillCy,
            crossingText = crossingPillText,
            crossingPillW = crossingPillW,
            crossingPillCy = crossingPillCy,
            resources = resources,
        )

        // 6. Off-screen edge arrows with body glyph and azimuth (suppressed when pointing at ground)
        if (!pointingAtGround) {
            if (layers.sun && !sunInSafeView) {
                val sunArrowLabel = if (sunPosition.elevation <= Cenit.RISE_SET_ALTITUDE_DEG) {
                    strings.sunEdgeBelow
                } else {
                    strings.sunEdgeAbove
                }
                drawEdgeArrowPill(
                    projector = projector,
                    rotation = rotation,
                    position = sunPosition,
                    label = sunArrowLabel,
                    accent = VisorHudSun,
                    isSun = true,
                    moonPhase = moonPhase,
                    resources = resources,
                )
            }
            if (layers.moon && !moonInSafeView && moonPosition.elevation > Cenit.RISE_SET_ALTITUDE_DEG) {
                drawEdgeArrowPill(
                    projector = projector,
                    rotation = rotation,
                    position = moonPosition,
                    label = strings.moonEdgeAbove,
                    accent = VisorHudMoon,
                    isSun = false,
                    moonPhase = moonPhase,
                    resources = resources,
                )
            }
        }
    }
}

private fun DrawScope.drawHorizonLayer(
    projector: ArProjector,
    rotation: DeviceRotation,
    pointingAtGround: Boolean,
    labelRollDeg: Float,
    strings: VisorStrings,
    resources: ArCanvasResources,
    w: Float,
    h: Float,
) {
    val path = resources.pathScratch
    val margin = resources.dp80
    val maxJump = w * 0.65f

    // Horizon ring sampled every 3° around full 360° (works cleanly at any pitch or roll)
    var hasPrev = false
    var prevX = 0f
    var prevY = 0f
    var runCount = 0
    path.rewind()

    fun flushHorizonRun() {
        if (runCount >= 2) {
            drawPath(path = path, color = VisorHudBg, alpha = 0.9f, style = resources.horizonCasing)
            drawPath(path = path, color = VisorHudMuted, alpha = 0.9f, style = resources.horizonCore)
        }
        path.rewind()
        runCount = 0
        hasPrev = false
    }

    for (step in 0..120) {
        val az = (step * 3) % 360
        val pt = projector.project(rotation, az.toDouble(), 0.0)
        if (pt != null && pt.x > -margin && pt.x < w + margin && pt.y > -margin && pt.y < h + margin) {
            val px = pt.x.toFloat()
            val py = pt.y.toFloat()
            if (hasPrev && hypot(px - prevX, py - prevY) > maxJump) {
                flushHorizonRun()
            }
            if (runCount == 0) path.moveTo(px, py) else path.lineTo(px, py)
            prevX = px
            prevY = py
            hasPrev = true
            runCount++
        } else if (runCount > 0) {
            flushHorizonRun()
        }
    }
    flushHorizonRun()

    // Horizon ticks every 15° + localized 45° cardinal letters
    val tickCasingWidth = 3.dp.toPx()
    val tickCoreWidth = 1.2.dp.toPx()
    val tickMajorHalf = 6.dp.toPx()
    val tickMinorHalf = 3.dp.toPx()
    val radRoll = Math.toRadians(labelRollDeg.toDouble())
    val upScreenX = sin(radRoll).toFloat()
    val upScreenY = -cos(radRoll).toFloat()

    for (az in 0 until 360 step 15) {
        val pt = projector.project(rotation, az.toDouble(), 0.0) ?: continue
        val px = pt.x.toFloat()
        val py = pt.y.toFloat()
        if (px < resources.dp18 || px > w - resources.dp18 || py < resources.dp18 || py > h - resources.dp18) continue

        val isCardinal = az % 45 == 0
        val half = if (isCardinal) tickMajorHalf else tickMinorHalf
        val p1 = Offset(px - upScreenX * half, py - upScreenY * half)
        val p2 = Offset(px + upScreenX * half, py + upScreenY * half)
        drawLine(color = VisorHudBg, start = p1, end = p2, strokeWidth = tickCasingWidth, cap = StrokeCap.Round)
        drawLine(color = VisorHudFg, start = p1, end = p2, strokeWidth = tickCoreWidth, cap = StrokeCap.Round)

        if (isCardinal && !pointingAtGround) {
            val windLabel = strings.winds[(az / 45) * 2]
            val lw = (resources.textFillPaint.measureText(windLabel) + resources.dp4).toDouble()
            val lh = (resources.textSizePx + resources.dp4).toDouble()
            val lx = px - upScreenX * (half + resources.dp12)
            val ly = py - upScreenY * (half + resources.dp12)
            if (resources.placer.tryPlaceInto(lx.toDouble(), ly.toDouble(), lw, lh, resources.placedCenter)) {
                drawCasedText(
                    text = windLabel,
                    cx = resources.placedCenter[0].toFloat(),
                    cy = resources.placedCenter[1].toFloat(),
                    color = VisorHudFg,
                    rollDeg = labelRollDeg,
                    resources = resources,
                )
            }
        }
    }

    // Subtle "0° · Horiz." label near the right (or left) horizon edge
    if (!pointingAtGround) {
        val hzText = strings.horizonLabel
        val lw = (resources.textFillPaint.measureText(hzText) + resources.dp8).toDouble()
        val lh = (resources.textSizePx + resources.dp4).toDouble()
        val rightEdgeAz = CompassMath.normalizeDegrees(projector.pointing(rotation).azimuthDeg + projector.fov.horizontalDeg * 0.38)
        val leftEdgeAz = CompassMath.normalizeDegrees(projector.pointing(rotation).azimuthDeg - projector.fov.horizontalDeg * 0.38)
        val candidates = arrayOf(
            projector.project(rotation, rightEdgeAz, 0.0),
            projector.project(rotation, leftEdgeAz, 0.0),
        )
        for (cand in candidates) {
            if (cand == null) continue
            val lx = cand.x
            val ly = cand.y - resources.dp12
            if (resources.placer.tryPlaceInto(lx, ly, lw, lh, resources.placedCenter, clampToBounds = true)) {
                drawCasedText(
                    text = hzText,
                    cx = resources.placedCenter[0].toFloat(),
                    cy = resources.placedCenter[1].toFloat(),
                    color = VisorHudMuted,
                    rollDeg = labelRollDeg,
                    resources = resources,
                )
                break
            }
        }
    }
}

private fun DrawScope.drawSolsticeLayer(
    projector: ArProjector,
    rotation: DeviceRotation,
    paths: ArPaths,
    pathAlpha: Float,
    pointingAtGround: Boolean,
    labelRollDeg: Float,
    strings: VisorStrings,
    resources: ArCanvasResources,
    w: Float,
    h: Float,
) {
    val alpha = 0.78f * pathAlpha
    drawProjectedPathRuns(
        points = paths.juneSolsticePath,
        projector = projector,
        rotation = rotation,
        minElevationDeg = -2.0,
        maxElevationDeg = 90.0,
        coreColor = VisorHudMuted,
        casingStroke = resources.solsticeCasing,
        coreStroke = resources.solsticeCore,
        alpha = alpha,
        resources = resources,
        w = w,
        h = h,
    )
    drawProjectedPathRuns(
        points = paths.marchEquinoxPath,
        projector = projector,
        rotation = rotation,
        minElevationDeg = -2.0,
        maxElevationDeg = 90.0,
        coreColor = VisorHudMuted,
        casingStroke = resources.equinoxCasing,
        coreStroke = resources.equinoxCore,
        alpha = alpha,
        resources = resources,
        w = w,
        h = h,
    )
    drawProjectedPathRuns(
        points = paths.decemberSolsticePath,
        projector = projector,
        rotation = rotation,
        minElevationDeg = -2.0,
        maxElevationDeg = 90.0,
        coreColor = VisorHudMuted,
        casingStroke = resources.solsticeCasing,
        coreStroke = resources.solsticeCore,
        alpha = alpha,
        resources = resources,
        w = w,
        h = h,
    )

    if (!pointingAtGround) {
        placeReferenceArcLabel(paths.juneSolsticePath, strings.juneLabel, projector, rotation, labelRollDeg, resources)
        placeReferenceArcLabel(paths.marchEquinoxPath, strings.equinoxLabel, projector, rotation, labelRollDeg, resources)
        placeReferenceArcLabel(paths.decemberSolsticePath, strings.decemberLabel, projector, rotation, labelRollDeg, resources)
    }
}

private fun DrawScope.placeReferenceArcLabel(
    points: List<PathPoint>,
    label: String,
    projector: ArProjector,
    rotation: DeviceRotation,
    labelRollDeg: Float,
    resources: ArCanvasResources,
) {
    val lw = (resources.textFillPaint.measureText(label) + resources.dp8).toDouble()
    val lh = (resources.textSizePx + resources.dp4).toDouble()
    for (i in points.indices) {
        val pos = points[i].position
        if (pos.elevation <= 4.0) continue
        val p = projector.project(rotation, pos) ?: continue
        if (resources.placer.tryPlaceInto(p.x, p.y - resources.dp12, lw, lh, resources.placedCenter)) {
            drawCasedText(
                text = label,
                cx = resources.placedCenter[0].toFloat(),
                cy = resources.placedCenter[1].toFloat(),
                color = VisorHudMuted,
                rollDeg = labelRollDeg,
                resources = resources,
            )
            return
        }
    }
}

private fun DrawScope.drawMoonLayer(
    projector: ArProjector,
    rotation: DeviceRotation,
    paths: ArPaths,
    moonPosition: SkyPosition,
    moonProjected: io.github.ivan_olivera_dev.cenit.core.ar.ScreenPoint?,
    moonInSafeView: Boolean,
    moonPhase: MoonPhase,
    pathAlpha: Float,
    pointingAtGround: Boolean,
    labelRollDeg: Float,
    strings: VisorStrings,
    resources: ArCanvasResources,
    w: Float,
    h: Float,
) {
    drawProjectedPathRuns(
        points = paths.moonPath,
        projector = projector,
        rotation = rotation,
        minElevationDeg = -1.0,
        maxElevationDeg = 90.0,
        coreColor = VisorHudMoon,
        casingStroke = resources.moonCasing,
        coreStroke = resources.moonCore,
        alpha = pathAlpha,
        resources = resources,
        w = w,
        h = h,
    )

    // Moon 2-hour interval dots (5-min samples -> every 24 samples = 120 min)
    val dotR = 3.2.dp.toPx()
    val bounds = resources.placer.bounds
    for (i in paths.moonPath.indices step 24) {
        val pos = paths.moonPath[i].position
        if (pos.elevation <= 2.0) continue
        val p = projector.project(rotation, pos) ?: continue
        if (p.x < bounds.left || p.x > bounds.right || p.y < bounds.top || p.y > bounds.bottom) continue
        val center = Offset(p.x.toFloat(), p.y.toFloat())
        drawCircle(color = VisorHudBg, radius = dotR, center = center, alpha = pathAlpha)
        drawCircle(color = VisorHudMoon, radius = dotR, center = center, alpha = pathAlpha, style = resources.moonDotStroke)
    }

    // Current moon marker with phase crescent/gibbous glyph
    if (moonProjected != null && moonInSafeView && moonPosition.elevation > Cenit.RISE_SET_ALTITUDE_DEG) {
        val mx = moonProjected.x.toFloat()
        val my = moonProjected.y.toFloat()
        val outerR = 10.5.dp.toPx()
        val innerR = 8.dp.toPx()
        drawCircle(color = VisorHudBg, radius = outerR, center = Offset(mx, my))
        val lit = buildMoonLitPath(mx, my, innerR, moonPhase, resources.moonLitScratch)
        if (lit != null) {
            drawPath(path = lit, color = VisorHudMoon)
        }
        drawCircle(
            color = VisorHudMoon,
            radius = outerR,
            center = Offset(mx, my),
            style = resources.moonDotStroke,
        )

        if (!pointingAtGround) {
            val lw = (resources.textFillPaint.measureText(strings.moonMarkerLabel) + resources.dp4).toDouble()
            val lh = (resources.textSizePx + resources.dp4).toDouble()
            val placed = resources.placer.tryPlaceInto(mx.toDouble(), (my + outerR + resources.dp12).toDouble(), lw, lh, resources.placedCenter, clampToBounds = true) ||
                resources.placer.tryPlaceInto(mx.toDouble(), (my - outerR - resources.dp12).toDouble(), lw, lh, resources.placedCenter, clampToBounds = true)
            if (placed) {
                drawCasedText(
                    text = strings.moonMarkerLabel,
                    cx = resources.placedCenter[0].toFloat(),
                    cy = resources.placedCenter[1].toFloat(),
                    color = VisorHudMoon,
                    rollDeg = labelRollDeg,
                    resources = resources,
                )
            }
        }
    }
}

private fun DrawScope.drawSunLayer(
    projector: ArProjector,
    rotation: DeviceRotation,
    paths: ArPaths,
    sunPosition: SkyPosition,
    sunProjected: io.github.ivan_olivera_dev.cenit.core.ar.ScreenPoint?,
    sunInSafeView: Boolean,
    pathAlpha: Float,
    pointingAtGround: Boolean,
    labelRollDeg: Float,
    strings: VisorStrings,
    resources: ArCanvasResources,
    w: Float,
    h: Float,
) {
    // Below-horizon dotted sun arc
    drawProjectedPathRuns(
        points = paths.sunPath,
        projector = projector,
        rotation = rotation,
        minElevationDeg = -90.0,
        maxElevationDeg = 0.2,
        coreColor = VisorHudMuted,
        casingStroke = resources.sunBelowCasing,
        coreStroke = resources.sunBelowCore,
        alpha = 0.72f * pathAlpha,
        resources = resources,
        w = w,
        h = h,
    )

    // Above-horizon solid sun arc
    drawProjectedPathRuns(
        points = paths.sunPath,
        projector = projector,
        rotation = rotation,
        minElevationDeg = -0.5,
        maxElevationDeg = 90.0,
        coreColor = VisorHudSun,
        casingStroke = resources.sunAboveCasing,
        coreStroke = resources.sunAboveCore,
        alpha = pathAlpha,
        resources = resources,
        w = w,
        h = h,
    )

    // Sunrise & sunset points on the horizon
    val riseSetR = 5.2.dp.toPx()
    val bounds = resources.placer.bounds
    fun drawHorizonLandmark(azimuthDeg: Double, pillLabel: String?) {
        val p = projector.project(rotation, azimuthDeg, 0.0) ?: return
        if (p.x < bounds.left || p.x > bounds.right || p.y < bounds.top || p.y > bounds.bottom) return
        val center = Offset(p.x.toFloat(), p.y.toFloat())
        drawCircle(color = VisorHudSun, radius = riseSetR, center = center, alpha = pathAlpha)
        drawCircle(color = VisorHudBg, radius = riseSetR, center = center, alpha = pathAlpha, style = resources.riseSetStroke)

        if (!pointingAtGround && pillLabel != null) {
            val pw = (resources.pillTextPaint.measureText(pillLabel) + resources.dp18).toDouble()
            val ph = resources.pillHeightPx.toDouble()
            val offsetDown = 22.dp.toPx().toDouble()
            val offsetUp = -22.dp.toPx().toDouble()
            val placed = resources.placer.tryPlaceInto(p.x, p.y + offsetDown, pw, ph, resources.placedCenter, clampToBounds = true) ||
                resources.placer.tryPlaceInto(p.x + pw * 0.45, p.y + offsetDown, pw, ph, resources.placedCenter, clampToBounds = true) ||
                resources.placer.tryPlaceInto(p.x - pw * 0.45, p.y + offsetDown, pw, ph, resources.placedCenter, clampToBounds = true) ||
                resources.placer.tryPlaceInto(p.x, p.y + offsetUp, pw, ph, resources.placedCenter, clampToBounds = true)
            if (placed) {
                drawHudPill(
                    cx = resources.placedCenter[0].toFloat(),
                    cy = resources.placedCenter[1].toFloat(),
                    width = pw.toFloat(),
                    height = ph.toFloat(),
                    text = pillLabel,
                    borderColor = VisorHudSun,
                    resources = resources,
                )
            }
        }
    }
    paths.sunrise?.let { drawHorizonLandmark(it.azimuthDeg, strings.sunrisePill) }
    paths.sunset?.let { drawHorizonLandmark(it.azimuthDeg, strings.sunsetPill) }

    // Whole-hour dots + non-overlapping hour labels
    val dotR = 4.2.dp.toPx()
    val hourMarkers = paths.hourMarkers
    for (i in hourMarkers.indices) {
        val marker = hourMarkers[i]
        if (marker.position.elevation <= 1.2) continue
        val p = projector.project(rotation, marker.position) ?: continue
        if (p.x < bounds.left || p.x > bounds.right || p.y < bounds.top || p.y > bounds.bottom) continue

        val center = Offset(p.x.toFloat(), p.y.toFloat())
        drawCircle(color = VisorHudBg, radius = dotR, center = center, alpha = pathAlpha)
        drawCircle(color = VisorHudSun, radius = dotR, center = center, alpha = pathAlpha, style = resources.sunDotStroke)

        if (!pointingAtGround && i < strings.hourLabels.size) {
            val lbl = strings.hourLabels[i]
            val lw = (resources.textFillPaint.measureText(lbl) + resources.dp4).toDouble()
            val lh = (resources.textSizePx + resources.dp4).toDouble()
            val offX = resources.dp14.toDouble()
            val offY = resources.dp12.toDouble()
            val placed = resources.placer.tryPlaceInto(p.x + offX, p.y - offY, lw, lh, resources.placedCenter) ||
                resources.placer.tryPlaceInto(p.x - offX, p.y - offY, lw, lh, resources.placedCenter) ||
                resources.placer.tryPlaceInto(p.x, p.y + offY + resources.dp4, lw, lh, resources.placedCenter)
            if (placed) {
                drawCasedText(
                    text = lbl,
                    cx = resources.placedCenter[0].toFloat(),
                    cy = resources.placedCenter[1].toFloat(),
                    color = VisorHudSun,
                    rollDeg = labelRollDeg,
                    resources = resources,
                )
            }
        }
    }

    // Selected-time sun marker (above horizon solid glow, below horizon dashed ring + label)
    if (sunProjected != null && sunInSafeView) {
        val sx = sunProjected.x.toFloat()
        val sy = sunProjected.y.toFloat()
        val center = Offset(sx, sy)
        val above = sunPosition.elevation > Cenit.RISE_SET_ALTITUDE_DEG
        if (above) {
            drawCircle(color = VisorHudSun, radius = resources.dp12, center = center, alpha = 0.22f)
            drawCircle(color = VisorHudBg, radius = resources.dp12, center = center, style = Stroke(width = resources.dp1))
            val discR = 7.5.dp.toPx()
            drawCircle(color = VisorHudSun, radius = discR, center = center)
            drawCircle(color = VisorHudBg, radius = discR, center = center, style = resources.sunMarkerStroke)
        } else {
            val belowR = 6.5.dp.toPx()
            drawCircle(color = VisorHudBg, radius = belowR, center = center)
            drawCircle(color = VisorHudSun, radius = belowR, center = center, style = resources.sunMarkerBelowStroke)
        }

        if (!pointingAtGround) {
            val markerLabel = if (above) strings.sunMarkerTime else strings.sunMarkerBelow
            val lw = (resources.textFillPaint.measureText(markerLabel) + resources.dp4).toDouble()
            val lh = (resources.textSizePx + resources.dp4).toDouble()
            val placed = resources.placer.tryPlaceInto(sx.toDouble(), (sy - resources.dp18).toDouble(), lw, lh, resources.placedCenter, clampToBounds = true) ||
                resources.placer.tryPlaceInto(sx.toDouble() - lw / 2.0 - resources.dp14, sy.toDouble(), lw, lh, resources.placedCenter, clampToBounds = true) ||
                resources.placer.tryPlaceInto(sx.toDouble(), (sy + resources.dp18).toDouble(), lw, lh, resources.placedCenter, clampToBounds = true)
            if (placed) {
                drawCasedText(
                    text = markerLabel,
                    cx = resources.placedCenter[0].toFloat(),
                    cy = resources.placedCenter[1].toFloat(),
                    color = VisorHudSun,
                    rollDeg = labelRollDeg,
                    resources = resources,
                )
            }
        }
    }
}

private fun DrawScope.drawCrosshairAndPills(
    cx: Float,
    cy: Float,
    crossR: Float,
    crossOuter: Float,
    hitColor: Color,
    aimText: String,
    aimPillW: Float,
    aimPillCy: Float,
    crossingText: String?,
    crossingPillW: Float,
    crossingPillCy: Float,
    resources: ArCanvasResources,
) {
    val tickInner = crossR - resources.dp2
    val casingW = resources.crosshairCasing.width
    val coreW = resources.crosshairCore.width

    // Dark casing underneath
    drawCircle(color = VisorHudBg, radius = crossR, center = Offset(cx, cy), style = resources.crosshairCasing)
    drawLine(VisorHudBg, Offset(cx - crossOuter, cy), Offset(cx - tickInner, cy), casingW, StrokeCap.Round)
    drawLine(VisorHudBg, Offset(cx + tickInner, cy), Offset(cx + crossOuter, cy), casingW, StrokeCap.Round)
    drawLine(VisorHudBg, Offset(cx, cy - crossOuter), Offset(cx, cy - tickInner), casingW, StrokeCap.Round)
    drawLine(VisorHudBg, Offset(cx, cy + tickInner), Offset(cx, cy + crossOuter), casingW, StrokeCap.Round)

    // Accent crosshair on top
    drawCircle(color = hitColor, radius = crossR, center = Offset(cx, cy), style = resources.crosshairCore)
    drawLine(hitColor, Offset(cx - crossOuter, cy), Offset(cx - tickInner, cy), coreW, StrokeCap.Round)
    drawLine(hitColor, Offset(cx + tickInner, cy), Offset(cx + crossOuter, cy), coreW, StrokeCap.Round)
    drawLine(hitColor, Offset(cx, cy - crossOuter), Offset(cx, cy - tickInner), coreW, StrokeCap.Round)
    drawLine(hitColor, Offset(cx, cy + tickInner), Offset(cx, cy + crossOuter), coreW, StrokeCap.Round)

    // Center dot with dark casing
    val dotR = 2.4.dp.toPx()
    drawCircle(color = VisorHudBg, radius = dotR + resources.dp1, center = Offset(cx, cy))
    drawCircle(color = hitColor, radius = dotR, center = Offset(cx, cy))

    // Aim azimuth/elevation pill above crosshair
    drawHudPill(
        cx = cx,
        cy = aimPillCy,
        width = aimPillW,
        height = resources.pillHeightPx,
        text = aimText,
        borderColor = VisorHudBorder,
        resources = resources,
    )

    // Crossing readout pill below crosshair (only when crosshair is within tolerance of a path)
    if (crossingText != null && crossingPillW > 0f) {
        drawHudPill(
            cx = cx,
            cy = crossingPillCy,
            width = crossingPillW,
            height = resources.pillHeightPx,
            text = crossingText,
            borderColor = hitColor,
            resources = resources,
        )
    }
}

private fun DrawScope.drawEdgeArrowPill(
    projector: ArProjector,
    rotation: DeviceRotation,
    position: SkyPosition,
    label: String,
    accent: Color,
    isSun: Boolean,
    moonPhase: MoonPhase,
    resources: ArCanvasResources,
) {
    val arrow = projector.edgeArrow(rotation, position, insetPx = resources.dp14.toDouble()) ?: return
    val bounds = resources.placer.bounds
    val pillH = resources.edgePillHeightPx.toDouble()
    val textW = resources.pillTextPaint.measureText(label)
    // Left section holds arrow (16 dp) + body glyph (14 dp) + spacing
    val leadingW = 36.dp.toPx()
    val pillW = (leadingW + textW + resources.dp12).toDouble()
        .coerceAtMost((bounds.width - resources.dp8).coerceAtLeast(80.0))

    val targetCx = arrow.x.coerceIn(bounds.left + pillW / 2.0, bounds.right - pillW / 2.0)
    val targetCy = arrow.y.coerceIn(bounds.top + pillH / 2.0, bounds.bottom - pillH / 2.0)

    val placed = resources.placer.tryPlaceInto(targetCx, targetCy, pillW, pillH, resources.placedCenter, clampToBounds = true) ||
        resources.placer.tryPlaceInto(targetCx, targetCy + pillH + resources.dp8, pillW, pillH, resources.placedCenter, clampToBounds = true) ||
        resources.placer.tryPlaceInto(targetCx, targetCy - pillH - resources.dp8, pillW, pillH, resources.placedCenter, clampToBounds = true) ||
        resources.placer.tryPlaceInto(targetCx, bounds.bottom - pillH / 2.0, pillW, pillH, resources.placedCenter, clampToBounds = true) ||
        resources.placer.tryPlaceInto(targetCx, bounds.top + pillH / 2.0, pillW, pillH, resources.placedCenter, clampToBounds = true)
    if (!placed) return

    val cx = resources.placedCenter[0].toFloat()
    val cy = resources.placedCenter[1].toFloat()
    val halfW = (pillW / 2.0).toFloat()
    val halfH = (pillH / 2.0).toFloat()
    val left = cx - halfW
    val top = cy - halfH
    val radius = halfH

    val nc = drawContext.canvas.nativeCanvas
    resources.pillRectScratch.set(left, top, left + pillW.toFloat(), top + pillH.toFloat())
    resources.pillBgPaint.color = VisorHudScrimStrong.toArgb()
    resources.pillStrokePaint.color = accent.toArgb()
    nc.drawRoundRect(resources.pillRectScratch, radius, radius, resources.pillBgPaint)
    nc.drawRoundRect(resources.pillRectScratch, radius, radius, resources.pillStrokePaint)

    // Rotated directional arrow pointing toward the off-screen body
    val arrowCx = left + 13.dp.toPx()
    val angleDeg = Math.toDegrees(arrow.angleRad).toFloat()
    val arrowLen = 4.5.dp.toPx()
    val headLen = 3.2.dp.toPx()
    val arrowRad = Math.toRadians(angleDeg.toDouble())
    val cosA = cos(arrowRad).toFloat()
    val sinA = sin(arrowRad).toFloat()
    val tail = Offset(arrowCx - cosA * arrowLen, cy - sinA * arrowLen)
    val tip = Offset(arrowCx + cosA * arrowLen, cy + sinA * arrowLen)
    val wing1 = Offset(
        tip.x - cosA * headLen - sinA * headLen,
        tip.y - sinA * headLen + cosA * headLen,
    )
    val wing2 = Offset(
        tip.x - cosA * headLen + sinA * headLen,
        tip.y - sinA * headLen - cosA * headLen,
    )
    val arrowStroke = 1.7.dp.toPx()
    drawLine(accent, tail, tip, arrowStroke, StrokeCap.Round)
    drawLine(accent, tip, wing1, arrowStroke, StrokeCap.Round)
    drawLine(accent, tip, wing2, arrowStroke, StrokeCap.Round)

    // Body glyph (sun disc or moon phase crescent)
    val glyphCx = left + 26.dp.toPx()
    val glyphR = 4.5.dp.toPx()
    if (isSun) {
        drawCircle(color = accent, radius = glyphR, center = Offset(glyphCx, cy))
    } else {
        drawCircle(color = VisorHudSubtle, radius = glyphR, center = Offset(glyphCx, cy))
        val lit = buildMoonLitPath(glyphCx, cy, glyphR - resources.dp1 * 0.5f, moonPhase, resources.moonLitScratch)
        if (lit != null) drawPath(path = lit, color = accent)
        drawCircle(color = accent, radius = glyphR, center = Offset(glyphCx, cy), style = Stroke(width = resources.dp1))
    }

    // Azimuth label
    val fm = resources.pillTextPaint.fontMetrics
    val baselineY = cy - (fm.ascent + fm.descent) / 2f
    resources.pillTextPaint.textAlign = Paint.Align.LEFT
    nc.drawText(label, left + leadingW, baselineY, resources.pillTextPaint)
    resources.pillTextPaint.textAlign = Paint.Align.CENTER
}

private fun DrawScope.drawProjectedPathRuns(
    points: List<PathPoint>,
    projector: ArProjector,
    rotation: DeviceRotation,
    minElevationDeg: Double,
    maxElevationDeg: Double,
    coreColor: Color,
    casingStroke: Stroke,
    coreStroke: Stroke,
    alpha: Float,
    resources: ArCanvasResources,
    w: Float,
    h: Float,
) {
    if (points.size < 2) return
    val path = resources.pathScratch
    val margin = resources.dp80
    val maxJump = w * 0.65f

    var hasPrev = false
    var prevX = 0f
    var prevY = 0f
    var runCount = 0
    path.rewind()

    fun flush() {
        if (runCount >= 2) {
            drawPath(path = path, color = VisorHudBg, alpha = alpha, style = casingStroke)
            drawPath(path = path, color = coreColor, alpha = alpha, style = coreStroke)
        }
        path.rewind()
        runCount = 0
        hasPrev = false
    }

    for (i in points.indices) {
        val pos = points[i].position
        if (pos.elevation < minElevationDeg || pos.elevation > maxElevationDeg) {
            if (runCount > 0) flush()
            continue
        }
        val pt = projector.project(rotation, pos)
        if (pt != null && pt.x > -margin && pt.x < w + margin && pt.y > -margin && pt.y < h + margin) {
            val px = pt.x.toFloat()
            val py = pt.y.toFloat()
            if (hasPrev && hypot(px - prevX, py - prevY) > maxJump) {
                flush()
            }
            if (runCount == 0) path.moveTo(px, py) else path.lineTo(px, py)
            prevX = px
            prevY = py
            hasPrev = true
            runCount++
        } else if (runCount > 0) {
            flush()
        }
    }
    flush()
}

private fun DrawScope.drawCasedText(
    text: String,
    cx: Float,
    cy: Float,
    color: Color,
    rollDeg: Float,
    resources: ArCanvasResources,
) {
    val nc = drawContext.canvas.nativeCanvas
    val fill = resources.textFillPaint
    val casing = resources.textCasingPaint
    fill.color = color.toArgb()
    val fm = fill.fontMetrics
    val baselineY = cy - (fm.ascent + fm.descent) / 2f

    if (rollDeg != 0f) {
        nc.save()
        nc.rotate(rollDeg, cx, cy)
        nc.drawText(text, cx, baselineY, casing)
        nc.drawText(text, cx, baselineY, fill)
        nc.restore()
    } else {
        nc.drawText(text, cx, baselineY, casing)
        nc.drawText(text, cx, baselineY, fill)
    }
}

private fun DrawScope.drawHudPill(
    cx: Float,
    cy: Float,
    width: Float,
    height: Float,
    text: String,
    borderColor: Color,
    resources: ArCanvasResources,
) {
    val nc = drawContext.canvas.nativeCanvas
    val halfW = width / 2f
    val halfH = height / 2f
    resources.pillRectScratch.set(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
    resources.pillBgPaint.color = VisorHudBg.copy(alpha = 0.90f).toArgb()
    resources.pillStrokePaint.color = borderColor.toArgb()
    nc.drawRoundRect(resources.pillRectScratch, halfH, halfH, resources.pillBgPaint)
    nc.drawRoundRect(resources.pillRectScratch, halfH, halfH, resources.pillStrokePaint)

    val paint = resources.pillTextPaint
    val maxTextW = (width - resources.dp12).coerceAtLeast(1f)
    val measuredW = paint.measureText(text)
    val fm = paint.fontMetrics
    val baselineY = cy - (fm.ascent + fm.descent) / 2f
    if (measuredW > maxTextW) {
        paint.textScaleX = (maxTextW / measuredW).coerceIn(0.7f, 1f)
        nc.drawText(text, cx, baselineY, paint)
        paint.textScaleX = 1f
    } else {
        nc.drawText(text, cx, baselineY, paint)
    }
}

private fun skyPhaseLabelRes(phase: SkyPhase): Int = when (phase) {
    SkyPhase.DAY -> R.string.phase_day
    SkyPhase.GOLDEN -> R.string.phase_golden
    SkyPhase.BLUE -> R.string.phase_blue
    SkyPhase.NAUTICAL -> R.string.phase_nautical
    SkyPhase.ASTRONOMICAL -> R.string.phase_astronomical
    SkyPhase.NIGHT -> R.string.phase_night
}

private fun moonPhaseNameRes(name: MoonPhaseName): Int = when (name) {
    MoonPhaseName.NEW -> R.string.moon_phase_new
    MoonPhaseName.WAXING_CRESCENT -> R.string.moon_phase_waxing_crescent
    MoonPhaseName.FIRST_QUARTER -> R.string.moon_phase_first_quarter
    MoonPhaseName.WAXING_GIBBOUS -> R.string.moon_phase_waxing_gibbous
    MoonPhaseName.FULL -> R.string.moon_phase_full
    MoonPhaseName.WANING_GIBBOUS -> R.string.moon_phase_waning_gibbous
    MoonPhaseName.LAST_QUARTER -> R.string.moon_phase_last_quarter
    MoonPhaseName.WANING_CRESCENT -> R.string.moon_phase_waning_crescent
}

private val FALLBACK_POINTING_PROJECTOR = ArProjector(
    fov = CameraFov(
        horizontalDeg = CameraFovCalculator.DEFAULT_HORIZONTAL_FOV_DEG,
        verticalDeg = CameraFovCalculator.DEFAULT_VERTICAL_FOV_DEG,
    ),
    widthPx = 1080.0,
    heightPx = 2400.0,
)
