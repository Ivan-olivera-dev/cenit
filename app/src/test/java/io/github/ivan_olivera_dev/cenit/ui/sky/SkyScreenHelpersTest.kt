package io.github.ivan_olivera_dev.cenit.ui.sky

import io.github.ivan_olivera_dev.cenit.core.LunarQuarter
import io.github.ivan_olivera_dev.cenit.ui.planner.PlannerState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Clock
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class SkyScreenHelpersTest {
    private val madridZone = ZoneId.of("Europe/Madrid")

    private fun fixedClock(isoLocal: String): Clock {
        val instant = LocalDateTime.parse(isoLocal).atZone(madridZone).toInstant()
        return Clock.fixed(instant, madridZone)
    }

    @Test
    fun `quarter preview phases match exact lunar angles and illuminations`() {
        val newMoon = quarterPreviewPhase(LunarQuarter.NEW)
        assertEquals(0.0, newMoon.angle, 1e-9)
        assertEquals(0.0, newMoon.illumination, 1e-9)

        val firstQuarter = quarterPreviewPhase(LunarQuarter.FIRST_QUARTER)
        assertEquals(90.0, firstQuarter.angle, 1e-9)
        assertEquals(0.5, firstQuarter.illumination, 1e-9)

        val fullMoon = quarterPreviewPhase(LunarQuarter.FULL)
        assertEquals(180.0, fullMoon.angle, 1e-9)
        assertEquals(1.0, fullMoon.illumination, 1e-9)

        val lastQuarter = quarterPreviewPhase(LunarQuarter.LAST_QUARTER)
        assertEquals(270.0, lastQuarter.angle, 1e-9)
        assertEquals(0.5, lastQuarter.illumination, 1e-9)
    }

    @Test
    fun `scrubChartFraction updates planner time cleanly including on DST day`() {
        val normalState = PlannerState.now(fixedClock("2026-06-21T16:20:00"), madridZone)
        assertEquals(LocalTime.of(0, 0), scrubChartFraction(normalState, 0f).selectedTime)
        assertEquals(LocalTime.of(12, 0), scrubChartFraction(normalState, 0.5f).selectedTime)
        assertEquals(LocalTime.of(23, 55), scrubChartFraction(normalState, 1f).selectedTime)

        // Spring DST day has 1380 minutes (02:00 jumps to 03:00): midpoint is 690 min = 12:30 CEST
        val dstState = PlannerState.now(fixedClock("2026-03-29T10:00:00"), madridZone)
        assertEquals(1380, dstState.dayMinutes)
        assertEquals(LocalTime.of(0, 0), scrubChartFraction(dstState, 0f).selectedTime)
        assertEquals(LocalTime.of(23, 55), scrubChartFraction(dstState, 1f).selectedTime)
    }
}
