package io.github.ivan_olivera_dev.cenit.ui.camera

import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.tan

/**
 * Primitive-only AR camera inputs exposed as Compose state by [CameraScreen] (L5 contract).
 *
 * @property rotation 3×3 row-major rotation matrix mapping device coordinates to world coordinates
 *   (East, North, Up) as produced by [android.hardware.SensorManager.getRotationMatrixFromVector]
 *   with NO coordinate remapping (portrait convention).
 * @property accuracy Last reported Android sensor accuracy (`SensorManager.SENSOR_STATUS_*`).
 * @property sensorAvailable True when [android.hardware.Sensor.TYPE_ROTATION_VECTOR] is available
 *   and registered on this device.
 * @property horizontalFovDeg Horizontal field of view in degrees of the visible on-screen preview
 *   after CameraX `FILL_CENTER` cropping.
 * @property verticalFovDeg Vertical field of view in degrees of the visible on-screen preview
 *   after CameraX `FILL_CENTER` cropping.
 * @property previewWidthPx Width of the visible preview viewport in pixels.
 * @property previewHeightPx Height of the visible preview viewport in pixels.
 */
data class ArInputs(
    val rotation: FloatArray = identityRotationMatrix(),
    val accuracy: Int = ACCURACY_INITIAL,
    val sensorAvailable: Boolean = true,
    val horizontalFovDeg: Double = CameraFovCalculator.DEFAULT_HORIZONTAL_FOV_DEG,
    val verticalFovDeg: Double = CameraFovCalculator.DEFAULT_VERTICAL_FOV_DEG,
    val previewWidthPx: Int = 0,
    val previewHeightPx: Int = 0,
) {
    init {
        require(rotation.size == 9) { "rotation matrix must have 9 elements, got ${rotation.size}" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ArInputs) return false
        return rotation.contentEquals(other.rotation) &&
            accuracy == other.accuracy &&
            sensorAvailable == other.sensorAvailable &&
            horizontalFovDeg == other.horizontalFovDeg &&
            verticalFovDeg == other.verticalFovDeg &&
            previewWidthPx == other.previewWidthPx &&
            previewHeightPx == other.previewHeightPx
    }

    override fun hashCode(): Int {
        var result = rotation.contentHashCode()
        result = 31 * result + accuracy
        result = 31 * result + sensorAvailable.hashCode()
        result = 31 * result + horizontalFovDeg.hashCode()
        result = 31 * result + verticalFovDeg.hashCode()
        result = 31 * result + previewWidthPx
        result = 31 * result + previewHeightPx
        return result
    }

    companion object {
        /** Matches `SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM` until the first sensor event arrives. */
        const val ACCURACY_INITIAL: Int = 2

        fun identityRotationMatrix(): FloatArray = floatArrayOf(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f,
        )
    }
}

/** Visible field of view in degrees of the on-screen camera preview. */
data class CameraFovResult(
    val horizontalFovDeg: Double,
    val verticalFovDeg: Double,
)

/**
 * Pure optics and crop math converting Camera2 physical sensor characteristics into the exact
 * horizontal and vertical FOV of a `PreviewView` rendered with `ScaleType.FILL_CENTER`.
 */
object CameraFovCalculator {

    const val DEFAULT_FOCAL_LENGTH_MM: Double = 4.25
    const val DEFAULT_SENSOR_WIDTH_MM: Double = 6.4
    const val DEFAULT_SENSOR_HEIGHT_MM: Double = 4.8
    const val DEFAULT_HORIZONTAL_FOV_DEG: Double = 37.44
    const val DEFAULT_VERTICAL_FOV_DEG: Double = 73.96

