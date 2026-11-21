package io.github.ivan_olivera_dev.cenit.core.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DeviceRotationTest {

    @Test
    fun `identity rotation is valid and preserves basis vectors`() {
        val r = DeviceRotation.IDENTITY
        assertEquals(1.0, r[0, 0], 1e-12)
        assertEquals(1.0, r[1, 1], 1e-12)
        assertEquals(1.0, r[2, 2], 1e-12)
        assertEquals(0.0, r[0, 1], 1e-12)
        assertEquals(0.0, r[1, 2], 1e-12)
        assertEquals(0.0, r[2, 0], 1e-12)
    }

    @Test
    fun `rejects wrong array size, non-finite values, non-orthonormal matrices, and reflections`() {
        assertThrows(IllegalArgumentException::class.java) {
            DeviceRotation(doubleArrayOf(1.0, 0.0, 0.0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            DeviceRotation(
                doubleArrayOf(
                    Double.NaN, 0.0, 0.0,
                    0.0, 1.0, 0.0,
                    0.0, 0.0, 1.0,
                ),
            )
        }
        // Scaled (non-unit columns)
        assertThrows(IllegalArgumentException::class.java) {
            DeviceRotation(
                doubleArrayOf(
                    2.0, 0.0, 0.0,
                    0.0, 1.0, 0.0,
                    0.0, 0.0, 1.0,
                ),
            )
        }
        // Sheared (non-orthogonal)
        assertThrows(IllegalArgumentException::class.java) {
            DeviceRotation(
                doubleArrayOf(
                    1.0, 0.2, 0.0,
                    0.0, 1.0, 0.0,
                    0.0, 0.0, 1.0,
                ),
            )
        }
        // Improper rotation (reflection, det = -1)
        assertThrows(IllegalArgumentException::class.java) {
            DeviceRotation(
                doubleArrayOf(
                    -1.0, 0.0, 0.0,
                    0.0, 1.0, 0.0,
                    0.0, 0.0, 1.0,
                ),
            )
        }
    }

    @Test
    fun `defensive copy prevents external mutation of matrix`() {
        val raw = doubleArrayOf(
            1.0, 0.0, 0.0,
            0.0, 0.0, -1.0,
            0.0, 1.0, 0.0,
        )
        val rotation = DeviceRotation(raw)
        raw[0] = 99.0
        assertEquals(1.0, rotation[0, 0], 1e-12)

        val copy = rotation.toDoubleArray()
        copy[0] = -42.0
        assertEquals(1.0, rotation[0, 0], 1e-12)
        assertNotEquals(copy[0], rotation[0, 0])
    }

    @Test
    fun `fromFloatArray converts Android SensorManager matrix within float precision`() {
        // Phone upright facing North: X_d = East, Y_d = Up, Z_d = South
        val sensorMatrix = floatArrayOf(
            1f, 0f, 0f,
            0f, 0f, -1f,
            0f, 1f, 0f,
        )
        val rotation = DeviceRotation.fromFloatArray(sensorMatrix)
        assertEquals(DeviceRotation.fromHeadingPitchRoll(0.0, 0.0, 0.0), rotation)
    }

    @Test
    fun `fromHeadingPitchRoll builds orthonormal proper rotations across angles including zenith and nadir`() {
        val cases = listOf(
            Triple(0.0, 0.0, 0.0),
            Triple(90.0, 30.0, 0.0),
            Triple(239.0, 5.0, -15.0),
            Triple(359.5, -42.0, 90.0),
            Triple(45.0, 90.0, 0.0),
            Triple(180.0, -90.0, 0.0),
            Triple(120.0, 60.0, 180.0),
        )
        for ((h, p, r) in cases) {
            val rot = DeviceRotation.fromHeadingPitchRoll(h, p, r)
            assertOrthonormalProper(rot)
        }
    }

    @Test
    fun `withDeclination rotates world azimuth clockwise by east-positive declination`() {
        val magneticNorth = DeviceRotation.fromHeadingPitchRoll(headingDeg = 0.0, pitchDeg = 10.0, rollDeg = 0.0)
        val trueEast = magneticNorth.withDeclination(declinationDeg = 5.0)
        val trueWest = magneticNorth.withDeclination(declinationDeg = -3.5)

        val projector = ArProjector(CameraFov(60.0, 80.0), widthPx = 1000.0, heightPx = 1500.0)
        val pEast = projector.pointing(trueEast)
        val pWest = projector.pointing(trueWest)

        assertEquals(5.0, pEast.azimuthDeg, 1e-6)
        assertEquals(10.0, pEast.elevationDeg, 1e-6)
        assertEquals(0.0, pEast.rollDeg, 1e-6)

        assertEquals(356.5, pWest.azimuthDeg, 1e-6)
        assertEquals(10.0, pWest.elevationDeg, 1e-6)
        assertEquals(0.0, pWest.rollDeg, 1e-6)
    }

    @Test
    fun `withDisplayRotation remaps portrait rotation matrix into active display frame`() {
        val projector = ArProjector(CameraFov(60.0, 80.0), widthPx = 1000.0, heightPx = 1500.0)

        // Portrait (0°): unchanged
        val portrait = DeviceRotation.fromHeadingPitchRoll(headingDeg = 120.0, pitchDeg = 15.0, rollDeg = 12.0)
        assertEquals(portrait, portrait.withDisplayRotation(0))

        // Device rotated 90° CCW (top on left => roll = -90° in portrait frame) with display at 90°
        val landLeft = DeviceRotation.fromHeadingPitchRoll(headingDeg = 239.0, pitchDeg = 5.0, rollDeg = -90.0)
        val remapped90 = landLeft.withDisplayRotation(90)
        assertOrthonormalProper(remapped90)
        val p90 = projector.pointing(remapped90)
        assertEquals(239.0, p90.azimuthDeg, 1e-6)
        assertEquals(5.0, p90.elevationDeg, 1e-6)
        assertEquals(0.0, p90.rollDeg, 1e-6)

        // Device rotated 90° CW (top on right => roll = +90° in portrait frame) with display at 270°
        val landRight = DeviceRotation.fromHeadingPitchRoll(headingDeg = 42.0, pitchDeg = -10.0, rollDeg = 90.0)
        val remapped270 = landRight.withDisplayRotation(270)
        assertOrthonormalProper(remapped270)
        val p270 = projector.pointing(remapped270)
        assertEquals(42.0, p270.azimuthDeg, 1e-6)
        assertEquals(-10.0, p270.elevationDeg, 1e-6)
        assertEquals(0.0, p270.rollDeg, 1e-6)

        // Upside-down (180°)
        val upsideDown = DeviceRotation.fromHeadingPitchRoll(headingDeg = 180.0, pitchDeg = 20.0, rollDeg = 180.0)
        val remapped180 = upsideDown.withDisplayRotation(180)
        assertOrthonormalProper(remapped180)
        val p180 = projector.pointing(remapped180)
        assertEquals(180.0, p180.azimuthDeg, 1e-6)
        assertEquals(20.0, p180.elevationDeg, 1e-6)
        assertEquals(0.0, p180.rollDeg, 1e-6)

        assertThrows(IllegalArgumentException::class.java) {
            portrait.withDisplayRotation(45)
        }
    }

    companion object {
        fun assertOrthonormalProper(rot: DeviceRotation, tol: Double = 1e-9) {
            for (i in 0..2) {
                for (j in 0..2) {
                    // (R^T R)[i, j] = sum_k R[k, i] * R[k, j]
                    val dot = rot[0, i] * rot[0, j] + rot[1, i] * rot[1, j] + rot[2, i] * rot[2, j]
                    val expected = if (i == j) 1.0 else 0.0
                    assertTrue("R^T R[$i,$j] = $dot expected $expected", abs(dot - expected) <= tol)
                }
            }
            val det = rot[0, 0] * (rot[1, 1] * rot[2, 2] - rot[1, 2] * rot[2, 1]) -
                rot[0, 1] * (rot[1, 0] * rot[2, 2] - rot[1, 2] * rot[2, 0]) +
                rot[0, 2] * (rot[1, 0] * rot[2, 1] - rot[1, 1] * rot[2, 0])
            assertEquals(1.0, det, tol)
        }
    }
}
