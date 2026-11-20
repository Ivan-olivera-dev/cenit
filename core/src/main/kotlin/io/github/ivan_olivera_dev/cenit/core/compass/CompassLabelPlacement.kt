package io.github.ivan_olivera_dev.cenit.core.compass

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.hypot

/**
 * Label anchored to an azimuth on the compass dome (e.g. sunrise/sunset or moonrise/moonset time).
 *
 * @property id Stable caller-defined identifier echoed in [PlacedCompassLabel.id].
 * @property anchorAzimuthDeg True azimuth of the event tick/ray in degrees.
 * @property angularWidthDeg Angular footprint occupied by the label on the ring in degrees (`> 0`).
 */
data class CompassRingLabel(
    val id: String,
    val anchorAzimuthDeg: Double,
    val angularWidthDeg: Double,
) {
    init {
        require(anchorAzimuthDeg.isFinite()) {
            "anchorAzimuthDeg must be finite, was $anchorAzimuthDeg"
        }
        require(angularWidthDeg.isFinite() && angularWidthDeg > 0.0) {
            "angularWidthDeg must be finite and positive, was $angularWidthDeg"
        }
    }
}

/**
 * Fixed obstacle on the primary ring (such as a cardinal letter `N`, `E`, `S`, `O`/`W`).
 *
 * @property azimuthDeg Centre azimuth of the obstacle in degrees.
 * @property angularWidthDeg Angular width occupied by the obstacle in degrees (`>= 0`).
 */
data class CompassRingObstacle(
    val azimuthDeg: Double,
    val angularWidthDeg: Double,
) {
    init {
        require(azimuthDeg.isFinite()) { "azimuthDeg must be finite, was $azimuthDeg" }
        require(angularWidthDeg.isFinite() && angularWidthDeg >= 0.0) {
            "angularWidthDeg must be finite and non-negative, was $angularWidthDeg"
        }
    }
}

/**
 * Collision-free placement for one [CompassRingLabel].
 *
 * @property id Matches [CompassRingLabel.id].
 * @property anchorAzimuthDeg Normalised original anchor azimuth in `[0, 360)`.
 * @property placedAzimuthDeg Final placed azimuth in `[0, 360)`.
 * @property angularOffsetDeg Signed shortest-arc shift from [anchorAzimuthDeg] to
 *   [placedAzimuthDeg] in `(-180, 180]`.
 * @property radialTier `0` for the primary ring (inside the horizon) or `1` for the outer
 *   fallback ring when the primary ring has no room within `maxAngularShiftDeg`.
 */
data class PlacedCompassLabel(
    val id: String,
    val anchorAzimuthDeg: Double,
    val placedAzimuthDeg: Double,
    val angularOffsetDeg: Double,
    val radialTier: Int,
)

/**
 * Pure, rotation-invariant label placement around the Brújula compass ring.
 *
 * Keeps unconflicted labels at their exact anchor azimuth (`angularOffsetDeg == 0`), nudges
 * conflicting labels along the primary ring so they never intersect fixed cardinal obstacles or
 * each other, and moves overflow labels to an outer radial tier (`radialTier == 1`) when a dense
 * cluster cannot fit within `maxAngularShiftDeg`.
 */
object CompassLabelPlacement {
    /** Default maximum angular displacement on the primary ring before moving a label to tier 1. */
    const val DEFAULT_MAX_SHIFT_DEG: Double = 45.0

    private const val MAX_RELAX_ITERATIONS = 160
    private const val EPS = 1e-6
    private const val VERIFY_TOLERANCE = 1e-3

