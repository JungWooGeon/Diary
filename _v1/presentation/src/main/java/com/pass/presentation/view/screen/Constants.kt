package com.pass.presentation.view.screen

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

    val FONT_TO_KOREAN = mapOf(
        "default" to "기본",
        "garam" to "가람연꽃",
        "restart" to "다시 시작해",
        "skybori" to "하늘보리"
    )

    val OPENSOURCE_LICENSE_LIST = arrayListOf(
        arrayOf(
            "SSJetPackComposeProgressButton\nCopyright 2022 Simform Solutions\n Licensed under the Apache License, Version 2.0 (the \"License\")",
            "https://github.com/SimformSolutionsPvtLtd/SSJetPackComposeProgressButton/blob/main/LICENSE"
        ),
        arrayOf(
            "MPAndroidChart\nCopyright 2020 Philipp Jahoda\nLicensed under the Apache License, Version 2.0 (the \"License\")",
            "https://github.com/PhilJay/MPAndroidChart/blob/master/LICENSE"
        ),
        arrayOf(
            "retrofit\nCopyright 2013 Square, Inc.\nLicensed under the Apache License 2.0",
            "https://github.com/square/retrofit/blob/master/LICENSE.txt"
        )
    )
}