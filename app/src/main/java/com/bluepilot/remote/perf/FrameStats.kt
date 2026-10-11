package com.bluepilot.remote.perf

import android.view.Choreographer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * V2 PART A — REAL frame statistics from Choreographer callbacks.
 *
 * Every vsync delta is a real measured frame time — no simulation.
 * Histogram buckets: <=8ms (120fps), <=11ms (90fps), <=17ms (60fps),
 * <=33ms (30fps, jank), >33ms (severe jank).
 *
 * Zero overhead when stopped (callback unregistered). Refresh-rate
 * agnostic: works identically on 60/90/120Hz panels.
 */
object FrameStats {

    data class Stats(
        val fps: Int = 0,
        /** Real frame-time histogram counts (5 buckets, see class doc). */
        val histogram: List<Int> = listOf(0, 0, 0, 0, 0),
        val jankPercent: Int = 0,
        val running: Boolean = false
    )

    private val _stats = MutableStateFlow(Stats())
    val stats: StateFlow<Stats> = _stats

    private var lastNanos = 0L
    private var frameCount = 0
    private var windowStartMs = 0L
    private val buckets = IntArray(5)
    private var callback: Choreographer.FrameCallback? = null

    fun start() {
        if (callback != null) return
        lastNanos = 0L; frameCount = 0
        windowStartMs = System.currentTimeMillis()
        buckets.fill(0)
        val cb = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (lastNanos != 0L) {
                    val deltaMs = (frameTimeNanos - lastNanos) / 1_000_000.0
                    frameCount++
                    when {
                        deltaMs <= 8.5 -> buckets[0]++
                        deltaMs <= 11.5 -> buckets[1]++
                        deltaMs <= 17.5 -> buckets[2]++
                        deltaMs <= 33.5 -> buckets[3]++
                        else -> buckets[4]++
                    }
                    val elapsed = System.currentTimeMillis() - windowStartMs
                    if (elapsed >= 1000) {
                        val total = buckets.sum().coerceAtLeast(1)
                        _stats.value = Stats(
                            fps = (frameCount * 1000L / elapsed).toInt(),
                            histogram = buckets.toList(),
                            jankPercent = (buckets[3] + buckets[4]) * 100 / total,
                            running = true
                        )
                        frameCount = 0
                        windowStartMs = System.currentTimeMillis()
                        buckets.fill(0)
                    }
                }
                lastNanos = frameTimeNanos
                if (callback != null) Choreographer.getInstance().postFrameCallback(this)
            }
        }
        callback = cb
        Choreographer.getInstance().postFrameCallback(cb)
        _stats.value = Stats(running = true)
    }

    fun stop() {
        callback?.let { Choreographer.getInstance().removeFrameCallback(it) }
        callback = null
        _stats.value = Stats(running = false)
    }
}