    /**
     * Minimum angular separation (degrees) between two equal horizontal boxes of size
     * `labelWidth × labelHeight` on a circle of [ringRadius] so they never overlap at any
     * compass heading rotation `0°..360°`, with at least [padding] clearance.
     */
    fun requiredPairAngularWidthDeg(
        labelWidth: Double,
        labelHeight: Double,
        ringRadius: Double,
        padding: Double = 0.0,
    ): Double {
        require(ringRadius.isFinite() && ringRadius > 0.0) {
            "ringRadius must be finite and positive, was $ringRadius"
        }
        require(labelWidth >= 0.0 && labelHeight >= 0.0 && padding >= 0.0) {
            "dimensions and padding must be non-negative"
        }
        val minCenterDistance = hypot(labelWidth + padding, labelHeight + padding)
        val sinHalf = (minCenterDistance / (2.0 * ringRadius)).coerceIn(0.0, 1.0)
        return Math.toDegrees(2.0 * asin(sinHalf))
    }

    /**
     * Minimum centre-to-centre angular clearance (degrees) between a label box at
     * [labelRingRadius] and a cardinal box at [cardinalRingRadius] so they never overlap at any
     * compass heading rotation `0°..360°`, with at least [padding] clearance.
     */
    fun requiredCardinalClearanceDeg(
        labelWidth: Double,
        labelHeight: Double,
        labelRingRadius: Double,
        cardinalWidth: Double,
        cardinalHeight: Double,
        cardinalRingRadius: Double,
        padding: Double = 0.0,
    ): Double {
        require(labelRingRadius.isFinite() && labelRingRadius > 0.0) {
            "labelRingRadius must be finite and positive, was $labelRingRadius"
        }
        require(cardinalRingRadius.isFinite() && cardinalRingRadius > 0.0) {
            "cardinalRingRadius must be finite and positive, was $cardinalRingRadius"
        }
        require(labelWidth >= 0.0 && labelHeight >= 0.0 && cardinalWidth >= 0.0 && cardinalHeight >= 0.0 && padding >= 0.0) {
            "dimensions and padding must be non-negative"
        }
        val minCenterDistance = hypot(
            (labelWidth + cardinalWidth) / 2.0 + padding,
            (labelHeight + cardinalHeight) / 2.0 + padding,
        )
        val cosAngle = (
            cardinalRingRadius * cardinalRingRadius +
                labelRingRadius * labelRingRadius -
                minCenterDistance * minCenterDistance
            ) / (2.0 * cardinalRingRadius * labelRingRadius)
        return Math.toDegrees(acos(cosAngle.coerceIn(-1.0, 1.0)))
    }

    /**
     * Converts a desired [cardinalClearanceDeg] and label [pairAngularWidthDeg] into the
     * [CompassRingObstacle.angularWidthDeg] value so `labelWidth/2 + obstacleWidth/2 >= cardinalClearanceDeg`.
     */
    fun obstacleAngularWidthDeg(
        pairAngularWidthDeg: Double,
        cardinalClearanceDeg: Double,
    ): Double = ((cardinalClearanceDeg - pairAngularWidthDeg / 2.0) * 2.0).coerceAtLeast(0.0)