    /**
     * Computes the horizontal and vertical FOV (in degrees) actually visible inside a
     * `FILL_CENTER` viewport of size [viewportWidthPx] × [viewportHeightPx].
     *
     * Pipeline:
     * 1. Effective active sensor physical dimensions (scaled by active/pixel array when both are
     *    known, or center-cropped to [activeArrayAspect]).
     * 2. Center-crop to [streamAspect] in sensor coordinates (if the camera stream aspect differs
     *    from the active sensor aspect, e.g. a 16:9 stream on a 4:3 sensor).
     * 3. Rotate from sensor coordinates to screen coordinates via
     *    `(sensorOrientationDeg - displayRotationDeg) mod 360`.
     * 4. Center-crop (`FILL_CENTER`) to the on-screen viewport aspect ratio
     *    `viewportWidthPx / viewportHeightPx`.
     * 5. Pinhole FOV: `2 * atan(visibleSizeMm / (2 * focalLengthMm))`.
     */
    fun computeVisibleFov(
        focalLengthMm: Double,
        sensorWidthMm: Double,
        sensorHeightMm: Double,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
        sensorOrientationDeg: Int = 90,
        displayRotationDeg: Int = 0,
        activeArrayAspect: Double? = null,
        streamAspect: Double? = null,
        activeArrayWidthPx: Int? = null,
        activeArrayHeightPx: Int? = null,
        pixelArrayWidthPx: Int? = null,
        pixelArrayHeightPx: Int? = null,
    ): CameraFovResult {
        val validViewport = viewportWidthPx > 0 && viewportHeightPx > 0
        val validOptics = focalLengthMm.isFinite() && focalLengthMm > 0.0 &&
            sensorWidthMm.isFinite() && sensorWidthMm > 0.0 &&
            sensorHeightMm.isFinite() && sensorHeightMm > 0.0

        if (!validOptics || !validViewport) {
            return fallbackFov(viewportWidthPx, viewportHeightPx)
        }

        // 1. Active physical sensor region in sensor coordinates
        val (activeW, activeH) = resolveActivePhysicalSize(
            sensorWidthMm = sensorWidthMm,
            sensorHeightMm = sensorHeightMm,
            activeArrayAspect = activeArrayAspect,
            activeArrayWidthPx = activeArrayWidthPx,
            activeArrayHeightPx = activeArrayHeightPx,
            pixelArrayWidthPx = pixelArrayWidthPx,
            pixelArrayHeightPx = pixelArrayHeightPx,
        )

        // 2. Camera stream crop in sensor coordinates (if stream aspect is specified)
        val (streamW, streamH) = if (streamAspect != null && streamAspect.isFinite() && streamAspect > 0.0) {
            centerCrop(activeW, activeH, streamAspect)
        } else {
            activeW to activeH
        }

        // 3. Orient stream dimensions into display (screen) coordinates
        val relativeRotation = ((sensorOrientationDeg - displayRotationDeg) % 360 + 360) % 360
        val swapSensorAxes = relativeRotation == 90 || relativeRotation == 270
        val screenStreamW = if (swapSensorAxes) streamH else streamW
        val screenStreamH = if (swapSensorAxes) streamW else streamH

        // 4. PreviewView FILL_CENTER crop to the actual on-screen viewport aspect
        val viewportAspect = viewportWidthPx.toDouble() / viewportHeightPx.toDouble()
        val (visibleW, visibleH) = centerCrop(screenStreamW, screenStreamH, viewportAspect)

        // 5. Pinhole FOV in degrees
        val hFov = 2.0 * Math.toDegrees(atan(visibleW / (2.0 * focalLengthMm)))
        val vFov = 2.0 * Math.toDegrees(atan(visibleH / (2.0 * focalLengthMm)))

        if (!hFov.isFinite() || !vFov.isFinite() || hFov <= 0.0 || vFov <= 0.0 || hFov >= 180.0 || vFov >= 180.0) {
            return fallbackFov(viewportWidthPx, viewportHeightPx)
        }
        return CameraFovResult(horizontalFovDeg = hFov, verticalFovDeg = vFov)
    }

