package com.pass.diary.presentation.state

import com.pass.diary.R
import com.pass.diary.presentation.view.screen.Constants

sealed class MainState (
    val title: Int,
    val icon: Int,
    val screenRoute: String
) {
    data object Timeline : MainState(R.string.timeline, R.drawable.ic_timeline, Constants.TIMELINE)
    data object Calendar : MainState(R.string.calendar, R.drawable.ic_calendar, Constants.CALENDAR)
    data object Analysis : MainState(R.string.analysis, R.drawable.ic_analysis, Constants.ANALYSIS)
    data object Settings : MainState(R.string.settings, R.drawable.ic_settings, Constants.SETTINGS)
}