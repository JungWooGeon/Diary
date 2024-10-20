package com.pass.presentation.state.route

import com.pass.diary.R
import com.pass.presentation.view.screen.Constants

sealed class MainRouteState (
    val title: Int,
    val icon: Int,
    val screenRoute: String
) {
    data object Timeline : MainRouteState(R.string.timeline, R.drawable.ic_timeline, Constants.TIMELINE)
    data object Calendar : MainRouteState(R.string.calendar, R.drawable.ic_calendar, Constants.CALENDAR)
    data object Analysis : MainRouteState(R.string.analysis, R.drawable.ic_analysis, Constants.ANALYSIS)
    data object Settings : MainRouteState(R.string.settings, R.drawable.ic_settings, Constants.SETTINGS)
}