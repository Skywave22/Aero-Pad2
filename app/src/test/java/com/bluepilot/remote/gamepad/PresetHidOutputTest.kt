package com.bluepilot.remote.gamepad

import com.bluepilot.remote.data.gamepad.GamepadProfileRepository
import com.bluepilot.remote.domain.GamepadRuntimeCore
import com.bluepilot.remote.model.GamepadSnapshot
import com.bluepilot.remote.model.gamepad.GamepadControlType
import com.bluepilot.remote.hid.HidReportBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rule 4: presets must produce correct HID output when pressed. */
class PresetHidOutputTest {

    private val repo = GamepadProfileRepository(FakeDao())

    @Test
    fun `fighting preset buttons map to distinct valid HID bits`() {
        val spec = repo.fightingLayout()
        val buttons = spec.controls.filter { it.type != GamepadControlType.DPAD }
        assertEquals(6, buttons.size)
        val idx = buttons.map { it.buttonIndex }
        assertEquals(idx.size, idx.toSet().size)
        idx.forEach { assertTrue(it in 0..15) }
        var st = GamepadSnapshot()
        buttons.forEach { st = GamepadRuntimeCore.withButton(st, it.buttonIndex, true) }
        assertEquals(6, Integer.bitCount(st.buttons))
        val report = HidReportBuilder.gamepad(st)
        assertEquals(7, report.size)
    }

    @Test
    fun `all four presets sanitize cleanly and stay in bounds`() {
        listOf(repo.fpsLayout(), repo.racingLayout(), repo.fightingLayout(), repo.casualLayout()).forEach { spec ->
            val clean = spec.sanitized()
            assertEquals(spec.controls.size, clean.controls.size)
            clean.controls.forEach { c ->
                val f = c.frame
                assertTrue(f.x >= 0f && f.x + f.w <= 1.0001f)
                assertTrue(f.y >= 0f && f.y + f.h <= 1.0001f)
            }
        }
    }
}

/** Minimal in-memory DAO: template functions never touch it. */
private class FakeDao : com.bluepilot.remote.data.db.GamepadProfileDao {
    override fun observeAll() = kotlinx.coroutines.flow.flowOf(emptyList<com.bluepilot.remote.data.db.GamepadProfileEntity>())
    override suspend fun byId(id: Long) = null
    override suspend fun upsert(profile: com.bluepilot.remote.data.db.GamepadProfileEntity) = 1L
    override suspend fun deleteById(id: Long) {}
    override suspend fun count() = 0
}