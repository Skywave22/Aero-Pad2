package com.aeropad.remote.hid

import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

/** A report sequence belongs to the input session in which it was queued. */
internal class HidInputSession(private val parent: Job) {
    @Volatile
    var job: Job = SupervisorJob(parent)
        private set

    /** Only the HID dispatcher replaces the job. Any caller can capture it. */
    fun reset() {
        job.cancel()
        job = SupervisorJob(parent)
    }
}