    private fun resolveActivePhysicalSize(
        sensorWidthMm: Double,
        sensorHeightMm: Double,
        activeArrayAspect: Double?,
        activeArrayWidthPx: Int?,
        activeArrayHeightPx: Int?,
        pixelArrayWidthPx: Int?,
        pixelArrayHeightPx: Int?,
    ): Pair<Double, Double> {
        if (
            activeArrayWidthPx != null && activeArrayHeightPx != null &&
            pixelArrayWidthPx != null && pixelArrayHeightPx != null &&
            activeArrayWidthPx > 0 && activeArrayHeightPx > 0 &&
            pixelArrayWidthPx > 0 && pixelArrayHeightPx > 0 &&
            activeArrayWidthPx <= pixelArrayWidthPx &&
            activeArrayHeightPx <= pixelArrayHeightPx
        ) {
            val w = sensorWidthMm * (activeArrayWidthPx.toDouble() / pixelArrayWidthPx.toDouble())
            val h = sensorHeightMm * (activeArrayHeightPx.toDouble() / pixelArrayHeightPx.toDouble())
            return w to h
        }

        val resolvedActiveAspect = when {
            activeArrayAspect != null && activeArrayAspect.isFinite() && activeArrayAspect > 0.0 -> activeArrayAspect
            activeArrayWidthPx != null && activeArrayHeightPx != null &&
                activeArrayWidthPx > 0 && activeArrayHeightPx > 0 ->
                activeArrayWidthPx.toDouble() / activeArrayHeightPx.toDouble()
            else -> null
        }

        return if (resolvedActiveAspect != null) {
            centerCrop(sensorWidthMm, sensorHeightMm, resolvedActiveAspect)
        } else {
            sensorWidthMm to sensorHeightMm
        }
    }

    /**
     * Largest centered sub-rectangle inside ([width], [height]) having aspect ratio [targetAspect].
     */
    private fun centerCrop(width: Double, height: Double, targetAspect: Double): Pair<Double, Double> {
        val currentAspect = width / height
        return if (targetAspect > currentAspect) {
            width to (width / targetAspect)
        } else {
            (height * targetAspect) to height
        }
    }

    private fun fallbackFov(viewportWidthPx: Int, viewportHeightPx: Int): CameraFovResult {
        if (viewportWidthPx > 0 && viewportHeightPx > 0) {
            val isLandscape = viewportWidthPx > viewportHeightPx
            val aspect = viewportWidthPx.toDouble() / viewportHeightPx.toDouble()
            val refV = if (isLandscape) DEFAULT_HORIZONTAL_FOV_DEG else DEFAULT_VERTICAL_FOV_DEG
            val refTanHalfV = tan(Math.toRadians(refV / 2.0))
            val hFov = 2.0 * Math.toDegrees(atan(refTanHalfV * aspect))
            return CameraFovResult(horizontalFovDeg = hFov, verticalFovDeg = refV)
        }
        return CameraFovResult(
            horizontalFovDeg = DEFAULT_HORIZONTAL_FOV_DEG,
            verticalFovDeg = DEFAULT_VERTICAL_FOV_DEG,
        )
    }
}

/** Simple camera aiming angles used only by the temporary debug overlay (replaced in T5). */
data class DebugPointing(
    val azimuthDeg: Double,
    val elevationDeg: Double,
)

/**
 * Extracts back-camera pointing direction from a 3×3 row-major device-to-world rotation matrix
 * (SensorManager convention: X east, Y north, Z up; back camera looks along device −Z).
 */
internal fun cameraPointingFromRotation(rotation: FloatArray): DebugPointing {
    require(rotation.size == 9) { "rotation matrix must have 9 elements" }
    val east = -rotation[2].toDouble()
    val north = -rotation[5].toDouble()
    val up = -rotation[8].toDouble()
    val rawAzimuth = Math.toDegrees(atan2(east, north))
    val azimuthDeg = ((rawAzimuth % 360.0) + 360.0) % 360.0
    val elevationDeg = Math.toDegrees(asin(up.coerceIn(-1.0, 1.0)))
    return DebugPointing(azimuthDeg = azimuthDeg, elevationDeg = elevationDeg)
}
