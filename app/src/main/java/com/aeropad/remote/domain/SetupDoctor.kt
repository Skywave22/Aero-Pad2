package com.aeropad.remote.domain

/**
 * F1 — Setup Doctor.
 *
 * Answers one question: *why isn't it working right now, and what do I tap
 * next?* Every finding is derived from state the app already records — no
 * probing, no network (the app has no INTERNET permission), no invented
 * numbers.
 *
 * Deliberately pure Kotlin (no Android imports) so the whole decision table is
 * unit-testable. The ViewModel maps real platform/engine state into
 * [SetupInputs]; the UI only renders the resulting [Finding] list.
 */
object SetupDoctor {

    /** How loud a finding is. Ordered by severity. */
    enum class Level { BLOCKER, WARNING, INFO, OK }

    /**
     * Where a finding's suggested fix lives. Pure enum (no Android types) —
     * the UI decides which Intent to fire.
     */
    enum class Fix { NONE, BLUETOOTH, PERMISSIONS, BATTERY, APP_SETTINGS, RECONNECT }

    /** One thing the doctor noticed. */
    data class Finding(
        val id: String,
        val level: Level,
        val title: String,
        val detail: String,
        /** What the user should do, phrased as an action. Empty for [Level.OK]. */
        val action: String = "",
        /** Which actionable place to send them to. [Fix.NONE] = informational. */
        val fix: Fix = Fix.NONE
    )

    /** Overall verdict for the header. */
    enum class Verdict { BLOCKED, NEEDS_ATTENTION, READY, NOT_CONNECTED }

    data class Report(
        val verdict: Verdict,
        val headline: String,
        val findings: List<Finding>
    )

    /**
     * Everything the doctor is allowed to look at. All fields are "what the app
     * actually knows"; nullable = unknown, and unknown is never treated as bad.
     */
    data class SetupInputs(
        /** True when the phone's Bluetooth adapter is on. */
        val bluetoothOn: Boolean = true,
        /** Required runtime permissions (BLUETOOTH_CONNECT / SCAN) granted. */
        val permissionsGranted: Boolean = true,
        /** Set when this ROM refused HID Device registration. */
        val hidUnsupportedReason: String? = null,
        /** Engine error message, if any. */
        val errorMessage: String? = null,
        /** A live HID link exists. */
        val connected: Boolean = false,
        /** Connected host name, for the success line. */
        val hostName: String? = null,
        /** PowerManager.isPowerSaveMode. */
        val batterySaverOn: Boolean = false,
        /** Battery capacity percent, 0..100, or null if unavailable. */
        val batteryPercent: Int? = null,
        /** Count of sendReport() calls that failed this session. */
        val sendFailures: Long = 0,
        /** Count of sendReport() calls attempted this session. */
        val sendTotal: Long = 0,
        /** Worst measured sendReport duration over the last 5 s (µs). */
        val recentMaxSendUs: Long = 0,
        /** Host RSSI in dBm, or null when the platform withholds it. */
        val rssiDbm: Int? = null,
        /** Unexpected disconnects this session. */
        val disconnects: Int = 0,
        /** Extended (digitizer/gamepad) descriptor is active. */
        val extendedDescriptor: Boolean = false,
        /** A reconnect is currently scheduled with backoff. */
        val reconnecting: Boolean = false,
        /** Which attempt the persistent loop is on (null when idle). */
        val reconnectAttempts: Int? = null,
        /** Seconds until the next scheduled attempt. */
        val reconnectNextInSeconds: Long? = null
    )

    /**
     * Locale-aware formatting helper.
     *
     * Kotlin's `String.format(vararg)` extension has no Locale overload, so
     * passing a Locale as the first vararg silently emits the raw template.
     * Always go through this.
     */
    private fun fmt(template: String, vararg args: Any?): String =
        java.lang.String.format(java.util.Locale.getDefault(), template, *args)

    // Thresholds — kept as named constants so the unit tests read as intent.
    const val SLOW_SEND_US = 5_000L
    const val WEAK_RSSI_DBM = -75
    const val LOW_BATTERY_PERCENT = 15
    const val UNSTABLE_DISCONNECTS = 2

