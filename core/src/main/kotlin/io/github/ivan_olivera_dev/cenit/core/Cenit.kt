package io.github.ivan_olivera_dev.cenit.core

/** Shared astronomical constants. The ephemeris itself arrives in T5 (Astronomy Engine). */
object Cenit {
    /**
     * Altitude of the body's centre at rise and set, in degrees: atmospheric
     * refraction (34') plus the solar semidiameter (16'). See design/system.md, event-rise-set.
     */
    const val RISE_SET_ALTITUDE_DEG: Double = -0.833
}
