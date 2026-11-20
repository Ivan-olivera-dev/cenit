package io.github.ivan_olivera_dev.cenit.core.compass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class CompassLabelPlacementTest {

    private val cardinals = listOf(
        CompassRingObstacle(0.0, 5.0),
        CompassRingObstacle(90.0, 5.0),
        CompassRingObstacle(180.0, 5.0),
        CompassRingObstacle(270.0, 5.0),
    )

    private val defaultLabelWidth = 24.0

    @Test
    fun `observed Madrid Nov 20 case clears E and O while keeping sunrise and sunset untouched`() {
        val labels = listOf(
            CompassRingLabel("sunrise", anchorAzimuthDeg = 116.0, angularWidthDeg = defaultLabelWidth),
            CompassRingLabel("sunset", anchorAzimuthDeg = 244.0, angularWidthDeg = defaultLabelWidth),
            CompassRingLabel("moonrise", anchorAzimuthDeg = 84.0, angularWidthDeg = defaultLabelWidth),
            CompassRingLabel("moonset", anchorAzimuthDeg = 272.0, angularWidthDeg = defaultLabelWidth),
        )

        val placed = CompassLabelPlacement.place(labels, cardinals)
        val byId = placed.associateBy { it.id }

        // Sunrise (116°) and sunset (244°) have no conflict: stay at exact anchor on tier 0
        assertEquals(116.0, byId.getValue("sunrise").placedAzimuthDeg, 1e-3)
        assertEquals(0.0, byId.getValue("sunrise").angularOffsetDeg, 1e-3)
        assertEquals(0, byId.getValue("sunrise").radialTier)

        assertEquals(244.0, byId.getValue("sunset").placedAzimuthDeg, 1e-3)
        assertEquals(0.0, byId.getValue("sunset").angularOffsetDeg, 1e-3)
        assertEquals(0, byId.getValue("sunset").radialTier)

        // Moonrise (84° vs E at 90°) nudges CCW below 84°, clear of E (half 12 + 2.5 = 14.5°)
        val mr = byId.getValue("moonrise")
        assertEquals(0, mr.radialTier)
        assertTrue("Expected moonrise nudged CCW, got offset ${mr.angularOffsetDeg}", mr.angularOffsetDeg < 0.0)
        assertTrue(
            "Expected moonrise >= 14.5° from E (90°), was at ${mr.placedAzimuthDeg}",
            abs(CompassMath.shortestArcDelta(mr.placedAzimuthDeg, 90.0)) >= 14.5 - 1e-3,
        )

        // Moonset (272° vs O at 270°) nudges CW above 272°, clear of O
        val ms = byId.getValue("moonset")
        assertEquals(0, ms.radialTier)
        assertTrue("Expected moonset nudged CW, got offset ${ms.angularOffsetDeg}", ms.angularOffsetDeg > 0.0)
        assertTrue(
            "Expected moonset >= 14.5° from O (270°), was at ${ms.placedAzimuthDeg}",
            abs(CompassMath.shortestArcDelta(ms.placedAzimuthDeg, 270.0)) >= 14.5 - 1e-3,
        )

        assertNoAngularOverlaps(placed, labels, cardinals)
    }

    @Test
    fun `sunrise 116 and sunset 244 alone have zero conflict and zero offset`() {
        val labels = listOf(
            CompassRingLabel("sunrise", anchorAzimuthDeg = 116.0, angularWidthDeg = defaultLabelWidth),
            CompassRingLabel("sunset", anchorAzimuthDeg = 244.0, angularWidthDeg = defaultLabelWidth),
        )

        val placed = CompassLabelPlacement.place(labels, cardinals)

        assertEquals(2, placed.size)
        assertEquals(116.0, placed[0].placedAzimuthDeg, 1e-6)
        assertEquals(0.0, placed[0].angularOffsetDeg, 1e-6)
        assertEquals(0, placed[0].radialTier)

        assertEquals(244.0, placed[1].placedAzimuthDeg, 1e-6)
        assertEquals(0.0, placed[1].angularOffsetDeg, 1e-6)
        assertEquals(0, placed[1].radialTier)
    }

    @Test
    fun `two labels at the same angle separate without overlapping each other or cardinals`() {
        val labels = listOf(
            CompassRingLabel("sunrise", anchorAzimuthDeg = 116.0, angularWidthDeg = defaultLabelWidth),
            CompassRingLabel("moonrise", anchorAzimuthDeg = 116.0, angularWidthDeg = defaultLabelWidth),
        )

        val placed = CompassLabelPlacement.place(labels, cardinals)

        assertEquals(2, placed.size)
        assertTrue(placed.all { it.radialTier == 0 })
        val separation = abs(CompassMath.shortestArcDelta(placed[0].placedAzimuthDeg, placed[1].placedAzimuthDeg))
        assertTrue("Expected separation >= 24°, got $separation", separation >= defaultLabelWidth - 1e-3)
        assertNoAngularOverlaps(placed, labels, cardinals)
    }

    @Test
    fun `two labels both exactly on a cardinal split cleanly to opposite sides`() {
        val labels = listOf(
            CompassRingLabel("sunrise", anchorAzimuthDeg = 90.0, angularWidthDeg = defaultLabelWidth),
            CompassRingLabel("moonrise", anchorAzimuthDeg = 90.0, angularWidthDeg = defaultLabelWidth),
        )

        val placed = CompassLabelPlacement.place(labels, cardinals)

        assertEquals(2, placed.size)
        assertTrue(placed.all { it.radialTier == 0 })
        assertNoAngularOverlaps(placed, labels, cardinals)
    }

    @Test
    fun `single label exactly on a cardinal moves off the cardinal`() {
        for (cardinalAz in listOf(0.0, 90.0, 180.0, 270.0)) {
            val labels = listOf(
                CompassRingLabel("exact", anchorAzimuthDeg = cardinalAz, angularWidthDeg = defaultLabelWidth),
            )
            val placed = CompassLabelPlacement.place(labels, cardinals)
            assertEquals(1, placed.size)
            assertEquals(0, placed[0].radialTier)
            val dist = abs(CompassMath.shortestArcDelta(placed[0].placedAzimuthDeg, cardinalAz))
            assertTrue(
                "Expected label at $cardinalAz° to clear cardinal by >= 14.5°, was $dist°",
                dist >= 14.5 - 1e-3,
            )
            assertNoAngularOverlaps(placed, labels, cardinals)
        }
    }

    @Test
    fun `label on a cardinal chooses the free side when the other side is occupied`() {
        val labels = listOf(
            CompassRingLabel("on_east", anchorAzimuthDeg = 90.0, angularWidthDeg = defaultLabelWidth),
            CompassRingLabel("south_of_east", anchorAzimuthDeg = 108.0, angularWidthDeg = defaultLabelWidth),
        )

        val placed = CompassLabelPlacement.place(labels, cardinals)
        val byId = placed.associateBy { it.id }

        // Since 108° occupies the CW side of E (90°), on_east should nudge CCW (< 90°)
        assertTrue(
            "Expected on_east to move CCW (< 90°), was ${byId.getValue("on_east").placedAzimuthDeg}",
            byId.getValue("on_east").placedAzimuthDeg < 90.0,
        )
        assertEquals(108.0, byId.getValue("south_of_east").placedAzimuthDeg, 1e-3)
        assertNoAngularOverlaps(placed, labels, cardinals)
    }

    @Test
    fun `labels straddling north 359 to 0 wrap clear N and each other`() {
        val labels = listOf(
            CompassRingLabel("west_of_n", anchorAzimuthDeg = 358.0, angularWidthDeg = defaultLabelWidth),
            CompassRingLabel("east_of_n", anchorAzimuthDeg = 2.0, angularWidthDeg = defaultLabelWidth),
        )

        val placed = CompassLabelPlacement.place(labels, cardinals)

        assertTrue(placed.all { it.radialTier == 0 })
        assertTrue(placed[0].placedAzimuthDeg in 320.0..346.0)
        assertTrue(placed[1].placedAzimuthDeg in 14.0..40.0)
        assertNoAngularOverlaps(placed, labels, cardinals)
    }

    @Test
    fun `crowded labels overflow cleanly to radialTier 1 without overlapping on either ring`() {
        val labels = listOf(
            CompassRingLabel("a", anchorAzimuthDeg = 45.0, angularWidthDeg = 30.0),
            CompassRingLabel("b", anchorAzimuthDeg = 45.0, angularWidthDeg = 30.0),
            CompassRingLabel("c", anchorAzimuthDeg = 45.0, angularWidthDeg = 30.0),
            CompassRingLabel("d", anchorAzimuthDeg = 45.0, angularWidthDeg = 30.0),
        )

        val placed = CompassLabelPlacement.place(labels, cardinals, maxAngularShiftDeg = 35.0)

        assertTrue("Expected at least one label moved to outer radialTier 1", placed.any { it.radialTier == 1 })
        assertNoAngularOverlaps(placed, labels, cardinals)
    }

    @Test
    fun `computed angular widths guarantee 2D box separation at every compass heading 0 to 359`() {
        val labelW = 34.0
        val labelH = 14.0
        val cardinalW = 10.0
        val cardinalH = 14.0
        val pad = 2.0
        val labelR = 97.0
        val cardinalR = 111.0

        val pairWidthDeg = CompassLabelPlacement.requiredPairAngularWidthDeg(
            labelWidth = labelW,
            labelHeight = labelH,
            ringRadius = labelR,
            padding = pad,
        )
        val cardinalClearanceDeg = CompassLabelPlacement.requiredCardinalClearanceDeg(
            labelWidth = labelW,
            labelHeight = labelH,
            labelRingRadius = labelR,
            cardinalWidth = cardinalW,
            cardinalHeight = cardinalH,
            cardinalRingRadius = cardinalR,
            padding = pad,
        )
        val obstacleWidthDeg = CompassLabelPlacement.obstacleAngularWidthDeg(
            pairAngularWidthDeg = pairWidthDeg,
            cardinalClearanceDeg = cardinalClearanceDeg,
        )
        val obs = listOf(0.0, 90.0, 180.0, 270.0).map { CompassRingObstacle(it, obstacleWidthDeg) }

        val scenarios = listOf(
            listOf(116.0, 244.0, 84.0, 272.0),
            listOf(90.0, 270.0, 90.0, 270.0),
            listOf(0.0, 180.0, 358.0, 182.0),
            listOf(116.0, 116.0, 244.0, 244.0),
        )

        for (angles in scenarios) {
            val labels = angles.mapIndexed { i, az -> CompassRingLabel("l$i", az, pairWidthDeg) }
            val placed = CompassLabelPlacement.place(labels, obs)

            for (heading in 0 until 360 step 3) {
                val tier0 = placed.filter { it.radialTier == 0 }
                // Check every tier-0 label against every cardinal letter box
                for (p in tier0) {
                    val (lx, ly) = polar(labelR, p.placedAzimuthDeg, heading.toDouble())
                    for (cAz in listOf(0.0, 90.0, 180.0, 270.0)) {
                        val (cx, cy) = polar(cardinalR, cAz, heading.toDouble())
                        assertFalse(
                            "Overlap with cardinal $cAz° at heading $heading° for ${p.id} (${p.placedAzimuthDeg}°)",
                            boxesOverlap(lx, ly, labelW, labelH, cx, cy, cardinalW, cardinalH, pad),
                        )
                    }
                }
                // Check every pair on tier 0
                for (i in tier0.indices) {
                    val (x1, y1) = polar(labelR, tier0[i].placedAzimuthDeg, heading.toDouble())
                    for (j in i + 1 until tier0.size) {
                        val (x2, y2) = polar(labelR, tier0[j].placedAzimuthDeg, heading.toDouble())
                        assertFalse(
                            "Overlap between ${tier0[i].id} and ${tier0[j].id} at heading $heading°",
                            boxesOverlap(x1, y1, labelW, labelH, x2, y2, labelW, labelH, pad),
                        )
                    }
                }
            }
        }
    }

    private fun polar(r: Double, azDeg: Double, headingDeg: Double): Pair<Double, Double> {
        val rad = Math.toRadians(azDeg - headingDeg)
        return r * sin(rad) to -r * cos(rad)
    }

    private fun boxesOverlap(
        cx1: Double,
        cy1: Double,
        w1: Double,
        h1: Double,
        cx2: Double,
        cy2: Double,
        w2: Double,
        h2: Double,
        pad: Double,
    ): Boolean = abs(cx1 - cx2) < (w1 + w2) / 2.0 + pad - 1e-6 &&
        abs(cy1 - cy2) < (h1 + h2) / 2.0 + pad - 1e-6

    private fun assertNoAngularOverlaps(
        placed: List<PlacedCompassLabel>,
        labels: List<CompassRingLabel>,
        obstacles: List<CompassRingObstacle>,
    ) {
        val widths = labels.associate { it.id to it.angularWidthDeg }
        val byTier = placed.groupBy { it.radialTier }

        // On tier 0, check clearance against all obstacles
        for (p in byTier[0].orEmpty()) {
            val hw = widths.getValue(p.id) / 2.0
            for (o in obstacles) {
                val dist = abs(CompassMath.shortestArcDelta(p.placedAzimuthDeg, o.azimuthDeg))
                val req = hw + o.angularWidthDeg / 2.0
                assertTrue(
                    "Label ${p.id} at ${p.placedAzimuthDeg}° overlaps obstacle at ${o.azimuthDeg}° (dist=$dist < req=$req)",
                    dist >= req - 1e-3,
                )
            }
        }

        // On every tier, check pairwise clearance
        for ((tier, group) in byTier) {
            for (i in group.indices) {
                for (j in i + 1 until group.size) {
                    val a = group[i]
                    val b = group[j]
                    val dist = abs(CompassMath.shortestArcDelta(a.placedAzimuthDeg, b.placedAzimuthDeg))
                    val req = (widths.getValue(a.id) + widths.getValue(b.id)) / 2.0
                    assertTrue(
                        "Tier $tier labels ${a.id} (${a.placedAzimuthDeg}°) and ${b.id} (${b.placedAzimuthDeg}°) overlap (dist=$dist < req=$req)",
                        dist >= req - 1e-3,
                    )
                }
            }
        }
    }
}
