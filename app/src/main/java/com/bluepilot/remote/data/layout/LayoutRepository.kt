package com.bluepilot.remote.data.layout

import com.bluepilot.remote.data.db.LayoutProfileDao
import com.bluepilot.remote.data.db.LayoutProfileEntity
import com.bluepilot.remote.model.widgets.LayoutSpec
import com.bluepilot.remote.model.widgets.WidgetAction
import com.bluepilot.remote.model.widgets.WidgetFrame
import com.bluepilot.remote.model.widgets.WidgetSpec
import com.bluepilot.remote.model.widgets.WidgetStyle
import com.bluepilot.remote.model.widgets.WidgetType
import com.bluepilot.remote.model.HidConsumer
import com.bluepilot.remote.model.HidKeys
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** A layout profile with its decoded spec (entities stay in the data layer). */
data class LayoutProfile(
    val id: Long,
    val spec: LayoutSpec,
    val isBuiltIn: Boolean
)

/**
 * Repository for user layouts. Seeds two built-in starter layouts on first
 * run so the Layouts screen is never empty and users have templates to
 * duplicate & customize in Module 6.
 */
@Singleton
class LayoutRepository @Inject constructor(
    private val dao: LayoutProfileDao
) {

    /** All profiles; rows with corrupt JSON are dropped (never crash). */
    fun observeProfiles(): Flow<List<LayoutProfile>> =
        dao.observeAll().map { entities ->
            entities.mapNotNull { entity ->
                LayoutSerializer.layoutFromJson(entity.layoutJson)?.let { spec ->
                    LayoutProfile(entity.id, spec, entity.isBuiltIn)
                }
            }
        }

    suspend fun byId(id: Long): LayoutProfile? {
        val entity = dao.byId(id) ?: return null
        val spec = LayoutSerializer.layoutFromJson(entity.layoutJson) ?: return null
        return LayoutProfile(entity.id, spec, entity.isBuiltIn)
    }

    /** Insert or update; returns row id. Spec is sanitized via the serializer. */
    suspend fun save(id: Long?, spec: LayoutSpec, isBuiltIn: Boolean = false): Long {
        val entity = LayoutProfileEntity(
            id = id ?: 0,
            name = spec.sanitized().name,
            layoutJson = LayoutSerializer.layoutToJson(spec),
            isBuiltIn = isBuiltIn,
            updatedAt = System.currentTimeMillis()
        )
        return dao.upsert(entity)
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    /** Export a profile as a shareable JSON string. */
    suspend fun exportJson(id: Long): String? = byId(id)?.let {
        LayoutSerializer.layoutToJson(it.spec)
    }

    /** Import from JSON; returns new row id or null when invalid. */
    suspend fun importJson(raw: String): Long? {
        val spec = LayoutSerializer.layoutFromJson(raw) ?: return null
        return save(null, spec.copy(name = spec.name + " (imported)"))
    }

    /** Seed starter layouts exactly once. */
    suspend fun seedIfEmpty() {
        runCatching {
            if (dao.count() > 0) return
            save(null, starterMediaRemote(), isBuiltIn = true)
            save(null, starterTrackpadCombo(), isBuiltIn = true)
            Timber.i("seeded built-in layouts")
        }.onFailure { Timber.e(it, "layout seeding failed") }
    }

    // ------------------------------------------------------------------
    // Built-in starter layouts
    // ------------------------------------------------------------------

    private fun button(
        id: String, x: Float, y: Float, w: Float, h: Float,
        label: String, icon: String, action: WidgetAction,
        bg: Long = 0xFF1A2238
    ) = WidgetSpec(
        id = id,
        type = WidgetType.BUTTON,
        frame = WidgetFrame(x, y, w, h),
        style = WidgetStyle(label = label, icon = icon, backgroundColor = bg),
        action = action
    )

    /** Starter 1: media remote — buttons only. */
    internal fun starterMediaRemote() = LayoutSpec(
        name = "Media Remote",
        widgets = listOf(
            button("m-prev", 0.02f, 0.05f, 0.30f, 0.14f, "Prev", "⏮", WidgetAction.Media(HidConsumer.PREV_TRACK)),
            button("m-play", 0.35f, 0.05f, 0.30f, 0.14f, "Play", "⏯", WidgetAction.Media(HidConsumer.PLAY_PAUSE), bg = 0xFF2F6BFF),
            button("m-next", 0.68f, 0.05f, 0.30f, 0.14f, "Next", "⏭", WidgetAction.Media(HidConsumer.NEXT_TRACK)),
            button("m-vdn", 0.02f, 0.24f, 0.30f, 0.14f, "Vol −", "🔉", WidgetAction.Media(HidConsumer.VOLUME_DOWN)),
            button("m-mute", 0.35f, 0.24f, 0.30f, 0.14f, "Mute", "🔇", WidgetAction.Media(HidConsumer.MUTE)),
            button("m-vup", 0.68f, 0.24f, 0.30f, 0.14f, "Vol +", "🔊", WidgetAction.Media(HidConsumer.VOLUME_UP)),
            button("m-esc", 0.02f, 0.43f, 0.47f, 0.13f, "Esc", "", WidgetAction.KeyTap(HidKeys.ESCAPE)),
            button("m-enter", 0.51f, 0.43f, 0.47f, 0.13f, "Enter", "", WidgetAction.KeyTap(HidKeys.ENTER)),
            WidgetSpec(
                id = "m-dpad",
                type = WidgetType.DPAD,
                frame = WidgetFrame(0.25f, 0.60f, 0.50f, 0.36f),
                style = WidgetStyle(label = "Navigate")
            )
        )
    )

    /** Starter 2: the classic combo — top trackpad + scroll, bottom keys. */
    internal fun starterTrackpadCombo() = LayoutSpec(
        name = "Trackpad + Keys",
        widgets = listOf(
            WidgetSpec(
                id = "t-pad",
                type = WidgetType.TRACKPAD,
                frame = WidgetFrame(0.02f, 0.02f, 0.78f, 0.55f),
                style = WidgetStyle(label = "Trackpad")
            ),
            WidgetSpec(
                id = "t-scroll",
                type = WidgetType.SCROLL_STRIP,
                frame = WidgetFrame(0.82f, 0.02f, 0.16f, 0.55f),
                style = WidgetStyle(label = "Scroll")
            ),
            button("t-copy", 0.02f, 0.60f, 0.23f, 0.11f, "Copy", "", WidgetAction.KeyTap(HidKeys.C, 0x01)),
            button("t-paste", 0.27f, 0.60f, 0.23f, 0.11f, "Paste", "", WidgetAction.KeyTap(HidKeys.V, 0x01)),
            button("t-undo", 0.52f, 0.60f, 0.23f, 0.11f, "Undo", "", WidgetAction.KeyTap(HidKeys.Z, 0x01)),
            button("t-del", 0.77f, 0.60f, 0.21f, 0.11f, "Del", "", WidgetAction.KeyTap(HidKeys.DELETE)),
            button("t-esc", 0.02f, 0.74f, 0.23f, 0.11f, "Esc", "", WidgetAction.KeyTap(HidKeys.ESCAPE)),
            button("t-tab", 0.27f, 0.74f, 0.23f, 0.11f, "Tab", "", WidgetAction.KeyTap(HidKeys.TAB)),
            button("t-space", 0.52f, 0.74f, 0.46f, 0.11f, "Space", "", WidgetAction.KeyTap(HidKeys.SPACE)),
            button("t-lclick", 0.02f, 0.87f, 0.47f, 0.11f, "Left click", "", WidgetAction.MouseClick(0x01), bg = 0xFF2F6BFF),
            button("t-rclick", 0.51f, 0.87f, 0.47f, 0.11f, "Right click", "", WidgetAction.MouseClick(0x02))
        )
    )
}
