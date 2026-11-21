package com.pass.diary.core.light

import com.pass.diary.core.model.EmotionGroup
import com.pass.diary.core.model.EmotionKey
import com.pass.diary.core.model.EmotionKey.*
import com.pass.diary.core.model.EntryEmotion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot

class LightMixerTest {
    private fun e(k: EmotionKey, i: Int = 3) = EntryEmotion(k, i)
    private fun chroma(rgb: Int) = Oklab.fromRgb(rgb).let { hypot(it[1], it[2]) }
    private fun lightness(rgb: Int) = Oklab.fromRgb(rgb)[0]

    @Test fun `oklab round-trips every group color exactly`() {
        for (g in EmotionGroup.entries) for (c in listOf(g.light, g.glow)) {
            val lab = Oklab.fromRgb(c)
            assertEquals(Light.hex(c), Light.hex(Oklab.toRgb(lab[0], lab[1], lab[2])))
        }
    }

    @Test fun `single emotion at full strength is exactly its group color`() {
        for (g in EmotionGroup.entries) {
            val l = LightMixer.mix(listOf(e(g.emotions.first(), 5)))
            assertEquals(Light.hex(g.light), l.lightHex)
            assertEquals(Light.hex(g.glow), l.glowHex)
            assertEquals(1f, l.strength)
        }
    }

    @Test fun `empty or unsure-only is the off light`() {
        assertEquals(LightMixer.OFF, LightMixer.mix(emptyList()))
        assertEquals(LightMixer.OFF, LightMixer.mix(listOf(e(UNSURE, 5))))
        assertEquals("#D6D0C9", LightMixer.OFF.lightHex)
    }

    @Test fun `unsure is ignored when other emotions are present`() =
        assertEquals(LightMixer.mix(listOf(e(JOY, 4))), LightMixer.mix(listOf(e(JOY, 4), e(UNSURE, 5))))

    @Test fun `weaker feelings give dimmer, less saturated light`() {
        val weak = LightMixer.mix(listOf(e(SAD, 1)))
        val strong = LightMixer.mix(listOf(e(SAD, 5)))
        assertTrue(weak.strength < strong.strength)
        assertTrue(chroma(weak.glow) < chroma(strong.glow))
        assertTrue(lightness(weak.light) < lightness(strong.light))
    }

    @Test fun `strength grows with total intensity and caps at 1`() {
        assertEquals(0.2f, LightMixer.mix(listOf(e(JOY, 1))).strength, 1e-6f)
        assertEquals(0.6f, LightMixer.mix(listOf(e(JOY, 3))).strength, 1e-6f)
        assertEquals(1f, LightMixer.mix(listOf(e(JOY, 3), e(SAD, 3))).strength, 1e-6f)
    }

    @Test fun `order does not matter and same-group emotions keep the group color`() {
        assertEquals(LightMixer.mix(listOf(e(JOY, 2), e(LOVE, 4))), LightMixer.mix(listOf(e(LOVE, 4), e(JOY, 2))))
        assertEquals(LightMixer.mix(listOf(e(JOY, 5))), LightMixer.mix(listOf(e(PROUD, 3), e(GRATEFUL, 2))))
    }

    @Test fun `heavier weight pulls the mix toward its color`() {
        val towardJoy = LightMixer.mix(listOf(e(JOY, 5), e(SAD, 1))).glow
        val towardSad = LightMixer.mix(listOf(e(JOY, 1), e(SAD, 5))).glow
        val joy = Oklab.fromRgb(EmotionGroup.BRIGHT.glow); val sad = Oklab.fromRgb(EmotionGroup.LOW.glow)
        fun dist(rgb: Int, ref: DoubleArray) = Oklab.fromRgb(rgb).let { hypot(it[1] - ref[1], it[2] - ref[2]) }
        assertTrue(dist(towardJoy, joy) < dist(towardJoy, sad))
        assertTrue(dist(towardSad, sad) < dist(towardSad, joy))
    }

