package io.github.ivan_olivera_dev.cenit.ui.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.Settings
import android.view.Surface
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.ivan_olivera_dev.cenit.R
import io.github.ivan_olivera_dev.cenit.ui.components.CenitIcon
import io.github.ivan_olivera_dev.cenit.ui.components.CenitIconKind
import io.github.ivan_olivera_dev.cenit.ui.components.cenitFocusRing
import io.github.ivan_olivera_dev.cenit.ui.planner.PlannerState
import io.github.ivan_olivera_dev.cenit.ui.theme.LocalCenitPalette
import kotlin.math.roundToInt

// HUD dark palette tokens from design/system.md (used over live camera preview for guaranteed contrast).
private val HudBg = Color(0xFF111318)
private val HudScrim = Color(0xD6111318) // rgba(17, 19, 24, 0.84)
private val HudFg = Color(0xFFE9E7E2)
private val HudBorder = Color(0xFF6C7382)

private data class CameraOpticalMetrics(
    val focalLengthMm: Double = CameraFovCalculator.DEFAULT_FOCAL_LENGTH_MM,
    val sensorWidthMm: Double = CameraFovCalculator.DEFAULT_SENSOR_WIDTH_MM,
    val sensorHeightMm: Double = CameraFovCalculator.DEFAULT_SENSOR_HEIGHT_MM,
    val sensorOrientationDeg: Int = 90,
    val displayRotationDeg: Int = 0,
    val activeArrayWidthPx: Int? = null,
    val activeArrayHeightPx: Int? = null,
    val pixelArrayWidthPx: Int? = null,
    val pixelArrayHeightPx: Int? = null,
    val streamAspect: Double? = null,
)

/**
 * Full-screen edge-to-edge CameraX viewfinder scaffold (L5 contract).
 *
 * Requests `CAMERA` permission only on entering this screen, keeps the screen on while visible,
 * registers `TYPE_ROTATION_VECTOR` at `SENSOR_DELAY_GAME` only while resumed, computes exact
 * visible FOV after `FILL_CENTER` cropping, renders [overlay] over the live preview, and handles
 * permission/hardware/error states per artboard `01-visor.html`.
 */
