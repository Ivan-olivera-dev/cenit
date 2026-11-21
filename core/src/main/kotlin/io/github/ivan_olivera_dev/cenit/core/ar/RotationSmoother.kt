package io.github.ivan_olivera_dev.cenit.core.ar

import kotlin.math.acos
import kotlin.math.sin

/**
 * Stateful quaternion low-pass filter for live device rotations.
 *
 * Snaps immediately to the first [DeviceRotation] sample so opening the camera never drifts from
 * an arbitrary initial pose, then interpolates subsequent updates toward each new sample along the
 * shortest geodesic on `SO(3)` (hemisphere-aligned SLERP with NLERP fallback for nearly identical
 * rotations). Because smoothing operates on unit quaternions rather than Euler angles, it never
 * flips across the `359° → 0°` azimuth wrap or over the zenith/nadir, and every output matrix
 * remains strictly orthonormal.
 *
 * @property alpha Smoothing factor in `[0, 1]`, where `0` holds the previous orientation and `1`
 *   tracks the raw input without lag.
 */
class RotationSmoother(
    val alpha: Double = DEFAULT_ALPHA,
) {
    init {
        require(alpha.isFinite() && alpha in 0.0..1.0) {
            "alpha must be finite and in [0, 1], was $alpha"
        }
    }

    private var currentQuat: UnitQuaternion? = null

    /**
     * Most recent smoothed [DeviceRotation], or `null` before the first [update] or after [reset].
     */
    var current: DeviceRotation? = null
        private set

    /**
     * Incorporates a new [sample] and returns the updated smoothed [DeviceRotation].
     */
    fun update(sample: DeviceRotation): DeviceRotation {
        val sampleQuat = UnitQuaternion.fromRotation(sample)
        val prevQuat = currentQuat
        if (prevQuat == null) {
            currentQuat = sampleQuat
            val snapped = sampleQuat.toDeviceRotation()
            current = snapped
            return snapped
        }

        // Align hemispheres so we always take the shortest geodesic arc on SO(3)
        var dot = prevQuat.dot(sampleQuat)
        val targetQuat = if (dot < 0.0) {
            dot = -dot
            sampleQuat.negated()
        } else {
            sampleQuat
        }

        val nextQuat = if (dot > NLERP_THRESHOLD) {
            val w0 = 1.0 - alpha
            val w1 = alpha
            UnitQuaternion.normalized(
                w = w0 * prevQuat.w + w1 * targetQuat.w,
                x = w0 * prevQuat.x + w1 * targetQuat.x,
                y = w0 * prevQuat.y + w1 * targetQuat.y,
                z = w0 * prevQuat.z + w1 * targetQuat.z,
            )
        } else {
            val theta = acos(dot.coerceIn(0.0, 1.0))
            val sinTheta = sin(theta)
            val s0 = sin((1.0 - alpha) * theta) / sinTheta
            val s1 = sin(alpha * theta) / sinTheta
            UnitQuaternion.normalized(
                w = s0 * prevQuat.w + s1 * targetQuat.w,
                x = s0 * prevQuat.x + s1 * targetQuat.x,
                y = s0 * prevQuat.y + s1 * targetQuat.y,
                z = s0 * prevQuat.z + s1 * targetQuat.z,
            )
        }

        currentQuat = nextQuat
        val smoothed = nextQuat.toDeviceRotation()
        current = smoothed
        return smoothed
    }

    /**
     * Clears the smoother state so the next [update] snaps immediately.
     */
    fun reset() {
        currentQuat = null
        current = null
    }

    companion object {
        /** Responsive yet steady low-pass gain for live AR camera overlays. */
        const val DEFAULT_ALPHA: Double = 0.18

        private const val NLERP_THRESHOLD = 0.9995
    }
}
