package io.github.ivan_olivera_dev.cenit.core.ar

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Proper 3×3 row-major rotation matrix mapping **device coordinates to world ENU coordinates**,
 * matching Android's `SensorManager.getRotationMatrixFromVector` convention (portrait, no remap).
 *
 * ### Coordinate frames
 * - **World frame (ENU)**: `+X` = East, `+Y` = North, `+Z` = Up (zenith).
 *   True-north azimuth is measured clockwise from `+Y` toward `+X`; elevation is measured from the
 *   XY horizon plane toward `+Z`.
 * - **Device frame (portrait default)**: `+X` = screen right, `+Y` = screen top (up),
 *   `+Z` = out of the screen toward the user. The rear camera looks along `-Z`.
 *
 * Columns of the matrix are the device basis vectors expressed in world ENU:
 * - Column 0 (`[r00, r10, r20]`): screen right (`+X_d`)
 * - Column 1 (`[r01, r11, r21]`): screen up (`+Y_d`)
 * - Column 2 (`[r02, r12, r22]`): out of screen (`+Z_d`); the camera optical axis (`-Z_d`) is its negation.
 *
 * Instances are immutable and validated on construction to be proper orthonormal rotations
 * (`RᵀR = I` and `det(R) = +1` within [ORTHONORMAL_TOLERANCE]).
 */