    @Test fun `mixes stay colorful, not muddy`() {
        // Yellow + blue averaged in a/b would collapse toward grey; chroma averaging keeps it lit.
        val mixed = LightMixer.mix(listOf(e(JOY, 5), e(SAD, 5)))
        val minGroup = minOf(chroma(EmotionGroup.BRIGHT.glow), chroma(EmotionGroup.LOW.glow))
        assertTrue(chroma(mixed.glow) > minGroup * 0.9)
        assertNotEquals(LightMixer.OFF.glow, mixed.glow)
    }

    @Test fun `day gradient is sorted by time with each entry's own light`() {
        val stops = LightMixer.dayGradient(listOf("22:30" to listOf(e(TIRED)), "08:00" to listOf(e(JOY)), "12:00" to listOf(e(UNSURE))))
        assertEquals(listOf(480 / 1440f, 720 / 1440f, 1350 / 1440f), stops.map { it.first })
        assertEquals(LightMixer.mix(listOf(e(JOY))), stops[0].second)
        assertTrue(stops[1].second.off)
    }

    @Test fun `mixDay flattens all entries`() =
        assertEquals(LightMixer.mix(listOf(e(JOY), e(SAD))), LightMixer.mixDay(listOf(listOf(e(JOY)), listOf(e(SAD)))))

    @Test fun `dominant group is the heaviest, ties go to the earlier group`() {
        assertEquals(EmotionGroup.LOW, LightMixer.dominantGroup(listOf(e(JOY, 2), e(SAD, 2), e(DOWN, 1))))
        assertEquals(EmotionGroup.BRIGHT, LightMixer.dominantGroup(listOf(e(SAD, 3), e(JOY, 3))))
        assertEquals(null, LightMixer.dominantGroup(listOf(e(UNSURE, 5))))
    }

    // ---- shared files (the web reuses both) ----

    private val shared = File(System.getProperty("shared.dir") ?: "../../shared")

