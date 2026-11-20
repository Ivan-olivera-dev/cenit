package io.github.ivan_olivera_dev.cenit.ui.compass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompassSensorStateTest {

    @Test
    fun `tracker applies declination and smooths across 359 to 0 wrap`() {
        val tracker = CompassHeadingTracker(alpha = 0.5)
        assertNull(tracker.state.headingDeg)
        assertEquals(CompassAvailability.AVAILABLE, tracker.state.availability)
        assertFalse(tracker.state.needsCalibration)

        // First magnetic reading 350° with +2° east declination -> snaps to 352° true north
        val first = tracker.onMagneticHeading(magneticDeg = 350.0, declinationDeg = 2.0)
        assertEquals(352.0, first.headingDeg!!, 1e-9)

        // Next magnetic reading 6° with +2° declination -> 8° true; midpoint across wrap from 352° is 0°
        val second = tracker.onMagneticHeading(magneticDeg = 6.0, declinationDeg = 2.0)
        assertEquals(0.0, second.headingDeg!!, 1e-9)
    }

    @Test
    fun `tracker flags calibration notice only for low or unreliable accuracy`() {
        val tracker = CompassHeadingTracker()

        assertFalse(tracker.onAccuracyChanged(CompassAccuracy.HIGH).needsCalibration)
        assertFalse(tracker.onAccuracyChanged(CompassAccuracy.MEDIUM).needsCalibration)
        assertTrue(tracker.onAccuracyChanged(CompassAccuracy.LOW).needsCalibration)
        assertTrue(tracker.onAccuracyChanged(CompassAccuracy.UNRELIABLE).needsCalibration)
        assertFalse(tracker.onAccuracyChanged(CompassAccuracy.HIGH).needsCalibration)
    }

    @Test
    fun `tracker reports unavailable state with north-up fallback and no calibration warning`() {
        val tracker = CompassHeadingTracker()
        tracker.onAccuracyChanged(CompassAccuracy.LOW)
        tracker.onMagneticHeading(magneticDeg = 120.0, declinationDeg = 0.0)

        val unavailable = tracker.onSensorUnavailable()
        assertEquals(CompassAvailability.UNAVAILABLE, unavailable.availability)
        assertNull(unavailable.headingDeg)
        assertEquals(0.0, unavailable.displayHeadingDeg, 1e-9)
        assertFalse(unavailable.needsCalibration)
    }

    @Test
    fun `tracker reset clears smoothed heading without dropping accuracy or availability`() {
        val tracker = CompassHeadingTracker(alpha = 0.2)
        tracker.onMagneticHeading(magneticDeg = 200.0, declinationDeg = 0.0)
        tracker.onAccuracyChanged(CompassAccuracy.LOW)

        tracker.resetSmoothing()
        // Next sample after pause/resume snaps directly instead of creeping from 200°
        val resumed = tracker.onMagneticHeading(magneticDeg = 40.0, declinationDeg = 0.0)
        assertEquals(40.0, resumed.headingDeg!!, 1e-9)
        assertTrue(resumed.needsCalibration)
    }

    @Test
    fun `tracker updates true heading when declination changes for a new place`() {
        val tracker = CompassHeadingTracker(alpha = 0.5)
        tracker.onMagneticHeading(magneticDeg = 100.0, declinationDeg = 0.0)
        assertEquals(100.0, tracker.state.headingDeg!!, 1e-9)

        val updated = tracker.onDeclinationChanged(declinationDeg = -4.5)
        assertEquals(95.5, updated.headingDeg!!, 1e-9)
    }
}
