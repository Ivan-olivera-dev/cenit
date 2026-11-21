package io.github.ivan_olivera_dev.cenit.core.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadingTapeModelTest {

    @Test
    fun `generates 5-degree ticks in monotonic left-to-right order around interior heading`() {
        val ticks = HeadingTapeModel.ticksForHeading(headingDeg = 239.0, spanDeg = 96.0)

        assertTrue("Expected multiple ticks across 96 deg span", ticks.size >= 18)
        for (i in 1 until ticks.size) {
            assertTrue(
                "Ticks must be strictly increasing left-to-right: ${ticks[i - 1]} vs ${ticks[i]}",
                ticks[i].normalizedPosition > ticks[i - 1].normalizedPosition,
            )
        }
        assertTrue(ticks.all { it.azimuthDeg in 0..359 && it.azimuthDeg % 5 == 0 })
        assertTrue(ticks.all { it.normalizedPosition in 0.0..1.0 })

        // Verify major / cardinal / quadrant classifications
        val tick225 = ticks.first { it.azimuthDeg == 225 }
        assertTrue(tick225.isMajor)
        assertTrue(tick225.isCardinal)
        assertFalse(tick225.isQuadrant)
        assertEquals(10, tick225.windIndex) // SW / SO

        val tick270 = ticks.first { it.azimuthDeg == 270 }
        assertTrue(tick270.isMajor)
        assertTrue(tick270.isCardinal)
        assertTrue(tick270.isQuadrant)
        assertEquals(12, tick270.windIndex) // W / O

        val tick240 = ticks.first { it.azimuthDeg == 240 }
        assertTrue(tick240.isMajor)
        assertFalse(tick240.isCardinal)
        assertNull(tick240.windIndex)

        val tick235 = ticks.first { it.azimuthDeg == 235 }
        assertFalse(tick235.isMajor)
        assertFalse(tick235.isCardinal)
        assertNull(tick235.windIndex)
    }

    @Test
    fun `wraps smoothly across 359 to 0 degrees without gaps or reversed order`() {
        val ticksNearZero = HeadingTapeModel.ticksForHeading(headingDeg = 2.0, spanDeg = 96.0)
        val azimuths = ticksNearZero.map { it.azimuthDeg }

        assertTrue("Expected 355 before 0, got $azimuths", azimuths.contains(355))
        assertTrue("Expected 0 (North), got $azimuths", azimuths.contains(0))
        assertTrue("Expected 5 after 0, got $azimuths", azimuths.contains(5))
        assertTrue(azimuths.indexOf(355) < azimuths.indexOf(0))
        assertTrue(azimuths.indexOf(0) < azimuths.indexOf(5))

        val north = ticksNearZero.first { it.azimuthDeg == 0 }
        assertTrue(north.isQuadrant)
        assertEquals(0, north.windIndex)
        // North is 2° left of heading 2°, so normalizedPosition = 0.5 - 2/96
        assertEquals(0.5 - 2.0 / 96.0, north.normalizedPosition, 1e-6)

        val ticksNear358 = HeadingTapeModel.ticksForHeading(headingDeg = 358.0, spanDeg = 96.0)
        val northRight = ticksNear358.first { it.azimuthDeg == 0 }
        assertEquals(0.5 + 2.0 / 96.0, northRight.normalizedPosition, 1e-6)
    }

    @Test
    fun `markerPosition maps target azimuth onto tape including wrap and returns null outside span`() {
        // Exact center
        val center = HeadingTapeModel.markerPosition(headingDeg = 180.0, targetAzimuthDeg = 180.0, spanDeg = 96.0)
        assertNotNull(center)
        assertEquals(0.5, center!!, 1e-6)

        // Wrap across North: heading 355°, target 5° -> +10° right of center
        val wrappedRight = HeadingTapeModel.markerPosition(headingDeg = 355.0, targetAzimuthDeg = 5.0, spanDeg = 96.0)
        assertNotNull(wrappedRight)
        assertEquals(0.5 + 10.0 / 96.0, wrappedRight!!, 1e-6)

        // Wrap across North: heading 5°, target 355° -> -10° left of center
        val wrappedLeft = HeadingTapeModel.markerPosition(headingDeg = 5.0, targetAzimuthDeg = 355.0, spanDeg = 96.0)
        assertNotNull(wrappedLeft)
        assertEquals(0.5 - 10.0 / 96.0, wrappedLeft!!, 1e-6)

        // Opposite side of compass is outside the 96° span
        assertNull(HeadingTapeModel.markerPosition(headingDeg = 10.0, targetAzimuthDeg = 190.0, spanDeg = 96.0))
    }
}
