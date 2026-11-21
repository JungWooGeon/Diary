package com.pass.diary.core.ai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Post-filter for LLM output, implementing shared/ai/llm/filters.json (Python twin: tools/model-eval/filters.py).
 * [check] returns the names of the rules that failed; empty = OK to show.
 * [people] = tags.json people, one list per tag (tag first, then its aliases).
 */
class OutputFilter(spec: JsonObject, private val people: List<List<String>> = emptyList()) {
    private val rules: Map<String, JsonObject> = spec["rules"]!!.obj.mapValues { it.value.obj }

    fun check(ruleNames: List<String>, text: String, input: String = ""): List<String> =
        ruleNames.filter { it in rules && !ok(it, text, input) }

    fun ok(name: String, text: String, input: String = ""): Boolean {
        val r = rules.getValue(name)
        return when (r.str("kind")) {
            "sentenceEndForbid" -> {
                val pat = Regex(r.str("pattern")!!); val allow = Regex(r.str("allow")!!)
                sentences(text).none { s ->
                    val end = s.replace(TRAILING_SOFT, "")
                    pat.containsMatchIn(end) && !allow.containsMatchIn(end)
                }
            }
            "charForbid" -> {
                if (Regex(r.str("forbid")!!).containsMatchIn(text)) return false
                val low = input.lowercase(); val allow = r.strings("latinAllow")
                Regex(r.str("latin")!!).findAll(text).all { it.value.lowercase().let { w -> w in low || w in allow } }
            }
            "sentenceCount" -> sentences(text).size in r.int("min")!!..r.int("max")!!
            "questionCount" -> {
                val q = unquote(text).count { it == '?' }; val ss = sentences(text)
                q in r.int("min")!!..r.int("max")!! &&
                    (q == 0 || (ss.isNotEmpty() && '?' in ss.last() && ss.dropLast(1).none { '?' in it }))
            }
            "length" -> {
                val t = text.trim(); val n = t.codePointCount(0, t.length)
                n in r.int("min")!!..r.int("max")!! && r.str("forbid")?.let { Regex(it).containsMatchIn(t) } != true
            }
            "regexForbid" -> {
                if (r.strings("anywhere").any { Regex(it).containsMatchIn(text) }) return false
                val last = sentences(text).lastOrNull()?.replace(TRAILING_PUNCT, "") ?: ""
                r.strings("lastSentence").none { Regex(it).containsMatchIn(last) }
            }
            "entitiesInInput" -> entitiesOk(r, text, input)
            else -> error("unknown filter kind ${r.str("kind")}")
        }
    }

    /** Tolerant to 조사·호칭 and common nouns: fails only on an invented name / role or a changed number (filters.json doc). */
    private fun entitiesOk(r: JsonObject, text: String, input: String): Boolean {
        val flat = input.replace(WS, "")
        val nums = inputNumbers(r, input)
        if (NUMBER.findAll(text).any { norm(it.value) !in nums }) return false
        val stop = r.strings("nameStop").toSet()
        for (p in r.strings("namePatterns")) for (m in Regex(p).findAll(text)) {
            val stem = m.groupValues[1].let { if (it.length > 2 && it.endsWith("들")) it.dropLast(1) else it }
            if (stem !in stop && stem !in peopleWords && stem !in flat) return false
        }
        val generic = r.strings("peopleGeneric").toSet(); val ignore = r.strings("peopleIgnore").toSet()
        val josa = r.str("peopleJosa")!!; val honorifics = r.strings("honorifics")
        for (g in people) {
            if (g.first() in generic) continue
            for (w in g) {
                if (w.length < 2 || w in ignore) continue
                if (!Regex("(?<![가-힣])" + Regex.escape(w) + "(?:$josa)(?![가-힣])").containsMatchIn(text)) continue
                val stem = honorifics.firstOrNull { w.endsWith(it) && w.length - it.length >= 2 }?.let { w.dropLast(it.length) }
                if (g.none { it in flat } && (stem == null || stem !in flat)) return false
            }
        }
        return true
    }

    private val peopleWords = people.flatten().toSet()

    private fun inputNumbers(r: JsonObject, input: String): Set<String> {
        val out = NUMBER.findAll(input).map { norm(it.value) }.toMutableSet()
        Regex(r.str("sinoNumeral")!!).findAll(input).forEach {
            val v = sinoValue(it.value); out += norm(v); out += norm(v.substringBefore('.')) // '점' may be a score unit
        }
        val unit = r.str("nativeUnit")!!
        r["nativeNumbers"]!!.obj.forEach { (w, v) ->
            if (Regex(w + unit).containsMatchIn(input)) out += v.jsonPrimitive.content
        }
        return out
    }

    companion object {
        private val QUOTES = Regex("'[^']*'|\"[^\"]*\"|‘[^’]*’|“[^”]*”|「[^」]*」")
        private val SPLIT = Regex("(?<=[.!?…\\n])")
        private val HAS_WORD = Regex("[가-힣A-Za-z0-9]")
        private val TRAILING_SOFT = Regex("[\\s.!?…~,ㅎㅋㅠㅜ^]+$")
        private val TRAILING_PUNCT = Regex("[\\s.!?…~,]+$")
        private val NUMBER = Regex("[0-9]+(?:\\.[0-9]+)?")
        private val WS = Regex("\\s")
        private const val SINO_DIGITS = "영일이삼사오육칠팔구"

        fun unquote(t: String) = t.replace(QUOTES, "")
        fun sentences(t: String): List<String> =
            unquote(t).split(SPLIT).map { it.trim() }.filter { HAS_WORD.containsMatchIn(it) }

        private fun norm(n: String) = n.trimStart('0').ifEmpty { "0" }.let { if (it.startsWith(".")) "0$it" else it }
        private fun digit(c: Char): Int? = if (c == '공') 0 else SINO_DIGITS.indexOf(c).takeIf { it >= 0 }

        /** "사 점 이" -> "4.2", "이천이십육" -> "2026", "삼" -> "3". */
        fun sinoValue(run: String): String {
            val s = run.replace(" ", "")
            val intp = s.substringBefore('점'); val frac = if ('점' in s) s.substringAfter('점') else null
            val n = if (intp.any { it in "십백천만" }) {
                var total = 0L; var section = 0L; var cur = 0L
                for (c in intp) when (c) {
                    '십' -> { section += (if (cur == 0L) 1 else cur) * 10; cur = 0 }
                    '백' -> { section += (if (cur == 0L) 1 else cur) * 100; cur = 0 }
                    '천' -> { section += (if (cur == 0L) 1 else cur) * 1000; cur = 0 }
                    '만' -> { total += (if (section + cur == 0L) 1 else section + cur) * 10000; section = 0; cur = 0 }
                    else -> digit(c)?.let { cur = it.toLong() }
                }
                (total + section + cur).toString()
            } else intp.mapNotNull { digit(it) }.joinToString("").trimStart('0').ifEmpty { "0" }
            return if (frac != null) n + "." + frac.mapNotNull { digit(it) }.joinToString("") else n
        }
    }
}
