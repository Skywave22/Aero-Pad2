package com.aeropad.remote

import android.os.Environment
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
        outDir = File(ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "tour")
        outDir.mkdirs()
        outDir.listFiles()?.forEach { it.delete() }
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

    @Test
    fun captureEveryTopLevelScreen() {
        shot("01-home-top")

        // Scroll the dashboard so the lower sections are captured too.
        device.swipe(540, 1400, 540, 500, 20)
        shot("02-home-scrolled")
        device.swipe(540, 1400, 540, 400, 20)
        shot("03-home-bottom")

        // Control surfaces — these are the screens that used to crash.
        if (tap("Mouse")) {
            shot("04-mouse")
            back()
        }
        if (tap("Keyboard")) {
            shot("05-keyboard")
            back()
        }
        if (tap("Numpad")) {
            shot("06-numpad")
            back()
        }
        if (tap("Gamepad")) {
            shot("07-gamepad")
            back()
        }
        if (tap("Multimedia")) {
            shot("08-multimedia")
            back()
        }
        if (tap("Text & Clips")) {
            shot("09-text-clips")
            back()
        }

        // Hubs with the floating dock.
        if (tap("Layouts")) {
            shot("10-layouts")
            // The dock is visible here; open Settings from it to test that path too.
            back()
        }
        if (tap("Devices")) {
            shot("11-devices")
            back()
        }
        if (tap("Settings")) {
            shot("12-settings-top")
            device.swipe(540, 1400, 540, 400, 20)
            shot("13-settings-mid")
            // Scroll ALL the way down: the last rows used to hide behind the dock.
            repeat(4) { device.swipe(540, 1400, 540, 300, 20) }
            shot("14-settings-bottom")
            back()
        }
        if (tap("Help")) {
            shot("15-help")
            back()
        }
    }
}
