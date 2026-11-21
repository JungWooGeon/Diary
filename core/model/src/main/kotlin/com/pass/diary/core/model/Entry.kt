package com.pass.diary.core.model

import java.util.UUID

/** ARCHITECTURE §2.1. date = "YYYY-MM-DD", time = "HH:mm", ids = UUID strings, *At = epoch millis. */
data class Entry(
    val id: String = UUID.randomUUID().toString(),
    val date: String,
    val time: String,
    /** v1 entries had no time of day: stored as "21:00" with this flag (also in the Drive JSON). */
    val timeUnknown: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    /** 휴지통: set with deleted=true while the content is kept (30 days). deleted && trashedAt == null = content-less tombstone. */
    val trashedAt: Long? = null,
    val title: String = "",
    val body: String = "",
    val template: Template = Template.FREE,
    val source: EntrySource = EntrySource.TEXT,
    val weather: String? = null,
    /** Cached LightMixer result "#RRGGBB" (light color); recomputed on every save. */
    val lightColor: String? = null,
    val emotions: List<EntryEmotion> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val photos: List<Photo> = emptyList(),
)

enum class Template(val key: String, val label: String, val starter: String) {
    FREE("free", "자유", ""),
    THREE_LINES("three_lines", "세 줄", "1. \n2. \n3. "),
    GRATITUDE("gratitude", "감사 3가지", "고마웠던 일 1. \n고마웠던 일 2. \n고마웠던 일 3. "),
    QUESTION("question", "오늘의 질문", "Q. 오늘 가장 기억에 남는 순간은?\n");

    companion object { fun of(key: String) = entries.firstOrNull { it.key == key } ?: FREE }
}

enum class EntrySource(val key: String) {
    TEXT("text"), VOICE("voice"), CHAT("chat");

    companion object { fun of(key: String) = entries.firstOrNull { it.key == key } ?: TEXT }
}

enum class TagKind(val key: String) {
    PERSON("person"), PLACE("place"), ACTIVITY("activity"), FREE("free");

    companion object { fun of(key: String) = entries.firstOrNull { it.key == key } ?: FREE }
}

data class Tag(val kind: TagKind, val value: String, val auto: Boolean = false)

data class Photo(
    val id: String = UUID.randomUUID().toString(),
    val entryId: String,
    val localPath: String,
    val w: Int,
    val h: Int,
    val driveFileId: String? = null,
)

enum class ReplySource(val key: String) {
    TEMPLATE("template"),
    /** Legacy: replies stored by the removed on-device LLM (2026-11). Read only, never written now. */
    LLM("llm");

    companion object { fun of(key: String) = entries.firstOrNull { it.key == key } ?: TEMPLATE }
}

data class Reply(
    val entryId: String,
    val text: String,
    val source: ReplySource,
    val templateId: String? = null,
    val createdAt: Long,
    val reported: Boolean = false,
)

data class ChatMessage(val entryId: String, val role: String, val text: String, val at: Long)

/** A user override of an emotion's name/color (emotion_def). */
data class EmotionDef(val key: String, val name: String, val groupKey: String?, val colorHex: String, val order: Int, val custom: Boolean)

/** PAPER = warm paper light theme (꾸미기). */
enum class ThemeMode { SYSTEM, LIGHT, DARK, PAPER }

/** 꾸미기 › 빛 팔레트. Display-only: stored light colors stay canonical (CONTRACTS §3). */
enum class LightPaletteId(val key: String, val label: String, val premium: Boolean) {
    BASIC("basic", "기본", false),
    /** Gift for a 14-day streak (or premium). */
    SUNSET("sunset", "노을", false),
    WINTER("winter", "겨울밤", true),
    SPRING("spring", "봄", true);

    companion object { fun of(key: String?) = entries.firstOrNull { it.key == key } ?: BASIC }
}

enum class FontId(val key: String, val label: String, val premium: Boolean) {
    GOTHIC("gothic", "기본 고딕", false),
    SERIF("serif", "부드러운 명조", false),
    HAND("hand", "손글씨", true);

    companion object { fun of(key: String?) = entries.firstOrNull { it.key == key } ?: GOTHIC }
}

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** FLAG_SECURE: hide content in recents / block screenshots. */
    val secureScreen: Boolean = false,
    /** Relock after the app was in the background this long. */
    val relockAfterSec: Int = 60,
    val palette: LightPaletteId = LightPaletteId.BASIC,
    /** A premium palette tried for a day via a rewarded ad ([paletteTrial]), usable until this epoch millis. */
    val paletteTrial: LightPaletteId? = null,
    val paletteTrialUntil: Long = 0,
    val font: FontId = FontId.GOTHIC,
    /** 0 작게 … 2 보통 … 4 크게. */
    val textSize: Int = 2,
    /** Last change of anything above (settings.json on Drive); 0 = never changed. */
    val updatedAt: Long = 0,
    // 설정 › AI (this phone only). Crisis detection runs regardless: it is a safety rule, not an AI feature.
    /** 온이 답장 after saving. Off = no reply (no classifier). */
    val aiReply: Boolean = true,
    /** 감정·태그 추천 chips in the editor, the light that follows the text (E1), title chips (E6). */
    val aiSuggest: Boolean = true,
    /** 문장별 감정 하이라이트 when reading an entry (E3). */
    val aiHighlight: Boolean = true,
    /** 비슷했던 날 (E4) and 뜻으로 찾기 (E5): the on-device sentence index. */
    val aiRecall: Boolean = true,
    /** Widgets show no emotion names / colors (also forced while the app lock is on). */
    val hideWidgetContent: Boolean = false,
) {
    companion object {
        val TEXT_SCALES = listOf(0.88f, 0.94f, 1f, 1.08f, 1.16f)
    }
}