@Composable
fun CameraScreen(
    state: PlannerState,
    onBack: () -> Unit,
    overlay: @Composable (ArInputs) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val palette = LocalCenitPalette.current

    // Keep the screen awake only while this camera screen is visible.
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = previous
        }
    }

    var uiState by remember(context) {
        mutableStateOf(
            CameraScreenStateMachine.initialState(
                hasCamera = hasBackCamera(context),
                hasRotationSensor = hasRotationVectorSensor(context),
                hasPermission = hasCameraPermission(context),
                shouldShowRationale = shouldShowCameraRationale(context),
            ),
        )
    }
    var permissionRequestLaunched by rememberSaveable { mutableStateOf(false) }
    var bindAttempt by remember { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionRequestLaunched = false
        uiState = CameraScreenStateMachine.onPermissionResult(
            granted = granted || hasCameraPermission(context),
            shouldShowRationale = shouldShowCameraRationale(context),
            hasCamera = hasBackCamera(context),
            hasRotationSensor = hasRotationVectorSensor(context),
        )
    }

    // Launch the runtime permission prompt only on entering (or after user taps Allow).
    LaunchedEffect(uiState) {
        if (uiState == CameraScreenUiState.RequestingPermission && !permissionRequestLaunched) {
            permissionRequestLaunched = true
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Re-evaluate permission & hardware on resume (e.g. returning from Android App Settings).
    val currentUiState by rememberUpdatedState(uiState)
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                uiState = CameraScreenStateMachine.onLifecycleResume(
                    current = currentUiState,
                    hasCamera = hasBackCamera(context),
                    hasRotationSensor = hasRotationVectorSensor(context),
                    hasPermission = hasCameraPermission(context),
                    shouldShowRationale = shouldShowCameraRationale(context),
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isLive = uiState == CameraScreenUiState.Ready
    val rotationSample = rememberArRotationState(enabled = isLive)

    LaunchedEffect(rotationSample.sensorAvailable, isLive) {
        if (isLive && !rotationSample.sensorAvailable) {
            uiState = CameraScreenUiState.NoSensor
        }
    }

    // Keep status bar icons light over the dark live viewfinder even in light theme.
    DisposableEffect(isLive, palette.isDark, view) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (controller != null && isLive) {
            controller.isAppearanceLightStatusBars = false
        }
        onDispose {
            if (controller != null) {
                controller.isAppearanceLightStatusBars = !palette.isDark
            }
        }
    }

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }
    var opticalMetrics by remember { mutableStateOf(CameraOpticalMetrics()) }

    val fov = remember(opticalMetrics, viewportSize) {
        CameraFovCalculator.computeVisibleFov(
            focalLengthMm = opticalMetrics.focalLengthMm,
            sensorWidthMm = opticalMetrics.sensorWidthMm,
            sensorHeightMm = opticalMetrics.sensorHeightMm,
            viewportWidthPx = viewportSize.width,
            viewportHeightPx = viewportSize.height,
            sensorOrientationDeg = opticalMetrics.sensorOrientationDeg,
            displayRotationDeg = opticalMetrics.displayRotationDeg,
            streamAspect = opticalMetrics.streamAspect,
            activeArrayWidthPx = opticalMetrics.activeArrayWidthPx,
            activeArrayHeightPx = opticalMetrics.activeArrayHeightPx,
            pixelArrayWidthPx = opticalMetrics.pixelArrayWidthPx,
            pixelArrayHeightPx = opticalMetrics.pixelArrayHeightPx,
        )
    }

    val arInputs = remember(rotationSample, fov, viewportSize) {
        ArInputs(
            rotation = rotationSample.rotation,
            accuracy = rotationSample.accuracy,
            sensorAvailable = rotationSample.sensorAvailable,
            horizontalFovDeg = fov.horizontalFovDeg,
            verticalFovDeg = fov.verticalFovDeg,
            previewWidthPx = viewportSize.width,
            previewHeightPx = viewportSize.height,
        )
    }

    val screenDesc = stringResource(R.string.camera_cd_screen)
    val backDesc = stringResource(R.string.camera_cd_back)

    // Keep `state` referenced in the L5 scaffold contract for T5 integration.
    @Suppress("UNUSED_EXPRESSION")
    state

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isLive) HudBg else palette.bgColor)
            .semantics { contentDescription = screenDesc },
    ) {
        when (val current = uiState) {
            CameraScreenUiState.Ready -> {
                CameraPreviewSurface(
                    bindAttempt = bindAttempt,
                    onMetricsUpdated = { opticalMetrics = it },
                    onNoCamera = { uiState = CameraScreenUiState.NoCamera },
                    onError = { detail ->
                        uiState = CameraScreenStateMachine.onCameraError(uiState, detail)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { viewportSize = it },
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    overlay(arInputs)
                }

                // Top-left 44 dp HUD back button over the live viewfinder
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .statusBarsPadding()
                        .padding(start = 12.dp, top = 8.dp)
                        .size(44.dp)
                        .cenitFocusRing(palette.focusRing, cornerRadius = 22.dp)
                        .clip(CircleShape)
                        .background(HudScrim)
                        .border(1.dp, HudBorder, CircleShape)
                        .clickable(role = Role.Button, onClick = onBack)
                        .semantics { contentDescription = backDesc },
                    contentAlignment = Alignment.Center,
                ) {
                    CenitIcon(kind = CenitIconKind.BACK, tint = HudFg, size = 20.dp)
                }
            }

            CameraScreenUiState.RequestingPermission -> {
                CameraStateScreen(
                    iconKind = CameraStateIcon.CAMERA_OFF,
                    title = stringResource(R.string.camera_rationale_title),
                    body = stringResource(R.string.camera_rationale_body),
                    primaryLabel = null,
                    onPrimaryClick = null,
                    secondaryLabel = stringResource(R.string.camera_action_use_compass),
                    onSecondaryClick = onBack,
                    onBack = onBack,
                )
            }

            CameraScreenUiState.PermissionRationale -> {
                CameraStateScreen(
                    iconKind = CameraStateIcon.CAMERA_OFF,
                    title = stringResource(R.string.camera_rationale_title),
                    body = stringResource(R.string.camera_rationale_body),
                    primaryLabel = stringResource(R.string.camera_action_allow),
                    onPrimaryClick = {
                        uiState = CameraScreenStateMachine.onRequestPermissionClicked(uiState)
                    },
                    secondaryLabel = stringResource(R.string.camera_action_use_compass),
                    onSecondaryClick = onBack,
                    onBack = onBack,
                )
            }

            CameraScreenUiState.PermissionDenied -> {
                CameraStateScreen(
                    iconKind = CameraStateIcon.CAMERA_OFF,
                    title = stringResource(R.string.camera_denied_title),
                    body = stringResource(R.string.camera_denied_body),
                    primaryLabel = stringResource(R.string.camera_action_allow),
                    onPrimaryClick = {
                        uiState = CameraScreenStateMachine.onRequestPermissionClicked(uiState)
                    },
                    secondaryLabel = stringResource(R.string.camera_action_open_settings),
                    onSecondaryClick = { openAppSettings(context) },
                    tertiaryLabel = stringResource(R.string.camera_action_use_compass),
                    onTertiaryClick = onBack,
                    onBack = onBack,
                )
            }

            CameraScreenUiState.PermissionPermanentlyDenied -> {
                CameraStateScreen(
                    iconKind = CameraStateIcon.CAMERA_OFF,
                    title = stringResource(R.string.camera_denied_title),
                    body = stringResource(R.string.camera_permanently_denied_body),
                    primaryLabel = stringResource(R.string.camera_action_open_settings),
                    onPrimaryClick = { openAppSettings(context) },
                    secondaryLabel = stringResource(R.string.camera_action_use_compass),
                    onSecondaryClick = onBack,
                    onBack = onBack,
                )
            }

            CameraScreenUiState.NoCamera -> {
                CameraStateScreen(
                    iconKind = CameraStateIcon.CAMERA_OFF,
                    title = stringResource(R.string.camera_no_camera_title),
                    body = stringResource(R.string.camera_no_camera_body),
                    primaryLabel = null,
                    onPrimaryClick = null,
                    secondaryLabel = stringResource(R.string.camera_action_use_compass),
                    onSecondaryClick = onBack,
                    onBack = onBack,
                )
            }

            CameraScreenUiState.NoSensor -> {
                CameraStateScreen(
                    iconKind = CameraStateIcon.SENSOR_OFF,
                    title = stringResource(R.string.camera_no_sensor_title),
                    body = stringResource(R.string.camera_no_sensor_body),
                    primaryLabel = null,
                    onPrimaryClick = null,
                    secondaryLabel = stringResource(R.string.camera_action_use_compass),
                    onSecondaryClick = onBack,
                    onBack = onBack,
                )
            }

            is CameraScreenUiState.CameraError -> {
                val baseBody = stringResource(R.string.camera_error_body)
                val body = if (!current.detail.isNullOrBlank()) {
                    "$baseBody (${current.detail})"
                } else {
                    baseBody
                }
                CameraStateScreen(
                    iconKind = CameraStateIcon.CAMERA_OFF,
                    title = stringResource(R.string.camera_error_title),
                    body = body,
                    primaryLabel = stringResource(R.string.camera_action_retry),
                    onPrimaryClick = {
                        bindAttempt += 1
                        uiState = CameraScreenStateMachine.onRetryCamera(
                            current = uiState,
                            hasPermission = hasCameraPermission(context),
                        )
                    },
                    secondaryLabel = stringResource(R.string.camera_action_use_compass),
                    onSecondaryClick = onBack,
                    onBack = onBack,
                )
            }
        }
    }
}

