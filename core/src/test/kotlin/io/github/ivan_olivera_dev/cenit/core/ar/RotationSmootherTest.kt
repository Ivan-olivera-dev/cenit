package io.github.ivan_olivera_dev.cenit.core.ar

import io.github.ivan_olivera_dev.cenit.core.compass.CompassMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RotationSmootherTest {
    private val projector = ArProjector(CameraFov(60.0, 80.0), widthPx = 1000.0, heightPx = 1500.0)

    @Test
    fun `rejects alpha outside zero to one`() {
        assertThrows(IllegalArgumentException::class.java) { RotationSmoother(-0.1) }
        assertThrows(IllegalArgumentException::class.java) { RotationSmoother(1.1) }
        assertThrows(IllegalArgumentException::class.java) { RotationSmoother(Double.NaN) }
    }

    @Test
    fun `first sample snaps immediately and reset clears state`() {
        val smoother = RotationSmoother(alpha = 0.2)
        assertNull(smoother.current)

        val initial = DeviceRotation.fromHeadingPitchRoll(headingDeg = 239.0, pitchDeg = 12.0, rollDeg = -5.0)
        val first = smoother.update(initial)
        val p = projector.pointing(first)
        assertEquals(239.0, p.azimuthDeg, 1e-6)
        assertEquals(12.0, p.elevationDeg, 1e-6)
        assertEquals(-5.0, p.rollDeg, 1e-6)

        smoother.reset()
        assertNull(smoother.current)

        val afterReset = DeviceRotation.fromHeadingPitchRoll(headingDeg = 42.0, pitchDeg = -10.0)
        val snapped = smoother.update(afterReset)
        assertEquals(42.0, projector.pointing(snapped).azimuthDeg, 1e-6)
    }

    @Test
    fun `sequence across north 359 to 1 degrees smooths along shortest arc without flipping through south`() {
        val smoother = RotationSmoother(alpha = 0.5)
        val start = DeviceRotation.fromHeadingPitchRoll(headingDeg = 359.0, pitchDeg = 0.0)
        val target = DeviceRotation.fromHeadingPitchRoll(headingDeg = 1.0, pitchDeg = 0.0)

        smoother.update(start)
        val mid = smoother.update(target)
        DeviceRotationTest.assertOrthonormalProper(mid, tol = 1e-9)

        // Halfway along the shortest arc from 359 to 1 is 0 degrees (NOT 180)
        val midPointing = projector.pointing(mid)
        assertEquals(0.0, CompassMath.shortestArcDelta(0.0, midPointing.azimuthDeg), 1e-3)
        assertEquals(0.0, midPointing.elevationDeg, 1e-3)
        assertEquals(0.0, midPointing.rollDeg, 1e-3)

        // Continue stepping across North in both directions
        val headings = listOf(355.0, 358.0, 359.5, 0.5, 2.0, 5.0, 1.0, 359.0, 356.0)
        for (h in headings) {
            val out = smoother.update(DeviceRotation.fromHeadingPitchRoll(headingDeg = h, pitchDeg = 10.0))
            DeviceRotationTest.assertOrthonormalProper(out, tol = 1e-9)
            val az = projector.pointing(out).azimuthDeg
            // Must stay within 10 deg of North the entire time (never swing through South)
            assertTrue(
                "Azimuth flipped away from North: $az",
                abs(CompassMath.shortestArcDelta(0.0, az)) < 10.0,
            )
        }
    }

    @Test
    fun `sequence tilting over the zenith stays smooth without flipping and remains orthonormal`() {
        val smoother = RotationSmoother(alpha = 0.5)
        // Tilt smoothly around the East axis from elevation +70 deg through +90 (zenith) to +110 deg
        // Past zenith, the camera points South at elevation +70 with roll 180 deg.
        val samples = listOf(
            DeviceRotation.fromHeadingPitchRoll(headingDeg = 0.0, pitchDeg = 70.0, rollDeg = 0.0),
            DeviceRotation.fromHeadingPitchRoll(headingDeg = 0.0, pitchDeg = 80.0, rollDeg = 0.0),
            DeviceRotation.fromHeadingPitchRoll(headingDeg = 0.0, pitchDeg = 90.0, rollDeg = 0.0),
            DeviceRotation.fromHeadingPitchRoll(headingDeg = 180.0, pitchDeg = 80.0, rollDeg = 180.0),
            DeviceRotation.fromHeadingPitchRoll(headingDeg = 180.0, pitchDeg = 70.0, rollDeg = 180.0),
        )

        var prevForward = forwardOf(smoother.update(samples.first()))
        for (sample in samples.drop(1)) {
            val out = smoother.update(sample)
            DeviceRotationTest.assertOrthonormalProper(out, tol = 1e-9)
            val fwd = forwardOf(out)
            // Forward vector must stay in the upper hemisphere and step smoothly (< 15 deg per update)
            assertTrue("Expected forward vector pointing upward near zenith, got z=${fwd[2]}", fwd[2] > 0.85)
            val dot = prevForward[0] * fwd[0] + prevForward[1] * fwd[1] + prevForward[2] * fwd[2]
            assertTrue("Detected flip across zenith: dot=$dot", dot > 0.96)
            // East axis (screen right) stays aligned with World East (+1, 0, 0) throughout
            assertEquals(1.0, out[0, 0], 1e-6)
            prevForward = fwd
        }
    }

    @Test
    fun `smoothed output remains orthonormal after many updates`() {
        val smoother = RotationSmoother(alpha = 0.18)
        for (i in 0 until 200) {
            val h = (i * 7.3) % 360.0
            val p = ((i % 120) - 60).toDouble()
            val r = ((i * 5) % 180 - 90).toDouble()
            val out = smoother.update(DeviceRotation.fromHeadingPitchRoll(h, p, r))
            DeviceRotationTest.assertOrthonormalProper(out, tol = 1e-9)
        }
    }

    private fun forwardOf(r: DeviceRotation): DoubleArray =
        doubleArrayOf(-r[0, 2], -r[1, 2], -r[2, 2])
}
