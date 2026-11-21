package io.github.ivan_olivera_dev.cenit.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CameraScreenStateTest {

    @Test
    fun `initial state prefers hardware availability checks before permission`() {
        assertEquals(
            CameraScreenUiState.NoCamera,
            CameraScreenStateMachine.initialState(
                hasCamera = false,
                hasRotationSensor = false,
                hasPermission = false,
                shouldShowRationale = false,
            ),
        )
        assertEquals(
            CameraScreenUiState.NoSensor,
            CameraScreenStateMachine.initialState(
                hasCamera = true,
                hasRotationSensor = false,
                hasPermission = false,
                shouldShowRationale = false,
            ),
        )
    }

    @Test
    fun `initial state requests permission on first entry and shows rationale if previously denied`() {
        assertEquals(
            CameraScreenUiState.RequestingPermission,
            CameraScreenStateMachine.initialState(
                hasCamera = true,
                hasRotationSensor = true,
                hasPermission = false,
                shouldShowRationale = false,
            ),
        )
        assertEquals(
            CameraScreenUiState.PermissionRationale,
            CameraScreenStateMachine.initialState(
                hasCamera = true,
                hasRotationSensor = true,
                hasPermission = false,
                shouldShowRationale = true,
            ),
        )
        assertEquals(
            CameraScreenUiState.Ready,
            CameraScreenStateMachine.initialState(
                hasCamera = true,
                hasRotationSensor = true,
                hasPermission = true,
                shouldShowRationale = false,
            ),
        )
    }

    @Test
    fun `permission result distinguishes granted, denied, and permanently denied`() {
        assertEquals(
            CameraScreenUiState.Ready,
            CameraScreenStateMachine.onPermissionResult(
                granted = true,
                shouldShowRationale = false,
                hasCamera = true,
                hasRotationSensor = true,
            ),
        )
        assertEquals(
            CameraScreenUiState.PermissionDenied,
            CameraScreenStateMachine.onPermissionResult(
                granted = false,
                shouldShowRationale = true,
                hasCamera = true,
                hasRotationSensor = true,
            ),
        )
        assertEquals(
            CameraScreenUiState.PermissionPermanentlyDenied,
            CameraScreenStateMachine.onPermissionResult(
                granted = false,
                shouldShowRationale = false,
                hasCamera = true,
                hasRotationSensor = true,
            ),
        )
    }

    @Test
    fun `explicit request action moves rationale or denied into RequestingPermission`() {
        assertEquals(
            CameraScreenUiState.RequestingPermission,
            CameraScreenStateMachine.onRequestPermissionClicked(CameraScreenUiState.PermissionRationale),
        )
        assertEquals(
            CameraScreenUiState.RequestingPermission,
            CameraScreenStateMachine.onRequestPermissionClicked(CameraScreenUiState.PermissionDenied),
        )
        assertEquals(
            CameraScreenUiState.PermissionPermanentlyDenied,
            CameraScreenStateMachine.onRequestPermissionClicked(CameraScreenUiState.PermissionPermanentlyDenied),
        )
    }

    @Test
    fun `lifecycle resume recovers from denied states when permission is granted in system settings`() {
        assertEquals(
            CameraScreenUiState.Ready,
            CameraScreenStateMachine.onLifecycleResume(
                current = CameraScreenUiState.PermissionPermanentlyDenied,
                hasCamera = true,
                hasRotationSensor = true,
                hasPermission = true,
                shouldShowRationale = false,
            ),
        )
        assertEquals(
            CameraScreenUiState.Ready,
            CameraScreenStateMachine.onLifecycleResume(
                current = CameraScreenUiState.PermissionDenied,
                hasCamera = true,
                hasRotationSensor = true,
                hasPermission = true,
                shouldShowRationale = false,
            ),
        )
        // Returning from Settings without granting keeps permanently denied without re-prompting
        assertEquals(
            CameraScreenUiState.PermissionPermanentlyDenied,
            CameraScreenStateMachine.onLifecycleResume(
                current = CameraScreenUiState.PermissionPermanentlyDenied,
                hasCamera = true,
                hasRotationSensor = true,
                hasPermission = false,
                shouldShowRationale = false,
            ),
        )
    }

    @Test
    fun `lifecycle resume handles permission revoked while backgrounded`() {
        assertEquals(
            CameraScreenUiState.PermissionPermanentlyDenied,
            CameraScreenStateMachine.onLifecycleResume(
                current = CameraScreenUiState.Ready,
                hasCamera = true,
                hasRotationSensor = true,
                hasPermission = false,
                shouldShowRationale = false,
            ),
        )
        assertEquals(
            CameraScreenUiState.PermissionRationale,
            CameraScreenStateMachine.onLifecycleResume(
                current = CameraScreenUiState.Ready,
                hasCamera = true,
                hasRotationSensor = true,
                hasPermission = false,
                shouldShowRationale = true,
            ),
        )
    }

    @Test
    fun `camera error transitions and retries cleanly`() {
        val errored = CameraScreenStateMachine.onCameraError(
            current = CameraScreenUiState.Ready,
            detail = "bind failed",
        )
        assertEquals(CameraScreenUiState.CameraError("bind failed"), errored)

        val retried = CameraScreenStateMachine.onRetryCamera(
            current = errored,
            hasPermission = true,
        )
        assertEquals(CameraScreenUiState.Ready, retried)
    }

    @Test
    fun `ArInputs compares rotation array by value and enforces 3x3 length`() {
        val a = ArInputs(
            rotation = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
            accuracy = 3,
            sensorAvailable = true,
            horizontalFovDeg = 37.4,
            verticalFovDeg = 73.9,
            previewWidthPx = 1080,
            previewHeightPx = 2400,
        )
        val b = ArInputs(
            rotation = floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f),
            accuracy = 3,
            sensorAvailable = true,
            horizontalFovDeg = 37.4,
            verticalFovDeg = 73.9,
            previewWidthPx = 1080,
            previewHeightPx = 2400,
        )
        val c = b.copy(rotation = floatArrayOf(0f, 1f, 0f, -1f, 0f, 0f, 0f, 0f, 1f))

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ArInputs rejects rotation arrays whose size is not 9`() {
        ArInputs(
            rotation = FloatArray(4),
            accuracy = 3,
            sensorAvailable = true,
            horizontalFovDeg = 50.0,
            verticalFovDeg = 70.0,
            previewWidthPx = 1080,
            previewHeightPx = 2400,
        )
    }
}
