package io.github.ivan_olivera_dev.cenit.alerts

import io.github.ivan_olivera_dev.cenit.core.Place
import io.github.ivan_olivera_dev.cenit.core.alerts.AlertKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class AlertReschedulePlannerTest {
    private val madrid = Place(latitude = 40.4165, longitude = -3.7026)
    private val madridZone = ZoneId.of("Europe/Madrid")

    private fun atMadrid(text: String): Instant =
        LocalDateTime.parse(text).atZone(madridZone).toInstant()

    @Test
    fun `planOne schedules valid subscription with window at most 5 minutes`() {
        val sub = AlertSubscription.create(AlertKind.SUNRISE, "Madrid", madrid, "Europe/Madrid")
        val now = atMadrid("2026-06-21T05:00:00")

        val decision = AlertReschedulePlanner.planOne(sub, now)
        assertTrue(decision is ScheduleDecision.Schedule)
        val scheduled = decision as ScheduleDecision.Schedule
        assertEquals(sub, scheduled.subscription)
        assertEquals(atMadrid("2026-06-21T06:44:00"), scheduled.trigger.eventInstant)
        assertEquals(atMadrid("2026-06-21T06:14:00"), scheduled.trigger.triggerInstant)
        assertTrue(scheduled.windowLengthMillis in 1L..AlertReschedulePlanner.MAX_WINDOW_LENGTH_MILLIS)
        assertTrue(AlertReschedulePlanner.MAX_WINDOW_LENGTH_MILLIS <= 5L * 60L * 1000L)
    }

    @Test
    fun `planOne cancels when zone is invalid or polar event does not occur within horizon`() {
        val invalidZoneSub = AlertSubscription.create(AlertKind.SUNRISE, "Nowhere", madrid, "Not/A_Zone")
        val invalidDecision = AlertReschedulePlanner.planOne(invalidZoneSub, atMadrid("2026-06-21T05:00:00"))
        assertEquals(
            ScheduleDecision.Cancel(invalidZoneSub, CancelReason.INVALID_ZONE),
            invalidDecision,
        )

        val polarSub = AlertSubscription.create(
            kind = AlertKind.GOLDEN_MORNING,
            placeName = "North Pole",
            place = Place(89.5, 0.0),
            zoneId = "UTC",
        )
        val polarDecision = AlertReschedulePlanner.planOne(polarSub, Instant.parse("2026-06-21T12:00:00Z"))
        assertEquals(
            ScheduleDecision.Cancel(polarSub, CancelReason.NO_UPCOMING_OCCURRENCE),
            polarDecision,
        )
    }

    @Test
    fun `planAfterFire always advances past fired occurrence even with clock jitter`() {
        val sub = AlertSubscription.create(AlertKind.SUNRISE, "Madrid", madrid, "Europe/Madrid")
        val firedTrigger = atMadrid("2026-06-21T06:14:00")

        // Even if now is 2 seconds before firedTrigger (e.g. clock adjusted slightly backwards),
        // planAfterFire advances to 2026-06-22 instead of re-scheduling 2026-06-21.
        val decision = AlertReschedulePlanner.planAfterFire(
            subscription = sub,
            firedTriggerInstant = firedTrigger,
            now = firedTrigger.minusSeconds(2),
        )
        assertTrue(decision is ScheduleDecision.Schedule)
        val next = (decision as ScheduleDecision.Schedule).trigger
        assertEquals(LocalDate.of(2026, 6, 22), next.eventInstant.atZone(madridZone).toLocalDate())
        assertEquals(atMadrid("2026-06-22T06:45:00"), next.eventInstant)
        assertEquals(atMadrid("2026-06-22T06:15:00"), next.triggerInstant)
    }

    @Test
    fun `planAll handles mixed schedulable and unschedulable subscriptions`() {
        val good = AlertSubscription.create(AlertKind.SUNSET, "Madrid", madrid, "Europe/Madrid")
        val badZone = AlertSubscription.create(AlertKind.SUNRISE, "Broken", madrid, "Mars/Phobos")
        val decisions = AlertReschedulePlanner.planAll(listOf(good, badZone), atMadrid("2026-06-21T12:00:00"))

        assertEquals(2, decisions.size)
        assertTrue(decisions[0] is ScheduleDecision.Schedule)
        assertEquals(ScheduleDecision.Cancel(badZone, CancelReason.INVALID_ZONE), decisions[1])
    }

    @Test
    fun `shouldRescheduleForAction matches only boot, package replace, timezone, and time set`() {
        assertTrue(AlertReschedulePlanner.shouldRescheduleForAction("android.intent.action.BOOT_COMPLETED"))
        assertTrue(AlertReschedulePlanner.shouldRescheduleForAction("android.intent.action.MY_PACKAGE_REPLACED"))
        assertTrue(AlertReschedulePlanner.shouldRescheduleForAction("android.intent.action.TIMEZONE_CHANGED"))
        assertTrue(AlertReschedulePlanner.shouldRescheduleForAction("android.intent.action.TIME_SET"))

        assertFalse(AlertReschedulePlanner.shouldRescheduleForAction(null))
        assertFalse(AlertReschedulePlanner.shouldRescheduleForAction(""))
        assertFalse(AlertReschedulePlanner.shouldRescheduleForAction("android.intent.action.SCREEN_ON"))
    }

    @Test
    fun `request codes are non-negative, deterministic, and distinct per subscription`() {
        val subA = AlertSubscription.create(AlertKind.GOLDEN_EVENING, "Madrid", madrid, "Europe/Madrid")
        val subB = AlertSubscription.create(AlertKind.SUNSET, "Madrid", madrid, "Europe/Madrid")
        val subC = AlertSubscription.create(AlertKind.GOLDEN_EVENING, "Barcelona", Place(41.3888, 2.1590), "Europe/Madrid")

        val codeA1 = AlertRequestCode.forSubscriptionId(subA.id)
        val codeA2 = AlertRequestCode.forSubscriptionId(subA.id)
        val codeB = AlertRequestCode.forSubscriptionId(subB.id)
        val codeC = AlertRequestCode.forSubscriptionId(subC.id)

        assertTrue(codeA1 >= 0)
        assertTrue(codeB >= 0)
        assertTrue(codeC >= 0)
        assertEquals(codeA1, codeA2)
        assertNotEquals(codeA1, codeB)
        assertNotEquals(codeA1, codeC)
        assertNotEquals(codeB, codeC)
    }
}