/**
 * Minimal debug overlay displaying live back-camera pointing heading, pitch, and computed FOV
 * derived from [ArInputs]. Replaced by the full 01 · Visor AR overlay in T5.
 */
@Composable
fun CameraDebugOverlay(
    arInputs: ArInputs,
    modifier: Modifier = Modifier,
) {
    val pointing = remember(arInputs.rotation) {
        cameraPointingFromRotation(arInputs.rotation)
    }
    val headingDeg = (pointing.azimuthDeg.roundToInt() % 360 + 360) % 360
    val pitchDeg = pointing.elevationDeg.roundToInt()
    val readout = stringResource(
        R.string.camera_debug_readout,
        headingDeg,
        pitchDeg,
        arInputs.horizontalFovDeg,
        arInputs.verticalFovDeg,
    )

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(HudScrim)
                .border(1.dp, HudBorder, RoundedCornerShape(999.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                text = readout,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                color = HudFg,
                maxLines = 1,
            )
        }
    }
}

@OptIn(ExperimentalCamera2Interop::class)
@Composable
private fun CameraPreviewSurface(
    bindAttempt: Int,
    onMetricsUpdated: (CameraOpticalMetrics) -> Unit,
    onNoCamera: () -> Unit,
    onError: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnMetricsUpdated by rememberUpdatedState(onMetricsUpdated)
    val currentOnNoCamera by rememberUpdatedState(onNoCamera)
    val currentOnError by rememberUpdatedState(onError)

    val previewView = remember(context) {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
            keepScreenOn = true
        }
    }

    DisposableEffect(context, lifecycleOwner, previewView, bindAttempt) {
        var disposed = false
        var boundProvider: ProcessCameraProvider? = null
        var boundPreview: Preview? = null
        var boundCharacteristics: CameraOpticalMetrics? = null

        val layoutListener = android.view.View.OnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
            val base = boundCharacteristics ?: return@OnLayoutChangeListener
            val updated = applyStreamAndDisplay(base, boundPreview, v.display?.rotation)
            currentOnMetricsUpdated(updated)
        }
        previewView.addOnLayoutChangeListener(layoutListener)

        val providerFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        providerFuture.addListener(
            {
                if (disposed) return@addListener
                runCatching {
                    val provider = providerFuture.get()
                    if (disposed) return@runCatching
                    boundProvider = provider

                    if (!provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                        currentOnNoCamera()
                        return@runCatching
                    }

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    boundPreview = preview

                    provider.unbindAll()
                    val camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                    )

                    val camera2Info = Camera2CameraInfo.from(camera.cameraInfo)
                    val focalLengths = camera2Info.getCameraCharacteristic(
                        CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS,
                    )
                    val physicalSize = camera2Info.getCameraCharacteristic(
                        CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE,
                    )
                    val activeArray = camera2Info.getCameraCharacteristic(
                        CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE,
                    )
                    val pixelArray = camera2Info.getCameraCharacteristic(
                        CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE,
                    )
                    val sensorOrientation = camera2Info.getCameraCharacteristic(
                        CameraCharacteristics.SENSOR_ORIENTATION,
                    ) ?: 90

                    val baseMetrics = CameraOpticalMetrics(
                        focalLengthMm = focalLengths?.firstOrNull()?.toDouble()
                            ?: CameraFovCalculator.DEFAULT_FOCAL_LENGTH_MM,
                        sensorWidthMm = physicalSize?.width?.toDouble()
                            ?: CameraFovCalculator.DEFAULT_SENSOR_WIDTH_MM,
                        sensorHeightMm = physicalSize?.height?.toDouble()
                            ?: CameraFovCalculator.DEFAULT_SENSOR_HEIGHT_MM,
                        sensorOrientationDeg = sensorOrientation,
                        activeArrayWidthPx = activeArray?.width(),
                        activeArrayHeightPx = activeArray?.height(),
                        pixelArrayWidthPx = pixelArray?.width,
                        pixelArrayHeightPx = pixelArray?.height,
                    )
                    boundCharacteristics = baseMetrics
                    currentOnMetricsUpdated(
                        applyStreamAndDisplay(baseMetrics, preview, previewView.display?.rotation),
                    )
                }.onFailure { throwable ->
                    if (!disposed) {
                        currentOnError(throwable.localizedMessage)
                    }
                }
            },
            executor,
        )

        onDispose {
            disposed = true
            previewView.removeOnLayoutChangeListener(layoutListener)
            boundProvider?.unbindAll()
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )
}

