package io.github.ivan_olivera_dev.cenit.core.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class ArProjectorTest {
    private val fov = CameraFov(horizontalDeg = 60.0, verticalDeg = 90.0)
    private val projector = ArProjector(fov = fov, widthPx = 1000.0, heightPx = 2000.0)

    @Test
    fun `rejects invalid CameraFov and non-positive viewport dimensions`() {
        assertThrows(IllegalArgumentException::class.java) { CameraFov(0.0, 60.0) }
        assertThrows(IllegalArgumentException::class.java) { CameraFov(180.0, 60.0) }
        assertThrows(IllegalArgumentException::class.java) { CameraFov(60.0, -10.0) }
        assertThrows(IllegalArgumentException::class.java) { CameraFov(Double.NaN, 60.0) }
        assertThrows(IllegalArgumentException::class.java) { ArProjector(fov, 0.0, 1000.0) }
        assertThrows(IllegalArgumentException::class.java) { ArProjector(fov, 1000.0, -1.0) }
    }

    @Test
    fun `north at horizon projects az 0 el 0 to exact screen centre`() {
        val north = DeviceRotation.fromHeadingPitchRoll(headingDeg = 0.0, pitchDeg = 0.0, rollDeg = 0.0)
        val p = projector.project(north, azimuthDeg = 0.0, elevationDeg = 0.0)
        assertNotNull(p)
        assertEquals(500.0, p!!.x, 1e-6)
        assertEquals(1000.0, p.y, 1e-6)
        assertTrue(p.inView)
    }

    @Test
    fun `half horizontal and vertical FOV project onto exact viewport edges`() {
        val north = DeviceRotation.fromHeadingPitchRoll(headingDeg = 0.0, pitchDeg = 0.0, rollDeg = 0.0)

        // +hFov/2 -> right edge
        val right = projector.project(north, azimuthDeg = fov.horizontalDeg / 2.0, elevationDeg = 0.0)!!
        assertEquals(1000.0, right.x, 1e-6)
        assertEquals(1000.0, right.y, 1e-6)
        assertTrue(right.inView)

        // -hFov/2 -> left edge
        val left = projector.project(north, azimuthDeg = 360.0 - fov.horizontalDeg / 2.0, elevationDeg = 0.0)!!
        assertEquals(0.0, left.x, 1e-6)
        assertEquals(1000.0, left.y, 1e-6)
        assertTrue(left.inView)

        // +vFov/2 -> top edge (y = 0, y down)
        val top = projector.project(north, azimuthDeg = 0.0, elevationDeg = fov.verticalDeg / 2.0)!!
        assertEquals(500.0, top.x, 1e-6)
        assertEquals(0.0, top.y, 1e-6)
        assertTrue(top.inView)

        // -vFov/2 -> bottom edge (y = heightPx)
        val bottom = projector.project(north, azimuthDeg = 0.0, elevationDeg = -fov.verticalDeg / 2.0)!!
        assertEquals(500.0, bottom.x, 1e-6)
        assertEquals(2000.0, bottom.y, 1e-6)
        assertTrue(bottom.inView)

        // Outside FOV but still in front of camera -> non-null with inView = false
        val outside = projector.project(north, azimuthDeg = 45.0, elevationDeg = 0.0)!!
        assertTrue(outside.x > 1000.0)
        assertFalse(outside.inView)
    }

    @Test
    fun `azimuth wraps cleanly across 359 and 1 degrees`() {
        val facing359 = DeviceRotation.fromHeadingPitchRoll(headingDeg = 359.0, pitchDeg = 0.0)
        val facing0 = DeviceRotation.fromHeadingPitchRoll(headingDeg = 0.0, pitchDeg = 0.0)
        val facing1 = DeviceRotation.fromHeadingPitchRoll(headingDeg = 1.0, pitchDeg = 0.0)

        val pRightOf359 = projector.project(facing359, azimuthDeg = 1.0, elevationDeg = 0.0)!!
        val pRightOf0 = projector.project(facing0, azimuthDeg = 2.0, elevationDeg = 0.0)!!
        assertEquals(pRightOf0.x, pRightOf359.x, 1e-6)
        assertEquals(pRightOf0.y, pRightOf359.y, 1e-6)
        assertTrue(pRightOf359.x > 500.0)

        val pLeftOf1 = projector.project(facing1, azimuthDeg = 359.0, elevationDeg = 0.0)!!
        assertEquals(1000.0 - pRightOf0.x, pLeftOf1.x, 1e-6)
        assertTrue(pLeftOf1.x < 500.0)
    }

    @Test
    fun `target behind camera at az 180 returns null and edge arrow points down sensibly`() {
        val north = DeviceRotation.fromHeadingPitchRoll(headingDeg = 0.0, pitchDeg = 0.0)
        assertNull(projector.project(north, azimuthDeg = 180.0, elevationDeg = 0.0))

        val inset = 24.0
        val arrow = projector.edgeArrow(north, azimuthDeg = 180.0, elevationDeg = 0.0, insetPx = inset)
        assertNotNull(arrow)
        assertEquals(500.0, arrow!!.x, 1e-6)
        assertEquals(2000.0 - inset, arrow.y, 1e-6)
        assertEquals(PI / 2.0, arrow.angleRad, 1e-6)
    }

    @Test
    fun `edge arrow returns null for targets in view and clamps out-of-view and behind targets to inset edge`() {
        val north = DeviceRotation.fromHeadingPitchRoll(headingDeg = 0.0, pitchDeg = 0.0)
        val inset = 30.0

        // Target in view -> null
        assertNull(projector.edgeArrow(north, azimuthDeg = 5.0, elevationDeg = 5.0, insetPx = inset))

        // Target out of view to the right (in front of camera plane)
        val rightFront = projector.edgeArrow(north, azimuthDeg = 50.0, elevationDeg = 0.0, insetPx = inset)!!
        assertEquals(1000.0 - inset, rightFront.x, 1e-6)
        assertEquals(1000.0, rightFront.y, 1e-6)
        assertEquals(0.0, rightFront.angleRad, 1e-6)

        // Target behind and to the right (az 120) -> points right on the right inset edge
        val rightBehind = projector.edgeArrow(north, azimuthDeg = 120.0, elevationDeg = 0.0, insetPx = inset)!!
        assertEquals(1000.0 - inset, rightBehind.x, 1e-6)
        assertEquals(1000.0, rightBehind.y, 1e-6)
        assertEquals(0.0, rightBehind.angleRad, 1e-6)

        // Target behind and to the left (az 240) -> points left on the left inset edge
        val leftBehind = projector.edgeArrow(north, azimuthDeg = 240.0, elevationDeg = 0.0, insetPx = inset)!!
        assertEquals(inset, leftBehind.x, 1e-6)
        assertEquals(1000.0, leftBehind.y, 1e-6)
        assertEquals(PI, abs(leftBehind.angleRad), 1e-6)

        // Target high above view -> top inset edge pointing up (-PI/2)
        val above = projector.edgeArrow(north, azimuthDeg = 0.0, elevationDeg = 75.0, insetPx = inset)!!
        assertEquals(500.0, above.x, 1e-6)
        assertEquals(inset, above.y, 1e-6)
        assertEquals(-PI / 2.0, above.angleRad, 1e-6)
    }

    @Test
    fun `pointing east tilted up 30 degrees reports azimuth 90 elevation 30 roll 0`() {
        val rot = DeviceRotation.fromHeadingPitchRoll(headingDeg = 90.0, pitchDeg = 30.0, rollDeg = 0.0)
        val pointing = projector.pointing(rot)
        assertEquals(90.0, pointing.azimuthDeg, 1e-6)
        assertEquals(30.0, pointing.elevationDeg, 1e-6)
        assertEquals(0.0, pointing.rollDeg, 1e-6)

        // Crosshair direction projects to exact screen centre
        val center = projector.project(rot, azimuthDeg = 90.0, elevationDeg = 30.0)!!
        assertEquals(500.0, center.x, 1e-6)
        assertEquals(1000.0, center.y, 1e-6)
    }

    @Test
    fun `roll 90 degrees landscape swaps horizontal and vertical screen axes`() {
        val squareProjector = ArProjector(
            fov = CameraFov(horizontalDeg = 60.0, verticalDeg = 60.0),
            widthPx = 1000.0,
            heightPx = 1000.0,
        )
        // Rotate phone 90 deg clockwise around optical axis (top of phone now points East)
        val landscapeCw = DeviceRotation.fromHeadingPitchRoll(headingDeg = 0.0, pitchDeg = 0.0, rollDeg = 90.0)
        val pointing = squareProjector.pointing(landscapeCw)
        assertEquals(0.0, pointing.azimuthDeg, 1e-6)
        assertEquals(0.0, pointing.elevationDeg, 1e-6)
        assertEquals(90.0, pointing.rollDeg, 1e-6)

        // Target slightly East (az +15, el 0) now projects straight UP on screen (y < 500, x == 500)
        val eastTarget = squareProjector.project(landscapeCw, azimuthDeg = 15.0, elevationDeg = 0.0)!!
        assertEquals(500.0, eastTarget.x, 1e-6)
        assertTrue("Expected east target above centre, got y=${eastTarget.y}", eastTarget.y < 500.0)

        // Target slightly above horizon (az 0, el +15) now projects straight LEFT on screen (x < 500, y == 500)
        val upTarget = squareProjector.project(landscapeCw, azimuthDeg = 0.0, elevationDeg = 15.0)!!
        assertEquals(500.0, upTarget.y, 1e-6)
        assertTrue("Expected elevated target left of centre, got x=${upTarget.x}", upTarget.x < 500.0)
        assertEquals(500.0 - eastTarget.y, 500.0 - upTarget.x, 1e-6)
    }

    @Test
    fun `zenith and nadir rotations never produce NaN and project straight-ahead poles to centre`() {
        val zenith = DeviceRotation.fromHeadingPitchRoll(headingDeg = 45.0, pitchDeg = 90.0, rollDeg = 0.0)
        val zenithPointing = projector.pointing(zenith)
        assertFalse(zenithPointing.azimuthDeg.isNaN())
        assertFalse(zenithPointing.elevationDeg.isNaN())
        assertFalse(zenithPointing.rollDeg.isNaN())
        assertEquals(45.0, zenithPointing.azimuthDeg, 1e-6)
        assertEquals(90.0, zenithPointing.elevationDeg, 1e-6)
        assertEquals(0.0, zenithPointing.rollDeg, 1e-6)

        val zCenter = projector.project(zenith, azimuthDeg = 123.0, elevationDeg = 90.0)!!
        assertEquals(500.0, zCenter.x, 1e-6)
        assertEquals(1000.0, zCenter.y, 1e-6)
        assertNull(projector.project(zenith, azimuthDeg = 0.0, elevationDeg = -90.0))

        val nadir = DeviceRotation.fromHeadingPitchRoll(headingDeg = 135.0, pitchDeg = -90.0, rollDeg = 0.0)
        val nadirPointing = projector.pointing(nadir)
        assertFalse(nadirPointing.azimuthDeg.isNaN())
        assertFalse(nadirPointing.elevationDeg.isNaN())
        assertFalse(nadirPointing.rollDeg.isNaN())
        assertEquals(135.0, nadirPointing.azimuthDeg, 1e-6)
        assertEquals(-90.0, nadirPointing.elevationDeg, 1e-6)
        assertEquals(0.0, nadirPointing.rollDeg, 1e-6)

        val nCenter = projector.project(nadir, azimuthDeg = 0.0, elevationDeg = -90.0)!!
        assertEquals(500.0, nCenter.x, 1e-6)
        assertEquals(1000.0, nCenter.y, 1e-6)
        assertNull(projector.project(nadir, azimuthDeg = 0.0, elevationDeg = 90.0))
    }
}
