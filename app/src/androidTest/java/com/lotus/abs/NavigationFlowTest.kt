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
        rule.onNodeWithText("ABS BLEEDING").performClick()
        rule.onNodeWithText("Assisted ABS Bleeding").assertIsDisplayed()
        rule.onNodeWithText("LF").performScrollTo().performClick()
        rule.onNodeWithText("CONTINUE TO REVIEW").performScrollTo().performClick()
        rule.onNodeWithText("Bleeding LF • FAST INIT").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back to wheel selection").performClick()
        rule.onNodeWithText("Assisted ABS Bleeding").assertIsDisplayed()
        rule.onNodeWithContentDescription("Return to Home").performClick()
        rule.onNodeWithText("ABS BLEEDING").assertIsDisplayed()
    }

    @Test
    fun androidBackFromWheelReturnsToSetupThenHome() {
        rule.onNodeWithText("ABS BLEEDING").performClick()
        rule.onNodeWithText("RF").performScrollTo().performClick()
        rule.onNodeWithText("CONTINUE TO REVIEW").performScrollTo().performClick()
        rule.onNodeWithText("Bleeding RF • FAST INIT").assertIsDisplayed()
        Espresso.pressBack()
        rule.onNodeWithText("Assisted ABS Bleeding").assertIsDisplayed()
        Espresso.pressBack()
        rule.onNodeWithText("ABS BLEEDING").assertIsDisplayed()
    }

    @Test
    fun navigationDrawerOpensBleedSetup() {
        rule.onNodeWithContentDescription("Open navigation menu").performClick()
        rule.onNodeWithText("ABS Bleeding").performClick()
        rule.onNodeWithText("Assisted ABS Bleeding").assertIsDisplayed()
        rule.onNodeWithContentDescription("Return to Home").performClick()
        rule.onNodeWithText("ABS BLEEDING").assertIsDisplayed()
    }
    @Test
    fun homeConnectionAndDiagnosticsReturnToHome() {
        rule.onNodeWithText("CONNECTION").performClick()
        rule.onNodeWithText("Choose a connection").assertIsDisplayed()
        rule.onNodeWithContentDescription("Return to Home").performClick()
        rule.onNodeWithText("DTC ERRORS").performClick()
        rule.onNodeWithText("ABS / DTC Diagnostics").assertIsDisplayed()
        rule.onNodeWithContentDescription("Return to Home").performClick()
        rule.onNodeWithText("ABS BLEEDING").assertIsDisplayed()
    }

    @Test
    fun setupAllowsSlowSelectionForOneWheel() {
        rule.onNodeWithText("ABS BLEEDING").performClick()
        rule.onNodeWithText("SLOW INIT").performScrollTo().performClick()
        rule.onNodeWithText("LR").performScrollTo().performClick()
        rule.onNodeWithText("CONTINUE TO REVIEW").performScrollTo().performClick()
        rule.onNodeWithText("Bleeding LR • SLOW INIT").assertIsDisplayed()
        rule.onNodeWithContentDescription("Back to wheel selection").performClick()
        rule.onNodeWithText("Assisted ABS Bleeding").assertIsDisplayed()
    }

}
