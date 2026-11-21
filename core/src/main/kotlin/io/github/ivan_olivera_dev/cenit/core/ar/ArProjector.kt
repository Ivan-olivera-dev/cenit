package io.github.ivan_olivera_dev.cenit.core.ar

import io.github.ivan_olivera_dev.cenit.core.SkyPosition
import io.github.ivan_olivera_dev.cenit.core.compass.CompassMath
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * Visible field of view of the camera preview, in degrees.
 *
 * @property horizontalDeg Horizontal field of view in `(0, 180)` degrees.
 * @property verticalDeg Vertical field of view in `(0, 180)` degrees.
 */
data class CameraFov(
    val horizontalDeg: Double,
    val verticalDeg: Double,
) {
    init {
        require(horizontalDeg.isFinite() && horizontalDeg > 0.0 && horizontalDeg < 180.0) {
            "horizontalDeg must be finite and in (0, 180), was $horizontalDeg"
        }
        require(verticalDeg.isFinite() && verticalDeg > 0.0 && verticalDeg < 180.0) {
            "verticalDeg must be finite and in (0, 180), was $verticalDeg"
        }
    }
}

/**
 * Projected 2D screen coordinates of a celestial direction.
 *
 * Screen origin `(0, 0)` is the top-left corner of the preview, `+x` runs right, and `+y` runs down.
 *
 * @property x Horizontal pixel coordinate.
 * @property y Vertical pixel coordinate.
 * @property inView `true` when the projected point lies within `[0, widthPx] × [0, heightPx]`.
 */
data class ScreenPoint(
    val x: Double,
    val y: Double,
    val inView: Boolean,
)

/**
 * Perimeter indicator pointing from the screen centre toward an off-screen or behind-camera target.
 *
 * @property x Horizontal pixel coordinate clamped onto the inset viewport perimeter.
 * @property y Vertical pixel coordinate clamped onto the inset viewport perimeter.
 * @property angleRad Direction angle in screen coordinates (`atan2(dy, dx)` in `[-π, π]`: `0` right,
 *   `+π/2` down, `-π/2` up, `±π` left).
 */
data class EdgeArrow(
    val x: Double,
    val y: Double,
    val angleRad: Double,
)

/**
 * Orientation of the rear camera crosshair in world ENU coordinates.
 *
 * @property azimuthDeg Compass bearing in `[0, 360)` degrees clockwise from north (true north if
 *   the input rotation already includes magnetic declination; otherwise magnetic north).
 * @property elevationDeg Elevation above (`+`) or below (`-`) the horizon, in `[-90, 90]` degrees.
 * @property rollDeg Clockwise device bank angle around the optical axis in `(-180, 180]` degrees
 *   (`0` = portrait upright, `+90` = top tilted right, `-90` = top tilted left).
 */
data class Pointing(
    val azimuthDeg: Double,
    val elevationDeg: Double,
    val rollDeg: Double,
)

/**
 * Pinhole perspective projector mapping celestial directions `(azimuthDeg, elevationDeg)` in world
 * ENU onto the 2D camera preview viewport.
 *
 * Focal lengths are derived independently from [CameraFov.horizontalDeg] and [CameraFov.verticalDeg]:
 * `fx = (widthPx / 2) / tan(horizontalFov / 2)` and `fy = (heightPx / 2) / tan(verticalFov / 2)`.
 *
 * @property fov Visible camera field of view in degrees.
 * @property widthPx Preview viewport width in pixels (must be positive).
 * @property heightPx Preview viewport height in pixels (must be positive).
 */
