package com.aeropad.remote

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * UI screenshot tour — no assertions.
 *
 * Deliberately does NOT use createAndroidComposeRule: that rule waits for
 * Compose to become idle before every interaction, and this app never idles
 * (live HID state, animations), which is why the other UI tests time out.
 * UiAutomator talks to the window manager instead, so it can drive and
 * photograph the real screens.
 *
 * Screenshots land in the app's external files dir and are pulled from the
 * device by the CI workflow, so the UI can actually be reviewed instead of
 * guessed at from source.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ScreenshotTourTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    private lateinit var scenario: ActivityScenario<MainActivity>
    private lateinit var device: UiDevice
    private lateinit var outDir: File

    @Before
    fun setUp() {
        hiltRule.inject()
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        // Internal files dir: /sdcard/Android/data is unreadable by `adb pull`
        // on API 30+, so the CI workflow pulls these with `run-as` + tar.
        outDir = File(ctx.filesDir, "tour")
        outDir.mkdirs()
        outDir.mkdirs()
        outDir.listFiles()?.forEach { it.delete() }
        android.util.Log.i("ScreenshotTour", "output dir = ${outDir.absolutePath}")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        device.waitForIdle()
        Thread.sleep(1200)
        dismissOnboarding()
    }

    @After
    fun tearDown() {
        runCatching {
            device.setOrientationNatural()
            device.unfreezeRotation()
        }
        scenario.close()
    }

    /** The first-run overlay covers everything; skip it if present. */
    private fun dismissOnboarding() {
        val skip = device.findObject(By.text("Skip"))
        if (skip != null) {
            skip.click()
            device.waitForIdle()
            Thread.sleep(500)
        }
    }

    private fun shot(name: String) {
        device.waitForIdle()
        Thread.sleep(700) // let the screen transition animation finish
        device.takeScreenshot(File(outDir, "$name.png"))
    }

    private fun tap(text: String): Boolean {
        val node = device.wait(Until.findObject(By.text(text)), 4000) ?: return false
        node.click()
        device.waitForIdle()
        Thread.sleep(900)
        return true
    }

    /** Same as [tap] but matches a substring (chips carry an emoji prefix). */
    private fun tapContains(text: String): Boolean {
        val node = device.wait(Until.findObject(By.textContains(text)), 4000) ?: return false
        node.click()
        device.waitForIdle()
        Thread.sleep(900)
        return true
    }

    /**
     * Find [text] anywhere on the page and tap it.
     *
     * The earlier version tapped from the top and silently gave up when the
     * tile was below the fold, so whole screens were never captured. This
     * scrolls down looking for it, and — when a horizontally scrolling chip
     * row hides the target — swipes that row sideways too.
     */
    private fun findAndTap(text: String, maxScrolls: Int = 6): Boolean {
        repeat(maxScrolls + 1) { attempt ->
            device.findObject(By.text(text))?.let { it.click(); device.waitForIdle(); Thread.sleep(900); return true }
            device.findObject(By.textContains(text))?.let { it.click(); device.waitForIdle(); Thread.sleep(900); return true }

            // Try revealing chips hidden to the right of any scrolling row.
            val row = device.findObject(By.textContains("Air Mouse"))
                ?: device.findObject(By.textContains("PC Combo"))
            if (row != null) {
                val b = row.visibleBounds
                device.swipe(b.right - 10, b.centerY(), b.left + 20, b.centerY(), 20)
                Thread.sleep(400)
                device.findObject(By.textContains(text))?.let {
                    it.click(); device.waitForIdle(); Thread.sleep(900); return true
                }
            }
            if (attempt < maxScrolls) scrollDown(1)
        }
        android.util.Log.w("ScreenshotTour", "not reachable: $text")
        return false
    }


    /**
     * Round 11 — open a screen from the home grid, robustly.
     *
     * In landscape the home grid is taller than the ~190 dp window and the
     * permission card sits on top, so a single findAndTap silently missed and
     * three screens (Keyboard, Mouse, Gamepad) were never photographed in
     * landscape at all. This retries from the top of the page with fewer
     * graceful scrolls, which is what actually lands the tap.
     */
    private fun openFromHome(text: String): Boolean {
        repeat(3) {
            scrollToTop()
            if (findAndTap(text, maxScrolls = 3)) return true
        }
        android.util.Log.w("ScreenshotTour", "gave up opening $text")
        return false
    }

    private fun back() {
        device.pressBack()
        device.waitForIdle()
        Thread.sleep(700)
    }

    private val w: Int get() = device.displayWidth
    private val h: Int get() = device.displayHeight

    /**
     * Swipe up to scroll further into the page.
     *
     * Coordinates are derived from the real display size: the earlier version
     * hard-coded x=540, which is off-screen on a 320px-wide emulator, so every
     * swipe silently did nothing and the "scrolled" screenshots were identical
     * to the top of the page.
     */
    private fun scrollDown(times: Int = 1) {
        repeat(times) {
            device.swipe(w / 2, (h * 0.75f).toInt(), w / 2, (h * 0.30f).toInt(), 30)
            Thread.sleep(400)
        }
    }

    /** Swipe down repeatedly to get back to the top of a page. */
    private fun scrollToTop() {
        repeat(5) {
            device.swipe(w / 2, (h * 0.30f).toInt(), w / 2, (h * 0.78f).toInt(), 30)
            Thread.sleep(250)
        }
    }

    @Test
    fun captureEveryTopLevelScreen() {
        shot("01-home-top")
        scrollDown(1)
        shot("02-home-scrolled")
        scrollDown(2)
        shot("03-home-bottom")
        scrollToTop()

        // Control surfaces — these are the screens that used to crash.
        val controls = listOf(
            "Mouse" to "04-mouse",
            "Keyboard" to "05-keyboard",
            "Gamepad" to "06-gamepad",
            "Multimedia" to "07-multimedia",
            "Presenter" to "08-presenter",
            "Android" to "09-android",
            "Text & Clips" to "10-text-clips"
        )
        for ((label, name) in controls) {
            scrollToTop()
            if (tap(label)) {
                shot(name)
                back()
            } else {
                android.util.Log.w("ScreenshotTour", "tile not found: $label")
            }
        }

        // Hubs (these show the floating dock).
        scrollToTop()
        if (tap("Layouts")) {
            shot("11-layouts")
            // Open a layout to check the player/canvas chrome.
            if (tap("Trackpad") || tap("Gamepad") || tap("Media")) shot("12-layout-player")
            scrollToTop()
            back()
        }
        scrollToTop()
        if (tap("Devices")) {
            shot("13-devices")
            back()
        }
        scrollToTop()
        if (tap("Settings")) {
            shot("14-settings-top")
            scrollDown(2)
            shot("15-settings-mid")
            // Scroll ALL the way down: the last rows used to hide behind the dock.
            scrollDown(6)
            shot("16-settings-bottom")
            back()
        }
        // These tiles sit below the fold on the home grid, so scrolling first
        // is what actually makes them reachable (tapping from the top silently
        // found nothing and the screenshots were never taken).
        scrollToTop()
        scrollDown(2)
        if (tap("Macros")) {
            shot("17-macros")
            back()
        }
        scrollToTop()
        scrollDown(2)
        if (tap("Help")) {
            shot("18-help")
            scrollDown(2)
            shot("19-help-scrolled")
            back()
        }

        // Round 8 — My Layout + the new key pad sheet.
        scrollToTop()
        scrollDown(3)
        // Quick Tools is a horizontally scrolling row; "My Layout" sits past the
        // right edge, so swipe the row left before looking for it.
        device.findObject(By.textContains("Air Mouse"))?.let { chip ->
            val b = chip.visibleBounds
            device.swipe(b.right - 5, b.centerY(), b.left + 30, b.centerY(), 20)
            device.waitForIdle()
            Thread.sleep(400)
        }
        if (tapContains("My Layout")) {
            shot("20-my-layout")
            if (tap("Keys")) {
                shot("21-key-pad")
                // "Add to layout" switches the pad into builder mode and
                // reveals the quick-add presets.
                if (tap("Add to layout")) shot("22-key-pad-add")
                back()
            }
            back()
        }
    }

    /**
     * Landscape pass.
     *
     * Every screenshot until now was portrait, so the whole app was effectively
     * untested in the orientation people actually use when it is propped next
     * to a keyboard or clamped to a TV. This drives the same screens sideways
     * and photographs them.
     *
     * Rotation is frozen rather than "left/right" so the result is deterministic
     * on any emulator image.
     */
    /**
     * Round 11 — the app had never been photographed in landscape.
     *
     * Landscape is the orientation people actually use when the phone is
     * propped next to a keyboard, so a broken sideways layout is a broken app.
     * This freezes rotation to landscape left, walks the top-level screens and
     * the three pointer/typing screens, then restores the sensor in tearDown.
     */
    @Test
    fun captureEveryTopLevelScreenLandscape() {
        device.setOrientationLeft()
        Thread.sleep(1500)
        device.waitForIdle()

        shot("L01-home-top")
        scrollDown(1)
        shot("L02-home-scrolled")
        scrollToTop()

        val visited = listOf("Keyboard", "Mouse", "Gamepad")
        visited.forEachIndexed { i, name ->
            if (openFromHome(name)) {
                shot("L0${3 + i}-${name.lowercase()}")
                back()
            }
        }

        // Top-level hubs, reached through the dock (no scrolling needed).
        val hubs = listOf("Layouts" to "L06-layouts", "Devices" to "L07-devices", "Settings" to "L08-settings")
        hubs.forEach { (hub, name) ->
            if (tap(hub)) {
                shot(name)
                scrollDown(1)
                shot("$name-scrolled")
                scrollToTop()
                back()
            }
        }
    }
}