    /**
     * Computes collision-free placements for [labels] given fixed [obstacles] on the primary ring.
     * Results are returned in the same order as [labels].
     */
    fun place(
        labels: List<CompassRingLabel>,
        obstacles: List<CompassRingObstacle>,
        maxAngularShiftDeg: Double = DEFAULT_MAX_SHIFT_DEG,
    ): List<PlacedCompassLabel> {
        require(maxAngularShiftDeg.isFinite() && maxAngularShiftDeg > 0.0) {
            "maxAngularShiftDeg must be finite and positive, was $maxAngularShiftDeg"
        }
        if (labels.isEmpty()) return emptyList()

        val normalizedAnchors = DoubleArray(labels.size) {
            CompassMath.normalizeDegrees(labels[it].anchorAzimuthDeg)
        }
        val normalizedObstacles = obstacles.map {
            CompassRingObstacle(CompassMath.normalizeDegrees(it.azimuthDeg), it.angularWidthDeg)
        }

        val tier0 = MutableList(labels.size) { it }
        val tier1 = mutableListOf<Int>()
        var tier0Positions = DoubleArray(0)

        while (true) {
            if (tier0.isEmpty()) {
                tier0Positions = DoubleArray(0)
                break
            }
            val (positions, valid) = relaxRing(
                activeIndices = tier0,
                labels = labels,
                anchors = normalizedAnchors,
                obstacles = normalizedObstacles,
            )
            var overflowSlot = -1
            var worstShift = -1.0
            if (!valid) {
                for (k in tier0.indices) {
                    val shift = abs(CompassMath.shortestArcDelta(normalizedAnchors[tier0[k]], positions[k]))
                    if (shift >= worstShift) {
                        worstShift = shift
                        overflowSlot = k
                    }
                }
            } else {
                for (k in tier0.indices) {
                    val shift = abs(CompassMath.shortestArcDelta(normalizedAnchors[tier0[k]], positions[k]))
                    if (shift > maxAngularShiftDeg + VERIFY_TOLERANCE && shift > worstShift) {
                        worstShift = shift
                        overflowSlot = k
                    }
                }
            }

            if (overflowSlot < 0) {
                tier0Positions = positions
                break
            }
            tier1.add(tier0.removeAt(overflowSlot))
        }

        val tier1Positions = if (tier1.isEmpty()) {
            DoubleArray(0)
        } else {
            relaxRing(
                activeIndices = tier1,
                labels = labels,
                anchors = normalizedAnchors,
                obstacles = emptyList(),
            ).first
        }

        val placedByIndex = arrayOfNulls<PlacedCompassLabel>(labels.size)
        for (k in tier0.indices) {
            val idx = tier0[k]
            val placedAz = CompassMath.normalizeDegrees(tier0Positions[k])
            placedByIndex[idx] = PlacedCompassLabel(
                id = labels[idx].id,
                anchorAzimuthDeg = normalizedAnchors[idx],
                placedAzimuthDeg = placedAz,
                angularOffsetDeg = CompassMath.shortestArcDelta(normalizedAnchors[idx], placedAz),
                radialTier = 0,
            )
        }
        for (k in tier1.indices) {
            val idx = tier1[k]
            val placedAz = CompassMath.normalizeDegrees(tier1Positions[k])
            placedByIndex[idx] = PlacedCompassLabel(
                id = labels[idx].id,
                anchorAzimuthDeg = normalizedAnchors[idx],
                placedAzimuthDeg = placedAz,
                angularOffsetDeg = CompassMath.shortestArcDelta(normalizedAnchors[idx], placedAz),
                radialTier = 1,
            )
        }

        return placedByIndex.map { requireNotNull(it) }
    }

