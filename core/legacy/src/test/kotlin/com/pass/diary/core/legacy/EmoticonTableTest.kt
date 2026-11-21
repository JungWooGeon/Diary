package com.pass.diary.core.legacy

import com.pass.diary.core.model.EmotionKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class EmoticonTableTest {
    private val json = Json.parseToJsonElement(File("../../tools/legacy/emoticon_ids.json").readText()).jsonObject

    @Test fun idsMatchJson() {
        val fromJson = json.getValue("ids").jsonObject.map { (k, v) -> k.toInt() to v.jsonPrimitive.content }.toMap()
        assertEquals(fromJson, LegacyEmoticons.NAME_BY_ID)
    }

    @Test fun emotionsMatchJson() {
        val fromJson = json.getValue("emotions").jsonObject.mapValues { (_, v) ->
            if (v is JsonNull) null else v.jsonObject.let { it.getValue("key").jsonPrimitive.content to it.getValue("intensity").jsonPrimitive.int }
        }
        assertEquals(fromJson, LegacyEmoticons.EMOTION_BY_NAME.mapValues { (_, e) -> e?.let { it.key.key to it.intensity } })
        assertEquals(15, LegacyEmoticons.EMOTION_BY_NAME.size)
    }

    @Test fun mapDedupesKeepsOrderAndCountsUnknown() {
        val m = LegacyEmoticons.map(listOf(0x7f10000e /*tired*/, 0x7f100009 /*sick*/, 123))
        assertEquals(listOf(EmotionKey.TIRED to 4, EmotionKey.UNSURE to 3), m.emotions.map { it.key to it.intensity })
        assertEquals(1, m.unknownIds)
        assertEquals(0, LegacyEmoticons.map(listOf(0x7f100006 /*picture*/, null, -1)).emotions.size)
    }
}
