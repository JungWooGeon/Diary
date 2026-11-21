package com.pass.diary.core.ai

import com.pass.diary.core.model.EmotionGroup
import com.pass.diary.core.model.EntryEmotion
import com.pass.diary.core.model.Tag
import com.pass.diary.core.model.TagKind
import kotlin.random.Random
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** A reply/question picked from shared/library; [id] goes into Reply.templateId for the 30-day no-repeat rule. */
data class LibraryText(val id: String, val text: String)

/**
 * The basic-AI reply (ARCHITECTURE §5.2): shared/library/replies.json by group × intensity, slots filled from tags.
 * [recentIds] = template ids used in the last 30 days (caller reads them from stored replies).
 */
class TemplateReplyEngine(
    replies: JsonArray,
    questions: JsonArray? = null,
    private val random: Random = Random.Default,
) {
    private val replies = replies.map { it.obj }
    private val questions = questions?.map { it.obj } ?: emptyList()

    fun reply(text: String, emotions: List<EntryEmotion>, tags: List<Tag>, recentIds: Set<String> = emptySet()): LibraryText? {
        val top = emotions.firstOrNull()
        var group = top?.key?.group?.key ?: "unsure"
        // A sarcastic diary tagged "joy" must not get a cheerful reply; a neutral one is safe either way.
        if (looksIronic(text) && group in POSITIVE) group = "unsure"
        val intensity = top?.intensity ?: 3
        val pool = replies.filter {
            it.str("group") == group && intensity in (it.int("minIntensity") ?: 1)..(it.int("maxIntensity") ?: 5)
        }
        return pick(pool, tags, recentIds)
    }

    /** Question library pick (fallback for 오늘의 질문 / chat diary). [time] = morning|day|night. */
    fun question(group: EmotionGroup?, tags: List<Tag>, time: String?, recentIds: Set<String> = emptySet()): LibraryText? {
        val pool = questions.filter {
            (it.str("group") == null || it.str("group") == group?.key) && (it.str("time") == null || it.str("time") == time)
        }
        return pick(pool, tags, recentIds)
    }

    private fun pick(pool: List<JsonObject>, tags: List<Tag>, recentIds: Set<String>): LibraryText? {
        val fillable = pool.filter { item -> item.strings("tags").all { slotValue(it, tags) != null } }
        val fresh = fillable.filter { it.str("id") !in recentIds }.ifEmpty { fillable }
        // prefer one that names something from the diary (온이 rule: mention a concrete detail)
        val withSlot = fresh.filter { it.strings("tags").isNotEmpty() }
        val chosen = (withSlot.ifEmpty { fresh }).randomOrNull(random) ?: return null
        var t = chosen.str("text")!!
        for (slot in chosen.strings("tags")) t = fillSlot(t, slot, slotValue(slot, tags)!!)
        return LibraryText(chosen.str("id")!!, t)
    }

    private fun slotValue(slot: String, tags: List<Tag>): String? {
        val kind = when (slot) { "person" -> TagKind.PERSON; "place" -> TagKind.PLACE; "activity" -> TagKind.ACTIVITY; else -> null }
        return tags.firstOrNull { it.kind == kind }?.value
    }

    companion object {
        private val POSITIVE = setOf("bright", "calm", "flutter")
        // ponytail: only near-unambiguous sarcasm markers; lexical irony detection overfits (REPORT_v2 held-out 3/12).
        private val IRONY = Regex("\\^\\^|퍽이나|네 ?아주 좋|아주 잘~|고맙기도 하셔라|참 잘났")

        fun looksIronic(text: String) = IRONY.containsMatchIn(text)

        /** "{person}(이)랑" with 민준 -> 민준이랑, with 엄마 -> 엄마랑. Handles (이)X incl. (이)가, (을)를, (은)는, (과)와, (으)로. */
        fun fillSlot(text: String, slot: String, value: String): String {
            val batchim = value.lastOrNull()?.let { it in '가'..'힣' && (it - '가') % 28 != 0 } ?: false
            val rieul = value.lastOrNull()?.let { it in '가'..'힣' && (it - '가') % 28 == 8 } ?: false
            return Regex("\\{$slot\\}(\\((이|을|은|과|으)\\)([가-힣]+))?").replace(text) { m ->
                val (_, open, rest) = m.destructured
                value + when {
                    open.isEmpty() -> ""
                    open == "으" -> if (batchim && !rieul) "으$rest" else rest
                    "$open$rest".let { it == "을를" || it == "은는" || it == "과와" } -> if (batchim) open else rest
                    else -> if (batchim) open + rest else rest // (이)랑, (이)야, (이)라고 ...
                }
            }
        }
    }
}
