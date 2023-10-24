package com.pass.diary.presentation.view.activity

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.pass.diary.presentation.ui.theme.DiaryTheme
import com.pass.diary.presentation.view.screen.BottomNavItems
import com.pass.diary.presentation.view.screen.MainScreen
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.pass.diary.R

@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityInstrumentedTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        composeTestRule.setContent {
            DiaryTheme {
                MainScreen()
            }
        }
    }

    @Test
    fun bottomNavigationBarIsDisplayed() {
        composeTestRule.onNodeWithContentDescription(context.getString(BottomNavItems.Timeline.title)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(context.getString(BottomNavItems.Calendar.title)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(context.getString(BottomNavItems.Analysis.title)).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(context.getString(BottomNavItems.Settings.title)).assertIsDisplayed()
    }

    @Test
    fun bottomNavigationBarNavigationWorksCorrectly() {
        composeTestRule.onNodeWithContentDescription(context.getString(BottomNavItems.Timeline.title)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.timeline)).assertExists()

        composeTestRule.onNodeWithContentDescription(context.getString(BottomNavItems.Calendar.title)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.calendar)).assertExists()

        composeTestRule.onNodeWithContentDescription(context.getString(BottomNavItems.Analysis.title)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.analysis)).assertExists()

        composeTestRule.onNodeWithContentDescription(context.getString(BottomNavItems.Settings.title)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.settings)).assertExists()
    }
}