class ArProjector(
    val fov: CameraFov,
    val widthPx: Double,
    val heightPx: Double,
) {
    init {
        require(widthPx.isFinite() && widthPx > 0.0) {
            "widthPx must be finite and positive, was $widthPx"
        }
        require(heightPx.isFinite() && heightPx > 0.0) {
            "heightPx must be finite and positive, was $heightPx"
        }
    }

    private val centerX: Double = widthPx / 2.0
    private val centerY: Double = heightPx / 2.0
    private val focalX: Double = centerX / tan(Math.toRadians(fov.horizontalDeg) / 2.0)
    private val focalY: Double = centerY / tan(Math.toRadians(fov.verticalDeg) / 2.0)

    /**
     * Projects the sky direction ([azimuthDeg], [elevationDeg]) onto screen pixels using [rotation].
     *
     * Returns `null` when the target lies on or behind the camera image plane (camera depth
     * `zCam <= 0`). Targets in front of the image plane but outside the viewport return a non-null
     * [ScreenPoint] with [ScreenPoint.inView] set to `false`.
     */
    fun project(
        rotation: DeviceRotation,
        azimuthDeg: Double,
        elevationDeg: Double,
    ): ScreenPoint? {
        require(azimuthDeg.isFinite()) { "azimuthDeg must be finite, was $azimuthDeg" }
        require(elevationDeg.isFinite() && elevationDeg in -90.0..90.0) {
            "elevationDeg must be finite and in [-90, 90], was $elevationDeg"
        }

        val az = Math.toRadians(azimuthDeg)
        val el = Math.toRadians(elevationDeg)
        val cosEl = cos(el)
        val wx = cosEl * sin(az)
        val wy = cosEl * cos(az)
        val wz = sin(el)

        // World -> device via R^T
        val dx = rotation[0, 0] * wx + rotation[1, 0] * wy + rotation[2, 0] * wz
        val dy = rotation[0, 1] * wx + rotation[1, 1] * wy + rotation[2, 1] * wz
        val dz = rotation[0, 2] * wx + rotation[1, 2] * wy + rotation[2, 2] * wz

        val zCam = -dz
        if (zCam <= MIN_CAMERA_DEPTH) return null

        val x = cleanZero(centerX + focalX * (dx / zCam))
        val y = cleanZero(centerY - focalY * (dy / zCam))
        val inView = x >= -VIEW_EPSILON && x <= widthPx + VIEW_EPSILON &&
            y >= -VIEW_EPSILON && y <= heightPx + VIEW_EPSILON
        return ScreenPoint(x = x, y = y, inView = inView)
    }

    /**
     * Convenience overload projecting a [SkyPosition].
     */
    fun project(
        rotation: DeviceRotation,
        position: SkyPosition,
    ): ScreenPoint? = project(rotation, position.azimuth, position.elevation)

    /**
     * Computes an [EdgeArrow] on the inset viewport boundary pointing from the screen centre toward
     * an off-screen or behind-camera target ([azimuthDeg], [elevationDeg]).
     *
     * Returns `null` when the target is already in view (`project(...)?.inView == true`).
     * When the target is directly behind the optical axis (degenerate direction in the image plane),
     * the arrow points straight down (`angleRad = +π/2`) at the bottom-centre inset edge.
     *
     * @param insetPx Margin from the viewport edges in pixels (`0 <= insetPx < min(widthPx, heightPx) / 2`).
     */
    fun edgeArrow(
        rotation: DeviceRotation,
        azimuthDeg: Double,
        elevationDeg: Double,
        insetPx: Double = 0.0,
    ): EdgeArrow? {
        require(azimuthDeg.isFinite()) { "azimuthDeg must be finite, was $azimuthDeg" }
        require(elevationDeg.isFinite() && elevationDeg in -90.0..90.0) {
            "elevationDeg must be finite and in [-90, 90], was $elevationDeg"
        }
        val maxInset = min(widthPx, heightPx) / 2.0
        require(insetPx.isFinite() && insetPx >= 0.0 && insetPx < maxInset) {
            "insetPx must be finite and in [0, $maxInset), was $insetPx"
        }

        val projected = project(rotation, azimuthDeg, elevationDeg)
        if (projected != null && projected.inView) return null

        val az = Math.toRadians(azimuthDeg)
        val el = Math.toRadians(elevationDeg)
        val cosEl = cos(el)
        val wx = cosEl * sin(az)
        val wy = cosEl * cos(az)
        val wz = sin(el)

        val dx = rotation[0, 0] * wx + rotation[1, 0] * wy + rotation[2, 0] * wz
        val dy = rotation[0, 1] * wx + rotation[1, 1] * wy + rotation[2, 1] * wz

        // Direction in screen coordinates (+x right, +y down), continuous across zCam = 0
        var dirX = focalX * dx
        var dirY = -focalY * dy
        if (hypot(dirX, dirY) < DIRECTION_EPSILON) {
            // Directly behind the optical axis: point straight down ("turn around / behind").
            dirX = 0.0
            dirY = 1.0
        }

        val halfW = centerX - insetPx
        val halfH = centerY - insetPx
        val tx = if (abs(dirX) > DIRECTION_EPSILON) halfW / abs(dirX) else Double.POSITIVE_INFINITY
        val ty = if (abs(dirY) > DIRECTION_EPSILON) halfH / abs(dirY) else Double.POSITIVE_INFINITY
        val t = min(tx, ty)

        val x = cleanZero((centerX + t * dirX).coerceIn(insetPx, widthPx - insetPx))
        val y = cleanZero((centerY + t * dirY).coerceIn(insetPx, heightPx - insetPx))
        val angleRad = cleanZero(atan2(dirY, dirX))
        return EdgeArrow(x = x, y = y, angleRad = angleRad)
    }

    /**
     * Convenience overload computing an [EdgeArrow] for a [SkyPosition].
     */
    fun edgeArrow(
        rotation: DeviceRotation,
        position: SkyPosition,
        insetPx: Double = 0.0,
    ): EdgeArrow? = edgeArrow(rotation, position.azimuth, position.elevation, insetPx)

    /**
     * Extracts the camera crosshair's [Pointing] (`azimuthDeg`, `elevationDeg`, `rollDeg`) from
     * [rotation].
     *
     * Near the zenith (`+90°`) or nadir (`-90°`), heading and roll become degenerate; in that
     * neighbourhood `azimuthDeg` falls back smoothly to the horizontal projection of the screen-up
     * axis and `rollDeg` is reported as `0.0`, guaranteeing finite, NaN-free outputs everywhere.
     */
    fun pointing(rotation: DeviceRotation): Pointing {
        // Camera optical axis (-Z_d) in world ENU
        val fx = -rotation[0, 2]
        val fy = -rotation[1, 2]
        val fz = -rotation[2, 2]

        val elevationDeg = cleanZero(Math.toDegrees(asin(fz.coerceIn(-1.0, 1.0))))
        val horizNorm = hypot(fx, fy)

        val azimuthDeg: Double
        val rollDeg: Double
        if (horizNorm < POLE_EPSILON) {
            // Gimbal lock at zenith / nadir: derive heading from screen-up (+Y_d) so portrait
            // tilts through zenith/nadir stay continuous and NaN-free.
            val ux = rotation[0, 1]
            val uy = rotation[1, 1]
            val refX = if (fz >= 0.0) -ux else ux
            val refY = if (fz >= 0.0) -uy else uy
            azimuthDeg = CompassMath.normalizeDegrees(Math.toDegrees(atan2(refX, refY)))
            rollDeg = 0.0
        } else {
            azimuthDeg = CompassMath.normalizeDegrees(Math.toDegrees(atan2(fx, fy)))
            // Third row of R gives z-components of (X_d, Y_d): r_z = -sin(roll)*cos(el), u_z = cos(roll)*cos(el)
            val rz = rotation[2, 0]
            val uz = rotation[2, 1]
            val rawRoll = Math.toDegrees(atan2(-rz, uz))
            rollDeg = cleanZero(if (rawRoll == -180.0) 180.0 else rawRoll)
        }

        return Pointing(
            azimuthDeg = azimuthDeg,
            elevationDeg = elevationDeg,
            rollDeg = rollDeg,
        )
    }

    private fun cleanZero(value: Double): Double = if (value == 0.0) 0.0 else value

    private companion object {
        const val MIN_CAMERA_DEPTH = 1e-6
        const val VIEW_EPSILON = 1e-6
        const val DIRECTION_EPSILON = 1e-9
        const val POLE_EPSILON = 1e-6
    }
}
