package com.strata.tv.wizard

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.strata.tv.testing.HiltTestActivity
import com.strata.tv.ui.setup.SetupWizardScreen
import com.strata.tv.ui.theme.StrataTheme
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SetupWizardScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<HiltTestActivity>()

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun loginScreen_showsXtreamFieldsAndConnect() {
        composeRule.setContent {
            StrataTheme {
                SetupWizardScreen(onFinished = {})
            }
        }
        composeRule.onNodeWithText("Xtream Codes login").assertIsDisplayed()
        composeRule.onNodeWithText("Playlist name").assertIsDisplayed()
        composeRule.onNodeWithText("Server URL").assertIsDisplayed()
        composeRule.onNodeWithText("Username").assertIsDisplayed()
        composeRule.onNodeWithText("Password").assertIsDisplayed()
        composeRule.onNodeWithText("Connect").assertIsDisplayed()
    }
}
