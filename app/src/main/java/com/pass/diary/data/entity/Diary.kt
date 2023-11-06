package com.pass.diary.data.entity

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize
@Entity
data class Diary (
    @PrimaryKey(autoGenerate = true) val id: Int?,
    @ColumnInfo(name = "year") var year: String,
    @ColumnInfo(name = "month") var month: String,
    @ColumnInfo(name = "day") var day: String,
    @ColumnInfo(name = "dayOfWeek") var dayOfWeek: String,
    @ColumnInfo(name = "emoticonId1") var emoticonId1: Int?,
    @ColumnInfo(name = "emoticonId2") var emoticonId2: Int?,
    @ColumnInfo(name = "emoticonId3") var emoticonId3: Int?,
    @ColumnInfo(name = "audioUri") var audioUri: String?,
    @ColumnInfo(name = "imageUri") var imageUri: String?,
    @ColumnInfo(name = "content") var content: String
) : Parcelable