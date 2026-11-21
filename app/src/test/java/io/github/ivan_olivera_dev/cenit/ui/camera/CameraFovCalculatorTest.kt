package io.github.ivan_olivera_dev.cenit.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

class CameraFovCalculatorTest {

    @Test
    fun `4_25mm focal on 6_4x4_8mm sensor with 4_3 stream in 9_20 portrait viewport`() {
        // Exact spec example: 4.25 mm focal, 6.4x4.8 mm sensor, 4:3 stream, 9:20 portrait viewport.
        // Sensor is mounted 90° relative to portrait display:
        //   screenStreamW = 4.8 mm, screenStreamH = 6.4 mm (aspect 0.75)
        //   viewportAspect = 9 / 20 = 0.45 < 0.75 -> height preserved (6.4 mm), width cropped to 6.4 * 0.45 = 2.88 mm
        val fov = CameraFovCalculator.computeVisibleFov(
            focalLengthMm = 4.25,
            sensorWidthMm = 6.4,
            sensorHeightMm = 4.8,
            viewportWidthPx = 1080,
            viewportHeightPx = 2400,
            sensorOrientationDeg = 90,
            displayRotationDeg = 0,
            activeArrayAspect = 4.0 / 3.0,
            streamAspect = 4.0 / 3.0,
        )

        val expectedVerticalDeg = 2.0 * Math.toDegrees(atan(6.4 / (2.0 * 4.25)))
        val expectedHorizontalDeg = 2.0 * Math.toDegrees(atan(2.88 / (2.0 * 4.25)))

        assertEquals(expectedVerticalDeg, fov.verticalFovDeg, 1e-6)
        assertEquals(expectedHorizontalDeg, fov.horizontalFovDeg, 1e-6)
        assertEquals(73.955193, fov.verticalFovDeg, 1e-5)
        assertEquals(37.435179, fov.horizontalFovDeg, 1e-5)

        // Square-pixel pinhole invariant: tan(hFov/2) / tan(vFov/2) == viewportW / viewportH
        val tanRatio = tan(Math.toRadians(fov.horizontalFovDeg / 2.0)) /
            tan(Math.toRadians(fov.verticalFovDeg / 2.0))
        assertEquals(9.0 / 20.0, tanRatio, 1e-9)
    }

    @Test
    fun `same camera in 20_9 landscape viewport swaps horizontal and vertical FOVs`() {
        // Landscape display rotation (90°) aligns with 90° sensor orientation -> relative rotation 0°
        val fov = CameraFovCalculator.computeVisibleFov(
            focalLengthMm = 4.25,
            sensorWidthMm = 6.4,
            sensorHeightMm = 4.8,
            viewportWidthPx = 2400,
            viewportHeightPx = 1080,
            sensorOrientationDeg = 90,
            displayRotationDeg = 90,
            activeArrayAspect = 4.0 / 3.0,
            streamAspect = 4.0 / 3.0,
        )

        val expectedHorizontalDeg = 2.0 * Math.toDegrees(atan(6.4 / (2.0 * 4.25)))
        val expectedVerticalDeg = 2.0 * Math.toDegrees(atan(2.88 / (2.0 * 4.25)))

        assertEquals(expectedHorizontalDeg, fov.horizontalFovDeg, 1e-6)
        assertEquals(expectedVerticalDeg, fov.verticalFovDeg, 1e-6)

        val tanRatio = tan(Math.toRadians(fov.horizontalFovDeg / 2.0)) /
            tan(Math.toRadians(fov.verticalFovDeg / 2.0))
        assertEquals(20.0 / 9.0, tanRatio, 1e-9)
    }

    @Test
    fun `4_3 stream in 3_4 portrait viewport has no FILL_CENTER crop`() {
        val fov = CameraFovCalculator.computeVisibleFov(
            focalLengthMm = 4.25,
            sensorWidthMm = 6.4,
            sensorHeightMm = 4.8,
            viewportWidthPx = 900,
            viewportHeightPx = 1200,
            sensorOrientationDeg = 90,
            displayRotationDeg = 0,
            activeArrayAspect = 4.0 / 3.0,
            streamAspect = 4.0 / 3.0,
        )

        val expectedVerticalDeg = 2.0 * Math.toDegrees(atan(6.4 / (2.0 * 4.25)))
        val expectedHorizontalDeg = 2.0 * Math.toDegrees(atan(4.8 / (2.0 * 4.25)))

        assertEquals(expectedVerticalDeg, fov.verticalFovDeg, 1e-6)
        assertEquals(expectedHorizontalDeg, fov.horizontalFovDeg, 1e-6)
    }

    @Test
    fun `16_9 stream on 4_3 sensor crops vertically when shown in wider 3_4 portrait viewport`() {
        // Sensor 6.4 x 4.8 mm -> 16:9 stream crops sensor height to 6.4 / (16/9) = 3.6 mm.
        // Rotated 90° for portrait: screenStreamW = 3.6 mm, screenStreamH = 6.4 mm (aspect 9/16 = 0.5625).
        // Viewport is 3:4 (0.75 > 0.5625): FILL_CENTER matches width (3.6 mm) and crops height to 3.6 / 0.75 = 4.8 mm.
        val fov = CameraFovCalculator.computeVisibleFov(
            focalLengthMm = 4.25,
            sensorWidthMm = 6.4,
            sensorHeightMm = 4.8,
            viewportWidthPx = 900,
            viewportHeightPx = 1200,
            sensorOrientationDeg = 90,
            displayRotationDeg = 0,
            activeArrayAspect = 4.0 / 3.0,
            streamAspect = 16.0 / 9.0,
        )

        val expectedHorizontalDeg = 2.0 * Math.toDegrees(atan(3.6 / (2.0 * 4.25)))
        val expectedVerticalDeg = 2.0 * Math.toDegrees(atan(4.8 / (2.0 * 4.25)))

        assertEquals(expectedHorizontalDeg, fov.horizontalFovDeg, 1e-6)
        assertEquals(expectedVerticalDeg, fov.verticalFovDeg, 1e-6)
    }

