package com.pass.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class DiaryEntity (
    @PrimaryKey(autoGenerate = true) val id: Int?,
    @ColumnInfo(name = "year") var year: String,
    @ColumnInfo(name = "month") var month: String,
    @ColumnInfo(name = "day") var day: String,
    @ColumnInfo(name = "dayOfWeek") var dayOfWeek: String,
    @ColumnInfo(name = "emoticonId1") var emoticonId1: Int?,
    @ColumnInfo(name = "emoticonId2") var emoticonId2: Int?,
    @ColumnInfo(name = "emoticonId3") var emoticonId3: Int?,
    @ColumnInfo(name = "imageUri") var imageUri: String?,
    @ColumnInfo(name = "title") var title: String,
    @ColumnInfo(name = "content") var content: String
)