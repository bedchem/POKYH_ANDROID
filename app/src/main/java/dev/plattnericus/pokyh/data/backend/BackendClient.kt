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
import dev.plattnericus.pokyh.core.status.PokyhService
import dev.plattnericus.pokyh.core.status.ServiceHealth
import dev.plattnericus.pokyh.data.storage.DiskCache
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.JsonObject
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
    private val diskCache: DiskCache,
    private val serviceHealth: ServiceHealth,
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

    // ── Mensa-Cache ─────────────────────────────────────────────────────────
    //
    // Drei Ebenen, jede für einen anderen Fall:
    //
    //  1. [dishCache] — RAM, 5 Minuten. Deckt Tabwechsel ab: Home und Mensa zeigen denselben
    //     Speiseplan, und ohne das lädt jeder Wechsel neu.
    //  2. [dishMutex] — Request-Bündelung. Home und Mensa starten beim App-Start praktisch
    //     gleichzeitig; ohne Sperre gingen zwei identische Requests raus und der zweite
    //     überschrieb den ersten. Wer die Sperre bekommt, lädt; alle anderen finden danach den
    //     gefüllten RAM-Cache vor (deshalb die zweite Prüfung IM Lock).
    //  3. [DiskCache] — überlebt den App-Neustart. Beim Kaltstart steht der Speiseplan damit
    //     sofort da, statt erst nach der Netzantwort; das Netz aktualisiert ihn danach.
    //
    // Auf der Platte liegt der ROHE Antwort-Body, nicht die gemappten [Dish]-Objekte: so bleibt
    // [Dish] ein reines Domänenmodell ohne Serialisierungs-Annotationen, und Gelesenes läuft
    // durch exakt denselben Parser wie eine frische Antwort.
    private val dishCache = TtlCache<String, List<Dish>>(ttlMillis = 300_000L)
    private val dishKey = "dishes"
    private val dishMutex = Mutex()
    private val dishDiskKey = "mensa-dishes-v1"

    /** Leert alle In-Memory-Caches dieses Clients (z. B. „Cache leeren"). */
    fun clearCaches() {
        dishCache.removeAll()
        ratingsCache.removeAll()
        subjectImageCache.removeAll()
    }

    /**
     * Der Speiseplan. [force] überspringt nur den RAM-Cache — die Request-Bündelung gilt immer,
     * sonst würden zwei parallele Pull-to-Refresh-Gesten wieder zwei Requests auslösen.
     */
    suspend fun dishes(force: Boolean = false): List<Dish> {
        if (!force) dishCache.get(dishKey)?.let { return it }
        return dishMutex.withLock {
            // Zweite Prüfung: während des Wartens auf die Sperre hat ein anderer Aufrufer den
            // Cache womöglich schon gefüllt. Das ist der eigentliche Bündelungs-Effekt.
            if (!force) dishCache.get(dishKey)?.let { return@withLock it }
            fetchDishes()
        }
    }

    /**
     * Der Speiseplan samt Herkunft — für die „Stand …"-Anzeige.
     *
     * [dishes] wirft bei Netzproblemen nicht, sondern fällt still auf die Platte zurück, und
     * genau das macht den Unterschied unsichtbar: der Screen zeigt dann einen Plan von letzter
     * Woche, ohne es zu sagen. Diese Variante gibt zusätzlich zurück, ob der Plan vom Netz kam
     * und wann die Kopie geschrieben wurde.
     */
    suspend fun dishesWithFreshness(force: Boolean = false): DishesResult {
        val before = serviceHealth.states.value[PokyhService.BACKEND]?.lastOkAt ?: 0L
        val dishes = dishes(force)
        val after = serviceHealth.states.value[PokyhService.BACKEND]?.lastOkAt ?: 0L
        // Hat der Server während dieses Aufrufs geantwortet, ist der Plan frisch. Das ist
        // zuverlässiger als ein Flag im Client: die Antwort kann aus dem RAM-Cache eines
        // Aufrufs von vor zwei Minuten stammen, der sehr wohl frisch war.
        val fresh = after > before || (after > 0L && System.currentTimeMillis() - after < DishFreshWindowMs)
        return DishesResult(
            dishes = dishes,
            savedAt = if (fresh) System.currentTimeMillis() else (diskCache.savedAt(dishDiskKey) ?: 0L),
            stale = !fresh,
        )
    }

    data class DishesResult(val dishes: List<Dish>, val savedAt: Long, val stale: Boolean)

    /** Innerhalb dieser Spanne gilt eine Backend-Antwort als „gerade eben" — deckt den RAM-Cache
     * ab, dessen TTL fünf Minuten beträgt. */
    private val DishFreshWindowMs = 300_000L

    /**
     * Der zuletzt gespeicherte Speiseplan von der Platte, ohne Netz.
     *
     * Dafür gedacht, dass ein Screen beim Kaltstart sofort etwas anzeigen kann, während
     * [dishes] im Hintergrund läuft. Füllt den RAM-Cache NICHT — sonst würde der
     * TTL-Cache-Treffer den anschließenden Netz-Abruf überflüssig erscheinen lassen und der
     * Plan bliebe bis zum Ablauf der TTL alt.
     */
    suspend fun cachedDishesOrNull(): List<Dish>? {
        dishCache.stale(dishKey)?.let { return it }
        return readDishesFromDisk()
    }

    private suspend fun fetchDishes(): List<Dish> {
        val request = Request.Builder()
            .url(Config.backendURL + Config.Routes.dishes)
            .header("Accept", "application/json")
            .header("X-API-Key", BuildConfig.BACKEND_API_KEY)
            .get()
            .build()
        val response = try {
            execute(request)
        } catch (e: IOException) {
            dishesFallback()?.let { return it }
            throw AppError.noConnection("Mensa nicht ladbar (keine Verbindung).")
        }
        return response.use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                dishesFallback()?.let { return@use it }
                val message = runCatching { json.decodeFromString<ApiErrorBody>(text).error }.getOrNull()
                throw AppError(message ?: "Mensa nicht ladbar (HTTP ${resp.code}).")
            }
            val dishes = parseDishes(text)
                ?: run {
                    dishesFallback()?.let { return@use it }
                    throw AppError("Mensa nicht ladbar (ungültige Antwort).")
                }
            dishCache.set(dishKey, dishes)
            diskCache.write(dishDiskKey, text, String.serializer())
            dishes
        }
    }

    private fun parseDishes(rawJson: String): List<Dish>? =
        runCatching { json.decodeFromString<MenuResponse>(rawJson) }
            .getOrNull()
            ?.menu
            ?.dishes
            ?.map { it.toDomain(Config.backendURL) }

    /** Abgelaufener RAM-Stand zuerst, sonst die Platte — beides besser als ein Fehler-Screen. */
    private suspend fun dishesFallback(): List<Dish>? =
        dishCache.stale(dishKey) ?: readDishesFromDisk()

    private suspend fun readDishesFromDisk(): List<Dish>? =
        diskCache.read(dishDiskKey, String.serializer())?.let { parseDishes(it) }

    // ── Fach-Bilder ─────────────────────────────────────────────────────────
    //
    // Das Backend hat für viele Fächer ein Titelbild. Es liefert aber KEINEN Platzhalter für
    // Fächer ohne Bild — ein Abruf auf einen unbekannten Key endet im Fehler. Deshalb wird
    // zuerst die Liste der vorhandenen Keys geholt und erst dann eine Bild-URL gebaut; genau so
    // macht es auch das Web-Frontend (`api.subjectImages`).

    @Serializable
    private data class SubjectImageRow(val subject: String)

    /** Lange TTL: die Liste ändert sich, wenn das Backend neue Bilder generiert — also selten. */
    private val subjectImageCache = TtlCache<String, Set<String>>(ttlMillis = 6 * 60 * 60 * 1000L)
    private val subjectImageKey = "subject-images"
    private val subjectImageMutex = Mutex()

    /**
     * Die Fach-Keys, für die ein Bild existiert. Leer bei jedem Fehler — ein fehlendes Bild ist
     * kein Fehlerzustand, den ein Screen anzeigen müsste.
     */
    suspend fun subjectImageKeys(): Set<String> {
        subjectImageCache.get(subjectImageKey)?.let { return it }
        return subjectImageMutex.withLock {
            subjectImageCache.get(subjectImageKey)?.let { return@withLock it }
            val keys = runCatching {
                decode<List<SubjectImageRow>>(request(Config.Routes.subjectImages))
                    .map { it.subject.lowercase().trim() }
                    .toSet()
            }.getOrDefault(emptySet())
            subjectImageCache.set(subjectImageKey, keys)
            keys
        }
    }

    /**
     * Der Cache-Key eines Fachs: bevorzugt der Langname, sonst das Kürzel — kleingeschrieben und
     * getrimmt. Muss exakt der Bildung im Web-Frontend entsprechen, sonst zeigen die beiden Apps
     * unterschiedliche (oder gar keine) Bilder für dasselbe Fach.
     */
    fun subjectImageKeyOf(subjectLong: String, subjectName: String): String =
        (subjectLong.ifBlank { subjectName }).lowercase().trim()

    /**
     * Die Bild-URL zu einem Key.
     *
     * OHNE `?apiKey=`-Query-Parameter: diese Route akzeptiert den Schlüssel ausschließlich als
     * Header und antwortet sonst mit
     * `400 {"error":"Use X-API-Key header for this route"}`. (Das Web-Frontend hängt den Key an
     * die Query, weil ein `<img src>` keine Header setzen kann — auf Android kann der
     * Image-Loader das, also wird hier der Weg genommen, den der Server tatsächlich unterstützt.)
     *
     * Aufrufer laden die URL mit [apiKeyHeaderName]/[apiKeyHeaderValue] als Header, siehe
     * `LessonDetailSheet`.
     */
    fun subjectImageUrl(key: String): String =
        Config.backendURL + Config.Routes.subjectImages + "/" + key.urlEncoded()

    /** Header-Name des Backend-API-Schlüssels — für Anfragen, die nicht über [request] laufen
     * (Bild-Loads). */
    val apiKeyHeaderName: String get() = "X-API-Key"

    /** Header-Wert dazu. Liegt hier statt in der UI, damit `BuildConfig` nicht quer durch die
     * Schichten gelesen wird. */
    val apiKeyHeaderValue: String get() = BuildConfig.BACKEND_API_KEY

    /**
     * Meldet dem Backend, welche Fächer es an dieser Schule gibt, damit es fehlende Bilder
     * nachgenerieren kann. Best effort und höchstens einmal pro Prozess: das ist ein Hinweis an
     * den Server, kein Feature dieser App.
     */
    suspend fun reportSubjects(entries: List<Pair<String, String>>) {
        if (subjectsReported || entries.isEmpty()) return
        subjectsReported = true
        val unique = LinkedHashMap<String, JsonObject>()
        for ((shortName, longName) in entries) {
            if (shortName.isBlank() && longName.isBlank()) continue
            val key = subjectImageKeyOf(longName, shortName)
            if (key.isEmpty() || unique.containsKey(key)) continue
            unique[key] = buildJsonObject {
                put("key", key)
                put("longName", longName.ifBlank { shortName })
                put("shortName", shortName)
            }
        }
        if (unique.isEmpty()) return
        val payload = buildJsonObject {
            putJsonArray("subjects") { unique.values.forEach { add(it) } }
        }
        runCatching {
            request(Config.Routes.subjectImages + "/report", method = "POST", jsonBody = payload.toString())
        }
        // A fresh report may have produced new images, so the key list is no longer current.
        subjectImageCache.remove(subjectImageKey)
    }

    private var subjectsReported = false

    // ── Bewertungen ─────────────────────────────────────────────────────────

    /**
     * Sternebewertungen, pro Gericht gecacht (1 Minute).
     *
     * Kürzere TTL als der Speiseplan: ein Menü ändert sich einmal am Tag, eine Bewertung kann
     * sich jederzeit ändern. Lang genug, damit ein Tabwechsel oder ein Zurück aus der Detailseite
     * nicht neu lädt, kurz genug, dass fremde Bewertungen zeitnah ankommen.
     */
    private val ratingsCache = TtlCache<String, DishRatingsData>(ttlMillis = 60_000L)

    suspend fun dishRatings(dishId: String, token: String, force: Boolean = false): DishRatingsData {
        if (!force) ratingsCache.get(dishId)?.let { return it }
        val dto = decode<DishRatingsResponse>(request(Config.Routes.dishRatings + "/" + dishId.urlEncoded(), token = token))
        return DishRatingsData(ratings = dto.ratings, myRating = dto.myRating).also { ratingsCache.set(dishId, it) }
    }

    /**
     * Der zuletzt bekannte Stand für [ids], ohne Netz — für den Platzhalter, der die Sterne beim
     * Öffnen sofort füllt, statt sie nach der Antwort hereinspringen zu lassen.
     */
    fun cachedRatings(ids: List<String>): Map<String, DishRatingsData> =
        ids.mapNotNull { id -> ratingsCache.stale(id)?.let { id to it } }.toMap()

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
            .also { fresh -> fresh.forEach { (id, data) -> ratingsCache.set(id, data) } }
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

    /**
     * Schreibt eine gerade abgegebene Bewertung in den Cache, damit jeder andere Screen sie
     * sofort sieht.
     *
     * Ohne das zeigte die Mensa-Liste nach einer Bewertung im Detail so lange den alten
     * Schnitt, bis die TTL ablief — die UI war optimistisch, der Cache nicht.
     */
    fun applyLocalRating(dishId: String, stars: Int, userKey: String) {
        val current = ratingsCache.stale(dishId) ?: DishRatingsData(emptyMap(), null)
        ratingsCache.set(
            dishId,
            current.copy(
                ratings = current.ratings + (userKey to stars.toDouble()),
                myRating = stars,
            ),
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
            throw AppError.noConnection()
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