    /**
     * Run every check. Returns findings ordered BLOCKER → WARNING → INFO → OK,
     * then by declaration order, so the top of the list is always the thing to
     * fix first.
     */
    fun diagnose(input: SetupInputs): Report {
        val out = mutableListOf<Finding>()

        // ---------- Blockers: nothing can work until these are fixed ----------
        if (!input.bluetoothOn) {
            out += Finding(
                id = "bt-off",
                level = Level.BLOCKER,
                title = "Bluetooth is off",
                detail = "AeroPad talks to your PC over Bluetooth HID, so it cannot " +
                    "work while the radio is off.",
                action = "Turn Bluetooth on",
                fix = Fix.BLUETOOTH
            )
        }

        if (!input.permissionsGranted) {
            out += Finding(
                id = "perms",
                level = Level.BLOCKER,
                title = "Permissions missing",
                detail = "Android is blocking access to nearby devices, so AeroPad " +
                    "cannot register itself or list your PC.",
                action = "Grant permissions",
                fix = Fix.PERMISSIONS
            )
        }

        input.hidUnsupportedReason?.let { reason ->
            out += Finding(
                id = "hid-unsupported",
                level = Level.BLOCKER,
                title = "This device can't act as a HID device",
                detail = "Android refused to register the HID profile: $reason. " +
                    "This is a limitation of the phone/ROM (common on some " +
                    "Chinese ROMs and on emulators), not a setting you can toggle.",
                action = "Open app settings",
                fix = Fix.APP_SETTINGS
            )
        }

        input.errorMessage?.let { message ->
            out += Finding(
                id = "engine-error",
                level = Level.BLOCKER,
                title = "Connection error",
                detail = message,
                action = "Open app settings",
                fix = Fix.APP_SETTINGS
            )
        }

        // ---------- Warnings: it works, but it will bite ----------
        if (input.sendTotal > 0 && input.sendFailures > 0) {
            val pct = (input.sendFailures * 100.0 / input.sendTotal)
            out += Finding(
                id = "send-failures",
                level = Level.WARNING,
                title = "Some input reports were rejected",
                detail = fmt(
                    "%,d of %,d reports failed (%.1f%%). A few at connect time " +
                        "is normal; a steady stream means the link is unstable.",
                    input.sendFailures, input.sendTotal, pct
                ),
                action = ""
            )
        }

        if (input.connected && input.recentMaxSendUs > SLOW_SEND_US) {
            out += Finding(
                id = "slow-sends",
                level = Level.WARNING,
                title = "Input feels laggy",
                detail = fmt(
                    "The slowest recent report took %d ms to hand off. " +
                        "Above %d ms you will notice the cursor stutter.",
                    input.recentMaxSendUs / 1000, SLOW_SEND_US / 1000
                ),
                action = ""
            )
        }

        if (input.connected && input.disconnects >= UNSTABLE_DISCONNECTS) {
            out += Finding(
                id = "unstable",
                level = Level.WARNING,
                title = "The link keeps dropping",
                detail = fmt(
                    "This session has already lost the host %d times. Move the " +
                        "phone closer, and check the PC isn't putting its Bluetooth " +
                        "adapter to sleep.",
                    input.disconnects
                ),
                action = ""
            )
        }

        val rssi = input.rssiDbm
        if (input.connected && rssi != null && rssi < WEAK_RSSI_DBM) {
            out += Finding(
                id = "weak-rssi",
                level = Level.WARNING,
                title = "Weak signal",
                detail = "Host signal strength is $rssi dBm. Below " +
                    "$WEAK_RSSI_DBM dBm, drops become likely — line of sight helps.",
                action = ""
            )
        }

        if (input.batterySaverOn) {
            out += Finding(
                id = "battery-saver",
                level = Level.WARNING,
                title = "Battery saver is on",
                detail = "Android throttles Bluetooth and background work in battery " +
                    "saver, which shows up as lag or surprise disconnects.",
                action = "Open battery settings",
                fix = Fix.BATTERY
            )
        }

        val battery = input.batteryPercent
        if (battery != null && battery in 0..LOW_BATTERY_PERCENT) {
            out += Finding(
                id = "low-battery",
                level = Level.WARNING,
                title = "Phone battery is low ($battery%)",
                detail = "Some phones cut Bluetooth power below " +
                    "$LOW_BATTERY_PERCENT% to protect the charge.",
                action = ""
            )
        }

        // ---------- Info: useful context, never an error ----------
        if (input.reconnecting) {
            out += Finding(
                id = "reconnecting",
                level = Level.INFO,
                title = input.reconnectAttempts?.let { attempt ->
                    if (attempt in 1..5) "Reconnecting (attempt $attempt)"
                    else "Still trying to reconnect"
                } ?: "Reconnecting automatically",
                detail = buildString {
                    append("AeroPad never gives up on its own: it retries quickly at " +
                        "first, then every 15–30 s, and immediately when you wake " +
                        "the phone. If the host is asleep, wake it and tap Retry.")
                    input.reconnectNextInSeconds?.let {
                        append(" Next attempt in about ${it}s.")
                    }
                },
                action = "Retry now",
                fix = Fix.RECONNECT
            )
        }

        if (!input.extendedDescriptor) {
            out += Finding(
                id = "legacy-descriptor",
                level = Level.INFO,
                title = "Standard descriptor in use",
                detail = "Keyboard, mouse and consumer controls are available. Turn on " +
                    "the extended descriptor in Settings if you want absolute " +
                    "touch (digitizer) reports."
            )
        }

        if (!input.connected) {
            out += Finding(
                id = "how-to-pair",
                level = Level.INFO,
                title = "Not connected yet",
                detail = "On the PC: Settings → Bluetooth → add a device, then pick " +
                    "the AeroPad entry that appears. Keep this screen open the " +
                    "first time so you can see the outcome."
            )
        }

        // ---------- All clear ----------
        if (out.none { it.level == Level.BLOCKER || it.level == Level.WARNING }) {
            if (input.connected) {
                out += Finding(
                    id = "all-good",
                    level = Level.OK,
                    title = "Everything checks out",
                    detail = "Connected to ${input.hostName ?: "your host"} with no " +
                        "problems detected."
                )
            }
        }

        val order = mapOf(Level.BLOCKER to 0, Level.WARNING to 1, Level.INFO to 2, Level.OK to 3)
        val sorted = out.sortedWith(compareBy { order.getValue(it.level) })

        val verdict = when {
            sorted.any { it.level == Level.BLOCKER } -> Verdict.BLOCKED
            sorted.any { it.level == Level.WARNING } -> Verdict.NEEDS_ATTENTION
            input.connected -> Verdict.READY
            else -> Verdict.NOT_CONNECTED
        }

        val headline = when (verdict) {
            Verdict.BLOCKED -> "Can't work yet — fix the first item"
            Verdict.NEEDS_ATTENTION -> "Working, but with caveats"
            Verdict.READY -> "Ready to go"
            Verdict.NOT_CONNECTED -> "Ready — just not paired yet"
        }

        return Report(verdict, headline, sorted)
    }
}