    @Test fun `model enums match shared emotions json`() {
        val root = Json.parseToJsonElement(File(shared, "emotions.json").readText()).jsonObject
        val groups = root["groups"]!!.jsonArray.map { it.jsonObject }
        assertEquals(EmotionGroup.entries.map { it.key }, groups.map { it.str("key") })
        for ((g, j) in EmotionGroup.entries.zip(groups)) {
            assertEquals(g.label, j.str("name"))
            assertEquals(Light.hex(g.light), j.str("light"))
            assertEquals(Light.hex(g.glow), j.str("glow"))
            assertEquals(g.emotions.map { it.key to it.label }, j["emotions"]!!.jsonArray.map { it.jsonObject.str("key") to it.jsonObject.str("name") })
        }
        val unsure = root["unsure"]!!.jsonObject
        assertEquals(UNSURE.key, unsure.str("key")); assertEquals(UNSURE.label, unsure.str("name"))
        assertEquals(Light.hex(EmotionGroup.OFF_LIGHT), unsure.str("light"))
        assertEquals(EmotionKey.QUICK.map { it.key }, root["quickMoods"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals(21, EmotionKey.entries.size)
    }

    private val mixCases: List<Pair<String, List<EntryEmotion>>> = buildList {
        add("empty" to emptyList()); add("unsure only" to listOf(e(UNSURE, 3)))
        for (g in EmotionGroup.entries) for (i in 1..5) add("${g.key} ${g.emotions.first().key}@$i" to listOf(e(g.emotions.first(), i)))
        add("joy5 + sad5" to listOf(e(JOY, 5), e(SAD, 5)))
        add("joy5 + sad1" to listOf(e(JOY, 5), e(SAD, 1)))
        add("comfy2 + flutter2" to listOf(e(COMFY, 2), e(FLUTTER, 2)))
        add("tired4 + anxious3 + unsure5" to listOf(e(TIRED, 4), e(ANXIOUS, 3), e(UNSURE, 5)))
        add("love3 + miss2 + sad1" to listOf(e(LOVE, 3), e(MISS, 2), e(SAD, 1)))
        add("all five groups @1" to EmotionGroup.entries.map { e(it.emotions.first(), 1) })
        add("all five groups @5" to EmotionGroup.entries.map { e(it.emotions.first(), 5) })
        add("proud1 + angry1" to listOf(e(PROUD, 1), e(ANGRY, 1)))
    }
    private val dayCase = listOf("21:40" to listOf(e(TIRED, 4)), "08:15" to listOf(e(JOY, 3), e(HOPE, 2)), "13:00" to listOf(e(UNSURE)))

    private fun emotionsJson(list: List<EntryEmotion>) = buildJsonArray {
        list.forEach { add(buildJsonObject { put("key", it.key.key); put("intensity", it.intensity) }) }
    }
    private fun lightJson(l: Light) = buildJsonObject { put("light", l.lightHex); put("glow", l.glowHex); put("strength", l.strength); put("off", l.off) }

    private fun vectors() = buildJsonObject {
        put("version", 1)
        put("spec", "core/light/src/main/kotlin/com/pass/diary/core/light/LightMixer.kt (KDoc of LightMixer). Regenerate: REGEN_LIGHT_VECTORS=1 ./gradlew :core:light:test")
        putJsonArray("mix") {
            mixCases.forEach { (name, list) ->
                add(buildJsonObject { put("name", name); put("emotions", emotionsJson(list)); put("expect", lightJson(LightMixer.mix(list))) })
            }
        }
        putJsonArray("dayGradient") {
            add(buildJsonObject {
                put("name", "three entries, out of order")
                put("entries", buildJsonArray { dayCase.forEach { (t, l) -> add(buildJsonObject { put("time", t); put("emotions", emotionsJson(l)) }) } })
                put("expect", buildJsonArray {
                    LightMixer.dayGradient(dayCase).forEach { (p, l) -> add(JsonObject(lightJson(l) + ("position" to JsonPrimitive(p)))) }
                })
            })
        }
    }

    @Test fun `implementation matches shared light-test-vectors json`() {
        val file = File(shared, "light-test-vectors.json")
        if (System.getenv("REGEN_LIGHT_VECTORS") == "1" || !file.exists()) {
            file.writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), vectors()) + "\n")
        }
        val root = Json.parseToJsonElement(file.readText()).jsonObject
        val cases = root["mix"]!!.jsonArray
        assertTrue(cases.size >= 30)
        for (c in cases.map { it.jsonObject }) {
            val input = c["emotions"]!!.jsonArray.map { EntryEmotion(EmotionKey.of(it.jsonObject.str("key"))!!, it.jsonObject["intensity"]!!.jsonPrimitive.int) }
            assertLight(c.str("name"), c["expect"]!!.jsonObject, LightMixer.mix(input))
        }
        for (c in root["dayGradient"]!!.jsonArray.map { it.jsonObject }) {
            val input = c["entries"]!!.jsonArray.map { en ->
                en.jsonObject.str("time") to en.jsonObject["emotions"]!!.jsonArray.map { EntryEmotion(EmotionKey.of(it.jsonObject.str("key"))!!, it.jsonObject["intensity"]!!.jsonPrimitive.int) }
            }
            val got = LightMixer.dayGradient(input)
            val want = c["expect"]!!.jsonArray.map { it.jsonObject }
            assertEquals(want.size, got.size)
            for ((w, g) in want.zip(got)) {
                assertTrue(abs(w["position"]!!.jsonPrimitive.float - g.first) < 1e-4)
                assertLight(c.str("name"), w, g.second)
            }
        }
    }

    private fun assertLight(name: String, want: JsonObject, got: Light) {
        assertEquals("$name light", want.str("light"), got.lightHex)
        assertEquals("$name glow", want.str("glow"), got.glowHex)
        assertEquals("$name strength", want["strength"]!!.jsonPrimitive.float, got.strength, 1e-4f)
        assertEquals("$name off", want["off"]!!.jsonPrimitive.boolean, got.off)
    }

    private fun JsonObject.str(k: String) = this[k]!!.jsonPrimitive.content
}