    @Test
    fun `landscape sensor with 0 degree orientation in portrait viewport crops horizontally`() {
        // Sensor 6.4 x 4.8 mm with relative rotation 0° -> screenStreamW = 6.4 mm, screenStreamH = 4.8 mm.
        // Portrait 9:16 viewport (0.5625 < 4/3): height stays 4.8 mm, width crops to 4.8 * 9/16 = 2.7 mm.
        val fov = CameraFovCalculator.computeVisibleFov(
            focalLengthMm = 4.25,
            sensorWidthMm = 6.4,
            sensorHeightMm = 4.8,
            viewportWidthPx = 900,
            viewportHeightPx = 1600,
            sensorOrientationDeg = 0,
            displayRotationDeg = 0,
            activeArrayAspect = 4.0 / 3.0,
            streamAspect = 4.0 / 3.0,
        )

        val expectedVerticalDeg = 2.0 * Math.toDegrees(atan(4.8 / (2.0 * 4.25)))
        val expectedHorizontalDeg = 2.0 * Math.toDegrees(atan(2.7 / (2.0 * 4.25)))

        assertEquals(expectedVerticalDeg, fov.verticalFovDeg, 1e-6)
        assertEquals(expectedHorizontalDeg, fov.horizontalFovDeg, 1e-6)
    }

    @Test
    fun `active array sub-rectangle shrinks effective sensor size proportionally`() {
        // Full pixel array 4000x3000 (6.4x4.8 mm), active array 3200x2400 (80% each axis -> 5.12x3.84 mm).
        val fov = CameraFovCalculator.computeVisibleFov(
            focalLengthMm = 4.25,
            sensorWidthMm = 6.4,
            sensorHeightMm = 4.8,
            viewportWidthPx = 900,
            viewportHeightPx = 1200,
            sensorOrientationDeg = 90,
            displayRotationDeg = 0,
            activeArrayWidthPx = 3200,
            activeArrayHeightPx = 2400,
            pixelArrayWidthPx = 4000,
            pixelArrayHeightPx = 3000,
        )

        val expectedVerticalDeg = 2.0 * Math.toDegrees(atan(5.12 / (2.0 * 4.25)))
        val expectedHorizontalDeg = 2.0 * Math.toDegrees(atan(3.84 / (2.0 * 4.25)))

        assertEquals(expectedVerticalDeg, fov.verticalFovDeg, 1e-6)
        assertEquals(expectedHorizontalDeg, fov.horizontalFovDeg, 1e-6)
    }

    @Test
    fun `invalid camera characteristics or zero viewport falls back to safe positive FOV`() {
        val fallback = CameraFovCalculator.computeVisibleFov(
            focalLengthMm = 0.0,
            sensorWidthMm = 0.0,
            sensorHeightMm = -1.0,
            viewportWidthPx = 0,
            viewportHeightPx = 0,
        )

        assertTrue(fallback.horizontalFovDeg in 10.0..120.0)
        assertTrue(fallback.verticalFovDeg in 10.0..120.0)
    }

    @Test
    fun `cameraPointingFromRotation extracts azimuth and elevation for back camera along device minus Z`() {
        // Upright portrait facing North: device +X = East(1,0,0), +Y = Up(0,0,1), +Z = South(0,-1,0)
        val facingNorth = floatArrayOf(
            1f, 0f, 0f,
            0f, 0f, -1f,
            0f, 1f, 0f,
        )
        val northPointing = cameraPointingFromRotation(facingNorth)
        assertEquals(0.0, northPointing.azimuthDeg, 1e-6)
        assertEquals(0.0, northPointing.elevationDeg, 1e-6)

        // Upright portrait facing East: device +X = South(0,-1,0), +Y = Up(0,0,1), +Z = West(-1,0,0)
        val facingEast = floatArrayOf(
            0f, 0f, -1f,
            -1f, 0f, 0f,
            0f, 1f, 0f,
        )
        val eastPointing = cameraPointingFromRotation(facingEast)
        assertEquals(90.0, eastPointing.azimuthDeg, 1e-6)
        assertEquals(0.0, eastPointing.elevationDeg, 1e-6)

        // Facing North, pitched 30° upward: camera -Z = (0, cos30, sin30) => device +Z = (0, -cos30, -sin30)
        val rad = Math.toRadians(30.0)
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val pitchedUp = floatArrayOf(
            1f, 0f, 0f,
            0f, -s, -c,
            0f, c, -s,
        )
        val upPointing = cameraPointingFromRotation(pitchedUp)
        assertEquals(0.0, upPointing.azimuthDeg, 1e-4)
        assertEquals(30.0, upPointing.elevationDeg, 1e-4)
    }
}
