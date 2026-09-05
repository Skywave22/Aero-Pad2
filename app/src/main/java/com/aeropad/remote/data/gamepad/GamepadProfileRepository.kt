package com.aeropad.remote.data.gamepad

import com.aeropad.remote.data.db.GamepadProfileDao
import com.aeropad.remote.data.db.GamepadProfileEntity
import com.aeropad.remote.model.gamepad.ArrowDirection
import com.aeropad.remote.model.gamepad.ArrowStyle
import com.aeropad.remote.model.gamepad.ControlShape
import com.aeropad.remote.model.gamepad.GamepadControlSpec
import com.aeropad.remote.model.gamepad.GamepadControlType
import com.aeropad.remote.model.gamepad.GamepadLayoutSpec
import com.aeropad.remote.model.gamepad.StickSide
import com.aeropad.remote.model.widgets.WidgetFrame
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

    companion object {
        /** GTA preset profile names (stable ids for the Gamepad ⋮ menu). */
        const val GTA_COMFORT_NAME = "GTA Comfort Grip"
        const val GTA_OBSIDIAN_NAME = "GTA Obsidian 3D"
    }

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
            if (dao.count() == 0) {
                save(null, fpsLayout(), isBuiltIn = true)
                save(null, racingLayout(), isBuiltIn = true)
                save(null, fightingLayout(), isBuiltIn = true)
                save(null, casualLayout(), isBuiltIn = true)
                Timber.i("seeded built-in gamepad profiles")
            }
            // GTA presets (v3.4) — also seeded into EXISTING installs whose
            // DB already has rows; byName check keeps this idempotent.
            if (dao.byName(GTA_COMFORT_NAME) == null) {
                save(null, gtaComfortLayout(), isBuiltIn = true)
            }
            if (dao.byName(GTA_OBSIDIAN_NAME) == null) {
                save(null, gtaObsidianLayout(), isBuiltIn = true)
            }
        }.onFailure { Timber.e(it, "gamepad seeding failed") }
    }

    /** Row id for a seeded preset (used by the Gamepad ⋮ menu quick-play). */
    suspend fun idByName(name: String): Long? = dao.byName(name)?.id

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

    // ------------------------------------------------------------------
    // GTA presets (user-picked Designs 10 + 13). Real GTA V Xbox mapping:
    // A(0)=Sprint, B(1)=Attack, X(2)=Jump, Y(3)=Enter Vehicle,
    // LB(4)=Cover, RB(5)=Radio, LT(6)=Aim, RT(7)=Shoot,
    // Select(8)=Map, Start(9)=Menu, D-pad=Phone/Weapon wheel.
    // ------------------------------------------------------------------

    /** Design 10 "Comfort Grip": big labeled action tiles, thumb-zone 2×2. */
    internal fun gtaComfortLayout() = GamepadLayoutSpec(
        name = GTA_COMFORT_NAME,
        tags = listOf("GTA", "Action"),
        controls = listOf(
            // Left thumb: movement stick.
            GamepadControlSpec(
                id = "gc-ls", type = GamepadControlType.STICK,
                frame = WidgetFrame(0.04f, 0.42f, 0.24f, 0.46f),
                stickSide = StickSide.LEFT, color = 0xFF3A3F4B, deadZone = 8
            ),
            // Phone (D-pad up) + Weapon wheel (D-pad left) as single arrows.
            GamepadControlSpec(
                id = "gc-phone", type = GamepadControlType.ARROW,
                frame = WidgetFrame(0.05f, 0.20f, 0.09f, 0.17f),
                arrowDirection = ArrowDirection.UP, arrowStyle = ArrowStyle.DOT,
                color = 0xFF4A4458, icon = "📱", label = "PHONE"
            ),
            GamepadControlSpec(
                id = "gc-weapon", type = GamepadControlType.ARROW,
                frame = WidgetFrame(0.16f, 0.20f, 0.09f, 0.17f),
                arrowDirection = ArrowDirection.LEFT, arrowStyle = ArrowStyle.DOT,
                color = 0xFF4A4458, icon = "🎯", label = "WEAPON"
            ),
            // Top-center pill: Map / Menu / Radio.
            GamepadControlSpec(
                id = "gc-map", type = GamepadControlType.BUTTON,
                frame = WidgetFrame(0.32f, 0.04f, 0.10f, 0.13f),
                shape = ControlShape.ROUNDED, label = "MAP", buttonIndex = 8, color = 0xFF2A2A30
            ),
            GamepadControlSpec(
                id = "gc-menu", type = GamepadControlType.BUTTON,
                frame = WidgetFrame(0.44f, 0.04f, 0.10f, 0.13f),
                shape = ControlShape.ROUNDED, label = "MENU", buttonIndex = 9, color = 0xFF2A2A30
            ),
            GamepadControlSpec(
                id = "gc-radio", type = GamepadControlType.BUTTON,
                frame = WidgetFrame(0.56f, 0.04f, 0.10f, 0.13f),
                shape = ControlShape.ROUNDED, label = "RADIO", buttonIndex = 5, color = 0xFF2A2A30
            ),
            // Right thumb zone: vehicle pill above a 2×2 action grid.
            GamepadControlSpec(
                id = "gc-vehicle", type = GamepadControlType.BUTTON,
                frame = WidgetFrame(0.66f, 0.24f, 0.30f, 0.16f),
                shape = ControlShape.ROUNDED, label = "VEHICLE", buttonIndex = 3,
                color = 0xFFB8860B, icon = "🚗"
            ),
            GamepadControlSpec(
                id = "gc-fire", type = GamepadControlType.BUTTON,
                frame = WidgetFrame(0.66f, 0.44f, 0.14f, 0.25f),
                shape = ControlShape.ROUNDED, label = "FIRE", buttonIndex = 7, color = 0xFFB0524D
            ),
            GamepadControlSpec(
                id = "gc-aim", type = GamepadControlType.BUTTON,
                frame = WidgetFrame(0.82f, 0.44f, 0.14f, 0.25f),
                shape = ControlShape.ROUNDED, label = "AIM", buttonIndex = 6, color = 0xFF5B7FB5
            ),
            GamepadControlSpec(
                id = "gc-sprint", type = GamepadControlType.BUTTON,
                frame = WidgetFrame(0.66f, 0.72f, 0.14f, 0.25f),
                shape = ControlShape.ROUNDED, label = "SPRINT", buttonIndex = 0, color = 0xFF4E9B6F
            ),
            GamepadControlSpec(
                id = "gc-jump", type = GamepadControlType.BUTTON,
                frame = WidgetFrame(0.82f, 0.72f, 0.14f, 0.25f),
                shape = ControlShape.ROUNDED, label = "JUMP", buttonIndex = 2, color = 0xFF6B6F78
            ),
            // Cover + melee kept reachable next to the stick (GTA needs them).
            GamepadControlSpec(
                id = "gc-cover", type = GamepadControlType.BUTTON,
                frame = WidgetFrame(0.31f, 0.60f, 0.11f, 0.19f),
                shape = ControlShape.ROUNDED, label = "COVER", buttonIndex = 4, color = 0xFF4A4458
            ),
            GamepadControlSpec(
                id = "gc-melee", type = GamepadControlType.BUTTON,
                frame = WidgetFrame(0.31f, 0.34f, 0.11f, 0.19f),
                shape = ControlShape.ROUNDED, label = "MELEE", buttonIndex = 1, color = 0xFF4A4458
            )
        )
    )

    /** Design 13 "Obsidian 3D": classic console diamond, obsidian palette. */
    internal fun gtaObsidianLayout() = GamepadLayoutSpec(
        name = GTA_OBSIDIAN_NAME,
        tags = listOf("GTA", "Action"),
        controls = listOf(
            // Bumpers (violet pills) + triggers below them.
            GamepadControlSpec(
                id = "go-lb", type = GamepadControlType.TRIGGER,
                frame = WidgetFrame(0.03f, 0.04f, 0.16f, 0.13f),
                shape = ControlShape.ROUNDED, label = "COVER", buttonIndex = 4, color = 0xFF7C6CF5
            ),
            GamepadControlSpec(
                id = "go-rb", type = GamepadControlType.TRIGGER,
                frame = WidgetFrame(0.81f, 0.04f, 0.16f, 0.13f),
                shape = ControlShape.ROUNDED, label = "RADIO", buttonIndex = 5, color = 0xFF7C6CF5
            ),
            GamepadControlSpec(
                id = "go-lt", type = GamepadControlType.TRIGGER,
                frame = WidgetFrame(0.03f, 0.20f, 0.13f, 0.13f),
                shape = ControlShape.ROUNDED, label = "AIM", buttonIndex = 6, color = 0xFF2A2A30
            ),
            GamepadControlSpec(
                id = "go-rt", type = GamepadControlType.TRIGGER,
                frame = WidgetFrame(0.84f, 0.20f, 0.13f, 0.13f),
                shape = ControlShape.ROUNDED, label = "SHOOT", buttonIndex = 7, color = 0xFF2A2A30
            ),
            // Left: cyan-ring stick + D-pad (phone/weapon wheel on hat).
            GamepadControlSpec(
                id = "go-ls", type = GamepadControlType.STICK,
                frame = WidgetFrame(0.05f, 0.38f, 0.22f, 0.42f),
                stickSide = StickSide.LEFT, color = 0xFF2FD9F4, deadZone = 8
            ),
            GamepadControlSpec(
                id = "go-dpad", type = GamepadControlType.DPAD,
                frame = WidgetFrame(0.29f, 0.56f, 0.16f, 0.32f),
                color = 0xFF131313, eightWay = false
            ),
            // Center strip: Select / Home / Start.
            GamepadControlSpec(
                id = "go-select", type = GamepadControlType.BUTTON,
                frame = WidgetFrame(0.355f, 0.30f, 0.09f, 0.12f),
                shape = ControlShape.ROUNDED, label = "SEL", buttonIndex = 8, color = 0xFF2A2A30
            ),
            GamepadControlSpec(
                id = "go-home", type = GamepadControlType.BUTTON,
                frame = WidgetFrame(0.455f, 0.30f, 0.09f, 0.12f),
                shape = ControlShape.ROUNDED, label = "HOME", buttonIndex = 12, color = 0xFF2A2A30
            ),
            GamepadControlSpec(
                id = "go-start", type = GamepadControlType.BUTTON,
                frame = WidgetFrame(0.555f, 0.30f, 0.09f, 0.12f),
                shape = ControlShape.ROUNDED, label = "START", buttonIndex = 9, color = 0xFF2A2A30
            ),
            // Right: gem ABXY diamond with GTA action labels.
            btn("go-y", 0.80f, 0.30f, 0.09f, "Y", 3, 0xFFF1C40F).copy(icon = "🚗"),
            btn("go-x", 0.71f, 0.48f, 0.09f, "X", 2, 0xFF29C5FF).copy(icon = "🦘"),
            btn("go-b", 0.89f, 0.48f, 0.09f, "B", 1, 0xFFE74C3C).copy(icon = "👊"),
            btn("go-a", 0.80f, 0.66f, 0.09f, "A", 0, 0xFF2ECC71).copy(icon = "🏃")
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
