package com.aeropad.remote

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Feature audit — every screen, every tappable control, both orientations.
 *
 * Modelled on how google/artemis drives a device: observe the screen, act on a
 * target, verify the app survived, and keep the evidence (screenshot + logcat).
 * Artemis itself needs a physical device and an LLM key, neither of which
 * exists in this pipeline, so this is the deterministic equivalent: a fixed
 * instruction list run on the CI emulator, writing machine-readable findings
 * instead of a model's judgement.
 *
 * Split in two passes on purpose. The landscape pass takes its screenshots
 * BEFORE touching anything, because a tap that navigates away used to cost the
 * landscape evidence for that screen — and landscape is where this app breaks.
 *
 * Output, in files/audit:
 *   findings.txt   screen | control | what happened
 *   A-<route>.png  portrait screenshot
 *   L-<route>.png  landscape screenshot
 *   crash-*.txt    logcat slice around a crash
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class FeatureAuditTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var device: UiDevice
    private lateinit var outDir: File
    private val findings = StringBuilder()
    private var screenHeight = 0

    /** Every destination in Routes — including the two dialogs-as-screens. */
    private val routes = listOf(
        "home", "layouts", "layout_editor", "my_layout", "keyboard", "full_keyboard",
        "numpad", "mouse", "air_mouse", "gamepad", "gamepad_builder", "multimedia",
        "presenter", "android_remote", "touch_screen", "text_clips", "macros",
        "shortcut_decks", "pc_combo", "devices", "connection", "permissions",
        "settings", "help", "connection_health"
    )

    /** Labels whose whole purpose is to destroy state; tapping empties the app. */
    private val destructive = Regex(
        "(?i)\\b(delete|remove|uninstall|reset|clear all|stop|disconnect|unpair|" +
            "forget|power off|erase|wipe|discard|cancel recording)\\b"
    )

    @Before
    fun setUp() {
        hiltRule.inject()
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        screenHeight = device.displayHeight
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        outDir = File(ctx.filesDir, "audit").apply {
            mkdirs()
            listFiles()?.forEach { it.delete() }
        }
    }

    @After
    fun tearDown() {
        runCatching {
            device.setOrientationNatural()
            device.unfreezeRotation()
        }
        File(outDir, "findings.txt").writeText(
            if (findings.isEmpty()) "no findings\n" else findings.toString()
        )
    }

    // ------------------------------------------------------------------ passes

    @Test
    fun auditEveryScreen() {
        // Portrait first: screenshots, then control probing.
        device.setOrientationNatural()
        device.waitForIdle()
        Thread.sleep(600)
        routes.forEach { route ->
            runCatching {
                open(route)
                shot("A-$route")
                if (visibleTextCount() == 0) note(route, "-", "no visible text on screen")
                if (tappableCount() == 0) note(route, "-", "no tappable control")
                exerciseControls(route)
            }.onFailure { note(route, "-", "audit threw: ${it.javaClass.simpleName}: ${it.message}") }
        }

        // Landscape last, screenshots only: a tap that navigates away would
        // otherwise cost that screen its landscape evidence.
        device.setOrientationLeft()
        device.waitForIdle()
        Thread.sleep(800)
        routes.forEach { route ->
            runCatching {
                open(route)
                Thread.sleep(400)
                shot("L-$route")
                if (visibleTextCount() == 0) note(route, "landscape", "no visible text on screen")
            }.onFailure { note(route, "landscape", "audit threw: ${it.javaClass.simpleName}") }
        }
        device.setOrientationNatural()
    }

    /**
     * Tap up to [MAX_TAPS] controls and verify the app survives each one.
     *
     * Deliberately conservative: system bars are excluded (the old version's
     * first "clickable" node was the status bar, which "left the app" on every
     * screen and produced 25 false findings), and each tap is isolated so a
     * stale node cannot abort the pass.
     */
    private fun exerciseControls(route: String) {
        val nodes = runCatching { device.findObjects(By.clickable(true)) }.getOrDefault(emptyList())
            .filter { node ->
                val b = node.visibleBounds
                b.width() > 0 && b.height() > 0 &&
                    b.top > dp(56) && b.bottom < screenHeight - dp(40) &&
                    !(node.text.isNullOrBlank() && node.contentDescription.isNullOrBlank())
            }
            .take(MAX_TAPS)

        nodes.forEachIndexed { index, node ->
            val label = (node.text ?: node.contentDescription ?: "node#$index").trim()
            if (destructive.containsMatchIn(label)) return@forEachIndexed

            clearLogcat()
            val clicked = runCatching { node.click() }.isSuccess
            if (!clicked) return@forEachIndexed
            Thread.sleep(320)
            runCatching { device.waitForIdle() }

            val crash = crashSinceClear()
            when {
                crash != null -> {
                    note(route, label, "CRASH: ${crash.lineSequence().first()}")
                    File(outDir, "crash-$route-$index.txt").writeText(crash + "\n\n" + dumpTree())
                    shot("CRASH-$route-$index")
                    open(route)
                }
                inApp().not() -> {
                    // Ask once: a stray tap into another package is a real
                    // defect (an intent with no target, usually).
                    note(route, label, "tap left the app")
                    open(route)
                }
                visibleTextCount() == 0 -> {
                    note(route, label, "blank screen after tap")
                    shot("BLANK-$route-$index")
                    open(route)
                }
            }
            if (label.any { it.code in TOFU_SUSPECTS }) {
                note(route, label, "symbol U+${label.first { it.code in TOFU_SUSPECTS }.code.toString(16)} may render as tofu")
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun dp(v: Int): Int =
        (v * InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.displayMetrics.density).toInt()

    private fun open(route: String) {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.startActivity(
            android.content.Intent(ctx, MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra("com.aeropad.remote.EXTRA_ROUTE", route)
        )
        Thread.sleep(1200)
        runCatching { device.waitForIdle() }
        dismissOnboarding()
    }

    private fun dismissOnboarding() {
        repeat(2) {
            val skip = device.findObject(By.text("Skip")) ?: return
            runCatching { skip.click() }
            Thread.sleep(350)
        }
    }

    private fun inApp(): Boolean {
        val focus = runCatching {
            device.executeShellCommand("dumpsys window | grep -m1 mCurrentFocus").orEmpty()
        }.getOrDefault("")
        return focus.contains("com.aeropad.remote")
    }

    private fun clearLogcat() {
        runCatching { device.executeShellCommand("logcat -c") }
    }

    private fun crashSinceClear(): String? {
        val out = runCatching { device.executeShellCommand("logcat -d -b crash -t 80").orEmpty() }
            .getOrDefault("")
        val lines = out.lines().filter {
            it.contains("FATAL EXCEPTION") || (it.contains("AndroidRuntime") && it.contains("com.aeropad.remote"))
        }
        return if (lines.isEmpty()) null else out.lines().take(14).joinToString("\n")
    }

    private fun tappableCount(): Int = runCatching {
        device.findObjects(By.clickable(true)).count { it.visibleBounds.height() > 0 }
    }.getOrDefault(0)

    private fun visibleTextCount(): Int = runCatching {
        device.findObjects(By.textContains(""))
            .count { it.visibleBounds.height() > 0 && !it.text.isNullOrBlank() }
    }.getOrDefault(0)

    private fun dumpTree(): String {
        val f = File(outDir, "tree-tmp.xml")
        runCatching { device.dumpWindowHierarchy(f) }
        return runCatching { f.readText() }.getOrDefault("")
    }

    private fun shot(name: String) {
        val ok = runCatching {
            device.waitForIdle()
            device.takeScreenshot(File(outDir, "$name.png"))
        }.isSuccess
        if (!ok) note(name, "screenshot", "could not capture (harness problem, not an app defect)")
    }

    private fun note(screen: String, control: String, what: String) {
        findings.append(screen).append(" | ").append(control).append(" | ").append(what).append('\n')
        android.util.Log.w("FeatureAudit", "$screen | $control | $what")
    }

    private companion object {
        const val MAX_TAPS = 8

        /** Symbol codepoints that tofu on many Android fonts (U+23FB bit us once). */
        val TOFU_SUSPECTS = setOf(
            0x23FB, 0x23F9, 0x23EF, 0x23EE, 0x23ED, 0x23F8, 0x2328, 0x2326, 0x2315,
            0x230A, 0x230B, 0x2309, 0x2302, 0x21E4, 0x21E5, 0x21E7, 0x21E9,
            0x26F6, 0x27F3, 0x2423
        )
    }
}
