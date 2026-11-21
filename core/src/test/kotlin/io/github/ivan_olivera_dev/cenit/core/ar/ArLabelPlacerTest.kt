package io.github.ivan_olivera_dev.cenit.core.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArLabelPlacerTest {

    @Test
    fun `places non-overlapping labels strictly inside viewport bounds`() {
        val bounds = ViewportRect(left = 12.0, top = 80.0, right = 348.0, bottom = 580.0)
        val placer = ArLabelPlacer(bounds = bounds, defaultPadding = 4.0)

        val placed = mutableListOf<ViewportRect>()
        // Attempt to place 8 labels clustered around the same anchor using multiple candidate offsets
        val anchorX = 180.0
        val anchorY = 250.0
        for (i in 0 until 8) {
            val candidates = listOf(
                anchorX to anchorY,
                anchorX + 55.0 to anchorY,
                anchorX - 55.0 to anchorY,
                anchorX to anchorY + 26.0,
                anchorX to anchorY - 26.0,
                anchorX + 55.0 to anchorY + 26.0,
                anchorX - 55.0 to anchorY + 26.0,
                anchorX + 55.0 to anchorY - 26.0,
                anchorX - 55.0 to anchorY - 26.0,
            )
            placer.tryPlace(width = 48.0, height = 20.0, candidates = candidates)?.let { placed.add(it) }
        }

        assertEquals(8, placed.size)
        for (rect in placed) {
            assertTrue("Expected $rect inside $bounds", rect.isInside(bounds))
        }
        for (i in placed.indices) {
            for (j in i + 1 until placed.size) {
                assertFalse(
                    "Placed labels $i (${placed[i]}) and $j (${placed[j]}) overlap",
                    placed[i].intersects(placed[j], padding = 4.0),
                )
            }
        }
    }

    @Test
    fun `never places a label overlapping a reserved crosshair region`() {
        val bounds = ViewportRect(left = 0.0, top = 0.0, right = 360.0, bottom = 640.0)
        val placer = ArLabelPlacer(bounds = bounds, defaultPadding = 4.0)

        val crosshair = ViewportRect.centeredAt(cx = 180.0, cy = 320.0, width = 64.0, height = 64.0)
        placer.reserve(crosshair)

        // Single candidate right on top of the crosshair must be rejected
        assertNull(
            placer.tryPlaceAt(cx = 180.0, cy = 320.0, width = 40.0, height = 18.0),
        )

        // Fallback list skips the crosshair candidate and picks the clear candidate below
        val placed = placer.tryPlace(
            width = 40.0,
            height = 18.0,
            candidates = listOf(
                180.0 to 320.0,
                180.0 to 375.0,
            ),
        )
        assertNotNull(placed)
        assertFalse(placed!!.intersects(crosshair, padding = 4.0))
        assertTrue(placed.isInside(bounds))
    }

    @Test
    fun `clamps edge candidates inside bounds when clampToBounds is true and skips when false`() {
        val bounds = ViewportRect(left = 10.0, top = 50.0, right = 350.0, bottom = 500.0)
        val placer = ArLabelPlacer(bounds = bounds, defaultPadding = 2.0)

        val unclamped = placer.tryPlaceAt(
            cx = 5.0,
            cy = 200.0,
            width = 40.0,
            height = 20.0,
            clampToBounds = false,
        )
        assertNull(unclamped)

        val clamped = placer.tryPlaceAt(
            cx = 5.0,
            cy = 200.0,
            width = 40.0,
            height = 20.0,
            clampToBounds = true,
        )
        assertNotNull(clamped)
        assertEquals(10.0, clamped!!.left, 1e-6)
        assertEquals(50.0, clamped.right, 1e-6)
        assertTrue(clamped.isInside(bounds))
    }

    @Test
    fun `returns null when label is larger than viewport or no candidate fits`() {
        val tinyBounds = ViewportRect(left = 0.0, top = 0.0, right = 30.0, bottom = 20.0)
        val placer = ArLabelPlacer(bounds = tinyBounds, defaultPadding = 2.0)

        assertNull(placer.tryPlaceAt(cx = 15.0, cy = 10.0, width = 50.0, height = 10.0, clampToBounds = true))
        assertNotNull(placer.tryPlaceAt(cx = 15.0, cy = 10.0, width = 20.0, height = 12.0, clampToBounds = true))
        // Viewport is now saturated
        assertNull(placer.tryPlaceAt(cx = 15.0, cy = 10.0, width = 20.0, height = 12.0, clampToBounds = true))
    }

    @Test
    fun `reset clears previous reservations and updates bounds for next frame reuse`() {
        val bounds = ViewportRect(left = 0.0, top = 0.0, right = 200.0, bottom = 200.0)
        val placer = ArLabelPlacer(bounds = bounds, defaultPadding = 2.0)

        placer.reserve(ViewportRect.centeredAt(100.0, 100.0, 60.0, 30.0))
        assertNull(placer.tryPlaceAt(100.0, 100.0, 40.0, 20.0))

        val nextBounds = ViewportRect(left = 0.0, top = 0.0, right = 300.0, bottom = 300.0)
        placer.reset(nextBounds)

        val placed = placer.tryPlaceAt(100.0, 100.0, 40.0, 20.0)
        assertNotNull(placed)
        assertTrue(placed!!.isInside(nextBounds))
    }
}
