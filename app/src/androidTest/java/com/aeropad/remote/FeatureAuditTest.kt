package com.aeropad.remote

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Feature audit — every screen, every tappable control, in both orientations.
 *
 * Modelled on how google/artemis drives a device: observe the screen, act on a
 * target, then verify the app survived and capture evidence (screenshot +
 * logcat). Artemis itself needs a physical device and an LLM key, neither of
 * which exists in this build pipeline, so this is the deterministic equivalent:
 * a fixed instruction list, executed on the CI emulator, with machine-readable
 * findings instead of a model's judgement.
 *
 * What it produces in files/audit:
 *   findings.txt   one line per problem: screen | control | what happened
 *   A-<route>.png  portrait screenshot of every screen
 *   L-<route>.png  landscape screenshot of every screen
 *   crash-*.txt    logcat slice around any crash
 *
 * The test itself never fails on a finding: a broken screen must not hide the
 * other 22. Everything is reported so it can be triaged and fixed.
 */
@RunWith(AndroidJUnit4::class)
class FeatureAuditTest {

    private lateinit var device: androidx.test.uiautomator.UiDevice
    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var outDir: File
    private val findings = StringBuilder()

    /** Every destination in Routes. Kept here so a new screen is audited. */
    private val routes = listOf(
        "home", "layouts", "layout_editor", "my_layout", "keyboard", "full_keyboard",
        "numpad", "mouse", "air_mouse", "gamepad", "gamepad_builder", "multimedia",
        "presenter", "android_remote", "touch_screen", "text_clips", "macros",
        "shortcut_decks", "pc_combo", "devices", "settings", "help", "connection_health"
    )

    /**
     * Controls whose whole purpose is to destroy state. Tapping them mid-audit
     * would empty the app and make every later screen report a false "empty".
     */
    private val destructive = Regex(
        "(?i)\\b(delete|remove|uninstall|reset|clear all|stop|disconnect|unpair|" +
            "forget|power off|erase|wipe|discard|deleting)\\b"
    )

    @Before
    fun setUp() {
        device = androidx.test.uiautomator.UiDevice
            .getInstance(InstrumentationRegistry.getInstrumentation())
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        outDir = File(ctx.filesDir, "audit").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        device.waitForIdle()
        Thread.sleep(1500)
        dismissOnboardingOnce()
    }

    @After
    fun tearDown() {
        runCatching {
            device.setOrientationNatural()
            device.unfreezeRotation()
        }
        runCatching { scenario.close() }
        File(outDir, "findings.txt").writeText(
            if (findings.isEmpty()) "no findings\n" else findings.toString()
        )
    }

    @Test
    fun auditEveryScreen() {
        routes.forEach { route ->
            runCatching { auditRoute(route) }
                .onFailure { note(route, "-", "audit threw: ${it.javaClass.simpleName}: ${it.message}") }
        }
    }

    private fun auditRoute(route: String) {
        open(route)
        val portraitNodes = tappableCount()
        shot("A-$route")
        if (portraitNodes == 0) {
            // A screen with nothing tappable is usually a screen that failed to
            // draw. Home legitimately has zero? No: it always has tiles.
            note(route, "-", "no tappable control found on the screen")
        }

        exerciseControls(route)

        // Landscape: same screen, rotated. Catches layout breakage that only
        // exists when the window is wide and short.
        device.setOrientationLeft()
        device.waitForIdle()
        Thread.sleep(900)
        shot("L-$route")
        val landscapeText = visibleTextCount()
        if (landscapeText == 0) note(route, "landscape", "screen rendered no visible text")
        device.setOrientationNatural()
        device.waitForIdle()
        Thread.sleep(700)
    }

