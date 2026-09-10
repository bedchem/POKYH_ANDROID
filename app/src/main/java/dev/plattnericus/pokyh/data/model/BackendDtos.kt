package dev.plattnericus.pokyh.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * POKYH backend DTOs — the backend returns exact matching camelCase JSON, safe to decode
 * directly with kotlinx.serialization. Mirrors the backend-facing structs in `Models.swift`.
 */

// ── Todos / Reminders / Class ───────────────────────────────────────────

@Serializable
data class ApiTodo(
    val id: String,
    val title: String,
    val details: String,
    val dueAt: String? = null,
    val done: Boolean,
    val doneAt: String? = null,
    val createdAt: String,
)

@Serializable
data class ApiReminder(
    val id: String,
    val classId: String,
    val title: String,
    val body: String,
    val remindAt: String,
    val createdBy: String,
    val createdByName: String,
    val createdByUsername: String,
    val createdAt: String,
)

@Serializable
data class ApiClassMember(
    val stableUid: String,
    val username: String,
    val joinedAt: String? = null,
)

@Serializable
data class ApiClass(
    val id: String,
    val name: String,
    val code: String,
    val webuntisKlasseId: Int,
    val createdBy: String,
    val createdByName: String,
    val createdAt: String,
    val members: List<ApiClassMember> = emptyList(),
)

@Serializable
data class ApiComment(
    val id: String,
    val stableUid: String,
    val username: String,
    val body: String,
    val createdAt: String,
    val updatedAt: String? = null,
)

@Serializable
data class ApiUser(
    val stableUid: String,
    val username: String,
    val webuntisKlasseId: Int? = null,
    val webuntisKlasseName: String? = null,
    val classId: String? = null,
    val isAdmin: Boolean,
    /** Account role: "student" (default) or "parent". */
    val role: String? = null,
)

// ── Auth ─────────────────────────────────────────────────────────────────

@Serializable
data class AuthResponse(
    val token: String,
    val refreshToken: String,
    val user: ApiUser,
)

@Serializable
data class RefreshResponse(
    val token: String,
)

@Serializable
data class ApiErrorBody(
    val error: String,
)

// ── Cafeteria ────────────────────────────────────────────────────────────

@Serializable
data class LocalizedText(
    val de: String = "",
    val it: String = "",
    val en: String = "",
)

@Serializable
data class DishDto(
    val id: String,
    val internalId: String? = null,
    val name: JsonElement,
    val description: JsonElement? = null,
    val imageUrl: String? = null,
    val category: String = "",
    val tags: List<String> = emptyList(),
    val prepTime: Int = 0,
    val calories: Double = 0.0,
    val price: Double = 0.0,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val allergens: List<String> = emptyList(),
    val isVegetarian: Boolean = false,
    val isVegan: Boolean = false,
    val date: String = "",
)

@Serializable
data class MenuResponse(
    val menu: MenuBody,
)

@Serializable
data class MenuBody(
    val dishes: List<DishDto> = emptyList(),
)

@Serializable
data class DishRatingsResponse(
    val ratings: Map<String, Double> = emptyMap(),
    val myRating: Int? = null,
)

/**
 * `name`/`description` are a plain string OR an object with de/it/en on the wire.
 * Tries a direct string first, then de -> it -> en from the object, else empty string.
 */
fun JsonElement.resolveLocalized(): String {
    (this as? JsonPrimitive)?.let { prim ->
        return prim.contentOrNull ?: ""
    }
    (this as? JsonObject)?.let { obj ->
        for (key in listOf("de", "it", "en")) {
            val value = (obj[key] as? JsonPrimitive)?.contentOrNull
            if (!value.isNullOrEmpty()) return value
        }
    }
    return ""
}

fun DishDto.toDomain(baseUrl: String): Dish {
    val resolvedImageUrl = imageUrl?.let { url ->
        if (url.startsWith("http://") || url.startsWith("https://")) url else baseUrl + url
    }
    return Dish(
        id = id,
        name = name.resolveLocalized(),
        description = description?.resolveLocalized(),
        category = category,
        date = date,
        imageUrl = resolvedImageUrl,
        price = price,
        allergens = allergens,
        tags = tags,
        calories = calories,
        protein = protein,
        carbs = null,
        fat = fat,
    )
}
