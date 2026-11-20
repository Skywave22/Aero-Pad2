package com.aeropad.remote

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
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
        if (composeRule.onAllNodesWithText("Skip").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("Skip").performClick()
        }
    }

    @Test
    fun homeShowsAppTitleAndTiles() {
        composeRule.onNodeWithText("AeroPad").assertIsDisplayed()
        composeRule.onNodeWithText("Bluetooth remote").assertIsDisplayed()
        composeRule.onNodeWithText("Controls").assertIsDisplayed()
        composeRule.onNodeWithText("Mouse").assertIsDisplayed()
        composeRule.onNodeWithText("Keyboard").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboardGrid").performScrollToNode(hasText("Macros"))
        composeRule.onNodeWithText("Macros").assertIsDisplayed()
    }

    @Test
    fun settingsDockOpensSettings() {
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Appearance").assertIsDisplayed()
        composeRule.onNodeWithText("Search settings").assertIsDisplayed()
    }

    @Test
    fun helpTileOpensHelp() {
        composeRule.onNodeWithTag("dashboardGrid").performScrollToNode(hasText("Help"))
        composeRule.onNodeWithText("Help").performClick()
        composeRule.onNodeWithText("PC can't find my phone").assertIsDisplayed()
    }
    @Test
    fun disconnectedControlOffersPairing() {
        composeRule.onNodeWithText("Mouse").performClick()
        composeRule.onNodeWithText("Not connected").assertIsDisplayed()
        composeRule.onNodeWithText("Connect").performClick()
        val bluetooth = composeRule.onAllNodesWithText("Bluetooth connection").fetchSemanticsNodes()
        val permissions = composeRule.onAllNodesWithText("Bluetooth permissions").fetchSemanticsNodes()
        check(bluetooth.isNotEmpty() || permissions.isNotEmpty())
    }
}
