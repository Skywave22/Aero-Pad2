package com.bluepilot.remote.data.keyboard

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bluepilot.remote.model.keyboard.DefaultKeyboards
import com.bluepilot.remote.model.keyboard.KeyboardLayoutSpec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.keyboardDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "bluepilot_keyboard"
)

/** V2 MATRIX 7 b2 — one-handed keyboard mode. */
enum class OneHandedMode { OFF, LEFT, RIGHT }

/**
 * Persistence for the customizable full keyboard.
 * Same defensive contract as everything else: corrupt JSON → default board,
 * writes sanitize first, reads never throw.
 */
@Singleton
class KeyboardLayoutStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val LAYOUT_JSON = stringPreferencesKey("layout_json")
        val FULL_MODE = booleanPreferencesKey("full_mode")
        val COMBO_RATIO = androidx.datastore.preferences.core.floatPreferencesKey("combo_ratio")
        val COMBO_SCROLL_SIDE = stringPreferencesKey("combo_scroll_side")
        val ONE_HANDED = stringPreferencesKey("one_handed")   // V2 M7 b2
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "kind"
    }

    private val safePrefs: Flow<Preferences> = context.keyboardDataStore.data
        .catch { e ->
            if (e is IOException) {
                Timber.e(e, "keyboard prefs read failed")
                emit(emptyPreferences())
            } else throw e
        }

    /** The board (user-customized or default). */
    val layout: Flow<KeyboardLayoutSpec> = safePrefs.map { p ->
        decode(p[Keys.LAYOUT_JSON]) ?: DefaultKeyboards.fullQwerty()
    }

    /** Full (all rows) vs Compact (hides F-row/nav/arrows). */
    val fullMode: Flow<Boolean> = safePrefs.map { p -> p[Keys.FULL_MODE] ?: false }

    fun decode(raw: String?): KeyboardLayoutSpec? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            json.decodeFromString(KeyboardLayoutSpec.serializer(), raw).sanitized()
        }.onFailure { Timber.w(it, "keyboard layout decode failed") }.getOrNull()
    }

    suspend fun save(spec: KeyboardLayoutSpec) {
        runCatching {
            context.keyboardDataStore.edit { p ->
                p[Keys.LAYOUT_JSON] =
                    json.encodeToString(KeyboardLayoutSpec.serializer(), spec.sanitized())
            }
        }.onFailure { Timber.e(it, "keyboard layout save failed") }
    }

    suspend fun setFullMode(value: Boolean) {
        runCatching {
            context.keyboardDataStore.edit { p -> p[Keys.FULL_MODE] = value }
        }.onFailure { Timber.e(it, "full mode save failed") }
    }

    /** Reset the board to factory default. */
    suspend fun reset() = save(DefaultKeyboards.fullQwerty())

    // ---------- V2 MATRIX 7 b2 — one-handed mode ----------

    /** OFF / LEFT / RIGHT: board shrinks toward one thumb. */
    val oneHanded: Flow<OneHandedMode> = safePrefs.map { p ->
        runCatching { OneHandedMode.valueOf(p[Keys.ONE_HANDED] ?: "OFF") }
            .getOrDefault(OneHandedMode.OFF)
    }

    suspend fun setOneHanded(mode: OneHandedMode) {
        runCatching {
            context.keyboardDataStore.edit { p -> p[Keys.ONE_HANDED] = mode.name }
        }.onFailure { Timber.e(it, "one-handed save failed") }
    }

    // ---------- PC Combo screen preferences ----------

    /** Trackpad : keyboard split ratio (fraction of height for trackpad). */
    val comboRatio: Flow<Float> = safePrefs.map { p ->
        (p[Keys.COMBO_RATIO] ?: 0.5f).coerceIn(0.25f, 0.75f)
    }

    val comboScrollSide: Flow<com.bluepilot.remote.viewmodel.ScrollBarSide> = safePrefs.map { p ->
        runCatching {
            com.bluepilot.remote.viewmodel.ScrollBarSide.valueOf(
                p[Keys.COMBO_SCROLL_SIDE] ?: "RIGHT"
            )
        }.getOrDefault(com.bluepilot.remote.viewmodel.ScrollBarSide.RIGHT)
    }

    suspend fun setComboRatio(value: Float) {
        runCatching {
            context.keyboardDataStore.edit { p -> p[Keys.COMBO_RATIO] = value }
        }.onFailure { Timber.e(it, "combo ratio save failed") }
    }

    suspend fun setComboScrollSide(side: com.bluepilot.remote.viewmodel.ScrollBarSide) {
        runCatching {
            context.keyboardDataStore.edit { p -> p[Keys.COMBO_SCROLL_SIDE] = side.name }
        }.onFailure { Timber.e(it, "combo side save failed") }
    }
}
