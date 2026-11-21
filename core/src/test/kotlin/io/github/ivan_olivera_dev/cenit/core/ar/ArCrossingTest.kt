package io.github.ivan_olivera_dev.cenit.core.ar

import io.github.ivan_olivera_dev.cenit.core.PathPoint
import io.github.ivan_olivera_dev.cenit.core.Place
import io.github.ivan_olivera_dev.cenit.core.SkyPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class ArCrossingTest {
    private val t0 = Instant.parse("2026-06-21T10:00:00Z")

    @Test
    fun `returns null for empty path or when crosshair misses beyond tolerance`() {
        val aim = DeviceRotation.fromHeadingPitchRoll(headingDeg = 180.0, pitchDeg = 30.0)
        assertNull(crossing(aim, emptyList(), toleranceDeg = 4.0))

        val farPath = listOf(
            PathPoint(t0, SkyPosition(azimuth = 180.0, elevation = 40.0)),
            PathPoint(t0.plusSeconds(600), SkyPosition(azimuth = 190.0, elevation = 30.0)),
        )
        assertNull(crossing(aim, farPath, toleranceDeg = 4.0))
    }

    @Test
    fun `returns matching sample when crosshair points within tolerance`() {
        val aim = DeviceRotation.fromHeadingPitchRoll(headingDeg = 200.0, pitchDeg = 25.0)
        val sample = PathPoint(t0, SkyPosition(azimuth = 202.0, elevation = 26.0))
        val hit = crossing(aim, listOf(sample), toleranceDeg = 4.0)
        assertEquals(sample, hit)
    }

    @Test
    fun `returns closest of two samples when both are within tolerance`() {
        val aim = DeviceRotation.fromHeadingPitchRoll(headingDeg = 180.0, pitchDeg = 30.0)
        val farther = PathPoint(t0, SkyPosition(azimuth = 180.0, elevation = 33.0)) // 3 deg away
        val closer = PathPoint(t0.plusSeconds(600), SkyPosition(azimuth = 180.0, elevation = 28.5)) // 1.5 deg away
        val hit = crossing(aim, listOf(farther, closer), toleranceDeg = 4.0)
        assertEquals(closer, hit)
    }

    @Test
    fun `handles azimuth wrap across 359 and 1 degrees and high elevation near zenith`() {
        val aimNorth = DeviceRotation.fromHeadingPitchRoll(headingDeg = 359.0, pitchDeg = 0.0)
        val acrossNorth = PathPoint(t0, SkyPosition(azimuth = 1.0, elevation = 0.0)) // 2 deg away
        assertEquals(acrossNorth, crossing(aimNorth, listOf(acrossNorth), toleranceDeg = 3.0))

        // Near zenith: az=0 el=89 and az=180 el=89 are only 2 deg apart on the sphere!
        val aimNearZenith = DeviceRotation.fromHeadingPitchRoll(headingDeg = 0.0, pitchDeg = 89.0)
        val oppositeNearZenith = PathPoint(t0, SkyPosition(azimuth = 180.0, elevation = 89.0))
        assertEquals(oppositeNearZenith, crossing(aimNearZenith, listOf(oppositeNearZenith), toleranceDeg = 3.0))
    }

    @Test
    fun `detects solar path crossing for Madrid 2026-06-21`() {
        val madrid = Place(latitude = 40.4165, longitude = -3.7026)
        val zone = ZoneId.of("Europe/Madrid")
        val paths = ArPaths.compute(madrid, LocalDate.of(2026, 6, 21), zone)

        // Aim directly at the 18:00 local-hour sun position
        val marker18 = paths.hourMarkers.single { it.localTime.hour == 18 }
        val aim18 = DeviceRotation.fromHeadingPitchRoll(
            headingDeg = marker18.position.azimuth,
            pitchDeg = marker18.position.elevation,
        )
        val hit = crossing(aim18, paths.sunPath, toleranceDeg = 2.0)
        assertNotNull(hit)
        assertEquals(marker18.instant, hit!!.instant)
    }

    @Test
    fun `rejects negative or non-finite tolerance`() {
        val aim = DeviceRotation.fromHeadingPitchRoll(headingDeg = 0.0, pitchDeg = 0.0)
        assertThrows(IllegalArgumentException::class.java) {
            crossing(aim, emptyList(), toleranceDeg = -1.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            crossing(aim, emptyList(), toleranceDeg = Double.NaN)
        }
    }
}
