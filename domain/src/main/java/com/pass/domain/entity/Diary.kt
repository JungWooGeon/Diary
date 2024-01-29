package com.pass.domain.entity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Diary (
    val id: Int?,
    var year: String,
    var month: String,
    var day: String,
    var dayOfWeek: String,
    var emoticonId1: Int?,
    var emoticonId2: Int?,
    var emoticonId3: Int?,
    var imageUri: String?,
    var title: String,
    var content: String
) : Parcelable