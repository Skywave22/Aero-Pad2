package com.aeropad.remote.gamepad

import com.aeropad.remote.data.gamepad.GamepadProfileRepository
import com.aeropad.remote.domain.GamepadRuntimeCore
import com.aeropad.remote.model.GamepadSnapshot
import com.aeropad.remote.model.gamepad.GamepadControlType
import com.aeropad.remote.hid.HidReportBuilder
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
    fun `gta presets carry the real GTA V xbox mapping`() {
        listOf(repo.gtaComfortLayout(), repo.gtaObsidianLayout()).forEach { spec ->
            val byIndex = spec.controls
                .filter { it.type == GamepadControlType.BUTTON || it.type == GamepadControlType.TRIGGER }
                .associateBy { it.buttonIndex }
            // A=0 sprint, B=1 attack, X=2 jump, Y=3 vehicle,
            // LB=4 cover, RB=5 radio, LT=6 aim, RT=7 shoot.
            listOf(0, 1, 2, 3, 4, 5, 6, 7).forEach { idx ->
                assertTrue("$idx missing in ${spec.name}", byIndex.containsKey(idx))
            }
            // Movement stick + all 8 actions pressable → distinct HID bits.
            assertTrue(spec.controls.any { it.type == GamepadControlType.STICK })
            var st = GamepadSnapshot()
            (0..7).forEach { st = GamepadRuntimeCore.withButton(st, it, true) }
            assertEquals(8, Integer.bitCount(st.buttons))
            assertEquals(7, HidReportBuilder.gamepad(st).size)
        }
    }

    @Test
    fun `gta presets sanitize without dropping controls`() {
        listOf(repo.gtaComfortLayout(), repo.gtaObsidianLayout()).forEach { spec ->
            val clean = spec.sanitized()
            assertEquals(spec.name, clean.name)
            assertEquals(spec.controls.size, clean.controls.size)
            clean.controls.forEach { c ->
                val f = c.frame
                assertTrue(f.x >= 0f && f.x + f.w <= 1.0001f)
                assertTrue(f.y >= 0f && f.y + f.h <= 1.0001f)
                assertTrue(c.buttonIndex in 0..15)
            }
        }
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
private class FakeDao : com.aeropad.remote.data.db.GamepadProfileDao {
    override fun observeAll() = kotlinx.coroutines.flow.flowOf(emptyList<com.aeropad.remote.data.db.GamepadProfileEntity>())
    override suspend fun byId(id: Long) = null
    override suspend fun upsert(profile: com.aeropad.remote.data.db.GamepadProfileEntity) = 1L
    override suspend fun deleteById(id: Long) {}
    override suspend fun count() = 0
    override suspend fun byName(name: String) = null
}