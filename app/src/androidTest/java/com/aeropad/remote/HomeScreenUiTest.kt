package com.aeropad.remote

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose UI smoke tests for the critical flows.
 * Run on a device/emulator: ./gradlew :app:connectedDebugAndroidTest
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class HomeScreenUiTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun homeShowsAppTitleAndTiles() {
        composeRule.onNodeWithText("AeroPad").assertIsDisplayed()
        // Round 19 — the old "Connect to a device" button and the status card
        // above it were merged into one tap-to-act status row, so the button
        // label no longer exists. The section labels and the brand tagline are
        // the stable things to assert on.
        composeRule.onNodeWithText("Your PC, in your hand").assertIsDisplayed()
        composeRule.onNodeWithText("CONTROLS").assertIsDisplayed()
        composeRule.onNodeWithText("Mouse").assertIsDisplayed()
        composeRule.onNodeWithText("Keyboard").assertIsDisplayed()
        composeRule.onNodeWithText("Layouts").assertIsDisplayed()
        composeRule.onNodeWithText("Macros").assertIsDisplayed()
    }

    @Test
    fun settingsTileOpensSettings() {
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeRule.onNodeWithText("Mouse & trackpad").assertIsDisplayed()
    }

    @Test
    fun helpTileOpensHelp() {
        composeRule.onNodeWithText("Help").performClick()
        composeRule.onNodeWithText("PC can't find my phone").assertIsDisplayed()
    }
}
