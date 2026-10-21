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
        scrollToTop()
        if (tap("Macros")) {
            shot("17-macros")
            back()
        }
        scrollToTop()
        if (tap("Help")) {
            shot("18-help")
            scrollDown(2)
            shot("19-help-scrolled")
            back()
        }

        // Round 8 — My Layout + the new key pad sheet.
        scrollToTop()
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
}
