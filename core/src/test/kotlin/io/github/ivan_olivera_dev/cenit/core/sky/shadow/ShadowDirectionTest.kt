package io.github.ivan_olivera_dev.cenit.core.sky.shadow

import io.github.ivan_olivera_dev.cenit.core.Ephemeris
import io.github.ivan_olivera_dev.cenit.core.Place
import io.github.ivan_olivera_dev.cenit.core.SkyPosition
import io.github.ivan_olivera_dev.cenit.core.sky.SkyFormatting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ShadowDirectionTest {

    @Test
    fun `shadow azimuth adds 180 degrees and wraps into 0 to 360`() {
        assertEquals(20.0, ShadowDirection.shadowAzimuth(200.0, 25.0)!!, 1e-9)
        assertEquals(180.0, ShadowDirection.shadowAzimuth(0.0, 15.0)!!, 1e-9)
        assertEquals(179.5, ShadowDirection.shadowAzimuth(359.5, 10.0)!!, 1e-9)
        assertEquals(0.0, ShadowDirection.shadowAzimuth(180.0, 45.0)!!, 1e-9)
        assertEquals(49.0, ShadowDirection.shadowAzimuth(229.0, 13.4)!!, 1e-9)
        assertEquals(270.0, ShadowDirection.shadowAzimuth(90.0, 30.0)!!, 1e-9)
        assertEquals(90.0, ShadowDirection.shadowAzimuth(270.0, 30.0)!!, 1e-9)
        assertEquals(170.0, ShadowDirection.shadowAzimuth(-10.0, 20.0)!!, 1e-9)
        assertEquals(0.0, ShadowDirection.shadowAzimuth(540.0, 20.0)!!, 1e-9)
    }

    @Test
    fun `shadow azimuth and info are null at or below the horizon or for non-finite inputs`() {
        assertNull(ShadowDirection.shadowAzimuth(SkyPosition(azimuth = 200.0, elevation = 0.0)))
        assertNull(ShadowDirection.shadowAzimuth(SkyPosition(azimuth = 200.0, elevation = -0.01)))
        assertNull(ShadowDirection.shadowAzimuth(SkyPosition(azimuth = 200.0, elevation = -0.833)))
        assertNull(ShadowDirection.shadowAzimuth(SkyPosition(azimuth = 200.0, elevation = -18.0)))
        assertNull(ShadowDirection.shadowAzimuth(Double.NaN, 20.0))
        assertNull(ShadowDirection.shadowAzimuth(180.0, Double.NaN))
        assertNull(ShadowDirection.shadowAzimuth(Double.POSITIVE_INFINITY, 20.0))
        assertNull(ShadowDirection.shadowAzimuth(180.0, Double.POSITIVE_INFINITY))

        val below = SkyPosition(azimuth = 250.0, elevation = -2.0)
        assertNull(below.shadowRatio)
        assertNull(ShadowDirection.shadowCompassIndex(below))
        assertNull(ShadowDirection.fromSunPosition(below))

        val horizon = SkyPosition(azimuth = 250.0, elevation = 0.0)
        assertNull(horizon.shadowRatio)
        assertNull(ShadowDirection.shadowCompassIndex(horizon))
        assertNull(ShadowDirection.fromSunPosition(horizon))
    }

    @Test
    fun `16-point wind index matches SkyFormatting for shadow bearings`() {
        // Sun 229 deg (SW) -> shadow 49 deg -> NE (index 2)
        val sunSw = SkyPosition(azimuth = 229.0, elevation = 13.4)
        assertEquals(SkyFormatting.compassIndex(49.0), ShadowDirection.shadowCompassIndex(sunSw))
        assertEquals(2, ShadowDirection.shadowCompassIndex(sunSw))

        // Sun 200 deg -> shadow 20 deg -> NNE (index 1)
        assertEquals(1, ShadowDirection.shadowCompassIndex(SkyPosition(200.0, 20.0)))
        // Sun 0 deg -> shadow 180 deg -> S (index 8)
        assertEquals(8, ShadowDirection.shadowCompassIndex(SkyPosition(0.0, 20.0)))
        // Sun 359.5 deg -> shadow 179.5 deg -> S (index 8)
        assertEquals(8, ShadowDirection.shadowCompassIndex(SkyPosition(359.5, 20.0)))
        // Sun 180 deg -> shadow 0 deg -> N (index 0)
        assertEquals(0, ShadowDirection.shadowCompassIndex(SkyPosition(180.0, 20.0)))
    }

    @Test
    fun `fromSunPosition matches SkyPosition shadowRatio on real ephemeris instants`() {
        val madrid = Place(latitude = 40.4165, longitude = -3.7026)
        val zone = ZoneId.of("Europe/Madrid")

        val afternoon = LocalDateTime.parse("2026-11-20T16:25").atZone(zone).toInstant()
        val afternoonSun = Ephemeris.sunPosition(madrid, afternoon)
        val info = ShadowDirection.fromSunPosition(afternoonSun)
        assertNotNull(info)
        assertEquals(afternoonSun.shadowRatio!!, info!!.ratio, 1e-9)
        assertEquals((afternoonSun.azimuth + 180.0) % 360.0, info.azimuthDeg, 1e-9)
        assertEquals(SkyFormatting.compassIndex(info.azimuthDeg), info.compassIndex)

        val night = LocalDateTime.parse("2026-11-20T21:00").atZone(zone).toInstant()
        val nightSun = Ephemeris.sunPosition(madrid, night)
        assertNull(nightSun.shadowRatio)
        assertNull(ShadowDirection.fromSunPosition(nightSun))
    }
}
