package com.aeropad.remote.hid

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HidInputSessionTest {
    @Test
    fun `reset cancels both suspended and still queued input without cancelling fresh input`() = runTest {
        val parent = Job()
        try {
            val session = HidInputSession(parent)
            val sent = mutableListOf<String>()
            val old = session.job
            launch(old) {
                sent += "press"
                delay(12)
                sent += "old release"
            }
            runCurrent()
            launch(old) { sent += "old queued press" }
            session.reset()
            launch(session.job) { sent += "new input" }
            advanceUntilIdle()
            assertFalse(old.isActive)
            assertEquals(listOf("press", "new input"), sent)
        } finally {
            parent.cancel()
        }
    }
}
