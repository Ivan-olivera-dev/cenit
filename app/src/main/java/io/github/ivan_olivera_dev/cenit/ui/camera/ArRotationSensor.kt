package io.github.ivan_olivera_dev.cenit.ui.camera

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

internal data class ArRotationSample(
    val rotation: FloatArray = ArInputs.identityRotationMatrix(),
    val accuracy: Int = ArInputs.ACCURACY_INITIAL,
    val sensorAvailable: Boolean = true,
)

/** Returns true when the device provides [Sensor.TYPE_ROTATION_VECTOR]. */
internal fun hasRotationVectorSensor(context: Context): Boolean {
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        ?: return false
    return sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
}

/**
 * Registers [Sensor.TYPE_ROTATION_VECTOR] at [SensorManager.SENSOR_DELAY_GAME] ONLY while
 * [enabled] is true and the host lifecycle is resumed, releasing it on pause and disposal.
 *
 * Emits the 3×3 row-major matrix directly from [SensorManager.getRotationMatrixFromVector]
 * with NO coordinate remapping (portrait convention per L5).
 */
@Composable
internal fun rememberArRotationState(enabled: Boolean): ArRotationSample {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var sample by remember(context) {
        mutableStateOf(
            ArRotationSample(
                sensorAvailable = hasRotationVectorSensor(context),
            ),
        )
    }

    DisposableEffect(context, lifecycleOwner, enabled) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationVector = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sensorManager == null || rotationVector == null) {
            sample = sample.copy(sensorAvailable = false)
            return@DisposableEffect onDispose {}
        }

        if (!enabled) {
            return@DisposableEffect onDispose {}
        }

        val scratchMatrix = FloatArray(9)
        var isRegistered = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR || event.values.size < 3) return
                SensorManager.getRotationMatrixFromVector(scratchMatrix, event.values)
                sample = ArRotationSample(
                    rotation = scratchMatrix.copyOf(),
                    accuracy = event.accuracy,
                    sensorAvailable = true,
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
                    sample = sample.copy(accuracy = accuracy)
                }
            }
        }

        fun register() {
            if (isRegistered) return
            val ok = sensorManager.registerListener(
                listener,
                rotationVector,
                SensorManager.SENSOR_DELAY_GAME,
            )
            if (ok) {
                isRegistered = true
                if (!sample.sensorAvailable) {
                    sample = sample.copy(sensorAvailable = true)
                }
            } else {
                sample = sample.copy(sensorAvailable = false)
            }
        }

        fun unregister() {
            if (!isRegistered) return
            sensorManager.unregisterListener(listener)
            isRegistered = false
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> register()
                Lifecycle.Event.ON_PAUSE -> unregister()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            register()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unregister()
        }
    }

    return sample
}