class DeviceRotation(
    matrix: DoubleArray,
) {
    private val values: DoubleArray

    init {
        require(matrix.size == MATRIX_SIZE) {
            "Rotation matrix must have $MATRIX_SIZE elements, had ${matrix.size}"
        }
        val copy = DoubleArray(MATRIX_SIZE) { i ->
            val v = matrix[i]
            require(v.isFinite()) { "Rotation matrix element [$i] must be finite, was $v" }
            if (v == 0.0) 0.0 else v
        }
        validateOrthonormalProper(copy)
        values = copy
    }

    /**
     * Returns the matrix element at zero-based [row] (`0..2`) and [col] (`0..2`).
     */
    operator fun get(row: Int, col: Int): Double {
        require(row in 0..2 && col in 0..2) {
            "Matrix index ($row, $col) out of bounds for 3×3 rotation"
        }
        return values[row * 3 + col]
    }

    /**
     * Returns a defensive copy of the 9 row-major elements.
     */
    fun toDoubleArray(): DoubleArray = values.copyOf()

    /**
     * Returns a 9-element row-major `FloatArray` matching Android `SensorManager` layout.
     */
    fun toFloatArray(): FloatArray = FloatArray(MATRIX_SIZE) { values[it].toFloat() }

    /**
     * Rotates the world frame clockwise around zenith (`+Z`) by [declinationDeg] (east positive,
     * west negative — matching `android.hardware.GeomagneticField.getDeclination()`), converting a
     * magnetic-north rotation matrix to true north.
     */
    fun withDeclination(declinationDeg: Double): DeviceRotation {
        require(declinationDeg.isFinite()) {
            "declinationDeg must be finite, was $declinationDeg"
        }
        val rad = Math.toRadians(declinationDeg)
        val c = cos(rad)
        val s = sin(rad)
        // R_true = R_z(-declination) * R_mag so magnetic north (0, 1, 0) maps to (sin d, cos d, 0).
        val m = values
        return DeviceRotation(
            doubleArrayOf(
                c * m[0] + s * m[3], c * m[1] + s * m[4], c * m[2] + s * m[5],
                -s * m[0] + c * m[3], -s * m[1] + c * m[4], -s * m[2] + c * m[5],
                m[6], m[7], m[8],
            ),
        )
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is DeviceRotation && values.contentEquals(other.values))

    override fun hashCode(): Int = values.contentHashCode()

    override fun toString(): String =
        "DeviceRotation(${values.joinToString(prefix = "[", postfix = "]")})"

    companion object {
        private const val MATRIX_SIZE = 9

        /** Maximum allowed element-wise deviation of `RᵀR` from `I` and `det(R)` from `+1`. */
        const val ORTHONORMAL_TOLERANCE: Double = 1e-3

        /**
         * Identity rotation: device lying flat screen-up with top pointing North and rear camera
         * pointing straight down at the nadir.
         */
        val IDENTITY: DeviceRotation = DeviceRotation(
            doubleArrayOf(
                1.0, 0.0, 0.0,
                0.0, 1.0, 0.0,
                0.0, 0.0, 1.0,
            ),
        )

        /**
         * Constructs a [DeviceRotation] from a 9-element row-major [FloatArray] such as the output
         * of Android's `SensorManager.getRotationMatrixFromVector`.
         */
        fun fromFloatArray(matrix: FloatArray): DeviceRotation {
            require(matrix.size == MATRIX_SIZE) {
                "Rotation matrix must have $MATRIX_SIZE elements, had ${matrix.size}"
            }
            return DeviceRotation(DoubleArray(MATRIX_SIZE) { matrix[it].toDouble() })
        }

        /**
         * Builds a synthetic [DeviceRotation] for a portrait phone whose rear camera looks along
         * ([headingDeg], [pitchDeg]) with clockwise bank angle [rollDeg] around the optical axis.
         *
         * - [headingDeg]: azimuth clockwise from North (`0°` = N, `90°` = E, `180°` = S, `270°` = W).
         * - [pitchDeg]: elevation of the optical axis above (`+`) or below (`-`) the horizon, in `[-90, 90]`.
         * - [rollDeg]: clockwise rotation of the device when looking toward the scene (`0°` = portrait
         *   upright, `+90°` = landscape with top tilted right, `-90°` = landscape with top tilted left).
         */
        fun fromHeadingPitchRoll(
            headingDeg: Double,
            pitchDeg: Double,
            rollDeg: Double = 0.0,
        ): DeviceRotation {
            require(headingDeg.isFinite()) { "headingDeg must be finite, was $headingDeg" }
            require(pitchDeg.isFinite() && pitchDeg in -90.0..90.0) {
                "pitchDeg must be finite and in [-90, 90], was $pitchDeg"
            }
            require(rollDeg.isFinite()) { "rollDeg must be finite, was $rollDeg" }

            val h = Math.toRadians(headingDeg)
            val p = Math.toRadians(pitchDeg)
            val r = Math.toRadians(rollDeg)

            val sinH = sin(h)
            val cosH = cos(h)
            val sinP = sin(p)
            val cosP = cos(p)
            val sinR = sin(r)
            val cosR = cos(r)

            // Optical axis (forward = -Z_d) in world ENU
            val fx = cosP * sinH
            val fy = cosP * cosH
            val fz = sinP

            // Zero-roll horizontal right and up vectors in world ENU (well-defined even at +-90 pitch)
            val r0x = cosH
            val r0y = -sinH
            val r0z = 0.0

            val u0x = -sinP * sinH
            val u0y = -sinP * cosH
            val u0z = cosP

            // Apply clockwise roll around forward axis
            val rx = cosR * r0x - sinR * u0x
            val ry = cosR * r0y - sinR * u0y
            val rz = cosR * r0z - sinR * u0z

            val ux = sinR * r0x + cosR * u0x
            val uy = sinR * r0y + cosR * u0y
            val uz = sinR * r0z + cosR * u0z

            // Columns are (X_d = r, Y_d = u, Z_d = -f)
            return DeviceRotation(
                doubleArrayOf(
                    rx, ux, -fx,
                    ry, uy, -fy,
                    rz, uz, -fz,
                ),
            )
        }

        private fun validateOrthonormalProper(m: DoubleArray) {
            for (i in 0..2) {
                for (j in i..2) {
                    val dot = m[i] * m[j] + m[3 + i] * m[3 + j] + m[6 + i] * m[6 + j]
                    val expected = if (i == j) 1.0 else 0.0
                    require(abs(dot - expected) <= ORTHONORMAL_TOLERANCE) {
                        "Rotation matrix is not orthonormal: (R^T R)[$i,$j] = $dot (expected $expected)"
                    }
                }
            }
            val det = m[0] * (m[4] * m[8] - m[5] * m[7]) -
                m[1] * (m[3] * m[8] - m[5] * m[6]) +
                m[2] * (m[3] * m[7] - m[4] * m[6])
            require(abs(det - 1.0) <= ORTHONORMAL_TOLERANCE) {
                "Rotation matrix must be proper (det = +1), was $det"
            }
        }
    }
}

