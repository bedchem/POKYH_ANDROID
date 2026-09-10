package dev.plattnericus.pokyh.data.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Lightweight dynamic JSON access — mirrors `JSON.swift`'s defensive navigation
 * (WebUntis responses vary; values can be missing / nested differently).
 * Wraps a nullable [JsonElement] and never throws on malformed input.
 */
class JsonDyn private constructor(private val element: JsonElement?) {

    operator fun get(key: String): JsonDyn {
        val obj = element as? JsonObject
        return JsonDyn(obj?.get(key))
    }

    operator fun get(index: Int): JsonDyn {
        val arr = element as? JsonArray
        if (arr == null || index < 0 || index >= arr.size) return JsonDyn(null)
        return JsonDyn(arr[index])
    }

    val array: List<JsonDyn>
        get() = (element as? JsonArray)?.map { JsonDyn(it) } ?: emptyList()

    val dictionary: Map<String, JsonDyn>
        get() = (element as? JsonObject)?.mapValues { JsonDyn(it.value) } ?: emptyMap()

    val string: String?
        get() {
            val prim = element as? JsonPrimitive ?: return null
            if (prim is JsonNull) return null
            return if (prim.isString) prim.contentOrNull else prim.content
        }

    val int: Int?
        get() {
            val prim = element as? JsonPrimitive ?: return null
            if (prim is JsonNull) return null
            prim.intOrNull?.let { return it }
            return if (prim.isString) prim.content.toIntOrNull() else null
        }

    val double: Double?
        get() {
            val prim = element as? JsonPrimitive ?: return null
            if (prim is JsonNull) return null
            prim.doubleOrNull?.let { return it }
            return if (prim.isString) prim.content.toDoubleOrNull() else null
        }

    val bool: Boolean?
        get() {
            val prim = element as? JsonPrimitive ?: return null
            if (prim is JsonNull) return null
            prim.booleanOrNull?.let { return it }
            if (prim.isString) {
                val s = prim.content.lowercase()
                return s == "true" || s == "1"
            }
            return null
        }

    val exists: Boolean
        get() = element != null && element != JsonNull

    val isArray: Boolean
        get() = element is JsonArray

    companion object {
        private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

        val empty: JsonDyn = JsonDyn(null)

        /** Never throws — matches JSON.swift's forgiving `try?` parse (WebUntis JSON varies). */
        fun parse(bytes: ByteArray): JsonDyn {
            return try {
                val text = bytes.toString(Charsets.UTF_8)
                JsonDyn(lenientJson.parseToJsonElement(text))
            } catch (_: Exception) {
                JsonDyn(null)
            }
        }

        fun parse(text: String): JsonDyn {
            return try {
                JsonDyn(lenientJson.parseToJsonElement(text))
            } catch (_: Exception) {
                JsonDyn(null)
            }
        }

        fun of(element: JsonElement?): JsonDyn = JsonDyn(element)
    }
}
