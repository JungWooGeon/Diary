package com.pass.diary.presentation.view.screen

import androidx.compose.ui.test.junit4.createComposeRule
import com.pass.diary.data.entity.Diary
import com.pass.diary.presentation.view.screen.Constants.EMOTICON_RAW_ID_LIST
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class AnalysisScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun calculateEmojiCountsCorrectly() {
        val diaries = listOf(
            Diary(
                null,
                LocalDate.now().year.toString(),
                LocalDate.now().monthValue.toString(),
                LocalDate.now().dayOfMonth.toString(),
                Constants.DAY_OF_WEEK_TO_KOREAN[LocalDate.now().dayOfWeek.toString()].toString(),
                EMOTICON_RAW_ID_LIST[0],
                null,
                null,
                null,
                null,
                ""
            ),
            Diary(
                null,
                LocalDate.now().year.toString(),
                LocalDate.now().monthValue.toString(),
                LocalDate.now().dayOfMonth.toString(),
                Constants.DAY_OF_WEEK_TO_KOREAN[LocalDate.now().dayOfWeek.toString()].toString(),
                EMOTICON_RAW_ID_LIST[0],
                null,
                null,
                null,
                null,
                ""
            ),
            Diary(
                null,
                LocalDate.now().year.toString(),
                LocalDate.now().monthValue.toString(),
                LocalDate.now().dayOfMonth.toString(),
                Constants.DAY_OF_WEEK_TO_KOREAN[LocalDate.now().dayOfWeek.toString()].toString(),
                EMOTICON_RAW_ID_LIST[1],
                null,
                null,
                null,
                null,
                ""
            )
        )

        val emojiCounts = calculateEmojiCounts(diaries)

        // 첫 번째 이모티콘의 횟수가 2번인지 확인
        assert(emojiCounts[EMOTICON_RAW_ID_LIST[0]] == 2)
        // 두 번째 이모티콘의 횟수가 1번인지 확인
        assert(emojiCounts[EMOTICON_RAW_ID_LIST[1]] == 1)
    }

    private fun calculateEmojiCounts(diaries: List<Diary>): Map<Int, Int> {
        val emojiCounts = mutableMapOf<Int, Int>()

        diaries.forEach { diary ->
            diary.emoticonId1?.let { emojiCounts[it] = emojiCounts.getOrDefault(it, 0) + 1 }
            diary.emoticonId2?.let { emojiCounts[it] = emojiCounts.getOrDefault(it, 0) + 1 }
            diary.emoticonId3?.let { emojiCounts[it] = emojiCounts.getOrDefault(it, 0) + 1 }
        }

        return emojiCounts
    }
}