    private fun relaxRing(
        activeIndices: List<Int>,
        labels: List<CompassRingLabel>,
        anchors: DoubleArray,
        obstacles: List<CompassRingObstacle>,
    ): Pair<DoubleArray, Boolean> {
        val m = activeIndices.size
        val pos = DoubleArray(m) { anchors[activeIndices[it]] }
        val activeAnchors = DoubleArray(m) { anchors[activeIndices[it]] }
        val widths = DoubleArray(m) { labels[activeIndices[it]].angularWidthDeg }

        // 1. Deterministically break exact anchor ties symmetrically around the shared anchor.
        val visited = BooleanArray(m)
        for (i in 0 until m) {
            if (visited[i]) continue
            val group = mutableListOf(i)
            visited[i] = true
            for (j in i + 1 until m) {
                if (!visited[j] && abs(CompassMath.shortestArcDelta(pos[i], pos[j])) < 1e-4) {
                    group.add(j)
                    visited[j] = true
                }
            }
            if (group.size > 1) {
                val center = (group.size - 1) / 2.0
                for ((rank, slot) in group.withIndex()) {
                    pos[slot] = CompassMath.normalizeDegrees(pos[slot] + (rank - center) * 0.01)
                }
            }
        }

        // 2. For any single label sitting directly on an obstacle, prefer the freer side.
        if (obstacles.isNotEmpty()) {
            for (i in 0 until m) {
                for (obs in obstacles) {
                    if (abs(CompassMath.shortestArcDelta(obs.azimuthDeg, pos[i])) < 1e-4) {
                        val req = widths[i] / 2.0 + obs.angularWidthDeg / 2.0
                        val cwCandidate = CompassMath.normalizeDegrees(obs.azimuthDeg + req)
                        val ccwCandidate = CompassMath.normalizeDegrees(obs.azimuthDeg - req)
                        val cwClearance = minClearanceToOthers(cwCandidate, widths[i], i, pos, widths)
                        val ccwClearance = minClearanceToOthers(ccwCandidate, widths[i], i, pos, widths)
                        val nudge = if (ccwClearance > cwClearance + 1e-3) -0.01 else 0.01
                        pos[i] = CompassMath.normalizeDegrees(pos[i] + nudge)
                    }
                }
            }
        }

        // 3. Iterative obstacle projection + pairwise repulsion.
        for (iter in 0 until MAX_RELAX_ITERATIONS) {
            var moved = false

            if (obstacles.isNotEmpty()) {
                for (i in 0 until m) {
                    val hw = widths[i] / 2.0
                    for (obs in obstacles) {
                        val req = hw + obs.angularWidthDeg / 2.0
                        val d = CompassMath.shortestArcDelta(obs.azimuthDeg, pos[i])
                        if (abs(d) < req - EPS) {
                            val da = CompassMath.shortestArcDelta(obs.azimuthDeg, activeAnchors[i])
                            val direction = when {
                                abs(d) > 1.0 -> if (d > 0.0) 1.0 else -1.0
                                abs(da) > 1e-4 -> if (da > 0.0) 1.0 else -1.0
                                else -> if (d >= 0.0) 1.0 else -1.0
                            }
                            pos[i] = CompassMath.normalizeDegrees(obs.azimuthDeg + direction * req)
                            moved = true
                        }
                    }
                }
            }

            for (i in 0 until m) {
                for (j in i + 1 until m) {
                    val req = (widths[i] + widths[j]) / 2.0
                    val d = CompassMath.shortestArcDelta(pos[i], pos[j])
                    if (abs(d) < req - EPS) {
                        val overlap = req - abs(d)
                        val direction = if (d >= 0.0) 1.0 else -1.0
                        val halfPush = overlap / 2.0 + 1e-5
                        pos[i] = CompassMath.normalizeDegrees(pos[i] - direction * halfPush)
                        pos[j] = CompassMath.normalizeDegrees(pos[j] + direction * halfPush)
                        moved = true
                    }
                }
            }

            if (!moved) break
        }

        // 4. Verify all obstacle and pairwise constraints hold.
        var valid = true
        for (i in 0 until m) {
            for (obs in obstacles) {
                val req = widths[i] / 2.0 + obs.angularWidthDeg / 2.0
                if (abs(CompassMath.shortestArcDelta(pos[i], obs.azimuthDeg)) < req - VERIFY_TOLERANCE) {
                    valid = false
                }
            }
            for (j in i + 1 until m) {
                val req = (widths[i] + widths[j]) / 2.0
                if (abs(CompassMath.shortestArcDelta(pos[i], pos[j])) < req - VERIFY_TOLERANCE) {
                    valid = false
                }
            }
        }

        return pos to valid
    }

    private fun minClearanceToOthers(
        candidateAz: Double,
        candidateWidth: Double,
        selfIndex: Int,
        positions: DoubleArray,
        widths: DoubleArray,
    ): Double {
        var minMargin = Double.POSITIVE_INFINITY
        for (j in positions.indices) {
            if (j == selfIndex) continue
            val dist = abs(CompassMath.shortestArcDelta(candidateAz, positions[j]))
            val margin = dist - (candidateWidth + widths[j]) / 2.0
            if (margin < minMargin) minMargin = margin
        }
        return minMargin
    }
}
