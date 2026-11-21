package com.pass.diary.core.ai

import com.pass.diary.core.model.Tag
import com.pass.diary.core.model.TagKind
import kotlinx.serialization.json.JsonObject

/**
 * Person/place/activity chips from shared/ai/tags.json (its `match` field is the spec): split into 어절, an 어절 matches
 * when it starts with a tag/alias (longest first) and the rest is only 조사/어미. No morphological analyzer (no LGPL natives).
 * [userTags] = tags the user typed before (ARCHITECTURE §5.2 "사용자가 쓴 태그 학습").
 * Places keep what the diary said ("한강", not the dictionary's "공원"); [category] gives the dictionary tag.
 * People / activities are normalized to the dictionary tag (울엄마 -> 엄마).
 */
class TagExtractor(dict: JsonObject, userTags: List<Tag> = emptyList()) {
    private data class Term(val surface: String, val tag: String, val kind: TagKind)

    private val terms: List<Term> = buildList {
        for ((key, kind) in listOf("people" to TagKind.PERSON, "places" to TagKind.PLACE, "activities" to TagKind.ACTIVITY))
            for (e in dict.objects(key)) {
                val tag = e.str("tag")!!
                for (s in listOf(tag) + e.strings("aliases")) {
                    add(Term(s, tag, kind))
                    if (' ' in s) add(Term(s.replace(" ", ""), tag, kind))
                }
            }
        userTags.filter { it.kind != TagKind.FREE }.forEach { add(Term(it.value, it.value, it.kind)) }
    }.sortedByDescending { it.surface.length }

    fun extract(text: String, max: Int = 6): List<Tag> {
        val found = LinkedHashMap<String, Tag>()
        val words = text.split(Regex("\\s+")).map { it.replace(EDGE_PUNCT, "") }.filter { it.isNotEmpty() }
        // multi-word aliases are matched against the text with spaces removed between two 어절
        val candidates = words + words.zipWithNext { a, b -> a + b }
        for (w in candidates) {
            val t = terms.firstOrNull { w.startsWith(it.surface) && JOSA.matches(w.substring(it.surface.length)) } ?: continue
            val value = if (t.kind == TagKind.PLACE) t.surface else t.tag
            found.putIfAbsent(t.kind.key + value, Tag(t.kind, value, auto = true))
        }
        // given names said with the 이 suffix ("민준이랑", "서현이가") that the dictionary doesn't know
        for (m in NAME.findAll(text)) {
            val name = m.groupValues[1]
            if (name !in NAME_STOP && terms.none { it.surface == name || it.surface == name + "이" })
                found.putIfAbsent("person$name", Tag(TagKind.PERSON, name, auto = true))
        }
        // "민지랑", "수진하고", "준혁과": no 이, so only names made of given-name syllables (커피랑, 치킨이랑 are not people)
        for (m in WITH.findAll(text)) {
            val (name, josa) = m.destructured
            val batchim = (name.last() - '가') % 28 != 0
            if (josa in setOf("랑", "와") && batchim || josa == "과" && !batchim) continue
            if (name in NAME_STOP || name in WITH_STOP || name.any { it !in NAME_SYLLABLES } || terms.any { it.surface == name }) continue
            found.putIfAbsent("person$name", Tag(TagKind.PERSON, name, auto = true))
        }
        return found.values.take(max)
    }

    /** Dictionary tag behind a value ("한강" -> "공원"), null when it isn't a dictionary word. */
    fun category(tag: Tag): String? = terms.firstOrNull { it.kind == tag.kind && it.surface == tag.value }?.tag

    companion object {
        private val EDGE_PUNCT = Regex("^[\\p{P}\\p{S}]+|[\\p{P}\\p{S}ㅋㅎㅠㅜ]+$")
        private val JOSA = Regex("(님|들|이|네)?(께서|에서|한테서|한테|에게|이랑|랑|하고|와|과|은|는|이|가|을|를|도|만|께|네|집|에|의|으로|로|까지|부터|처럼|보다|이나|나|야|아|은요)*((하|했|해|할|함|합|한|하러|시키|시켰)[가-힣]*)?")
        private val NAME = Regex("(?<![가-힣])([가-힣]{2})이(랑|가|한테|는|도|를|하고|네|와)(?![가-힣])")
        private val WITH = Regex("(?<![가-힣])([가-힣]{2})(랑|하고|와|과)(?![가-힣])")
        // ponytail: common given-name syllables + a stop list; chips are only suggestions. A name list if this misfires.
        private const val NAME_SYLLABLES = "강겸결광권균남단달담대덕두련률륜별봄빛엽웅임창천초탁택혁휘가건경규근기나다도동라란래려령로루리린마명모무문미민범병보빈산상새서석선설성세소솔송수숙순슬승시신아연영예온완용우욱운원유윤율은의이인재정제조종주준지진찬채철태하한해현형혜호홍화환효후훈희"
        private val WITH_STOP = setOf("우유", "고기", "수박", "자두", "두부", "하루", "어제", "일상", "현실", "자연", "세상", "미래", "정리", "상황", "노래", "소리", "이유", "의미", "주제", "문제", "숙제", "가을", "서로", "모두", "자기", "나라", "이야", "누나", "형아")
        private val NAME_STOP = setOf("우리", "마음", "기분", "오늘", "하루", "시간", "생각", "사람", "친구", "아이", "누구", "이것", "그것", "저것", "고양", "강아")
    }
}
