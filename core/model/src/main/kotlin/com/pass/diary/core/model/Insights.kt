package com.pass.diary.core.model

/** 문장별 감정 하이라이트 (E3): chars [start, end) of an entry's body felt strongly as [emotion]. */
data class SentenceMark(val start: Int, val end: Int, val emotion: EmotionKey)
