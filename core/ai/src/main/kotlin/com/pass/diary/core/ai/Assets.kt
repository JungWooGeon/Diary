package com.pass.diary.core.ai

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Files under repo `shared/` (packaged as app assets, see core/ai/build.gradle.kts). Paths like "ai/crisis.json". */
interface AssetSource {
    fun bytes(path: String): ByteArray?
    fun text(path: String): String? = bytes(path)?.toString(Charsets.UTF_8)
    fun json(path: String): JsonElement? = text(path)?.let { Json.parseToJsonElement(it) }
}

class AndroidAssets(private val context: Context) : AssetSource {
    override fun bytes(path: String): ByteArray? =
        runCatching { context.assets.open(path).use { it.readBytes() } }.getOrNull()
}

// Small JSON accessors so the shared/*.json formats can be read without mirroring every schema as classes.
internal val JsonElement.obj: JsonObject get() = jsonObject
internal val JsonElement.arr: JsonArray get() = jsonArray
internal fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
internal fun JsonObject.int(k: String): Int? = (this[k] as? JsonPrimitive)?.intOrNull
internal fun JsonObject.dbl(k: String): Double? = (this[k] as? JsonPrimitive)?.doubleOrNull
internal fun JsonObject.bool(k: String): Boolean? = (this[k] as? JsonPrimitive)?.booleanOrNull
internal fun JsonObject.strings(k: String): List<String> =
    (this[k] as? JsonArray)?.map { it.jsonPrimitive.content } ?: emptyList()
internal fun JsonObject.objects(k: String): List<JsonObject> =
    (this[k] as? JsonArray)?.map { it.jsonObject } ?: emptyList()
