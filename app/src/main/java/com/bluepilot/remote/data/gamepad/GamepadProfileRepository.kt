package com.bluepilot.remote.data.gamepad

import com.bluepilot.remote.data.db.GamepadProfileDao
import com.bluepilot.remote.data.db.GamepadProfileEntity
import com.bluepilot.remote.model.gamepad.ControlShape
import com.bluepilot.remote.model.gamepad.GamepadControlSpec
import com.bluepilot.remote.model.gamepad.GamepadControlType
import com.bluepilot.remote.model.gamepad.GamepadLayoutSpec
import com.bluepilot.remote.model.gamepad.StickSide
import com.bluepilot.remote.model.widgets.WidgetFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** A stored gamepad profile with decoded spec. */
data class GamepadProfile(
    val id: Long,
    val spec: GamepadLayoutSpec,
    val isBuiltIn: Boolean
)

/**
 * Room-backed storage for custom gamepad profiles.
 * Same defensive JSON contract as layouts/macros: decode never throws,
 * corrupt rows dropped, writes sanitized. Seeds FPS + Racing templates.
 */
@Singleton
class GamepadProfileRepository @Inject constructor(
    private val dao: GamepadProfileDao
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "kind"
    }

    fun toJson(spec: GamepadLayoutSpec): String =
        json.encodeToString(GamepadLayoutSpec.serializer(), spec.sanitized())

    fun fromJson(raw: String?): GamepadLayoutSpec? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            json.decodeFromString(GamepadLayoutSpec.serializer(), raw).sanitized()
        }.onFailure { Timber.w(it, "gamepad JSON decode failed") }.getOrNull()
    }

    fun observeAll(): Flow<List<GamepadProfile>> =
        dao.observeAll().map { entities ->
            entities.mapNotNull { e ->
                fromJson(e.layoutJson)?.let { GamepadProfile(e.id, it, e.isBuiltIn) }
            }
        }

    suspend fun byId(id: Long): GamepadProfile? {
        val e = dao.byId(id) ?: return null
        return fromJson(e.layoutJson)?.let { GamepadProfile(e.id, it, e.isBuiltIn) }
    }

    suspend fun save(id: Long?, spec: GamepadLayoutSpec, isBuiltIn: Boolean = false): Long {
        val clean = spec.sanitized()
        return dao.upsert(
            GamepadProfileEntity(
                id = id ?: 0,
                name = clean.name,
                layoutJson = toJson(clean),
                isBuiltIn = isBuiltIn,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun exportJson(id: Long): String? = byId(id)?.let { toJson(it.spec) }

    suspend fun importJson(raw: String): Long? {
        val spec = fromJson(raw) ?: return null
        return save(null, spec.copy(name = spec.name + " (imported)"))
    }

    suspend fun seedIfEmpty() {
        runCatching {
            if (dao.count() > 0) return
            save(null, fpsLayout(), isBuiltIn = true)
            save(null, racingLayout(), isBuiltIn = true)
            save(null, fightingLayout(), isBuiltIn = true)
            save(null, casualLayout(), isBuiltIn = true)
            Timber.i("seeded built-in gamepad profiles")
        }.onFailure { Timber.e(it, "gamepad seeding failed") }
    }

    // ------------------------------------------------------------------
    // Built-in templates (landscape fractional coordinates)
    // ------------------------------------------------------------------

    private fun btn(
        id: String, x: Float, y: Float, size: Float,
        label: String, index: Int, color: Long
    ) = GamepadControlSpec(
        id = id, type = GamepadControlType.BUTTON,
        frame = WidgetFrame(x, y, size, size * 1.8f),
        shape = ControlShape.CIRCLE, color = color,
        label = label, buttonIndex = index
    )

    /** "FPS Layout": left stick + ABXY diamond + shoulders + dpad. */
    internal fun fpsLayout() = GamepadLayoutSpec(
        name = "FPS Layout",
        controls = listOf(
            GamepadControlSpec(
                id = "f-ls", type = GamepadControlType.STICK,
                frame = WidgetFrame(0.04f, 0.35f, 0.22f, 0.42f),
                stickSide = StickSide.LEFT, color = 0xFF2F6BFF, deadZone = 10
            ),
            GamepadControlSpec(
                id = "f-dpad", type = GamepadControlType.DPAD,
                frame = WidgetFrame(0.28f, 0.55f, 0.16f, 0.34f),
                color = 0xFF1A2238, eightWay = false
            ),
            btn("f-a", 0.84f, 0.62f, 0.09f, "A", 0, 0xFF2ECC71),
            btn("f-b", 0.92f, 0.44f, 0.09f, "B", 1, 0xFFE74C3C),
            btn("f-x", 0.76f, 0.44f, 0.09f, "X", 2, 0xFF29C5FF),
            btn("f-y", 0.84f, 0.26f, 0.09f, "Y", 3, 0xFFF1C40F),
            GamepadControlSpec(
                id = "f-l1", type = GamepadControlType.TRIGGER,
                frame = WidgetFrame(0.03f, 0.05f, 0.16f, 0.14f),
                shape = ControlShape.ROUNDED, label = "L1", buttonIndex = 4, color = 0xFF1A2238
            ),
            GamepadControlSpec(
                id = "f-r1", type = GamepadControlType.TRIGGER,
                frame = WidgetFrame(0.81f, 0.05f, 0.16f, 0.14f),
                shape = ControlShape.ROUNDED, label = "R1", buttonIndex = 5, color = 0xFF1A2238
            ),
            GamepadControlSpec(
                id = "f-start", type = GamepadControlType.BUTTON,
                frame = WidgetFrame(0.55f, 0.06f, 0.10f, 0.12f),
                shape = ControlShape.ROUNDED, label = "Start", buttonIndex = 9, color = 0xFF1A2238
            ),
            GamepadControlSpec(
                id = "f-select", type = GamepadControlType.BUTTON,
                frame = WidgetFrame(0.35f, 0.06f, 0.10f, 0.12f),
                shape = ControlShape.ROUNDED, label = "Sel", buttonIndex = 8, color = 0xFF1A2238
            )
        )
    )

    /** "Fighting Layout": dpad + 6 attack buttons in arcade arrangement. */
    internal fun fightingLayout() = GamepadLayoutSpec(
        name = "Fighting Layout",
        controls = listOf(
            GamepadControlSpec(
                id = "fg-dpad", type = GamepadControlType.DPAD,
                frame = WidgetFrame(0.04f, 0.30f, 0.24f, 0.55f),
                color = 0xFF1A2238, eightWay = true
            ),
            btn("fg-lp", 0.55f, 0.25f, 0.09f, "LP", 2, 0xFF29C5FF),
            btn("fg-mp", 0.68f, 0.20f, 0.09f, "MP", 3, 0xFFF1C40F),
            btn("fg-hp", 0.81f, 0.20f, 0.09f, "HP", 5, 0xFFE67E22),
            btn("fg-lk", 0.55f, 0.60f, 0.09f, "LK", 0, 0xFF2ECC71),
            btn("fg-mk", 0.68f, 0.65f, 0.09f, "MK", 1, 0xFFE74C3C),
            btn("fg-hk", 0.81f, 0.65f, 0.09f, "HK", 7, 0xFFFF2D95)
        )
    )

    /** "Casual Layout": dpad + 2 big action buttons for browser games. */
    internal fun casualLayout() = GamepadLayoutSpec(
        name = "Casual Layout",
        controls = listOf(
            GamepadControlSpec(
                id = "cs-dpad", type = GamepadControlType.DPAD,
                frame = WidgetFrame(0.05f, 0.30f, 0.26f, 0.55f),
                color = 0xFF1A2238, eightWay = false
            ),
            btn("cs-a", 0.72f, 0.55f, 0.13f, "JUMP", 0, 0xFF2ECC71),
            btn("cs-b", 0.86f, 0.35f, 0.11f, "FIRE", 1, 0xFFE74C3C),
            btn("cs-x", 0.60f, 0.30f, 0.09f, "X", 2, 0xFF29C5FF)
        )
    )

    /** "Racing Layout": steering by dual triggers + pedals as big buttons. */
    internal fun racingLayout() = GamepadLayoutSpec(
        name = "Racing Layout",
        controls = listOf(
            GamepadControlSpec(
                id = "r-steer", type = GamepadControlType.STICK,
                frame = WidgetFrame(0.05f, 0.30f, 0.24f, 0.46f),
                stickSide = StickSide.LEFT, color = 0xFF29C5FF, deadZone = 5
            ),
            GamepadControlSpec(
                id = "r-gas", type = GamepadControlType.BUTTON,
                frame = WidgetFrame(0.82f, 0.50f, 0.14f, 0.40f),
                shape = ControlShape.ROUNDED, label = "GAS", buttonIndex = 0, color = 0xFF2ECC71
            ),
            GamepadControlSpec(
                id = "r-brake", type = GamepadControlType.BUTTON,
                frame = WidgetFrame(0.64f, 0.56f, 0.13f, 0.34f),
                shape = ControlShape.ROUNDED, label = "BRK", buttonIndex = 1, color = 0xFFE74C3C
            ),
            GamepadControlSpec(
                id = "r-nitro", type = GamepadControlType.BUTTON,
                frame = WidgetFrame(0.70f, 0.14f, 0.10f, 0.20f),
                label = "N2O", buttonIndex = 2, color = 0xFFF1C40F
            ),
            GamepadControlSpec(
                id = "r-shift-up", type = GamepadControlType.TRIGGER,
                frame = WidgetFrame(0.84f, 0.04f, 0.13f, 0.14f),
                shape = ControlShape.ROUNDED, label = "▲", buttonIndex = 5, color = 0xFF1A2238
            ),
            GamepadControlSpec(
                id = "r-shift-dn", type = GamepadControlType.TRIGGER,
                frame = WidgetFrame(0.03f, 0.04f, 0.13f, 0.14f),
                shape = ControlShape.ROUNDED, label = "▼", buttonIndex = 4, color = 0xFF1A2238
            )
        )
    )
}
