package com.lotus.abs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke tests for Home / Bleed Setup / Wheel Detail and system Back.
 * Run on an emulator or device with Android permissions already granted.
 * No hydraulic actuator command is sent by these tests.
 */
@RunWith(AndroidJUnit4::class)
class NavigationFlowTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeBleedWheelBackToSetupAndHome() {
        rule.onNodeWithText("SPURGO ABS").performClick()
        rule.onNodeWithText("Spurgo ABS assistito").assertIsDisplayed()
        rule.onNodeWithText("LF").performScrollTo().performClick()
        rule.onNodeWithText("CONTINUA E VERIFICA").performScrollTo().performClick()
        rule.onNodeWithText("Spurgo LF • FAST").assertIsDisplayed()
        rule.onNodeWithContentDescription("Torna alla selezione").performClick()
        rule.onNodeWithText("Spurgo ABS assistito").assertIsDisplayed()
        rule.onNodeWithContentDescription("Torna alla Home").performClick()
        rule.onNodeWithText("SPURGO ABS").assertIsDisplayed()
    }

    @Test
    fun androidBackFromWheelReturnsToSetupThenHome() {
        rule.onNodeWithText("SPURGO ABS").performClick()
        rule.onNodeWithText("RF").performScrollTo().performClick()
        rule.onNodeWithText("CONTINUA E VERIFICA").performScrollTo().performClick()
        rule.onNodeWithText("Spurgo RF • FAST").assertIsDisplayed()
        Espresso.pressBack()
        rule.onNodeWithText("Spurgo ABS assistito").assertIsDisplayed()
        Espresso.pressBack()
        rule.onNodeWithText("SPURGO ABS").assertIsDisplayed()
    }

    @Test
    fun navigationDrawerOpensBleedSetup() {
        rule.onNodeWithContentDescription("Apri menu").performClick()
        rule.onNodeWithText("Spurgo ABS").performClick()
        rule.onNodeWithText("Spurgo ABS assistito").assertIsDisplayed()
        rule.onNodeWithContentDescription("Torna alla Home").performClick()
        rule.onNodeWithText("SPURGO ABS").assertIsDisplayed()
    }
}
