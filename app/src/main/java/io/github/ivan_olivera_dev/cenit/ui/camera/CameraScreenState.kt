package io.github.ivan_olivera_dev.cenit.ui.camera

/**
 * Explicit UI state for [CameraScreen] covering hardware availability, runtime permission flow,
 * and live preview lifecycle.
 */
sealed interface CameraScreenUiState {
    /** Waiting for the Android runtime permission dialog launched on entering the camera screen. */
    data object RequestingPermission : CameraScreenUiState

    /** User previously denied camera once; explain live-only privacy before asking again. */
    data object PermissionRationale : CameraScreenUiState

    /** User denied the permission request just now; can retry or open system settings. */
    data object PermissionDenied : CameraScreenUiState

    /** Permission permanently denied ("Don't ask again"); must be enabled in Android settings. */
    data object PermissionPermanentlyDenied : CameraScreenUiState

    /** Device has no usable back camera hardware. */
    data object NoCamera : CameraScreenUiState

    /** Device has no [android.hardware.Sensor.TYPE_ROTATION_VECTOR] sensor. */
    data object NoSensor : CameraScreenUiState

    /** CameraX failed to initialize or bind the preview use case. */
    data class CameraError(val detail: String? = null) : CameraScreenUiState

    /** Camera permission granted, hardware ready, live preview and overlay active. */
    data object Ready : CameraScreenUiState
}

/**
 * Pure state machine governing [CameraScreenUiState] transitions so every permission and hardware
 * edge case is verifiable with JVM unit tests.
 */
object CameraScreenStateMachine {
    /** Saved form of [state] for `rememberSaveable`, so rotation never re-asks a denied permission. */
    fun encode(state: CameraScreenUiState): String = when (state) {
        CameraScreenUiState.RequestingPermission -> "RequestingPermission"
        CameraScreenUiState.PermissionRationale -> "PermissionRationale"
        CameraScreenUiState.PermissionDenied -> "PermissionDenied"
        CameraScreenUiState.PermissionPermanentlyDenied -> "PermissionPermanentlyDenied"
        CameraScreenUiState.NoCamera -> "NoCamera"
        CameraScreenUiState.NoSensor -> "NoSensor"
        is CameraScreenUiState.CameraError -> "CameraError:" + (state.detail ?: "")
        CameraScreenUiState.Ready -> "Ready"
    }

    /** Inverse of [encode]; an unknown value starts over by asking for the permission. */
    fun decode(saved: String): CameraScreenUiState = when {
        saved == "RequestingPermission" -> CameraScreenUiState.RequestingPermission
        saved == "PermissionRationale" -> CameraScreenUiState.PermissionRationale
        saved == "PermissionDenied" -> CameraScreenUiState.PermissionDenied
        saved == "PermissionPermanentlyDenied" -> CameraScreenUiState.PermissionPermanentlyDenied
        saved == "NoCamera" -> CameraScreenUiState.NoCamera
        saved == "NoSensor" -> CameraScreenUiState.NoSensor
        saved.startsWith("CameraError:") ->
            CameraScreenUiState.CameraError(saved.removePrefix("CameraError:").ifEmpty { null })
        saved == "Ready" -> CameraScreenUiState.Ready
        else -> CameraScreenUiState.RequestingPermission
    }


    fun initialState(
        hasCamera: Boolean,
        hasRotationSensor: Boolean,
        hasPermission: Boolean,
        shouldShowRationale: Boolean,
    ): CameraScreenUiState = when {
        !hasCamera -> CameraScreenUiState.NoCamera
        !hasRotationSensor -> CameraScreenUiState.NoSensor
        hasPermission -> CameraScreenUiState.Ready
        shouldShowRationale -> CameraScreenUiState.PermissionRationale
        else -> CameraScreenUiState.RequestingPermission
    }

    fun onPermissionResult(
        granted: Boolean,
        shouldShowRationale: Boolean,
        hasCamera: Boolean,
        hasRotationSensor: Boolean,
    ): CameraScreenUiState = when {
        !hasCamera -> CameraScreenUiState.NoCamera
        !hasRotationSensor -> CameraScreenUiState.NoSensor
        granted -> CameraScreenUiState.Ready
        shouldShowRationale -> CameraScreenUiState.PermissionDenied
        else -> CameraScreenUiState.PermissionPermanentlyDenied
    }

    fun onRequestPermissionClicked(current: CameraScreenUiState): CameraScreenUiState = when (current) {
        CameraScreenUiState.PermissionRationale,
        CameraScreenUiState.PermissionDenied -> CameraScreenUiState.RequestingPermission
        else -> current
    }

    fun onLifecycleResume(
        current: CameraScreenUiState,
        hasCamera: Boolean,
        hasRotationSensor: Boolean,
        hasPermission: Boolean,
        shouldShowRationale: Boolean,
    ): CameraScreenUiState = when {
        !hasCamera -> CameraScreenUiState.NoCamera
        !hasRotationSensor -> CameraScreenUiState.NoSensor
        hasPermission -> when (current) {
            is CameraScreenUiState.CameraError -> current
            else -> CameraScreenUiState.Ready
        }
        else -> when (current) {
            CameraScreenUiState.Ready,
            is CameraScreenUiState.CameraError -> if (shouldShowRationale) {
                CameraScreenUiState.PermissionRationale
            } else {
                CameraScreenUiState.PermissionPermanentlyDenied
            }
            else -> current
        }
    }

    fun onCameraError(
        current: CameraScreenUiState,
        detail: String? = null,
    ): CameraScreenUiState = when (current) {
        CameraScreenUiState.Ready -> CameraScreenUiState.CameraError(detail)
        else -> current
    }

    fun onRetryCamera(
        current: CameraScreenUiState,
        hasPermission: Boolean,
    ): CameraScreenUiState = when {
        current is CameraScreenUiState.CameraError && hasPermission -> CameraScreenUiState.Ready
        else -> current
    }
}
