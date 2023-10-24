package com.pass.diary.presentation.viewmodel

import com.pass.diary.presentation.intent.MainIntent
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test

class MainViewModelTest {

    private lateinit var viewModel: MainViewModel

    @Before
    fun setup() {
        viewModel = MainViewModel()
    }

    @Test
    fun whenOnNavItemSelected_thenStateIsUpdatedCorrectly() {
        // Arrange
        val expectedCalendarItem = MainIntent.Calendar

        viewModel.onNavItemSelected(expectedCalendarItem)
        assertEquals(expectedCalendarItem, viewModel.state.value.selectedNavItem)
    }
}
