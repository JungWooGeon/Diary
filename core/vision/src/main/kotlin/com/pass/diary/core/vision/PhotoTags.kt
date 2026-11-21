package com.pass.diary.core.vision

import com.pass.diary.core.model.Tag
import com.pass.diary.core.model.TagKind

/**
 * ML Kit base labels (English, ~450) -> the editor's tag chips. Only labels a diary would keep are listed; the rest
 * ("Fun", "Hand", "Room"…) are dropped. Words that exist in shared/ai/tags.json keep its kind and spelling (강아지 =
 * person, 바다 = place, 커피 = activity) so photo chips and text chips look and group the same.
 */
object PhotoTags {
    const val MIN_CONFIDENCE = 0.7f

    fun from(labels: List<ImageLabel>, max: Int = 3): List<Tag> = labels
        .filter { it.confidence >= MIN_CONFIDENCE }
        .sortedByDescending { it.confidence }
        .mapNotNull { DICT[it.text.lowercase()] }
        .distinctBy { it.value }
        .take(max)

    private fun p(v: String) = Tag(TagKind.PERSON, v, auto = true)
    private fun pl(v: String) = Tag(TagKind.PLACE, v, auto = true)
    private fun a(v: String) = Tag(TagKind.ACTIVITY, v, auto = true)
    private fun f(v: String) = Tag(TagKind.FREE, v, auto = true)

    private val DICT: Map<String, Tag> = mapOf(
        // 사람·동물 (tags.json people)
        "dog" to p("강아지"), "cat" to p("고양이"), "pet" to p("반려동물"), "baby" to p("아기"),
        // 장소
        "beach" to pl("바다"), "sea" to pl("바다"), "ocean" to pl("바다"), "mountain" to pl("산"), "park" to pl("공원"),
        "restaurant" to pl("식당"), "cafe" to pl("카페"), "café" to pl("카페"), "church" to pl("교회"), "stadium" to pl("경기장"),
        "aquarium" to pl("아쿠아리움"), "museum" to pl("박물관"), "lake" to pl("호수"), "river" to pl("강"), "forest" to pl("숲"),
        "garden" to pl("정원"), "waterfall" to pl("폭포"), "swimming pool" to pl("수영장"), "pier" to pl("부두"), "bridge" to pl("다리"),
        "cave" to pl("동굴"), "desert" to pl("사막"),
        // 활동 (tags.json activities)
        "swimming" to a("수영"), "cycling" to a("자전거"), "bicycle" to a("자전거"), "hiking" to a("등산"), "running" to a("운동"),
        "skiing" to f("스키"), "snowboarding" to f("스노보드"), "surfing" to f("서핑"), "waterskiing" to f("수상스키"),
        "concert" to f("콘서트"), "party" to f("파티"), "wedding" to f("결혼식"), "shopping" to a("쇼핑"), "picnic" to f("소풍"),
        "camping" to f("캠핑"), "bonfire" to f("모닥불"), "fireworks" to f("불꽃놀이"), "christmas" to f("크리스마스"),
        "selfie" to f("셀카"), "coffee" to a("커피"), "cappuccino" to a("커피"), "latte" to a("커피"), "cooking" to a("요리"),
        // 음식
        "food" to f("음식"), "cake" to f("케이크"), "dessert" to f("디저트"), "cookie" to f("쿠키"), "bread" to f("빵"),
        "pizza" to f("피자"), "ice cream" to f("아이스크림"), "fruit" to f("과일"), "bento" to f("도시락"), "sushi" to f("초밥"),
        "noodle" to f("면"), "wine" to f("와인"), "beer" to f("맥주"), "cocktail" to f("칵테일"), "tea" to f("티타임"),
        "chocolate" to f("초콜릿"), "candy" to f("사탕"),
        // 자연·하늘
        "flower" to f("꽃"), "sky" to f("하늘"), "sunset" to f("노을"), "rainbow" to f("무지개"), "snow" to f("눈"),
        "moon" to f("달"), "cloud" to f("구름"), "tree" to f("나무"), "plant" to f("식물"), "bird" to f("새"), "leaf" to f("나뭇잎"),
        // 물건
        "book" to f("책"), "musical instrument" to f("악기"), "guitar" to f("기타"), "piano" to f("피아노"), "toy" to f("장난감"),
        "balloon" to f("풍선"), "car" to f("자동차"), "train" to f("기차"), "airplane" to f("비행기"), "bus" to f("버스"),
        "boat" to f("보트"), "comics" to f("만화"), "painting" to f("그림"), "shoe" to f("신발"), "bag" to f("가방"),
    )
}