/**
 * Internal unit quaternion (`w + xi + yj + zk`) used for gimbal-lock-free rotation smoothing.
 */
internal data class UnitQuaternion(
    val w: Double,
    val x: Double,
    val y: Double,
    val z: Double,
) {
    fun dot(other: UnitQuaternion): Double =
        w * other.w + x * other.x + y * other.y + z * other.z

    fun negated(): UnitQuaternion = UnitQuaternion(-w, -x, -y, -z)

    fun toDeviceRotation(): DeviceRotation {
        val xx = x * x
        val yy = y * y
        val zz = z * z
        val xy = x * y
        val xz = x * z
        val yz = y * z
        val wx = w * x
        val wy = w * y
        val wz = w * z
        return DeviceRotation(
            doubleArrayOf(
                1.0 - 2.0 * (yy + zz), 2.0 * (xy - wz), 2.0 * (xz + wy),
                2.0 * (xy + wz), 1.0 - 2.0 * (xx + zz), 2.0 * (yz - wx),
                2.0 * (xz - wy), 2.0 * (yz + wx), 1.0 - 2.0 * (xx + yy),
            ),
        )
    }

    companion object {
        fun normalized(w: Double, x: Double, y: Double, z: Double): UnitQuaternion {
            val norm = sqrt(w * w + x * x + y * y + z * z)
            require(norm > 0.0 && norm.isFinite()) { "Cannot normalise zero/non-finite quaternion" }
            return UnitQuaternion(w / norm, x / norm, y / norm, z / norm)
        }

        /**
         * Extracts a unit quaternion from [rotation] using Shepperd's numerically stable branch
         * selection (accurate across all rotations, including 180° turns).
         */
        fun fromRotation(rotation: DeviceRotation): UnitQuaternion {
            val r00 = rotation[0, 0]
            val r01 = rotation[0, 1]
            val r02 = rotation[0, 2]
            val r10 = rotation[1, 0]
            val r11 = rotation[1, 1]
            val r12 = rotation[1, 2]
            val r20 = rotation[2, 0]
            val r21 = rotation[2, 1]
            val r22 = rotation[2, 2]

            val trace = r00 + r11 + r22
            val w: Double
            val x: Double
            val y: Double
            val z: Double
            when {
                trace > 0.0 -> {
                    val s = 2.0 * sqrt(trace + 1.0)
                    w = 0.25 * s
                    x = (r21 - r12) / s
                    y = (r02 - r20) / s
                    z = (r10 - r01) / s
                }
                r00 > r11 && r00 > r22 -> {
                    val s = 2.0 * sqrt(1.0 + r00 - r11 - r22)
                    w = (r21 - r12) / s
                    x = 0.25 * s
                    y = (r01 + r10) / s
                    z = (r02 + r20) / s
                }
                r11 > r22 -> {
                    val s = 2.0 * sqrt(1.0 + r11 - r00 - r22)
                    w = (r02 - r20) / s
                    x = (r01 + r10) / s
                    y = 0.25 * s
                    z = (r12 + r21) / s
                }
                else -> {
                    val s = 2.0 * sqrt(1.0 + r22 - r00 - r11)
                    w = (r10 - r01) / s
                    x = (r02 + r20) / s
                    y = (r12 + r21) / s
                    z = 0.25 * s
                }
            }
            return normalized(w, x, y, z)
        }
    }
}
