package io.github.ivan_olivera_dev.cenit.core.ar

/**
 * Axis-aligned 2D rectangle in screen/viewport pixel coordinates (`+x` right, `+y` down).
 */
data class ViewportRect(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
) {
    init {
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()) {
            "ViewportRect coordinates must be finite, was ($left, $top, $right, $bottom)"
        }
        require(right >= left && bottom >= top) {
            "ViewportRect must have right >= left and bottom >= top, was ($left, $top, $right, $bottom)"
        }
    }

    val width: Double get() = right - left
    val height: Double get() = bottom - top
    val centerX: Double get() = (left + right) / 2.0
    val centerY: Double get() = (top + bottom) / 2.0

    /**
     * Returns `true` when this rectangle overlaps [other] after expanding both boxes by [padding]
     * pixels of clearance.
     */
    fun intersects(other: ViewportRect, padding: Double = 0.0): Boolean =
        !(right + padding <= other.left ||
            other.right + padding <= left ||
            bottom + padding <= other.top ||
            other.bottom + padding <= top)

    /**
     * Returns `true` when this rectangle lies completely inside [bounds] (inclusive).
     */
    fun isInside(bounds: ViewportRect): Boolean =
        left >= bounds.left - EPSILON &&
            right <= bounds.right + EPSILON &&
            top >= bounds.top - EPSILON &&
            bottom <= bounds.bottom + EPSILON

    companion object {
        private const val EPSILON = 1e-9

        fun centeredAt(cx: Double, cy: Double, width: Double, height: Double): ViewportRect {
            require(width >= 0.0 && height >= 0.0) {
                "width and height must be non-negative, were ($width, $height)"
            }
            val halfW = width / 2.0
            val halfH = height / 2.0
            return ViewportRect(
                left = cx - halfW,
                top = cy - halfH,
                right = cx + halfW,
                bottom = cy + halfH,
            )
        }
    }
}

/**
 * Greedy 2D collision-free label placer for AR overlays.
 *
 * Callers reserve high-priority HUD regions (crosshair, aim pill, crossing pill, active markers)
 * first via [reserve], then request placement for lower-priority labels via [tryPlace] or
 * [tryPlaceAt]. Placed rectangles are guaranteed to stay inside [bounds] and never overlap each
 * other or any reserved region.
 *
 * Instances can be reused across animation frames via [reset] to avoid per-frame list allocations.
 */
