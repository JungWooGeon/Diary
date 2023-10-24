package com.pass.diary.presentation.intent

import com.pass.diary.R
import com.pass.diary.presentation.view.screen.Constants

sealed class MainIntent (
    val title: Int,
    val icon: Int,
    val screenRoute: String
) {
    object Timeline : MainIntent(R.string.timeline, R.drawable.ic_timeline, Constants.TIMELINE)
    object Calendar : MainIntent(R.string.calendar, R.drawable.ic_calendar, Constants.CALENDAR)
    object Analysis : MainIntent(R.string.analysis, R.drawable.ic_analysis, Constants.ANALYSIS)
    object Settings : MainIntent(R.string.settings, R.drawable.ic_settings, Constants.SETTINGS)
}