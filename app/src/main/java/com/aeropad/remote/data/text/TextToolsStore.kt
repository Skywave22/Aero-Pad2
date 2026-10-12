package com.aeropad.remote.data.text

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** A saved snippet of frequently-sent text (address, command, code…). */
@Serializable
data class Snippet(
    val id: String,
    val label: String,
    val text: String
)

/**
 * Quick Text & Clipboard tools persistence.
 *
 * Snippets are user-managed; clipboard history mirrors THIS phone's
 * clipboard locally (Bluetooth HID is one-way — full two-way clipboard
 * sync would need a companion app or another data channel, and the UI
 * says exactly that).
 */
@Singleton
class TextToolsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val CLIP_LIMIT = 12
        const val LABEL_MAX = 40
        const val TEXT_MAX = 2000
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ---------- snippets ----------

    private val _snippets = MutableStateFlow(loadSnippets())
    val snippets: StateFlow<List<Snippet>> = _snippets.asStateFlow()

    fun addSnippet(label: String, text: String) {
        val clean = Snippet(
            id = java.util.UUID.randomUUID().toString(),
            label = label.trim().take(LABEL_MAX).ifEmpty { "Snippet" },
            text = text.take(TEXT_MAX)
        )
        if (clean.text.isBlank()) return
        persistSnippets(_snippets.value + clean)
    }

    fun removeSnippet(id: String) {
        persistSnippets(_snippets.value.filterNot { it.id == id })
    }

    // ---------- clipboard history (local mirror) ----------

    private val _clips = MutableStateFlow(loadClips())
    val clips: StateFlow<List<String>> = _clips.asStateFlow()

    /** Records a clipboard entry (newest first, de-duplicated, capped). */
    fun pushClip(text: String) {
        val t = text.take(TEXT_MAX)
        if (t.isBlank()) return
        persistClips((listOf(t) + _clips.value.filterNot { it == t }).take(CLIP_LIMIT))
    }

    fun clearClips() = persistClips(emptyList())

    // ---------- io ----------

    private fun snippetsFile() = java.io.File(context.filesDir, "snippets.json")
    private fun clipsFile() = java.io.File(context.filesDir, "clip_history.json")

    private fun loadSnippets(): List<Snippet> = runCatching {
        snippetsFile().takeIf { it.exists() }?.let { json.decodeFromString<List<Snippet>>(it.readText()) }
    }.getOrNull().orEmpty()

    private fun loadClips(): List<String> = runCatching {
        clipsFile().takeIf { it.exists() }?.let { json.decodeFromString<List<String>>(it.readText()) }
    }.getOrNull().orEmpty()

    private fun persistSnippets(list: List<Snippet>) {
        _snippets.value = list
        write(
            snippetsFile(),
            json.encodeToString(ListSerializer(Snippet.serializer()), list)
        )
    }

    private fun persistClips(list: List<String>) {
        _clips.value = list
        write(
            clipsFile(),
            json.encodeToString(ListSerializer(String.serializer()), list)
        )
    }

    private fun write(f: java.io.File, raw: String) = runCatching {
        val tmp = java.io.File(f.parentFile, f.name + ".tmp")
        tmp.writeText(raw)
        tmp.renameTo(f)
    }.onFailure { Timber.e(it, "text tools save failed") }
}