    /**
     * Tap every Control-like node once and verify the app is still alive.
     * Bounded so a screen with a long list cannot stall the whole audit.
     */
    private fun exerciseControls(route: String) {
        val nodes = device.findObjects(androidx.test.uiautomator.By.clickable(true))
            .filter { it.visibleBounds.height() > 0 && it.visibleBounds.width() > 0 }
        nodes.take(MAX_TAPS_PER_SCREEN).forEachIndexed { index, node ->
            val label = (node.text ?: node.contentDescription ?: "").trim()
            if (label.isNotEmpty() && destructive.containsMatchIn(label)) return@forEachIndexed

            clearLogcat()
            val clicked = runCatching { node.click() }.isSuccess
            Thread.sleep(350)
            device.waitForIdle()

            val crash = crashSinceClear()
            when {
                crash != null -> {
                    note(route, label.ifEmpty { "node#$index" }, "CRASH: $crash")
                    File(outDir, "crash-$route-$index.txt").writeText(crash + "\n\n" + dumpTree())
                    shot("CRASH-$route-$index")
                    open(route)
                }
                !inApp() -> {
                    note(route, label.ifEmpty { "node#$index" }, "left the app (current package changed)")
                    open(route)
                }
                clicked && visibleTextCount() == 0 -> {
                    note(route, label.ifEmpty { "node#$index" }, "screen went blank after tap")
                    open(route)
                }
            }
            if (label.any { it.code in TOFU_SUSPECTS }) {
                note(route, label, "label uses a symbol codepoint that may render as tofu")
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun open(route: String) {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = android.content.Intent(ctx, MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra("com.aeropad.remote.EXTRA_ROUTE", route)
        ctx.startActivity(intent)
        Thread.sleep(1400)
        device.waitForIdle()
        dismissOnboardingOnce()
    }

    private fun dismissOnboardingOnce() {
        repeat(2) {
            val skip = device.wait(
                androidx.test.uiautomator.Until.findObject(
                    androidx.test.uiautomator.By.text("Skip")
                ),
                1200
            ) ?: return
            skip.click()
            device.waitForIdle()
            Thread.sleep(400)
        }
    }

    private fun inApp(): Boolean {
        val pkg = runCatching {
            device.executeShellCommand("dumpsys window | grep -m1 mCurrentFocus").orEmpty()
        }.getOrDefault("")
        return pkg.contains("com.aeropad.remote")
    }

    private fun clearLogcat() {
        runCatching { device.executeShellCommand("logcat -c") }
    }

    /** Returns the first fatal line since the last clear, or null. */
    private fun crashSinceClear(): String? {
        val out = runCatching {
            device.executeShellCommand("logcat -d -b crash -t 120").orEmpty()
        }.getOrDefault("")
        val relevant = out.lineSequence()
            .filter { it.contains("com.aeropad.remote") || it.contains("FATAL EXCEPTION") }
            .take(12)
            .toList()
        return if (relevant.any { it.contains("FATAL EXCEPTION") || it.contains("AndroidRuntime") })
            relevant.joinToString("\n") else null
    }

    private fun tappableCount(): Int =
        runCatching {
            device.findObjects(androidx.test.uiautomator.By.clickable(true))
                .count { it.visibleBounds.height() > 0 }
        }.getOrDefault(0)

    private fun visibleTextCount(): Int =
        runCatching {
            device.findObjects(androidx.test.uiautomator.By.textContains(""))
                .count { it.visibleBounds.height() > 0 && !it.text.isNullOrBlank() }
        }.getOrDefault(0)

    private fun dumpTree(): String {
        val f = File(outDir, "tree-tmp.xml")
        runCatching { device.dumpWindowHierarchy(f) }
        return runCatching { f.readText() }.getOrDefault("")
    }

    private fun shot(name: String) {
        runCatching {
            device.waitForIdle()
            device.takeScreenshot(File(outDir, "$name.png"))
        }
    }

    private fun note(screen: String, control: String, what: String) {
        findings.append(screen).append(" | ").append(control).append(" | ").append(what).append('\n')
        android.util.Log.w("FeatureAudit", "$screen | $control | $what")
    }

    private companion object {
        const val MAX_TAPS_PER_SCREEN = 10

        /**
         * Codepoints that are missing from many Android system fonts and have
         * already produced a tofu box in this app (U+23FB rendered as ▯ next to
         * "Power"). Emoji ranges are fine; these are the risky symbol blocks.
         */
        val TOFU_SUSPECTS = setOf(
            0x23FB, 0x23F9, 0x23EF, 0x23EE, 0x23ED, 0x23F8, 0x2328, 0x2326, 0x2315,
            0x230A, 0x230B, 0x2309, 0x2302, 0x21E4, 0x21E5, 0x21E7, 0x21E9,
            0x26F6, 0x27F3, 0x27A1, 0x2B05, 0x2B06, 0x2B07, 0x2423
        )
    }
}