class ArLabelPlacer(
    bounds: ViewportRect,
    val defaultPadding: Double = DEFAULT_PADDING_PX,
) {
    init {
        require(defaultPadding.isFinite() && defaultPadding >= 0.0) {
            "defaultPadding must be finite and non-negative, was $defaultPadding"
        }
    }

    var bounds: ViewportRect = bounds
        private set

    // Parallel primitive arrays avoid boxing and allow zero-allocation reset between frames.
    private var count: Int = 0
    private var lefts = DoubleArray(INITIAL_CAPACITY)
    private var tops = DoubleArray(INITIAL_CAPACITY)
    private var rights = DoubleArray(INITIAL_CAPACITY)
    private var bottoms = DoubleArray(INITIAL_CAPACITY)

    /**
     * Clears all reserved and placed boxes and updates the active viewport [newBounds].
     */
    fun reset(newBounds: ViewportRect = bounds) {
        bounds = newBounds
        count = 0
    }

    /**
     * Unconditionally reserves [rect] as an occupied obstacle (for example the crosshair or a
     * fixed HUD pill).
     */
    fun reserve(rect: ViewportRect) {
        addBox(rect.left, rect.top, rect.right, rect.bottom)
    }

    /**
     * Unconditionally reserves an obstacle rectangle without allocating a [ViewportRect].
     */
    fun reserve(left: Double, top: Double, right: Double, bottom: Double) {
        if (right >= left && bottom >= top) {
            addBox(left, top, right, bottom)
        }
    }

    /**
     * Attempts to place a label of size [width] × [height] centered at ([cx], [cy]).
     *
     * When [clampToBounds] is `true`, the center is clamped so the rectangle fits within [bounds]
     * (failing if the label itself exceeds [bounds]). Returns the placed [ViewportRect], or `null`
     * if it does not fit or collides with an existing box.
     */
    fun tryPlaceAt(
        cx: Double,
        cy: Double,
        width: Double,
        height: Double,
        padding: Double = defaultPadding,
        clampToBounds: Boolean = false,
    ): ViewportRect? {
        if (!cx.isFinite() || !cy.isFinite() || width < 0.0 || height < 0.0) return null
        if (width > bounds.width + EPSILON || height > bounds.height + EPSILON) return null

        val halfW = width / 2.0
        val halfH = height / 2.0
        val resolvedCx = if (clampToBounds) {
            cx.coerceIn(bounds.left + halfW, bounds.right - halfW)
        } else {
            cx
        }
        val resolvedCy = if (clampToBounds) {
            cy.coerceIn(bounds.top + halfH, bounds.bottom - halfH)
        } else {
            cy
        }

        val l = resolvedCx - halfW
        val t = resolvedCy - halfH
        val r = resolvedCx + halfW
        val b = resolvedCy + halfH

        if (l < bounds.left - EPSILON || r > bounds.right + EPSILON ||
            t < bounds.top - EPSILON || b > bounds.bottom + EPSILON
        ) {
            return null
        }

        if (hitsAny(l, t, r, b, padding)) return null
        addBox(l, t, r, b)
        return ViewportRect(l, t, r, b)
    }

    /**
     * Tries each `(cx, cy)` candidate in order and returns the first collision-free placement
     * inside [bounds], or `null` if no candidate fits.
     */
    fun tryPlace(
        width: Double,
        height: Double,
        candidates: List<Pair<Double, Double>>,
        padding: Double = defaultPadding,
        clampToBounds: Boolean = true,
    ): ViewportRect? {
        for (i in candidates.indices) {
            val (cx, cy) = candidates[i]
            val placed = tryPlaceAt(
                cx = cx,
                cy = cy,
                width = width,
                height = height,
                padding = padding,
                clampToBounds = clampToBounds,
            )
            if (placed != null) return placed
        }
        return null
    }

    /**
     * Zero-allocation placement check for hot draw loops: tries a single candidate centered at
     * ([cx], [cy]), records it if free, and writes the placed center into [outCenter] (`[0] = cx`,
     * `[1] = cy`). Returns `true` if placed.
     */
    fun tryPlaceInto(
        cx: Double,
        cy: Double,
        width: Double,
        height: Double,
        outCenter: DoubleArray,
        padding: Double = defaultPadding,
        clampToBounds: Boolean = false,
    ): Boolean {
        if (!cx.isFinite() || !cy.isFinite() || width < 0.0 || height < 0.0) return false
        if (width > bounds.width + EPSILON || height > bounds.height + EPSILON) return false

        val halfW = width / 2.0
        val halfH = height / 2.0
        val resolvedCx = if (clampToBounds) {
            cx.coerceIn(bounds.left + halfW, bounds.right - halfW)
        } else {
            cx
        }
        val resolvedCy = if (clampToBounds) {
            cy.coerceIn(bounds.top + halfH, bounds.bottom - halfH)
        } else {
            cy
        }

        val l = resolvedCx - halfW
        val t = resolvedCy - halfH
        val r = resolvedCx + halfW
        val b = resolvedCy + halfH

        if (l < bounds.left - EPSILON || r > bounds.right + EPSILON ||
            t < bounds.top - EPSILON || b > bounds.bottom + EPSILON
        ) {
            return false
        }
        if (hitsAny(l, t, r, b, padding)) return false

        addBox(l, t, r, b)
        outCenter[0] = resolvedCx
        outCenter[1] = resolvedCy
        return true
    }

    private fun hitsAny(l: Double, t: Double, r: Double, b: Double, pad: Double): Boolean {
        for (i in 0 until count) {
            if (!(r + pad <= lefts[i] || rights[i] + pad <= l || b + pad <= tops[i] || bottoms[i] + pad <= t)) {
                return true
            }
        }
        return false
    }

    private fun addBox(l: Double, t: Double, r: Double, b: Double) {
        if (count == lefts.size) {
            val newSize = lefts.size * 2
            lefts = lefts.copyOf(newSize)
            tops = tops.copyOf(newSize)
            rights = rights.copyOf(newSize)
            bottoms = bottoms.copyOf(newSize)
        }
        lefts[count] = l
        tops[count] = t
        rights[count] = r
        bottoms[count] = b
        count++
    }

    companion object {
        const val DEFAULT_PADDING_PX: Double = 4.0
        private const val INITIAL_CAPACITY = 64
        private const val EPSILON = 1e-9
    }
}
