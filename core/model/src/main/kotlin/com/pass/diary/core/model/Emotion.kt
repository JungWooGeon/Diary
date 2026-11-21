package com.pass.diary.core.model

/** CONTRACTS §3 / shared/emotions.json. Colors are 0xRRGGBB. A test in core:light checks this against the JSON. */
enum class EmotionGroup(val key: String, val label: String, val light: Int, val glow: Int) {
    BRIGHT("bright", "밝음", 0xFFF0A8, 0xF4C23E),
    CALM("calm", "평온", 0xD6F0CB, 0x7FC08F),
    FLUTTER("flutter", "설렘", 0xFFD6DF, 0xEE8BA5),
    LOW("low", "가라앉음", 0xD3E2F6, 0x7F9FD1),
    WORN("worn", "지침", 0xE6DDF4, 0xA595CF);

    val emotions: List<EmotionKey> get() = EmotionKey.entries.filter { it.group == this }

    companion object {
        /** The "light off" color for `unsure`-only (or empty) days. */
        const val OFF_LIGHT = 0xD6D0C9
        const val OFF_GLOW = 0xA39A91
        fun of(key: String): EmotionGroup? = entries.firstOrNull { it.key == key }
    }
}

/** The 21 emotion keys; [key] is what gets stored (DB, Drive JSON, AI labels). */
enum class EmotionKey(val key: String, val label: String, val group: EmotionGroup?) {
    JOY("joy", "기쁨", EmotionGroup.BRIGHT),
    PROUD("proud", "뿌듯함", EmotionGroup.BRIGHT),
    EXCITED("excited", "신남", EmotionGroup.BRIGHT),
    GRATEFUL("grateful", "감사", EmotionGroup.BRIGHT),
    COMFY("comfy", "편안함", EmotionGroup.CALM),
    SERENE("serene", "차분함", EmotionGroup.CALM),
    RELIEVED("relieved", "안심", EmotionGroup.CALM),
    LIGHT("light", "홀가분", EmotionGroup.CALM),
    FLUTTER("flutter", "설렘", EmotionGroup.FLUTTER),
    LOVE("love", "사랑", EmotionGroup.FLUTTER),
    HOPE("hope", "기대", EmotionGroup.FLUTTER),
    MISS("miss", "그리움", EmotionGroup.FLUTTER),
    SAD("sad", "슬픔", EmotionGroup.LOW),
    DOWN("down", "우울", EmotionGroup.LOW),
    LONELY("lonely", "외로움", EmotionGroup.LOW),
    HURT("hurt", "서운함", EmotionGroup.LOW),
    TIRED("tired", "피곤", EmotionGroup.WORN),
    ANXIOUS("anxious", "불안", EmotionGroup.WORN),
    ANNOYED("annoyed", "짜증", EmotionGroup.WORN),
    ANGRY("angry", "화남", EmotionGroup.WORN),
    UNSURE("unsure", "잘 모르겠음", null);

    companion object {
        private val byKey = entries.associateBy { it.key }
        fun of(key: String): EmotionKey? = byKey[key]

        /** 1-tap moods: the first emotion of each group, saved at intensity 3. */
        val QUICK = listOf(JOY, COMFY, FLUTTER, SAD, TIRED)
    }
}

object Intensity {
    const val MIN = 1
    const val MAX = 5
    const val DEFAULT = 3
    val range = MIN..MAX
}

data class EntryEmotion(val key: EmotionKey, val intensity: Int = Intensity.DEFAULT) {
    init { require(intensity in Intensity.range) { "intensity $intensity not in 1..5" } }
}
