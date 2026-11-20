package io.github.ivan_olivera_dev.cenit.ui.compass

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.ivan_olivera_dev.cenit.core.Place
import io.github.ivan_olivera_dev.cenit.core.compass.CompassMath
import io.github.ivan_olivera_dev.cenit.core.compass.HeadingSmoother
import java.time.Instant

/**
 * Hardware availability of a compass heading source on the device.
 */
enum class CompassAvailability {
    AVAILABLE,
    UNAVAILABLE,
}

/**
 * Platform-independent representation of magnetic/rotation-vector sensor accuracy.
 */
enum class CompassAccuracy {
    UNRELIABLE,
    LOW,
    MEDIUM,
    HIGH,
    UNKNOWN,
}

/**
 * Immutable UI state emitted by the compass sensor pipeline.
 *
 * @property availability Whether a heading sensor (rotation vector or accel+mag fallback) is present.
 * @property headingDeg Smoothed true-north heading in `[0, 360)`, or `null` when unavailable or
 *   awaiting the first sensor reading.
 * @property accuracy Last reported sensor accuracy.
 */
data class CompassSensorState(
    val availability: CompassAvailability = CompassAvailability.AVAILABLE,
    val headingDeg: Double? = null,
    val accuracy: CompassAccuracy = CompassAccuracy.UNKNOWN,
) {
    /** Heading used to orient the dome (`0.0` north-up when unavailable or awaiting first sample). */
    val displayHeadingDeg: Double
        get() = if (availability == CompassAvailability.AVAILABLE) headingDeg ?: 0.0 else 0.0

    /** True when the live sensor is active and reports low or unreliable accuracy. */
    val needsCalibration: Boolean
        get() = availability == CompassAvailability.AVAILABLE &&
            (accuracy == CompassAccuracy.LOW || accuracy == CompassAccuracy.UNRELIABLE)
}

/**
 * Pure state-machine tracking true-north heading, angle-aware low-pass smoothing, and calibration
 * accuracy independently of Android's [SensorManager] so it can be verified with JVM unit tests.
 */
internal class CompassHeadingTracker(
    alpha: Double = HeadingSmoother.DEFAULT_ALPHA,
) {
    private val smoother = HeadingSmoother(alpha = alpha)
    private var lastMagneticHeadingDeg: Double? = null

    var state: CompassSensorState = CompassSensorState()
        private set

    fun onMagneticHeading(
        magneticDeg: Double,
        declinationDeg: Double,
    ): CompassSensorState {
        if (!magneticDeg.isFinite() || !declinationDeg.isFinite()) return state
        val normalizedMagnetic = CompassMath.normalizeDegrees(magneticDeg)
        lastMagneticHeadingDeg = normalizedMagnetic
        val trueHeading = CompassMath.magneticToTrueNorth(normalizedMagnetic, declinationDeg)
        val smoothed = smoother.update(trueHeading)
        state = state.copy(
            availability = CompassAvailability.AVAILABLE,
            headingDeg = smoothed,
        )
        return state
    }

    fun onDeclinationChanged(declinationDeg: Double): CompassSensorState {
        if (!declinationDeg.isFinite()) return state
        val magnetic = lastMagneticHeadingDeg ?: return state
        if (state.availability != CompassAvailability.AVAILABLE) return state
        val trueHeading = CompassMath.magneticToTrueNorth(magnetic, declinationDeg)
        smoother.reset()
        val snapped = smoother.update(trueHeading)
        state = state.copy(headingDeg = snapped)
        return state
    }

    fun onAccuracyChanged(accuracy: CompassAccuracy): CompassSensorState {
        state = state.copy(accuracy = accuracy)
        return state
    }

    fun onSensorUnavailable(): CompassSensorState {
        smoother.reset()
        lastMagneticHeadingDeg = null
        state = CompassSensorState(
            availability = CompassAvailability.UNAVAILABLE,
            headingDeg = null,
            accuracy = CompassAccuracy.UNKNOWN,
        )
        return state
    }

    fun resetSmoothing() {
        smoother.reset()
    }
}

/**
 * Registers live heading sensors while the composable is in composition and the host lifecycle is
 * resumed, unregistering on pause and disposal.
 *
 * Strategy:
 * 1. Primary: [Sensor.TYPE_ROTATION_VECTOR] via [SensorManager.getRotationMatrixFromVector] and
 *    [SensorManager.getOrientation].
 * 2. Fallback: [Sensor.TYPE_ACCELEROMETER] + [Sensor.TYPE_MAGNETIC_FIELD] via
 *    [SensorManager.getRotationMatrix] and [SensorManager.getOrientation].
 * 3. Neither available: emits [CompassAvailability.UNAVAILABLE] so the dome renders north-up.
 *
 * Assumes portrait orientation and flat-compass holding posture (device top points along heading);
 * display rotation remapping is intentionally omitted for this portrait-first flat compass.
 */
