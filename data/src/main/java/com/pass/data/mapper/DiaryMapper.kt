package com.pass.data.mapper

import com.pass.data.db.entity.DiaryEntity
import com.pass.domain.entity.Diary

object DiaryMapper {
    fun fromDomain(diary: Diary): DiaryEntity {
        return DiaryEntity(
            id = diary.id,
            year = diary.year,
            month = diary.month,
            day = diary.day,
            dayOfWeek = diary.dayOfWeek,
            emoticonId1 = diary.emoticonId1,
            emoticonId2 = diary.emoticonId2,
            emoticonId3 = diary.emoticonId3,
            imageUri = diary.imageUri,
            title = diary.title,
            content = diary.content
        )
    }

    fun toDomain(diaryEntity: DiaryEntity): Diary {
        return Diary(
            id = diaryEntity.id,
            year = diaryEntity.year,
            month = diaryEntity.month,
            day = diaryEntity.day,
            dayOfWeek = diaryEntity.dayOfWeek,
            emoticonId1 = diaryEntity.emoticonId1,
            emoticonId2 = diaryEntity.emoticonId2,
            emoticonId3 = diaryEntity.emoticonId3,
            imageUri = diaryEntity.imageUri,
            title = diaryEntity.title,
            content = diaryEntity.content
        )
    }

    fun fromDomainWithList(diaryList: List<Diary>): List<DiaryEntity> {
        return diaryList.map {
            fromDomain(it)
        }
    }

    fun toDomainWithList(diaryEntityList: List<DiaryEntity>): List<Diary> {
        return diaryEntityList.map {
            toDomain(it)
        }
    }
}