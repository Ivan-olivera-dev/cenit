package io.github.ivan_olivera_dev.cenit.alerts

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactAlarmPolicyTest {

    @Test
    fun `hasCapabilityChanged returns true only when a known previous state flips`() {
        assertFalse(ExactAlarmPolicy.hasCapabilityChanged(lastKnown = null, current = false))
        assertFalse(ExactAlarmPolicy.hasCapabilityChanged(lastKnown = null, current = true))
        assertFalse(ExactAlarmPolicy.hasCapabilityChanged(lastKnown = false, current = false))
        assertFalse(ExactAlarmPolicy.hasCapabilityChanged(lastKnown = true, current = true))
        assertTrue(ExactAlarmPolicy.hasCapabilityChanged(lastKnown = false, current = true))
        assertTrue(ExactAlarmPolicy.hasCapabilityChanged(lastKnown = true, current = false))
    }

    @Test
    fun `shouldShowHoyNotice is true only on API 31+ when exact alarms are off and notice was not yet shown`() {
        assertTrue(
            ExactAlarmPolicy.shouldShowHoyNotice(
                requiresExactAlarmPermission = true,
                canScheduleExactAlarms = false,
                alreadyShown = false,
            ),
        )
        assertFalse(
            ExactAlarmPolicy.shouldShowHoyNotice(
                requiresExactAlarmPermission = true,
                canScheduleExactAlarms = false,
                alreadyShown = true,
            ),
        )
        assertFalse(
            ExactAlarmPolicy.shouldShowHoyNotice(
                requiresExactAlarmPermission = true,
                canScheduleExactAlarms = true,
                alreadyShown = false,
            ),
        )
        assertFalse(
            ExactAlarmPolicy.shouldShowHoyNotice(
                requiresExactAlarmPermission = false,
                canScheduleExactAlarms = false,
                alreadyShown = false,
            ),
        )
    }
}
