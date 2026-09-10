package dev.plattnericus.pokyh.data.backend

import dev.plattnericus.pokyh.BuildConfig
import dev.plattnericus.pokyh.core.util.TtlCache
import dev.plattnericus.pokyh.data.model.ApiClass
import dev.plattnericus.pokyh.data.model.ApiComment
import dev.plattnericus.pokyh.data.model.ApiErrorBody
import dev.plattnericus.pokyh.data.model.ApiReminder
import dev.plattnericus.pokyh.data.model.ApiTodo
import dev.plattnericus.pokyh.data.model.ApiUser
import dev.plattnericus.pokyh.data.model.AppError
import dev.plattnericus.pokyh.data.model.AuthResponse
import dev.plattnericus.pokyh.data.model.Dish
import dev.plattnericus.pokyh.data.model.DishRatingsData
import dev.plattnericus.pokyh.data.model.DishRatingsResponse
import dev.plattnericus.pokyh.data.model.MenuResponse
import dev.plattnericus.pokyh.data.model.toDomain
import dev.plattnericus.pokyh.data.network.Config
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** POKYH-Backend-Client (api.pokyh.com): App-Auth, Todos, Erinnerungen, Klasse, Mensa. */
@Singleton
class BackendClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // ── Auth ─────────────────────────────────────────────────────────────────

    /** Ergebnis des Server-zu-Server-Logins (mit Diagnose für die UI). */
    sealed interface UntisLoginResult {
        data class Ok(val token: String, val refresh: String) : UntisLoginResult
        /** klasseId <= 0 -> Backend verlangt > 0. */
        data object NoClass : UntisLoginResult
        /** HTTP-/Netzwerkfehler (Meldung). */
        data class Failed(val message: String) : UntisLoginResult
    }

    @Serializable
    private data class UntisLoginResponse(val token: String, val refreshToken: String)

    /**
     * Server-zu-Server-Login nach erfolgreichem WebUntis-Login (kein Passwort nötig).
     * Das Backend legt bei gültiger klasseId automatisch ein Konto an. Bei `role = "parent"`
     * wird ein Elternkonto erstellt (eigene Todos, sieht nur den Klassennamen, keine
     * Erinnerungen, unsichtbares Mitglied). Für Eltern ist die klasseId die des Kindes.
     */
    suspend fun loginWithUntis(
        username: String,
        klasseId: Int,
        klasseName: String,
        role: String = "student",
    ): UntisLoginResult {
        if (klasseId <= 0) return UntisLoginResult.NoClass
        val body = buildJsonObject {
            put("username", username)
            put("klasseId", klasseId)
            put("klasseName", klasseName)
            put("role", role)
        }
        val request = Request.Builder()
            .url(Config.backendURL + Config.Routes.authLogin)
            .header("Content-Type", "application/json")
            .header("X-Server-Key", BuildConfig.BACKEND_SERVER_KEY)
            .header("X-API-Key", BuildConfig.BACKEND_API_KEY)
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()
        val response = try {
            execute(request)
        } catch (e: IOException) {
            return UntisLoginResult.Failed("Keine Verbindung")
        }
        return response.use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val message = runCatching { json.decodeFromString<ApiErrorBody>(text).error }.getOrNull()
                return@use UntisLoginResult.Failed(message ?: "HTTP ${resp.code}")
            }
            val parsed = runCatching { json.decodeFromString<UntisLoginResponse>(text) }.getOrNull()
                ?: return@use UntisLoginResult.Failed("Ungültige Antwort")
            UntisLoginResult.Ok(parsed.token, parsed.refreshToken)
        }
    }

    /** Direkter App-Login (Nicht-Untis-Konto): Benutzername + Passwort. */
    suspend fun login(username: String, password: String): AuthResponse {
        val body = buildJsonObject {
            put("username", username.lowercase())
            put("password", password)
        }
        return decode(request(Config.Routes.authLogin, method = "POST", jsonBody = body.toString()))
    }

    suspend fun register(username: String, password: String): AuthResponse {
        val body = buildJsonObject {
            put("username", username.lowercase())
            put("password", password)
        }
        return decode(request(Config.Routes.authRegister, method = "POST", jsonBody = body.toString()))
    }

    suspend fun me(token: String): ApiUser = decode(request(Config.Routes.authMe, token = token))

    // ── Todos ────────────────────────────────────────────────────────────────

    suspend fun todos(username: String, token: String): List<ApiTodo> =
        decode(request(Config.Routes.todos(username.urlEncoded()), token = token))

    suspend fun createTodo(username: String, title: String, details: String, dueAt: String?, token: String): ApiTodo {
        val body = buildJsonObject {
            put("title", title)
            put("details", details)
            if (dueAt != null) put("dueAt", dueAt)
        }
        return decode(
            request(Config.Routes.todos(username.urlEncoded()), method = "POST", token = token, jsonBody = body.toString()),
        )
    }

    suspend fun updateTodo(username: String, id: String, done: Boolean, token: String) {
        val body = buildJsonObject { put("done", done) }
        request(
            Config.Routes.todos(username.urlEncoded()) + "/" + id,
            method = "PATCH",
            token = token,
            jsonBody = body.toString(),
        )
    }

    suspend fun deleteTodo(username: String, id: String, token: String) {
        request(Config.Routes.todos(username.urlEncoded()) + "/" + id, method = "DELETE", token = token)
    }

    // ── Klasse / Erinnerungen ───────────────────────────────────────────────

    suspend fun myClass(token: String): ApiClass? {
        val text = try {
            request(Config.Routes.classesMine, token = token)
        } catch (e: AppError) {
            return null
        }
        if (text.isBlank() || text == "null") return null
        return runCatching { decode<ApiClass>(text) }.getOrNull()
    }

    suspend fun reminders(classId: String, token: String): List<ApiReminder> =
        decode(request(Config.Routes.reminders(classId), token = token))

    suspend fun createReminder(classId: String, title: String, body: String, remindAt: String, token: String): ApiReminder {
        val payload = buildJsonObject {
            put("title", title)
            put("body", body)
            put("remindAt", remindAt)
        }
        return decode(
            request(Config.Routes.reminders(classId), method = "POST", token = token, jsonBody = payload.toString()),
        )
    }

    suspend fun deleteReminder(classId: String, id: String, token: String) {
        request(Config.Routes.reminders(classId) + "/" + id, method = "DELETE", token = token)
    }

    // ── Erinnerungs-Kommentare ──────────────────────────────────────────────

    suspend fun reminderComments(classId: String, reminderId: String, token: String): List<ApiComment> =
        decode(request(Config.Routes.reminders(classId) + "/" + reminderId + "/comments", token = token))

    suspend fun createReminderComment(classId: String, reminderId: String, body: String, token: String): ApiComment {
        val payload = buildJsonObject { put("body", body) }
        return decode(
            request(
                Config.Routes.reminders(classId) + "/" + reminderId + "/comments",
                method = "POST",
                token = token,
                jsonBody = payload.toString(),
            ),
        )
    }

    suspend fun deleteReminderComment(classId: String, reminderId: String, commentId: String, token: String) {
        request(
            Config.Routes.reminders(classId) + "/" + reminderId + "/comments/" + commentId,
            method = "DELETE",
            token = token,
        )
    }

    // ── Mensa-Kommentare ────────────────────────────────────────────────────

    suspend fun dishComments(dishId: String, token: String): List<ApiComment> =
        decode(request(Config.Routes.dishComments + "/" + dishId.urlEncoded(), token = token))

    suspend fun createDishComment(dishId: String, body: String, token: String): ApiComment {
        val payload = buildJsonObject { put("body", body) }
        return decode(
            request(
                Config.Routes.dishComments + "/" + dishId.urlEncoded(),
                method = "POST",
                token = token,
                jsonBody = payload.toString(),
            ),
        )
    }

    suspend fun deleteDishComment(dishId: String, commentId: String, token: String) {
        request(Config.Routes.dishComments + "/" + dishId.urlEncoded() + "/" + commentId, method = "DELETE", token = token)
    }

    // ── Mensa ────────────────────────────────────────────────────────────────

    // Zentrales Caching (Performance): /dishes max. alle 5 Min neu laden.
    private val dishCache = TtlCache<String, List<Dish>>(ttlMillis = 300_000L)
    private val dishKey = "dishes"

    /** Leert alle In-Memory-Caches dieses Clients (z. B. „Cache leeren"). */
    fun clearCaches() = dishCache.removeAll()

    suspend fun dishes(force: Boolean = false): List<Dish> {
        if (!force) dishCache.get(dishKey)?.let { return it }
        val request = Request.Builder()
            .url(Config.backendURL + Config.Routes.dishes)
            .header("Accept", "application/json")
            .header("X-API-Key", BuildConfig.BACKEND_API_KEY)
            .get()
            .build()
        val response = try {
            execute(request)
        } catch (e: IOException) {
            dishCache.stale(dishKey)?.let { return it }
            throw AppError("Mensa nicht ladbar (keine Verbindung).")
        }
        return response.use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                dishCache.stale(dishKey)?.let { return@use it }
                val message = runCatching { json.decodeFromString<ApiErrorBody>(text).error }.getOrNull()
                throw AppError(message ?: "Mensa nicht ladbar (HTTP ${resp.code}).")
            }
            val menu = runCatching { json.decodeFromString<MenuResponse>(text) }.getOrNull()
                ?: run {
                    dishCache.stale(dishKey)?.let { return@use it }
                    throw AppError("Mensa nicht ladbar (ungültige Antwort).")
                }
            val dishes = menu.menu.dishes.map { it.toDomain(Config.backendURL) }
            dishCache.set(dishKey, dishes)
            dishes
        }
    }

    // ── Bewertungen ─────────────────────────────────────────────────────────

    suspend fun dishRatings(dishId: String, token: String): DishRatingsData {
        val dto = decode<DishRatingsResponse>(request(Config.Routes.dishRatings + "/" + dishId.urlEncoded(), token = token))
        return DishRatingsData(ratings = dto.ratings, myRating = dto.myRating)
    }

    suspend fun dishRatingsBatch(ids: List<String>, token: String): Map<String, DishRatingsData> {
        val payload = buildJsonObject { putJsonArray("dishIds") { ids.forEach { add(it) } } }
        val text = request(
            Config.Routes.dishRatings + "/batch",
            method = "POST",
            token = token,
            jsonBody = payload.toString(),
        )
        val map = decode<Map<String, DishRatingsResponse>>(text)
        return map.mapValues { (_, dto) -> DishRatingsData(ratings = dto.ratings, myRating = dto.myRating) }
    }

    suspend fun rateDish(dishId: String, stars: Int, token: String) {
        val payload = buildJsonObject { put("stars", stars) }
        request(
            Config.Routes.dishRatings + "/" + dishId.urlEncoded(),
            method = "POST",
            token = token,
            jsonBody = payload.toString(),
        )
    }

    // ── Authentifizierte Requests ───────────────────────────────────────────

    private suspend fun request(
        path: String,
        method: String = "GET",
        token: String? = null,
        jsonBody: String? = null,
    ): String {
        val builder = Request.Builder()
            .url(Config.backendURL + path)
            .header("Content-Type", "application/json")
            .header("X-API-Key", BuildConfig.BACKEND_API_KEY)
        if (token != null) builder.header("Authorization", "Bearer $token")
        val requestBody = when {
            jsonBody != null -> jsonBody.toRequestBody(jsonMediaType)
            method == "POST" || method == "PUT" || method == "PATCH" -> "{}".toRequestBody(jsonMediaType)
            else -> null
        }
        builder.method(method, requestBody)
        val response = try {
            execute(builder.build())
        } catch (e: IOException) {
            throw AppError("Keine Verbindung")
        }
        return response.use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val message = runCatching { json.decodeFromString<ApiErrorBody>(text).error }.getOrNull()
                throw AppError(message ?: "HTTP ${resp.code}")
            }
            text
        }
    }

    private inline fun <reified T> decode(text: String): T = json.decodeFromString(text)

    private suspend fun execute(request: Request): Response = suspendCancellableCoroutine { cont ->
        val call = okHttpClient.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
        })
    }
}

private fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")
