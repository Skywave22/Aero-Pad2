package com.bluepilot.remote.domain

/**
 * ADV SECTION 5 — connection health metrics (pure aggregation, unit-tested).
 *
 * HONESTY CONTRACT: this class only aggregates numbers that were actually
 * measured by the callers:
 *  - report send outcomes + durations measured with System.nanoTime around
 *    the REAL BluetoothHidDevice.sendReport() call,
 *  - connection state transitions with real timestamps,
 *  - RSSI values from real BluetoothDevice.readRemoteRssi-style callbacks
 *    where the platform provides them.
 * Nothing here invents values. What Android does not expose (true host
 * round-trip latency — HID has no ACK) is NOT synthesized; the dashboard
 * labels every metric with what it truly is.
 */
class ConnectionHealthTracker(
    private val clock: () -> Long = System::currentTimeMillis,
    /** Stability buckets: one per second, last [windowSeconds]. */
    private val windowSeconds: Int = 60
) {

    data class SecondBucket(
        val epochSecond: Long,
        val sent: Int,
        val failed: Int,
        /** Mean measured sendReport() duration in microseconds. */
        val meanSendUs: Long,
        /** Max measured sendReport() duration in microseconds. */
        val maxSendUs: Long
    )

    data class Snapshot(
        /** Total reports handed to sendReport since session start. */
        val totalSent: Long,
        /** sendReport() calls that returned false / threw. */
        val totalFailed: Long,
        /** Reports per second over the last full second. */
        val reportsPerSecond: Int,
        /** Rolling mean of measured sendReport durations (µs), last 5s. */
        val recentMeanSendUs: Long,
        /** Worst measured sendReport duration (µs), last 5s. */
        val recentMaxSendUs: Long,
        /** Last known RSSI in dBm; null = platform gave none. */
        val rssiDbm: Int?,
        /** Per-second history, oldest → newest (max windowSeconds). */
        val history: List<SecondBucket>,
        /** Session length ms since first report or connect mark. */
        val sessionMs: Long,
        /** Disconnect count this session. */
        val disconnects: Int
    )

    private data class Rec(val atMs: Long, val ok: Boolean, val durUs: Long)

    private val records = ArrayDeque<Rec>()
    private var totalSent = 0L
    private var totalFailed = 0L
    private var rssi: Int? = null
    private var sessionStart = 0L
    private var disconnects = 0

    @Synchronized
    fun markConnected() {
        if (sessionStart == 0L) sessionStart = clock()
    }

    @Synchronized
    fun markDisconnected() {
        disconnects++
    }

    @Synchronized
    fun reset() {
        records.clear(); totalSent = 0; totalFailed = 0
        rssi = null; sessionStart = 0L; disconnects = 0
    }

    /** Record one REAL measured send: [ok] result + [durUs] measured duration. */
    @Synchronized
    fun onReport(ok: Boolean, durUs: Long) {
        if (sessionStart == 0L) sessionStart = clock()
        totalSent++
        if (!ok) totalFailed++
        val now = clock()
        records.addLast(Rec(now, ok, durUs.coerceAtLeast(0)))
        val cutoff = now - windowSeconds * 1000L
        while (records.isNotEmpty() && records.first().atMs < cutoff) records.removeFirst()
    }

    /** Store a REAL RSSI reading (dBm) from the platform; null clears. */
    @Synchronized
    fun onRssi(dbm: Int?) {
        rssi = dbm
    }

    @Synchronized
    fun snapshot(): Snapshot {
        val now = clock()
        val lastSecond = records.count { it.atMs >= now - 1000 }
        val recent = records.filter { it.atMs >= now - 5000 }
        val recentMean = if (recent.isEmpty()) 0L else recent.sumOf { it.durUs } / recent.size
        val recentMax = recent.maxOfOrNull { it.durUs } ?: 0L

        // Per-second history buckets.
        val bySecond = records.groupBy { it.atMs / 1000 }
        val history = bySecond.entries.sortedBy { it.key }.map { (sec, list) ->
            SecondBucket(
                epochSecond = sec,
                sent = list.size,
                failed = list.count { !it.ok },
                meanSendUs = if (list.isEmpty()) 0 else list.sumOf { it.durUs } / list.size,
                maxSendUs = list.maxOfOrNull { it.durUs } ?: 0
            )
        }

        return Snapshot(
            totalSent = totalSent,
            totalFailed = totalFailed,
            reportsPerSecond = lastSecond,
            recentMeanSendUs = recentMean,
            recentMaxSendUs = recentMax,
            rssiDbm = rssi,
            history = history,
            sessionMs = if (sessionStart == 0L) 0 else now - sessionStart,
            disconnects = disconnects
        )
    }

    companion object {
        /**
         * Health classification from REAL inputs. Thresholds:
         *  - any failures in window or RSSI < -85dBm → RED
         *  - RSSI < -70dBm or recent max send > 5ms → YELLOW
         *  - else GREEN. RSSI null → judged on failures/latency only.
         */
        fun classify(snapshot: Snapshot): Health {
            val failedRecently = snapshot.history.takeLast(10).sumOf { it.failed } > 0
            val rssi = snapshot.rssiDbm
            return when {
                failedRecently || (rssi != null && rssi < -85) -> Health.POOR
                (rssi != null && rssi < -70) || snapshot.recentMaxSendUs > 5_000 -> Health.FAIR
                else -> Health.GOOD
            }
        }

        /** Diagnostic suggestions from REAL metric conditions only. */
        fun suggestions(snapshot: Snapshot, effects3dOn: Boolean, batterySaver: Boolean): List<String> {
            val out = mutableListOf<String>()
            val rssi = snapshot.rssiDbm
            if (rssi != null && rssi < -80)
                out += "Weak signal ($rssi dBm) — move closer to the host device."
            if (snapshot.recentMaxSendUs > 5_000)
                out += "Send latency spiked to ${snapshot.recentMaxSendUs / 1000}ms" +
                    (if (effects3dOn) " — try Reduce Motion / FLAT 3D quality temporarily." else ".")
            if (snapshot.history.takeLast(10).sumOf { it.failed } > 0)
                out += "Reports are failing — the link may be about to drop. Check host Bluetooth."
            if (batterySaver)
                out += "Battery saver is ON — Android may throttle Bluetooth performance."
            if (snapshot.disconnects > 1)
                out += "${snapshot.disconnects} disconnects this session — check for interference (Wi-Fi routers, USB 3.0 hubs)."
            return out
        }
    }

    enum class Health { GOOD, FAIR, POOR }
}
