package com.bluepilot.remote

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Module 0 smoke test — keeps the CI `testDebugUnitTest` step green from day one.
 * Real HID engine tests land in Module 1.
 */
class SmokeTest {

    @Test
    fun `project test harness works`() {
        assertTrue(true)
    }
}
