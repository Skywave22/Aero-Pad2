package com.bluepilot.remote.domain

/**
 * ADV SECTION 3 — contextual profile suggestion (pure, unit-tested).
 *
 * Maps the REAL Bluetooth major device class of the connected host
 * (android.bluetooth.BluetoothClass.Device.Major constants — captured at
 * connect time, not guessed) plus the host name to suggested profile tags.
 */
object ProfileSuggester {

    // android.bluetooth.BluetoothClass.Device.Major values (stable API ints).
    const val MAJOR_COMPUTER = 0x0100
    const val MAJOR_PHONE = 0x0200
    const val MAJOR_AUDIO_VIDEO = 0x0400   // TVs/set-top boxes report AV
    const val MAJOR_UNCATEGORIZED = 0x1F00

    enum class HostKind { PC, TV, PHONE, UNKNOWN }

    /** Classify host by BT major class first, then name heuristics. */
    fun classify(majorClass: Int, name: String): HostKind {
        when (majorClass) {
            MAJOR_COMPUTER -> return HostKind.PC
            MAJOR_AUDIO_VIDEO -> return HostKind.TV
            MAJOR_PHONE -> return HostKind.PHONE
        }
        val n = name.lowercase()
        return when {
            listOf("tv", "bravia", "shield", "fire", "chromecast", "roku", "mibox").any { it in n } -> HostKind.TV
            listOf("pc", "desktop", "laptop", "book", "mac", "nuc").any { it in n } -> HostKind.PC
            else -> HostKind.UNKNOWN
        }
    }

    /**
     * Suggested profile tags for a host kind, best first. Tag names match
     * the editor's predefined tag set.
     */
    fun suggestedTags(kind: HostKind): List<String> = when (kind) {
        HostKind.PC -> listOf("FPS", "Racing", "Fighting")
        HostKind.TV -> listOf("Platformer", "Racing", "Casual")
        HostKind.PHONE -> listOf("Casual", "Platformer")
        HostKind.UNKNOWN -> emptyList()
    }

    /** Standard tag vocabulary shown in the editor + filter row. */
    val ALL_TAGS: List<String> =
        listOf("FPS", "Racing", "Fighting", "Platformer", "Casual", "Custom")
}
