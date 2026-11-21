package com.pass.diary.feature.report

import com.pass.diary.core.model.Entry
import com.pass.diary.core.model.Tag

/** core:ai behind a seam (bound in :app, AiBindings.kt), like feature/write's AiHooks. Both run off the main thread. */
interface ReportAi {
    /**
     * E7 이번 달의 한 줄 / 이번 주의 한 줄: the most typical sentence of [bodies] (SentenceEmbedder + centrality).
     * Sentences with crisis wording are never picked. null = no embedding model, or too little to choose from.
     */
    suspend fun centralSentence(bodies: List<String>): String?

    /** The crisis rules (PLAN §3.2): a week with such an entry puts the help card before the letter. */
    suspend fun isCrisis(text: String): Boolean

    /** E13: people · places · activities the tag dictionary finds in [body] (on top of the tags the user saved). */
    suspend fun tags(body: String): List<Tag>
}

/** Remembers AI results per set of entries: the report flow re-emits on every change, the model shouldn't rerun. */
internal class AiMemo<T> {
    private val memo = HashMap<Int, T>()
    suspend fun get(entries: List<Entry>, salt: Int = 0, compute: suspend () -> T): T {
        val key = 31 * salt + entries.map { it.id to it.updatedAt }.hashCode()
        @Suppress("UNCHECKED_CAST") if (key in memo) return memo[key] as T
        return compute().also { if (memo.size > 24) memo.clear(); memo[key] = it }
    }
}
