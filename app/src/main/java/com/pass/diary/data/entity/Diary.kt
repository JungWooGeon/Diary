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
    @ColumnInfo(name = "year") val year: String,
    @ColumnInfo(name = "month") val month: String,
    @ColumnInfo(name = "day") val day: String,
    @ColumnInfo(name = "dayOfWeek") val dayOfWeek: String,
    @ColumnInfo(name = "emoticonId1") val emoticonId1: Int?,
    @ColumnInfo(name = "emoticonId2") val emoticonId2: Int?,
    @ColumnInfo(name = "emoticonId3") val emoticonId3: Int?,
    @ColumnInfo(name = "audioUri") val audioUri: String?,
    @ColumnInfo(name = "imageUri") val imageUri: String?,
    @ColumnInfo(name = "content") val content: String
) : Parcelable