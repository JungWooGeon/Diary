package com.pass.diary.presentation.view.screen

import com.pass.diary.R

object Constants {
    const val CALENDAR = "CALENDAR"
    const val TIMELINE = "TIMELINE"
    const val ANALYSIS = "ANALYSIS"
    const val SETTINGS = "SETTINGS"

    const val INTENT_NAME_DIARY = "DIARY"

    const val NOT_EDIT_INDEX = -1

    val EMOTICON_RAW_ID_LIST = arrayListOf(
        R.raw.em_smile,
        R.raw.em_happy,
        R.raw.em_laughing,
        R.raw.em_like,
        R.raw.em_thumbsup,
        R.raw.em_angry,
        R.raw.em_hot,
        R.raw.em_nervous,
        R.raw.em_sad,
        R.raw.em_shocked,
        R.raw.em_sick,
        R.raw.em_surprised,
        R.raw.em_thinking,
        R.raw.em_tired,
        R.raw.em_picture,
    )

    val DAY_OF_WEEK_TO_KOREAN = mapOf(
        "SUNDAY" to "일",
        "MONDAY" to "월",
        "TUESDAY" to "화",
        "WEDNESDAY" to "수",
        "THURSDAY" to "목",
        "FRIDAY" to "금",
        "SATURDAY" to "토"
    )
}