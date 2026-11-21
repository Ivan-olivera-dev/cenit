package io.github.ivan_olivera_dev.cenit.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Test

/** The camera screen state survives rotation, so a denied permission is not asked again (L8). */
class CameraScreenStateSaverTest {
    @Test
    fun `every state round-trips through its saved form`() {
        val states = listOf(
            CameraScreenUiState.RequestingPermission,
            CameraScreenUiState.PermissionRationale,
            CameraScreenUiState.PermissionDenied,
            CameraScreenUiState.PermissionPermanentlyDenied,
            CameraScreenUiState.NoCamera,
            CameraScreenUiState.NoSensor,
            CameraScreenUiState.CameraError(),
            CameraScreenUiState.CameraError("Camera in use"),
            CameraScreenUiState.Ready,
        )
        for (state in states) {
            assertEquals(state, CameraScreenStateMachine.decode(CameraScreenStateMachine.encode(state)))
        }
    }

    @Test
    fun `an unknown saved value falls back to asking again`() {
        assertEquals(CameraScreenUiState.RequestingPermission, CameraScreenStateMachine.decode("Garbage"))
    }
}