private fun applyStreamAndDisplay(
    base: CameraOpticalMetrics,
    preview: Preview?,
    displaySurfaceRotation: Int?,
): CameraOpticalMetrics {
    val resInfo = preview?.resolutionInfo
    return if (resInfo != null && resInfo.resolution.width > 0 && resInfo.resolution.height > 0) {
        base.copy(
            streamAspect = resInfo.resolution.width.toDouble() / resInfo.resolution.height.toDouble(),
            sensorOrientationDeg = resInfo.rotationDegrees,
            displayRotationDeg = 0,
        )
    } else {
        base.copy(
            displayRotationDeg = surfaceRotationToDegrees(displaySurfaceRotation),
        )
    }
}

private fun surfaceRotationToDegrees(rotation: Int?): Int = when (rotation) {
    Surface.ROTATION_90 -> 90
    Surface.ROTATION_180 -> 180
    Surface.ROTATION_270 -> 270
    else -> 0
}

private enum class CameraStateIcon {
    CAMERA_OFF,
    SENSOR_OFF,
}

@Composable
private fun CameraStateScreen(
    iconKind: CameraStateIcon,
    title: String,
    body: String,
    primaryLabel: String?,
    onPrimaryClick: (() -> Unit)?,
    secondaryLabel: String,
    onSecondaryClick: () -> Unit,
    onBack: () -> Unit,
    tertiaryLabel: String? = null,
    onTertiaryClick: (() -> Unit)? = null,
) {
    val palette = LocalCenitPalette.current
    val backDesc = stringResource(R.string.camera_cd_back)

    Box(
        modifier = Modifier
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
            // Top-left 44 dp back button
            Box(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .size(44.dp)
                    .cenitFocusRing(palette.focusRing, cornerRadius = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button, onClick = onBack)
                    .semantics { contentDescription = backDesc },
                contentAlignment = Alignment.Center,
            ) {
                CenitIcon(kind = CenitIconKind.BACK, tint = palette.textPrimary, size = 22.dp)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(bottom = 44.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                // 48 dp icon ring matching artboard mini-icon-ring
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(palette.surfaceColor)
                        .border(1.dp, palette.controlBorder, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    CameraStateGlyph(kind = iconKind, tint = palette.sun)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge.copy(fontFamily = FontFamily.Serif),
                    color = palette.textPrimary,
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                )

                Spacer(modifier = Modifier.height(24.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (primaryLabel != null && onPrimaryClick != null) {
                        val primaryShape = RoundedCornerShape(16.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 54.dp)
                                .cenitFocusRing(palette.focusRing, cornerRadius = 16.dp)
                                .clip(primaryShape)
                                .background(palette.textPrimary)
                                .clickable(role = Role.Button, onClick = onPrimaryClick)
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = primaryLabel,
                                style = MaterialTheme.typography.bodyLarge,
                                color = palette.bgColor,
                            )
                        }
                    }

                    val secondaryShape = RoundedCornerShape(14.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp)
                            .cenitFocusRing(palette.focusRing, cornerRadius = 14.dp)
                            .clip(secondaryShape)
                            .border(1.dp, palette.controlBorder, secondaryShape)
                            .clickable(role = Role.Button, onClick = onSecondaryClick)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = secondaryLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textPrimary,
                        )
                    }

                    if (tertiaryLabel != null && onTertiaryClick != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .cenitFocusRing(palette.focusRing, cornerRadius = 14.dp)
                                .clip(secondaryShape)
                                .clickable(role = Role.Button, onClick = onTertiaryClick)
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = tertiaryLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraStateGlyph(
    kind: CameraStateIcon,
    tint: Color,
) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val sx = size.width / 24f
        val sy = size.height / 24f
        val stroke = Stroke(
            width = 1.5f * sx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        when (kind) {
            CameraStateIcon.CAMERA_OFF -> {
                // Matches ICON.camOff in design/cenit-stage-2/01-visor.html
                drawLine(tint, Offset(3f * sx, 3f * sy), Offset(21f * sx, 21f * sy), stroke.width, StrokeCap.Round)
                val topRight = Path().apply {
                    moveTo(9f * sx, 5f * sy)
                    lineTo(14f * sx, 5f * sy)
                    lineTo(15.5f * sx, 7f * sy)
                    lineTo(19f * sx, 7f * sy)
                    lineTo(21f * sx, 9f * sy)
                    lineTo(21f * sx, 16.5f * sy)
                }
                drawPath(topRight, tint, style = stroke)
                val bottomLeft = Path().apply {
                    moveTo(17.5f * sx, 18.5f * sy)
                    lineTo(5f * sx, 18.5f * sy)
                    lineTo(3f * sx, 16.5f * sy)
                    lineTo(3f * sx, 9f * sy)
                    lineTo(5f * sx, 7f * sy)
                    lineTo(6.5f * sx, 7f * sy)
                }
                drawPath(bottomLeft, tint, style = stroke)
                drawCircle(tint, radius = 2.8f * sx, center = Offset(12f * sx, 13f * sy), style = stroke)
            }

            CameraStateIcon.SENSOR_OFF -> {
                // Matches ICON.sensorOff in design/cenit-stage-2/01-visor.html
                drawCircle(tint, radius = 9f * sx, center = Offset(12f * sx, 12f * sy), style = stroke)
                drawLine(tint, Offset(4.5f * sx, 4.5f * sy), Offset(19.5f * sx, 19.5f * sy), stroke.width, StrokeCap.Round)
                val needle = Path().apply {
                    moveTo(14.5f * sx, 9.5f * sy)
                    lineTo(13f * sx, 13.5f * sy)
                    lineTo(9.5f * sx, 15f * sy)
                }
                drawPath(needle, tint, style = stroke)
            }
        }
    }
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private fun shouldShowCameraRationale(context: Context): Boolean {
    val activity = context.findActivity() ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
}

private fun hasBackCamera(context: Context): Boolean {
    val pm = context.packageManager
    val hasFeature = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) ||
        pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)
    if (!hasFeature) return false
    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        ?: return true
    return runCatching {
        cameraManager.cameraIdList.any { id ->
            val facing = cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING)
            facing == CameraCharacteristics.LENS_FACING_BACK
        }
    }.getOrDefault(false)
}

private fun openAppSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