@Composable
internal fun rememberCompassSensorState(
    place: Place,
    instant: Instant,
): CompassSensorState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val declinationDeg = remember(place, instant) {
        GeomagneticField(
            place.latitude.toFloat(),
            place.longitude.toFloat(),
            place.elevationMeters.toFloat(),
            instant.toEpochMilli(),
        ).declination.toDouble()
    }
    val currentDeclination by rememberUpdatedState(declinationDeg)

    val tracker = remember { CompassHeadingTracker() }
    var sensorState by remember { mutableStateOf(tracker.state) }

    LaunchedEffect(declinationDeg) {
        sensorState = tracker.onDeclinationChanged(declinationDeg)
    }

    DisposableEffect(context, lifecycleOwner) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (sensorManager == null) {
            sensorState = tracker.onSensorUnavailable()
            return@DisposableEffect onDispose {}
        }

        val rotationVector = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val accelerometer = if (rotationVector == null) {
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        } else {
            null
        }
        val magnetometer = if (rotationVector == null) {
            sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        } else {
            null
        }

        val hasFallbackPair = accelerometer != null && magnetometer != null
        if (rotationVector == null && !hasFallbackPair) {
            sensorState = tracker.onSensorUnavailable()
            return@DisposableEffect onDispose {}
        }

        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        val gravity = FloatArray(3)
        val geomagnetic = FloatArray(3)
        var hasGravity = false
        var hasGeomagnetic = false
        var usingRotationVector = rotationVector != null
        var isRegistered = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        if (event.values.size < 3) return
                        sensorState = tracker.onAccuracyChanged(mapSensorAccuracy(event.accuracy))
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)
                        val magneticDeg = Math.toDegrees(orientationAngles[0].toDouble())
                        sensorState = tracker.onMagneticHeading(magneticDeg, currentDeclination)
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        if (event.values.size < 3) return
                        System.arraycopy(event.values, 0, gravity, 0, 3)
                        hasGravity = true
                        emitFallbackHeading()
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        if (event.values.size < 3) return
                        sensorState = tracker.onAccuracyChanged(mapSensorAccuracy(event.accuracy))
                        System.arraycopy(event.values, 0, geomagnetic, 0, 3)
                        hasGeomagnetic = true
                        emitFallbackHeading()
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (sensor == null) return
                val relevant = if (usingRotationVector) {
                    sensor.type == Sensor.TYPE_ROTATION_VECTOR
                } else {
                    sensor.type == Sensor.TYPE_MAGNETIC_FIELD
                }
                if (relevant) {
                    sensorState = tracker.onAccuracyChanged(mapSensorAccuracy(accuracy))
                }
            }

            private fun emitFallbackHeading() {
                if (!hasGravity || !hasGeomagnetic) return
                if (SensorManager.getRotationMatrix(rotationMatrix, null, gravity, geomagnetic)) {
                    SensorManager.getOrientation(rotationMatrix, orientationAngles)
                    val magneticDeg = Math.toDegrees(orientationAngles[0].toDouble())
                    sensorState = tracker.onMagneticHeading(magneticDeg, currentDeclination)
                }
            }
        }

        fun registerSensors() {
            if (isRegistered) return
            tracker.resetSmoothing()
            hasGravity = false
            hasGeomagnetic = false

            if (rotationVector != null) {
                val ok = sensorManager.registerListener(
                    listener,
                    rotationVector,
                    SensorManager.SENSOR_DELAY_UI,
                )
                if (ok) {
                    usingRotationVector = true
                    isRegistered = true
                    return
                }
            }

            // Fallback to accelerometer + magnetometer when rotation vector is missing or refused
            val fbAccel = accelerometer ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            val fbMag = magnetometer ?: sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            if (fbAccel != null && fbMag != null) {
                val accelOk = sensorManager.registerListener(listener, fbAccel, SensorManager.SENSOR_DELAY_UI)
                val magOk = sensorManager.registerListener(listener, fbMag, SensorManager.SENSOR_DELAY_UI)
                if (accelOk && magOk) {
                    usingRotationVector = false
                    isRegistered = true
                    return
                }
                sensorManager.unregisterListener(listener)
            }

            sensorState = tracker.onSensorUnavailable()
        }

        fun unregisterSensors() {
            if (!isRegistered) return
            sensorManager.unregisterListener(listener)
            isRegistered = false
            tracker.resetSmoothing()
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> registerSensors()
                Lifecycle.Event.ON_PAUSE -> unregisterSensors()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            registerSensors()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unregisterSensors()
        }
    }

    return sensorState
}

private fun mapSensorAccuracy(accuracy: Int): CompassAccuracy = when (accuracy) {
    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> CompassAccuracy.HIGH
    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> CompassAccuracy.MEDIUM
    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> CompassAccuracy.LOW
    SensorManager.SENSOR_STATUS_UNRELIABLE,
    SensorManager.SENSOR_STATUS_NO_CONTACT -> CompassAccuracy.UNRELIABLE
    else -> CompassAccuracy.UNKNOWN
}
