package io.github.ivan_olivera_dev.cenit.core.eclipses

import io.github.ivan_olivera_dev.cenit.core.TimeRange
import java.time.Instant

/**
 * Celestial body undergoing an eclipse.
 */
enum class EclipseBody {
    SUN,
    MOON,
}

/**
 * Classification of a solar or lunar eclipse.
 */
enum class EclipseKind {
    /** Penumbral lunar eclipse (never used for solar eclipses). */
    PENUMBRAL,

    /** Partial solar or lunar eclipse. */
    PARTIAL,

    /** Annular solar eclipse (never used for lunar eclipses). */
    ANNULAR,

    /** Total solar or lunar eclipse. */
    TOTAL,
}

/**
 * A solar or lunar eclipse evaluated for an observer's location (or globally when not locally visible).
 *
 * Named `CenitEclipse` to avoid colliding with Astronomy Engine's `EclipseEvent`.
 *
 * @property body Whether the eclipsed body is the Sun or the Moon.
 * @property kind Local classification when [visible] (or global classification when not locally visible).
 * @property peak Instant of maximum eclipse (local peak for visible solar eclipses, global peak otherwise).
 * @property obscuration Fraction of the disc obscured at [peak] in `[0, 1]`, or `null` when unavailable
 *   (for example, a non-visible global partial solar eclipse).
 * @property visible Whether the eclipse is observable from the requested location.
 * @property peakElevationDeg Apparent elevation of the eclipsed body at [peak] from the location, in degrees.
 * @property penumbralRange Penumbral phase span (`peak ± sdPenum`) for lunar eclipses; `null` for solar eclipses.
 * @property partialRange Partial phase span (local `partialBegin..partialEnd` for visible solar eclipses,
 *   `peak ± sdPartial` for partial/total lunar eclipses); `null` if no partial phase or not locally visible.
 * @property totalRange Total or annular phase span (local `totalBegin..totalEnd` for visible total/annular
 *   solar eclipses, `peak ± sdTotal` for total lunar eclipses); `null` otherwise.
 */
data class CenitEclipse(
    val body: EclipseBody,
    val kind: EclipseKind,
    val peak: Instant,
    val obscuration: Double?,
    val visible: Boolean,
    val peakElevationDeg: Double,
    val penumbralRange: TimeRange? = null,
    val partialRange: TimeRange? = null,
    val totalRange: TimeRange? = null,
)

/**
 * A global solar eclipse evaluated across the entire Earth, independent of observer location.
 *
 * @property kind Global classification ([EclipseKind.PARTIAL], [EclipseKind.ANNULAR], or [EclipseKind.TOTAL]).
 * @property peak Instant of greatest global eclipse (shadow axis closest to Earth's centre).
 * @property obscuration Peak obscuration at the shadow axis for annular/total eclipses, or `null` for partial.
 * @property latitude Geodetic latitude of greatest eclipse for annular/total eclipses, or `null` for partial.
 * @property longitude Geodetic longitude of greatest eclipse for annular/total eclipses, or `null` for partial.
 */
data class GlobalSolarEclipse(
    val kind: EclipseKind,
    val peak: Instant,
    val obscuration: Double?,
    val latitude: Double?,
    val longitude: Double?,
)
