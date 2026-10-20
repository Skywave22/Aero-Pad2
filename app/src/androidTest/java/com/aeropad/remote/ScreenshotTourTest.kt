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

    private fun back() {
        device.pressBack()
        device.waitForIdle()
        Thread.sleep(700)
    }

    /** Swipe up to scroll content down (moves further into the page). */
    private fun scrollDown(times: Int = 1) {
        repeat(times) {
            device.swipe(540, 1500, 540, 500, 40)
            Thread.sleep(350)
        }
    }

    /** Swipe down repeatedly to get back to the top of a page. */
    private fun scrollToTop() {
        repeat(4) {
            device.swipe(540, 500, 540, 1500, 40)
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
    }
}
