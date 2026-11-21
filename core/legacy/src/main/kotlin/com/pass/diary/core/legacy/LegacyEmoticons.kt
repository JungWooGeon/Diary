package com.pass.diary.core.legacy

import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.EmotionKey.*
import com.pass.diary.core.model.EntryEmotion

/**
 * v1 stored aapt-assigned `R.raw.em_*` ints. Source of truth: tools/legacy/emoticon_ids.json
 * (EmoticonTableTest keeps the two in sync). Reasoning per row: tools/legacy/MAPPING.md.
 */
object LegacyEmoticons {
    /** 0x7f100000.. in alphabetical order, identical in the committed 1.0 and 1.1 release bundles. */
    val NAME_BY_ID: Map<Int, String> = listOf(
        "em_angry", "em_happy", "em_hot", "em_laughing", "em_like", "em_nervous", "em_picture", "em_sad",
        "em_shocked", "em_sick", "em_smile", "em_surprised", "em_thinking", "em_thumbsup", "em_tired",
    ).mapIndexed { i, name -> 0x7f100000 + i to name }.toMap()

    /** null value = not an emotion (em_picture): dropped. */
    val EMOTION_BY_NAME: Map<String, EntryEmotion?> = mapOf(
        "em_smile" to EntryEmotion(JOY, 2),
        "em_happy" to EntryEmotion(JOY, 3),
        "em_laughing" to EntryEmotion(EXCITED, 4),
        "em_like" to EntryEmotion(LOVE, 4),
        "em_thumbsup" to EntryEmotion(PROUD, 3),
        "em_angry" to EntryEmotion(ANGRY, 4),
        "em_hot" to EntryEmotion(ANNOYED, 3),
        "em_nervous" to EntryEmotion(ANXIOUS, 3),
        "em_sad" to EntryEmotion(SAD, 3),
        "em_shocked" to EntryEmotion(UNSURE, 3),
        "em_sick" to EntryEmotion(TIRED, 4),
        "em_surprised" to EntryEmotion(UNSURE, 3),
        "em_thinking" to EntryEmotion(UNSURE, 3),
        "em_tired" to EntryEmotion(TIRED, 3),
        "em_picture" to null,
    )

    class Mapped(val emotions: List<EntryEmotion>, val unknownIds: Int)

    /** Slot order kept; same key twice -> one entry with the higher intensity; unknown id -> `unsure`. */
    fun map(slots: List<Int?>): Mapped {
        var unknown = 0
        val out = LinkedHashMap<EmotionKey, EntryEmotion>()
        for (id in slots) {
            if (id == null || id == -1) continue
            val name = NAME_BY_ID[id]
            val e = if (name == null) { unknown++; EntryEmotion(UNSURE, 3) } else EMOTION_BY_NAME.getValue(name) ?: continue
            val prev = out[e.key]
            if (prev == null || prev.intensity < e.intensity) out[e.key] = e
        }
        return Mapped(out.values.toList(), unknown)
    }
}
