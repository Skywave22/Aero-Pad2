package com.aeropad.remote.domain

/**
 * Tiny CSV list codec used for "favorites / recents" rows in settings
 * (e.g. favorite gamepad profiles). Pure string logic, unit-tested.
 */
object CsvListCodec {

    fun decode(csv: String): List<String> =
        csv.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    fun encode(ids: List<String>): String = ids.joinToString(",")

    /** Pushes [id] to the front, dedupes, caps at [max]. */
    fun push(csv: String, id: String, max: Int = 6): String {
        val list = decode(csv).toMutableList()
        list.remove(id)
        list.add(0, id)
        return encode(list.take(max))
    }

    /** Adds or removes [id]. */
    fun toggle(csv: String, id: String): String {
        val list = decode(csv).toMutableList()
        if (!list.remove(id)) list.add(id)
        return encode(list)
    }

    fun contains(csv: String, id: String): Boolean = decode(csv).contains(id)
}
