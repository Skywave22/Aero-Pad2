package com.bluepilot.remote.gamepad

import com.bluepilot.remote.data.gamepad.VersionCodec
import com.bluepilot.remote.domain.ProfileSuggester
import com.bluepilot.remote.model.gamepad.GamepadLayoutSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** ADV SECTION 3 — versioning codec + contextual suggestion logic. */
class ProfileEnhancementsTest {

    // ---------- Version history codec ----------

    @Test
    fun `push keeps newest first and caps history`() {
        var versions = emptyList<GamepadLayoutSpec>()
        for (i in 1..8) {
            versions = VersionCodec.push(versions, GamepadLayoutSpec(name = "v$i"))
        }
        assertEquals(VersionCodec.MAX_VERSIONS, versions.size)
        assertEquals("v8", versions.first().name)      // newest first
        assertEquals("v4", versions.last().name)       // oldest kept = v4
    }

    @Test
    fun `version list JSON round-trips and corrupt input yields empty`() {
        val versions = listOf(
            GamepadLayoutSpec(name = "One", tags = listOf("FPS")),
            GamepadLayoutSpec(name = "Two", stickSensitivity = 45)
        )
        val decoded = VersionCodec.decode(VersionCodec.encode(versions))
        assertEquals(versions.map { it.sanitized() }, decoded)
        assertEquals(emptyList<GamepadLayoutSpec>(), VersionCodec.decode("{corrupt!!"))
        assertEquals(emptyList<GamepadLayoutSpec>(), VersionCodec.decode(null))
        assertEquals(emptyList<GamepadLayoutSpec>(), VersionCodec.decode(""))
    }

    // ---------- Contextual suggestion ----------

    @Test
    fun `classify uses real BT major class first`() {
        assertEquals(
            ProfileSuggester.HostKind.PC,
            ProfileSuggester.classify(ProfileSuggester.MAJOR_COMPUTER, "whatever")
        )
        assertEquals(
            ProfileSuggester.HostKind.TV,
            ProfileSuggester.classify(ProfileSuggester.MAJOR_AUDIO_VIDEO, "x")
        )
        assertEquals(
            ProfileSuggester.HostKind.PHONE,
            ProfileSuggester.classify(ProfileSuggester.MAJOR_PHONE, "x")
        )
    }

    @Test
    fun `classify falls back to name heuristics`() {
        assertEquals(ProfileSuggester.HostKind.TV, ProfileSuggester.classify(0, "Living Room TV"))
        assertEquals(ProfileSuggester.HostKind.TV, ProfileSuggester.classify(0, "NVIDIA Shield"))
        assertEquals(ProfileSuggester.HostKind.PC, ProfileSuggester.classify(0, "DESKTOP-4F2K"))
        assertEquals(ProfileSuggester.HostKind.PC, ProfileSuggester.classify(0, "Aamir's MacBook"))
        assertEquals(ProfileSuggester.HostKind.UNKNOWN, ProfileSuggester.classify(0, "XYZ-9000"))
    }

    @Test
    fun `suggestions map to real tag vocabulary`() {
        ProfileSuggester.HostKind.entries.forEach { kind ->
            ProfileSuggester.suggestedTags(kind).forEach { tag ->
                assertTrue("$tag not in vocabulary", tag in ProfileSuggester.ALL_TAGS)
            }
        }
        assertTrue(ProfileSuggester.suggestedTags(ProfileSuggester.HostKind.UNKNOWN).isEmpty())
        assertEquals("FPS", ProfileSuggester.suggestedTags(ProfileSuggester.HostKind.PC).first())
    }
}
