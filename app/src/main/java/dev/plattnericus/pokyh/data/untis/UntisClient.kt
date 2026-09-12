package dev.plattnericus.pokyh.data.untis

import android.util.Base64
import dev.plattnericus.pokyh.data.json.JsonDyn
import dev.plattnericus.pokyh.data.model.AbsenceEntry
import dev.plattnericus.pokyh.data.model.AppError
import dev.plattnericus.pokyh.data.model.ClassregEvent
import dev.plattnericus.pokyh.data.model.GradeEntry
import dev.plattnericus.pokyh.data.model.MessageAttachment
import dev.plattnericus.pokyh.data.model.MessageDetail
import dev.plattnericus.pokyh.data.model.MessageFolder
import dev.plattnericus.pokyh.data.model.MessagePreview
import dev.plattnericus.pokyh.data.model.MessageRecipient
import dev.plattnericus.pokyh.data.model.OutgoingAttachment
import dev.plattnericus.pokyh.data.model.SubjectGrades
import dev.plattnericus.pokyh.data.model.TimetableEntry
import dev.plattnericus.pokyh.data.model.UserSession
import dev.plattnericus.pokyh.data.network.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.todayIn
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * WebUntis client — replicates the frontend's server-proxy routes natively: JSON-RPC
 * `authenticate`, bearer token, timetable/grades/absences/messages. Ported 1:1 from the iOS
 * `UntisClient.swift`.
 *
 * Cookie handling is entirely manual (no [OkHttpClient] cookie jar): every request carries an
 * explicit `Cookie: JSESSIONID=...; schoolname="..."` header, and `Authorization: Bearer <token>`
 * once a token has been obtained. Every public function additionally takes the [UserSession] it
 * authenticates as, since — unlike the singleton-session iOS app — one client instance may serve
 * more than one signed-in account.
 */
@Singleton
class UntisClient @Inject constructor(private val okHttpClient: OkHttpClient) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /** Never sends/accepts cookies automatically — cookies are set by hand on every call. */
    private val noCookieClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .cookieJar(CookieJar.NO_COOKIES)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    private fun cookieHeader(sessionId: String): String =
        "JSESSIONID=$sessionId; schoolname=\"${Config.schoolCookie}\""

    private fun Request.Builder.withAuth(s: UserSession): Request.Builder {
        header("Cookie", cookieHeader(s.sessionId))
        header("Accept", "application/json")
        if (s.bearerToken.isNotEmpty()) header("Authorization", "Bearer ${s.bearerToken}")
        return this
    }

    private suspend fun get(urlString: String, s: UserSession, timeoutMs: Long? = null): Pair<String, Response> =
        withContext(Dispatchers.IO) {
            val client = if (timeoutMs != null) {
                noCookieClient.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()
            } else noCookieClient
            val request = Request.Builder().url(urlString).withAuth(s).build()
            val response = client.newCall(request).execute()
            val body = response.use { it.body?.string().orEmpty() }
            body to response
        }

    private fun isHtmlOrAuthError(body: String, response: Response): Boolean {
        if (response.code == 401 || response.code == 403) return true
        return body.firstOrNull() == '<'
    }

    // ── JSON-RPC ─────────────────────────────────────────────────────────────

    private suspend fun rpcFull(method: String, s: UserSession, id: String, params: JSONObject = JSONObject()): JsonDyn =
        withContext(Dispatchers.IO) {
            val url = "${Config.untisBase}${Config.Routes.jsonRpc}?school=${Config.school}"
            val bodyJson = JSONObject().apply {
                put("id", id)
                put("method", method)
                put("params", params)
                put("jsonrpc", "2.0")
            }
            val request = Request.Builder()
                .url(url)
                .header("Content-Type", "application/json")
                .header("Cookie", cookieHeader(s.sessionId))
                .post(bodyJson.toString().toRequestBody(jsonMediaType))
                .build()
            val response = noCookieClient.newCall(request).execute()
            val text = response.use { it.body?.string().orEmpty() }
            JsonDyn.parse(text)
        }

    private suspend fun rpc(method: String, s: UserSession, id: String): JsonDyn = rpcFull(method, s, id)["result"]

    // ── Login ────────────────────────────────────────────────────────────────

    suspend fun login(username: String, password: String): UserSession {
        val user = username.trim().lowercase()
        if (user.isEmpty() || password.isEmpty()) {
            throw AppError("Benutzername und Passwort erforderlich.")
        }

        // 1. JSON-RPC authenticate
        val rpcUrl = "${Config.untisBase}${Config.Routes.jsonRpc}?school=${Config.school}"
        val rpcBody = JSONObject().apply {
            put("id", "pockyh-web")
            put("method", "authenticate")
            put(
                "params",
                JSONObject().apply {
                    put("user", user)
                    put("password", password)
                    put("client", Config.untisClient)
                },
            )
            put("jsonrpc", "2.0")
        }
        val authRequest = Request.Builder()
            .url(rpcUrl)
            .header("Content-Type", "application/json")
            .post(rpcBody.toString().toRequestBody(jsonMediaType))
            .build()
        val authResponse = withContext(Dispatchers.IO) { noCookieClient.newCall(authRequest).execute() }
        val authBody = authResponse.use { it.body?.string().orEmpty() }

        // Set-Cookie → JSESSIONID (search every Set-Cookie header line, not just the first).
        val sessionId = authResponse.headers("Set-Cookie")
            .firstNotNullOfOrNull { Regex("JSESSIONID=([^;]+)").find(it)?.groupValues?.get(1) }
            ?: ""

        val json = JsonDyn.parse(authBody)
        if (json["error"].exists) {
            throw AppError(json["error"]["message"].string ?: "Anmeldung fehlgeschlagen.")
        }
        val studentId = json["result"]["personId"].int ?: throw AppError("Anmeldung fehlgeschlagen.")
        val klasseId0 = json["result"]["klasseId"].int ?: 0
        val personType = json["result"]["personType"].int

        var bearerToken = ""
        var klasseName = ""
        var resolvedStudentId = studentId
        var resolvedKlasseId = klasseId0
        var personName: String? = null
        var isParent = false
        var imageUrl: String? = null

        // 2.–4. Bearer token, class name and student list — in parallel. None of the three
        // needs the bearer token: the token endpoint and getKlassen/getStudents (JSON-RPC)
        // authenticate via the JSESSIONID cookie alone. Immutable snapshot for the concurrent
        // tasks (no data race — the mutable locals above are only touched after the await).
        val snapshot = UserSession(
            sessionId = sessionId, bearerToken = "", studentId = studentId, klasseId = klasseId0,
            klasseName = "", username = user, personName = null, personType = personType, isParent = false,
            apiToken = null, apiRefresh = null, stableUid = null, classId = null, imageUrl = null,
        )
        coroutineScope {
            val tokenDeferred = async { runCatching { fetchToken(snapshot) }.getOrDefault("") }
            val klassenDeferred = async { runCatching { fetchKlassen(snapshot, klasseId0) }.getOrDefault("") }
            val studentsDeferred = async { runCatching { fetchStudents(snapshot) }.getOrDefault(emptyList()) }
            bearerToken = tokenDeferred.await()
            klasseName = klassenDeferred.await()
            val studentList = studentsDeferred.await()

            // Resolve the effective student/parent + name + klasseId. personType 5 = student
            // (WebUntis) — so a student is never mistakenly treated as a parent, even when
            // `getStudents` comes back empty.
            val isStudentType = personType == 5
            val isStudentSelf = studentList.any { it.id == studentId }
            val isParentCandidate = !isStudentType && !isStudentSelf

            if (isStudentType || isStudentSelf) {
                val me = studentList.firstOrNull { it.id == studentId }
                personName = me?.name
                val kid = me?.klasseId
                if (resolvedKlasseId <= 0 && kid != null && kid > 0) resolvedKlasseId = kid
            } else if (studentList.isNotEmpty()) {
                val first = studentList[0]
                resolvedStudentId = first.id
                val kid = first.klasseId
                if (kid != null && kid > 0) resolvedKlasseId = kid
                personName = first.name
                isParent = true
            }

            // App data AT MOST once (expensive: several endpoints) — only when something is
            // still missing: klasseId unknown, or a real parent account without a student list.
            val needsParentResolve = isParentCandidate && studentList.isEmpty()
            if (resolvedKlasseId <= 0 || needsParentResolve) {
                val appDataSnapshot = snapshot.copy(
                    bearerToken = bearerToken, studentId = resolvedStudentId, klasseId = resolvedKlasseId,
                )
                val appData = runCatching { fetchAppData(appDataSnapshot) }.getOrNull()
                if (appData != null) {
                    if (needsParentResolve) {
                        isParent = detectParentRaw(appData.raw)
                        extractChildStudentIdRaw(appData.raw, studentId)?.let { resolvedStudentId = it }
                        if (personName == null) personName = extractChildName(appData.json)
                    }
                    if (resolvedKlasseId <= 0) {
                        extractKlasseIdRaw(appData.raw, resolvedStudentId)?.let { if (it > 0) resolvedKlasseId = it }
                    }
                    imageUrl = extractImageUrl(appData.json, isParent)
                }
            }

            // Last resort: the class only appears in the timetable (block-teaching schools:
            // authenticate=0, getStudents locked, app/data has no class). Pull the class name
            // out of the timetable, map it to the numeric id via getKlassen. Cached. Also
            // applies to parents: resolvedStudentId is then the child, whose timetable carries
            // the class — otherwise parent accounts end up with no resolvable klasseId.
            if (resolvedKlasseId <= 0 && (isStudentType || isParent) && resolvedStudentId > 0) {
                val cacheKey = classCacheKey(user)
                val cached = classCache[cacheKey]
                if (cached != null) {
                    resolvedKlasseId = cached.id
                    if (klasseName.isEmpty()) klasseName = cached.name
                } else {
                    val classSnapshot = snapshot.copy(
                        bearerToken = bearerToken, studentId = resolvedStudentId, klasseId = resolvedKlasseId,
                    )
                    val resolved = resolveStudentClass(classSnapshot)
                    if (resolved != null) {
                        resolvedKlasseId = resolved.first
                        if (klasseName.isEmpty()) klasseName = resolved.second
                        classCache[cacheKey] = KlasseCacheEntry(resolved.first, resolved.second)
                    }
                }
            }
        }

        if (personName?.isEmpty() == true) personName = null

        return UserSession(
            sessionId = sessionId, bearerToken = bearerToken, studentId = resolvedStudentId,
            klasseId = resolvedKlasseId, klasseName = klasseName, username = user, personName = personName,
            personType = personType, isParent = isParent, apiToken = null, apiRefresh = null,
            stableUid = null, classId = null, imageUrl = imageUrl,
        )
    }

    private suspend fun fetchToken(s: UserSession): String {
        val (body, resp) = get("${Config.untisBase}${Config.Routes.token}", s)
        if (resp.code != 200) return ""
        val tok = body.trim()
        return if (tok.count { it == '.' } == 2) tok else ""
    }

    private suspend fun fetchKlassen(s: UserSession, klasseId: Int): String {
        val result = rpc("getKlassen", s, "pockyh-klassen")
        for (k in result.array) {
            if (k["id"].int == klasseId) return k["name"].string ?: ""
        }
        return ""
    }

    // ── Class resolution via the timetable (students) ──────────────────────────
    // The class shows up per lesson as an element with `type == "CLASS"` (no numeric id).
    // Extract the name → map it to the numeric klasseId via getKlassen.

    private data class StudentInfo(val id: Int, val klasseId: Int?, val name: String)

    private suspend fun fetchStudents(s: UserSession): List<StudentInfo> {
        val result = rpc("getStudents", s, "pockyh-students")
        return result.array.mapNotNull { st ->
            val id = st["id"].int ?: return@mapNotNull null
            val fore = st["foreName"].string ?: st["firstName"].string ?: ""
            val long = st["longName"].string ?: st["lastName"].string ?: ""
            val full = listOf(fore, long).filter { it.isNotEmpty() }.joinToString(" ")
            val kid = st["klasseId"].int ?: st["klasse"]["id"].int
            StudentInfo(id, kid, full.ifEmpty { st["name"].string ?: "" })
        }
    }

    /** Combined: class name from the timetable, mapped to the numeric klasseId. */
    private suspend fun resolveStudentClass(s: UserSession): Pair<Int, String>? {
        val cls = classNameFromTimetable(s) ?: return null
        val id = klasseIdForName(cls.first, cls.second, s) ?: return null
        if (id <= 0) return null
        return id to cls.first
    }

    /**
     * Finds a week WITH lessons and returns (shortName, longName) of the class. Vocational
     * schools run block teaching, so whole weeks can be empty — sample across the school year
     * (current week first).
     */
    private suspend fun classNameFromTimetable(s: UserSession): Pair<String, String>? {
        val sy = currentSchoolYear()
        val days = mutableListOf<LocalDate>()
        days.add(Clock.System.todayIn(TimeZone.currentSystemDefault()))
        for (m in listOf(9, 10, 11, 12)) days.add(LocalDate(sy, m, 15))
        for (m in listOf(1, 2, 3, 4, 5, 6)) days.add(LocalDate(sy + 1, m, 15))
        for (day in days) {
            val start = day.toString()
            val end = day.plus(DatePeriod(days = 5)).toString()
            val url = "${Config.untisBase}${Config.Routes.timetable}?start=$start&end=$end&format=1" +
                "&resourceType=STUDENT&resources=${s.studentId}&periodTypes=&timetableType=MY_TIMETABLE&layout=START_TIME"
            val (body, resp) = runCatching { get(url, s, timeoutMs = 8000) }.getOrNull() ?: continue
            if (resp.code != 200) continue
            extractClassName(JsonDyn.parse(body))?.let { return it }
        }
        return null
    }

    /** Class element (`type == "CLASS"`) out of the timetable's grid positions. */
    private fun extractClassName(json: JsonDyn): Pair<String, String>? {
        for (day in json["days"].array) {
            for (ge in day["gridEntries"].array) {
                for (posKey in listOf("position1", "position2", "position3", "position4", "position5", "position6", "position7")) {
                    for (el in ge[posKey].array) {
                        if (el["current"]["type"].string == "CLASS") {
                            val short = el["current"]["shortName"].string ?: el["current"]["displayName"].string ?: ""
                            if (short.isEmpty()) continue
                            return short to (el["current"]["longName"].string ?: short)
                        }
                    }
                }
            }
        }
        return null
    }

    private suspend fun getKlassenFull(schoolyearId: Int?, s: UserSession): JsonDyn {
        val params = JSONObject()
        if (schoolyearId != null) params.put("schoolyearId", schoolyearId)
        return rpcFull("getKlassen", s, "pockyh-klassen", params)
    }

    /** Schoolyear id for the current school year (via getSchoolyears, robust to a null "current"). */
    private suspend fun resolvedSchoolyearId(s: UserSession): Int? {
        val id = runCatching { schoolyearId(currentSchoolYear(), s) }.getOrNull()
        return if (id != null && id > 0) id else null
    }

    /** Numeric klasseId by name match via getKlassen (with schoolyear context). */
    private suspend fun klasseIdForName(short: String, long: String, s: UserSession): Int? {
        val syId = resolvedSchoolyearId(s)
        val j = runCatching { getKlassenFull(syId, s) }.getOrNull() ?: return null
        val arr = j["result"].array
        val shortLower = short.lowercase()
        for (k in arr) {
            if ((k["name"].string ?: "").lowercase() == shortLower) {
                val id = k["id"].int
                if (id != null && id > 0) return id
            }
        }
        val longLower = long.lowercase()
        for (k in arr) {
            val ln = (k["longName"].string ?: "").lowercase()
            if (ln.isNotEmpty() && (longLower.contains(ln) || ln.contains(shortLower))) {
                val id = k["id"].int
                if (id != null && id > 0) return id
            }
        }
        return null
    }

    // ── Class cache (per user + school year) — avoids re-hitting the timetable/getKlassen ──
    // TODO: this is in-memory only for now (dies with the process); wire it into a persistent
    // PreferencesStore-backed cache (keyed the same way) once that component exists, mirroring
    // the UserDefaults-backed `cachedKlasse`/`cacheKlasse` on iOS.
    private data class KlasseCacheEntry(val id: Int, val name: String)
    private val classCache = ConcurrentHashMap<String, KlasseCacheEntry>()
    private fun classCacheKey(username: String): String = "pokyh_klasse_${username.lowercase()}_${currentSchoolYear()}"

    // ── App data (parent/guardian resolution) ───────────────────────────────

    private data class AppDataResult(val json: JsonDyn, val raw: Any?)

    private suspend fun fetchAppData(s: UserSession): AppDataResult? {
        // Each endpoint independent + time-boxed → one slow endpoint doesn't block the whole login.
        for (path in Config.Routes.appDataCandidates) {
            val (body, resp) = runCatching { get("${Config.untisBase}$path", s, timeoutMs = 6000) }.getOrNull() ?: continue
            if (isHtmlOrAuthError(body, resp) || resp.code != 200) continue
            val raw = runCatching { JSONTokener(body).nextValue() }.getOrNull()
            if (raw is JSONObject) return AppDataResult(JsonDyn.parse(body), raw)
        }
        return null
    }

    // The four heuristics below walk the RAW parsed app-data tree (org.json, not JsonDyn) —
    // exactly like the iOS source, which also bypasses its JSON wrapper here and walks
    // `json.raw` (`[String: Any]`/`[Any]`) directly, since the shape is unknown ahead of time.

    private val parentRoles = setOf(
        "PARENT", "GUARDIAN", "LEGAL_GUARDIAN", "LEGALGUARDIAN", "ERZIEHUNGSBERECHTIGT", "PARENT_ROLE", "PERSONTYPE_PARENT",
    )

    private fun detectParentRaw(root: Any?): Boolean {
        var found = false
        fun collect(node: Any?, depth: Int) {
            if (depth > 8) return
            when (node) {
                is String -> if (node.uppercase() in parentRoles) found = true
                is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), depth + 1)
                is JSONObject -> for (key in node.keys()) collect(node.opt(key), depth + 1)
            }
        }
        collect(root, 0)
        return found
    }

    private fun rawInt(value: Any?): Int? = when (value) {
        is Number -> value.toInt()
        is String -> value.toIntOrNull()
        else -> null
    }

    /** Child student id from the app data (`user.students[0].id`) — mirrors the frontend. */
    private fun extractChildStudentIdRaw(root: Any?, exclude: Int): Int? {
        val fromArrays = mutableListOf<Int>()
        val heuristic = mutableListOf<Int>()
        fun walk(node: Any?, depth: Int) {
            if (depth > 8) return
            if (node is JSONArray) {
                for (i in 0 until node.length()) walk(node.opt(i), depth + 1)
                return
            }
            val o = node as? JSONObject ?: return
            for (key in listOf("students", "children")) {
                val arr = o.opt(key) as? JSONArray ?: continue
                for (i in 0 until arr.length()) {
                    val c = arr.opt(i) as? JSONObject ?: continue
                    val id = rawInt(c.opt("id")) ?: rawInt(c.opt("studentId")) ?: rawInt(c.opt("personId"))
                    if (id != null && id > 0 && id != exclude) fromArrays.add(id)
                }
            }
            val hasKlasse = o.has("klasseId") || o.has("klasse") || o.has("className") || o.has("klasseName")
            val typeStr = (o.opt("type") ?: o.opt("role") ?: o.opt("personType"))?.toString()?.uppercase() ?: ""
            if (hasKlasse || typeStr.contains("STUDENT") || typeStr == "5") {
                val id = rawInt(o.opt("id")) ?: rawInt(o.opt("personId")) ?: rawInt(o.opt("studentId"))
                if (id != null && id > 0 && id != exclude) heuristic.add(id)
            }
            for (key in o.keys()) walk(o.opt(key), depth + 1)
        }
        walk(root, 0)
        return fromArrays.firstOrNull() ?: heuristic.firstOrNull()
    }

    /**
     * Class id from the app data (for students whose `authenticate`/`getStudents` doesn't carry
     * a klasseId). Priority: the signed-in student's own class, then `students[].klasseId`, then
     * any `klasse.id`/`klasseId`.
     */
    private fun extractKlasseIdRaw(root: Any?, personId: Int): Int? {
        val matched = mutableListOf<Int>()
        val fromStudents = mutableListOf<Int>()
        val fromKlassenArr = mutableListOf<Int>()
        val fromKlasseObj = mutableListOf<Int>()
        val fromField = mutableListOf<Int>()

        fun klasseIdOf(o: JSONObject): Int? {
            (o.opt("klasse") as? JSONObject)?.let { k -> rawInt(k.opt("id"))?.let { if (it > 0) return it } }
            rawInt(o.opt("klasseId"))?.let { if (it > 0) return it }
            return null
        }

        fun walk(node: Any?, depth: Int) {
            if (depth > 8) return
            if (node is JSONArray) {
                for (i in 0 until node.length()) walk(node.opt(i), depth + 1)
                return
            }
            val o = node as? JSONObject ?: return
            val oid = rawInt(o.opt("id")) ?: rawInt(o.opt("personId")) ?: rawInt(o.opt("studentId"))
            if (oid != null && oid == personId) klasseIdOf(o)?.let { matched.add(it) }
            (o.opt("students") as? JSONArray)?.let { arr ->
                for (i in 0 until arr.length()) (arr.opt(i) as? JSONObject)?.let { c -> klasseIdOf(c)?.let { fromStudents.add(it) } }
            }
            (o.opt("children") as? JSONArray)?.let { arr ->
                for (i in 0 until arr.length()) (arr.opt(i) as? JSONObject)?.let { c -> klasseIdOf(c)?.let { fromStudents.add(it) } }
            }
            (o.opt("klassen") as? JSONArray)?.let { arr ->
                for (i in 0 until arr.length()) (arr.opt(i) as? JSONObject)?.let { k -> rawInt(k.opt("id"))?.let { if (it > 0) fromKlassenArr.add(it) } }
            }
            (o.opt("klasse") as? JSONObject)?.let { k -> rawInt(k.opt("id"))?.let { if (it > 0) fromKlasseObj.add(it) } }
            rawInt(o.opt("klasseId"))?.let { if (it > 0) fromField.add(it) }
            for (key in o.keys()) walk(o.opt(key), depth + 1)
        }
        walk(root, 0)
        return matched.firstOrNull() ?: fromStudents.firstOrNull() ?: fromKlassenArr.firstOrNull()
            ?: fromKlasseObj.firstOrNull() ?: fromField.firstOrNull()
    }

    /**
     * The profile picture URL out of app-data.
     *
     * WebUntis puts it in one of several places depending on the endpoint that answered and the
     * account type, so this walks the same candidate list the web frontend's `extractImageUrl`
     * does (`lib/untis-permissions.ts`) rather than checking only `user.person.imageUrl` —
     * which is what it used to do, and why guardian accounts and the `v2/app/data` shape never
     * produced a picture. For a guardian the child's image comes first: that's whose data the
     * app is showing.
     */
    private fun extractImageUrl(appData: JsonDyn, isParent: Boolean): String? {
        val roots = listOf(appData["data"]["user"], appData["user"], appData["data"], appData)
        val candidates = buildList {
            if (isParent) {
                roots.forEach { root -> add(root["students"].array.firstOrNull()?.get("imageUrl")?.string) }
            }
            roots.forEach { root ->
                add(root["person"]["imageUrl"].string)
                add(root["imageUrl"].string)
            }
        }
        return candidates.firstNotNullOfOrNull { it?.trim()?.takeIf(String::isNotEmpty) }
    }

    /**
     * Fetches app-data purely to resolve the signed-in user's profile picture, for sessions that
     * never needed app-data during login (a student whose class resolved from `getStudents`
     * doesn't, which is the common case — so the picture was simply never looked up).
     *
     * Deliberately lazy and on-demand rather than folded into login: app-data is several
     * endpoints, and most of the time nothing needs it. The web frontend takes the same approach
     * in its `profile-image` route, which falls back to a live app-data lookup when the value
     * captured at login is missing.
     */
    suspend fun fetchProfileImageUrl(session: UserSession): String? {
        val appData = runCatching { fetchAppData(session) }.getOrNull() ?: return null
        return extractImageUrl(appData.json, session.isParent)
    }

    private fun extractChildName(appData: JsonDyn): String? {
        val lists = listOf(
            appData["data"]["user"]["students"].array,
            appData["user"]["students"].array,
            appData["students"].array,
        )
        val first = lists.firstOrNull { it.isNotEmpty() }?.first() ?: return null
        val name = first["displayName"].string ?: return null
        return name.trim().takeIf { it.isNotEmpty() }
    }

    // ── School years / dates ─────────────────────────────────────────────────

    private fun currentSchoolYear(): Int {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        return if (today.monthNumber >= 9) today.year else today.year - 1
    }

    private fun todayCompactInt(): Int {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        return today.year * 10000 + today.monthNumber * 100 + today.dayOfMonth
    }

    /** Schoolyear id; -1 = session expired, null = not found. `year` null = the current one. */
    private suspend fun schoolyearId(year: Int?, s: UserSession): Int? {
        fun isAuthErr(j: JsonDyn): Boolean {
            val code = j["error"]["code"].int
            return code == -8500 || code == -8501 || code == -8520
        }
        if (year != null) {
            val j = rpcFull("getSchoolyears", s, "sy2")
            if (isAuthErr(j)) return -1
            for (y in j["result"].array) {
                if ((y["startDate"].int ?: 0) / 10000 == year) y["id"].int?.let { return it }
            }
            return null
        }
        val cur = rpcFull("getCurrentSchoolyear", s, "sy")
        if (isAuthErr(cur)) return -1
        cur["result"]["id"].int?.let { return it }
        val all = rpcFull("getSchoolyears", s, "sy2")
        if (isAuthErr(all)) return -1
        val years = all["result"].array
        val now = todayCompactInt()
        val current = years.firstOrNull { (it["startDate"].int ?: 0) <= now && (it["endDate"].int ?: 0) >= now }
        if (current != null) return current["id"].int
        return years.sortedByDescending { it["startDate"].int ?: 0 }.firstOrNull()?.get("id")?.int
    }

    // ── Stundenplan / Timetable ──────────────────────────────────────────────

    /**
     * Week of entries starting [weekStartYyyyMMdd] (ISO `yyyy-MM-dd`); end = start + 5 days,
     * matching the frontend/iOS week window (Mon–Sat).
     */
    suspend fun timetable(session: UserSession, studentId: Int, weekStartYyyyMMdd: String): List<TimetableEntry> {
        if (session.bearerToken.isEmpty()) return emptyList()
        val start = LocalDate.parse(weekStartYyyyMMdd)
        val end = start.plus(DatePeriod(days = 5)).toString()
        val url = "${Config.untisBase}${Config.Routes.timetable}?start=$weekStartYyyyMMdd&end=$end&format=1" +
            "&resourceType=STUDENT&resources=$studentId&periodTypes=&timetableType=MY_TIMETABLE&layout=START_TIME"
        val (body, resp) = get(url, session)
        if (isHtmlOrAuthError(body, resp)) throw AppError.sessionExpired
        if (resp.code != 200) throw AppError("Stundenplan nicht ladbar (HTTP ${resp.code}).")
        return parseTimetable(JsonDyn.parse(body))
    }

    private fun parseTimetable(json: JsonDyn): List<TimetableEntry> {
        val entries = mutableListOf<TimetableEntry>()
        for (day in json["days"].array) {
            val dateStr = day["date"].string ?: continue
            val dateNum = dateStr.replace("-", "").toIntOrNull() ?: 0
            for (ge in day["gridEntries"].array) {
                val startStr = ge["duration"]["start"].string ?: continue
                val endStr = ge["duration"]["end"].string ?: continue
                fun hm(iso: String): Int {
                    val parts = iso.split("T")
                    if (parts.size != 2) return 0
                    val t = parts[1].split(":")
                    val h = t.getOrNull(0)?.toIntOrNull() ?: 0
                    val m = t.getOrNull(1)?.toIntOrNull() ?: 0
                    return h * 100 + m
                }
                val startTime = hm(startStr)
                val endTime = hm(endStr)

                val pos1 = ge["position1"].array
                val pos2 = ge["position2"].array
                val pos3 = ge["position3"].array
                val activeTeachers = pos1.mapNotNull { it["current"]["displayName"].string }.filter { it.isNotEmpty() }
                val activeTeachersLong = pos1.mapNotNull { it["current"]["longName"].string ?: it["current"]["displayName"].string }
                    .filter { it.isNotEmpty() }
                val removedTeachers = pos1.mapNotNull { it["removed"]["displayName"].string }.filter { it.isNotEmpty() }
                val removedTeachersLong = pos1.mapNotNull { it["removed"]["longName"].string ?: it["removed"]["displayName"].string }
                    .filter { it.isNotEmpty() }
                val activeSub = pos2.firstOrNull { it["current"].exists }?.get("current")
                val removedSub = pos2.firstOrNull { it["removed"].exists }?.get("removed")
                val activeRooms = pos3.mapNotNull { it["current"]["displayName"].string }.filter { it.isNotEmpty() }
                val removedRooms = pos3.mapNotNull { it["removed"]["displayName"].string }.filter { it.isNotEmpty() }

                val type = ge["type"].string ?: ""
                val status = ge["status"].string ?: ""
                val isExam = type == "EXAM"
                val isCancelled = status == "CANCELLED"
                val isChanged = status == "CHANGED"
                val isSubstitution = isChanged && removedTeachers.isNotEmpty()

                entries.add(
                    TimetableEntry(
                        id = ge["ids"][0].int ?: 0,
                        lessonId = ge["ids"][0].int ?: 0,
                        date = dateNum, startTime = startTime, endTime = endTime,
                        subjectName = activeSub?.get("shortName")?.string ?: removedSub?.get("shortName")?.string ?: "",
                        subjectLong = activeSub?.get("longName")?.string ?: removedSub?.get("longName")?.string ?: "",
                        teacherName = activeTeachers.joinToString(", "),
                        teacherLongName = activeTeachersLong.takeIf { it.isNotEmpty() }?.joinToString(", "),
                        roomName = activeRooms.joinToString(", "),
                        isExam = isExam, isCancelled = isCancelled, isSubstitution = isSubstitution,
                        isAdditional = type == "ADDITIONAL",
                        originalSubject = removedSub?.get("shortName")?.string ?: "",
                        originalSubjectLong = removedSub?.get("longName")?.string ?: "",
                        originalTeacher = removedTeachers.joinToString(", "),
                        originalTeacherLong = removedTeachersLong.takeIf { it.isNotEmpty() }?.joinToString(", "),
                        originalRoom = removedRooms.joinToString(", "),
                        note = ge["lessonInfo"].string ?: ge["lessonText"].string,
                        examDescription = ge["exam"]["description"].string,
                    ),
                )
            }
        }
        return entries.sortedWith(compareBy({ it.date }, { it.startTime }))
    }

    // ── Noten / Grades ────────────────────────────────────────────────────────

    /** `schoolYear` null = the current school year (resolved via getCurrentSchoolyear). */
    suspend fun grades(session: UserSession, studentId: Int, schoolYear: Int? = null): List<SubjectGrades> {
        if (session.bearerToken.isEmpty()) return emptyList()
        val syId = schoolyearId(schoolYear, session) ?: throw AppError("Schuljahr nicht gefunden.")
        if (syId == -1) throw AppError.sessionExpired

        val listUrl = "${Config.untisBase}${Config.Routes.gradeList}?studentId=$studentId&schoolyearId=$syId"
        val (body, resp) = get(listUrl, session)
        if (isHtmlOrAuthError(body, resp)) throw AppError.sessionExpired
        if (resp.code != 200) throw AppError("Noten nicht ladbar (HTTP ${resp.code}).")

        val listJson = JsonDyn.parse(body)
        var lessons = listJson["data"]["lessons"].array
        if (lessons.isEmpty()) lessons = listJson["data"]["lesson"].array
        if (lessons.isEmpty()) return emptyList()

        val subjects = coroutineScope {
            lessons.map { lesson ->
                async {
                    val lessonId = lesson["id"].int ?: 0
                    val subjectName = lesson["subjects"].string ?: lesson["subject"].string ?: ""
                    val teacherName = lesson["teachers"].string ?: lesson["teacher"].string ?: ""
                    runCatching { gradesForLesson(session, studentId, lessonId, subjectName, teacherName) }.getOrNull()
                }
            }.awaitAll()
        }.filterNotNull()

        // Subjects with NO grades yet are kept on purpose. `grade/grading/list` returns every
        // lesson the student is enrolled in for the year, so they're all known from the start of
        // the school year — dropping the gradeless ones (which the web frontend's `parseGrades`
        // does) means the Noten screen looks empty in September even though the subject list is
        // right there. A deliberate divergence from the web: the screen shows every subject and
        // marks the ones without grades as "–".
        return subjects.filter { it.subjectName.isNotEmpty() }
            .sortedBy { it.subjectName.lowercase() }
    }

    private suspend fun gradesForLesson(
        session: UserSession,
        studentId: Int,
        lessonId: Int,
        subjectName: String,
        teacherName: String,
    ): SubjectGrades? {
        val url = "${Config.untisBase}${Config.Routes.gradeLesson}?studentId=$studentId&lessonId=$lessonId"
        val (body, resp) = get(url, session)
        if (isHtmlOrAuthError(body, resp) || resp.code != 200) return null
        val json = JsonDyn.parse(body)
        val entries = json["data"]["grades"].array.mapNotNull { g ->
            val markValue = g["mark"]["markValue"].double ?: 0.0
            if (markValue <= 0) return@mapNotNull null
            GradeEntry(
                id = g["id"].int ?: 0,
                text = g["text"].string ?: "",
                date = g["date"].int ?: 0,
                markName = g["mark"]["name"].string ?: "",
                markValue = markValue,
                markDisplayValue = g["mark"]["markDisplayValue"].double ?: markValue,
                examType = g["examType"]["longname"].string ?: g["examType"]["name"].string ?: "",
            )
        }
        val vals = entries.map { it.markDisplayValue }.filter { it > 0 }
        val average = if (vals.isEmpty()) 0.0 else vals.sum() / vals.size
        return SubjectGrades(
            lessonId = lessonId, subjectName = subjectName, teacherName = teacherName,
            grades = entries, average = average,
            positiveCount = vals.count { it >= 6 },
            negativeCount = vals.count { it < 6 },
        )
    }

    // ── Abwesenheiten / Absences ─────────────────────────────────────────────

    suspend fun absences(session: UserSession, studentId: Int, startDateYyyyMMdd: String, endDateYyyyMMdd: String): List<AbsenceEntry> {
        if (session.bearerToken.isEmpty()) return emptyList()
        val pageSize = 100
        val baseUrl = "${Config.untisBase}${Config.Routes.absences}?studentId=$studentId&startDate=$startDateYyyyMMdd" +
            "&endDate=$endDateYyyyMMdd&excuseStatusId=-1&limit=$pageSize&pageSize=$pageSize"
        val all = mutableListOf<JsonDyn>()
        var page = 0
        var totalCount: Int? = null
        while (page < 50) {
            val url = if (page == 0) baseUrl else "$baseUrl&page=$page"
            val (body, resp) = get(url, session)
            if (isHtmlOrAuthError(body, resp)) {
                if (page == 0) throw AppError.sessionExpired
                break
            }
            if (resp.code != 200) {
                if (page == 0) throw AppError("Abwesenheiten nicht ladbar (HTTP ${resp.code}).")
                break
            }
            val parsed = JsonDyn.parse(body)
            val inner = if (parsed["data"].exists) parsed["data"] else parsed
            val pageItems = inner["absences"].array
            if (totalCount == null) totalCount = inner["count"].int ?: inner["totalCount"].int ?: inner["totalElements"].int
            if (pageItems.isEmpty()) break
            val firstId = pageItems.firstOrNull()?.get("id")?.int
            if (page > 0 && all.any { it["id"].int == firstId }) break
            all.addAll(pageItems)
            val tc = totalCount
            if (pageItems.size < pageSize || (tc != null && all.size >= tc)) break
            page++
        }
        return all.map(::parseAbsence)
    }

    private fun parseAbsence(item: JsonDyn): AbsenceEntry {
        val startTime = item["startTime"].int ?: 0
        val endTime = item["endTime"].int ?: 0
        val rawHours = item["hours"].int ?: item["lessonHours"].int
        val hours = if (rawHours != null && rawHours > 0) {
            rawHours
        } else {
            val s = minutesOfDayTime(startTime)
            val e = minutesOfDayTime(endTime)
            if (e > s) maxOf(1, Math.round((e - s) / 50.0).toInt()) else 1
        }
        var teacher = item["teacherName"].string ?: item["teacher"].string
        if (teacher == null) {
            val fn = item["teacherFirstname"].string
            if (fn != null) teacher = "$fn ${item["teacherLastname"].string ?: ""}"
        }
        return AbsenceEntry(
            id = item["id"].int ?: 0,
            startDate = item["startDate"].int ?: 0,
            endDate = item["endDate"].int ?: 0,
            startTime = startTime, endTime = endTime,
            isExcused = item["isExcused"].bool ?: false,
            reasonName = item["reasonName"].string ?: item["reason"].string,
            absenceType = item["absenceType"].string,
            hours = hours,
            note = item["text"].string ?: item["note"].string,
            excuseNote = item["excuseNote"].string,
            teacherName = teacher,
            subjectName = item["subject"].string ?: item["subjectName"].string ?: item["subjectShortName"].string,
        )
    }

    private fun minutesOfDayTime(t: Int): Int {
        val s = t.toString().padStart(4, '0')
        return (s.substring(0, 2).toIntOrNull() ?: 0) * 60 + (s.substring(2, 4).toIntOrNull() ?: 0)
    }

    // ── Nachrichten / Messages ────────────────────────────────────────────────

    suspend fun messages(session: UserSession, folder: MessageFolder): List<MessagePreview> {
        if (session.bearerToken.isEmpty()) return emptyList()
        val path = when (folder) {
            MessageFolder.Inbox -> Config.Routes.messages
            MessageFolder.Sent -> Config.Routes.messagesSent
            MessageFolder.Drafts -> Config.Routes.messagesDrafts
        }
        val url = "${Config.untisBase}$path?pageSize=100&start=0"
        val (body, resp) = get(url, session)
        if (isHtmlOrAuthError(body, resp)) throw AppError.sessionExpired
        if (resp.code != 200) throw AppError("Nachrichten nicht ladbar (HTTP ${resp.code}).")
        return parseMessages(JsonDyn.parse(body))
    }

    private fun parseMessages(json: JsonDyn): List<MessagePreview> {
        val data = json["data"]
        var arr = json["incomingMessages"].array
        if (arr.isEmpty()) arr = json["sentMessages"].array
        if (arr.isEmpty()) arr = json["draftMessages"].array
        if (arr.isEmpty()) arr = json["messages"].array
        if (arr.isEmpty()) arr = data["incomingMessages"].array
        if (arr.isEmpty()) arr = data["sentMessages"].array
        if (arr.isEmpty()) arr = data["draftMessages"].array
        if (arr.isEmpty() && data.isArray) arr = data.array

        return arr.map { m ->
            val sender = m["sender"]
            val recipientLabel = m["recipients"].array
                .mapNotNull { it["displayName"].string ?: it["name"].string }
                .filter { it.isNotEmpty() }
                .joinToString(", ")
            val senderName = sender["displayName"].string
                ?: sender["name"].string
                ?: m["senderName"].string
                ?: (if (recipientLabel.isEmpty()) "Unbekannt" else "An: $recipientLabel")
            val sentDate = m["sentDateTime"].string ?: m["sentDate"].string ?: m["date"].string ?: ""
            val isRead = m["isRead"].bool ?: m["read"].bool ?: m["readFlag"].bool ?: true
            MessagePreview(
                id = m["id"].int ?: 0,
                subject = m["subject"].string ?: "(Kein Betreff)",
                contentPreview = m["contentPreview"].string ?: "",
                senderName = senderName,
                senderId = sender["userId"].int ?: 0,
                sentDate = sentDate,
                isRead = isRead,
                hasAttachments = m["hasAttachments"].bool ?: false,
            )
        }
    }

    suspend fun messageDetail(session: UserSession, id: Int): MessageDetail {
        val url = "${Config.untisBase}${Config.Routes.messageDetail(id)}"
        val (body, resp) = get(url, session)
        if (isHtmlOrAuthError(body, resp)) throw AppError.sessionExpired
        if (resp.code != 200) throw AppError("Nachricht nicht ladbar (HTTP ${resp.code}).")
        val m = JsonDyn.parse(body)
        val sender = m["sender"]
        val senderName = sender["displayName"].string ?: sender["name"].string ?: m["senderName"].string ?: "Unbekannt"
        val attachments = parseAttachments(m)
        return MessageDetail(
            id = m["id"].int ?: id,
            subject = m["subject"].string ?: "(Kein Betreff)",
            senderName = senderName,
            sentDate = m["sentDateTime"].string ?: m["sentDate"].string ?: "",
            body = m["body"].string ?: m["content"].string ?: "",
            attachments = attachments,
        )
    }

    suspend fun markMessageRead(session: UserSession, id: Int) {
        val url = "${Config.untisBase}${Config.Routes.messageMarkRead(id)}"
        runCatching {
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(url).withAuth(session).post("".toRequestBody()).build()
                noCookieClient.newCall(request).execute().close()
            }
        }
    }

    // ── Message attachments ──────────────────────────────────────────────────

    /** A downloaded attachment: the raw bytes plus the type the server labelled them with. */
    class DownloadedAttachment(val name: String, val mimeType: String, val bytes: ByteArray)

    /**
     * Attachments in any of the shapes the MessageCenter reports them: an `attachments` array
     * (or one of its aliases), an object wrapping one, or `storageAttachments` — whose `id` is
     * the storage UUID and which carries no numeric id at all. Same candidate order as the web
     * frontend, because which shape arrives depends on the WebUntis version.
     */
    private fun parseAttachments(node: JsonDyn): List<MessageAttachment> {
        val direct = listOf("attachments", "messageFile", "files", "fileAttachments", "attachment", "items", "content", "data")
            .map { node[it] }.firstOrNull { it.isArray }
            ?: if (node.isArray) node else JsonDyn.empty
        val list = direct.array.map { a ->
            val rawId = a["id"]
            MessageAttachment(
                id = rawId.int ?: a["fileId"].int ?: 0,
                // A non-numeric `id` IS the storage UUID on instances that omit `storageId`.
                storageId = a["storageId"].string
                    ?: rawId.string?.takeIf { it.toIntOrNull() == null }
                    ?: "",
                name = a["name"].string ?: a["fileName"].string ?: a["originalName"].string
                    ?: a["src"].string ?: "Anhang",
                size = a["size"].int ?: a["fileSize"].int ?: 0,
            )
        }
        if (list.isNotEmpty()) return list
        return node["storageAttachments"].array.map { a ->
            MessageAttachment(id = 0, storageId = a["id"].string ?: "", name = a["name"].string ?: "Anhang", size = 0)
        }
    }

    /**
     * Re-asks for a message's attachments when the detail body came back without them (some
     * instances only fill the list on the dedicated endpoint). Best-effort: an empty list means
     * "none found", never an error.
     */
    suspend fun messageAttachments(session: UserSession, id: Int): List<MessageAttachment> {
        for (path in listOf(Config.Routes.messageAttachments(id), Config.Routes.messageDetail(id))) {
            val (body, resp) = runCatching { get("${Config.untisBase}$path", session) }.getOrNull() ?: continue
            if (resp.code != 200 || body.firstOrNull() == '<') continue
            val found = parseAttachments(JsonDyn.parse(body))
            if (found.isNotEmpty()) return found
        }
        return emptyList()
    }

    /**
     * Downloads one attachment, mirroring the web frontend's two strategies in order:
     *  1. ask the MessageCenter for a pre-signed storage URL (`attachmentstorageurl`, keyed on
     *     the storage UUID) and fetch that with the extra headers it hands back, then
     *  2. fall back to the direct `/messages/{id}/attachments/...` candidates.
     *
     * Returns null when every candidate fails or only ever answers with HTML/JSON — that is an
     * error page, not a file.
     */
    suspend fun downloadAttachment(
        session: UserSession,
        messageId: Int,
        attachment: MessageAttachment,
    ): DownloadedAttachment? {
        storageDownload(session, attachment)?.let { return it }
        for (path in Config.Routes.attachmentCandidates(messageId, attachment.storageId, attachment.id)) {
            fetchFile("${Config.untisBase}$path", attachment.name, session = session)?.let { return it }
        }
        return null
    }

    private suspend fun storageDownload(session: UserSession, attachment: MessageAttachment): DownloadedAttachment? {
        if (attachment.storageId.isEmpty()) return null
        val (body, resp) = runCatching {
            get("${Config.untisBase}${Config.Routes.attachmentStorageUrl(attachment.storageId)}", session)
        }.getOrNull() ?: return null
        if (resp.code != 200 || body.firstOrNull() == '<') return null
        val info = JsonDyn.parse(body)
        val downloadUrl = info["downloadUrl"].string ?: return null
        // The storage's own headers come back as {key|value} pairs — some deployments say
        // {name|value}. `host` is skipped: OkHttp sets it from the URL.
        val extra = info["additionalHeaders"].array.mapNotNull { h ->
            val key = h["key"].string ?: h["name"].string ?: return@mapNotNull null
            val value = h["value"].string ?: return@mapNotNull null
            if (key.equals("host", ignoreCase = true)) null else key to value
        }
        return fetchFile(downloadUrl, attachment.name, headers = extra)
    }

    /** GETs a URL expecting a *file* back: an HTML or JSON answer is an error page, not content. */
    private suspend fun fetchFile(
        url: String,
        name: String,
        session: UserSession? = null,
        headers: List<Pair<String, String>> = emptyList(),
    ): DownloadedAttachment? = withContext(Dispatchers.IO) {
        runCatching {
            val builder = Request.Builder().url(url)
            session?.let { builder.withAuth(it) }
            headers.forEach { (key, value) -> builder.header(key, value) }
            val client = noCookieClient.newBuilder().callTimeout(60, TimeUnit.SECONDS).build()
            client.newCall(builder.build()).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val type = response.header("Content-Type").orEmpty()
                if (type.contains("text/html") || type.contains("application/json")) return@use null
                val bytes = response.body?.bytes() ?: return@use null
                if (bytes.isEmpty()) return@use null
                DownloadedAttachment(
                    name = name,
                    mimeType = type.substringBefore(';').trim().ifEmpty { "application/octet-stream" },
                    bytes = bytes,
                )
            }
        }.getOrNull()
    }

    /** Marks every id read in parallel, each failing on its own (the web's `Promise.allSettled`). */
    suspend fun markAllMessagesRead(session: UserSession, ids: List<Int>) {
        if (ids.isEmpty()) return
        coroutineScope { ids.map { id -> async { markMessageRead(session, id) } }.awaitAll() }
    }

    // ── Message writing (MessageCenter) ──────────────────────────────────────

    /** The people this account may write to, grouped by the API into class teachers / others. */
    suspend fun messageRecipients(session: UserSession): List<MessageRecipient> {
        val (body, resp) = get("${Config.untisBase}${Config.Routes.messageRecipients}", session)
        if (isHtmlOrAuthError(body, resp)) throw AppError.sessionExpired
        if (resp.code != 200) return emptyList()
        val seen = mutableSetOf<String>()
        return JsonDyn.parse(body).array.flatMap { group ->
            val type = (group["type"].string ?: "TEACHERS").uppercase()
            group["persons"].array.mapNotNull { person ->
                val id = person["userId"].int ?: person["id"].int ?: return@mapNotNull null
                val name = (person["displayName"].string ?: person["name"].string ?: "").trim()
                if (name.isEmpty() || !seen.add("$type:$id")) return@mapNotNull null
                val tags = person["tags"].array.mapNotNull { it.string }.filter { it.isNotEmpty() }
                MessageRecipient(
                    id = id,
                    type = type,
                    name = name,
                    role = tags.joinToString(", ").ifEmpty { null },
                    isClassTeacher = type == "CLASS_TEACHERS",
                )
            }
        }
    }

    /**
     * Sends a message, or saves it as a draft.
     *
     * Unlike every read endpoint, MessageCenter writes are `multipart/form-data`: a JSON
     * `request` part plus one `attachments` part per file. They also run as a separate service,
     * so they need the `Tenant-Id` (a claim inside the bearer token) and the school-year header
     * the browser sends — without them the service rejects the request. Nothing here is
     * instance-specific; both are derived from the session. Ported from the web frontend's
     * `/api/webuntis/messages/send` route.
     */
    suspend fun sendMessage(
        session: UserSession,
        subject: String,
        content: String,
        recipients: List<MessageRecipient>,
        attachments: List<OutgoingAttachment> = emptyList(),
        asDraft: Boolean = false,
    ) {
        val token = freshMessageToken(session)
        val schoolYearId = runCatching { rpc("getCurrentSchoolyear", session, "sy")["id"].int }.getOrNull()
        val permissions = messagePermissions(session, token, schoolYearId)

        if (attachments.size > permissions.maxFileCount) {
            throw AppError("Maximal ${permissions.maxFileCount} Anhänge erlaubt.")
        }
        attachments.firstOrNull { it.bytes.size > permissions.maxFileSize }?.let {
            throw AppError("„${it.name}“ ist zu groß (max. ${permissions.maxFileSize / (1024 * 1024)} MB pro Datei).")
        }

        val meta = JSONObject().apply {
            put("subject", subject)
            put("content", content)
            put("recipientOption", permissions.recipientOption)
            put("recipientPersons", JSONArray().apply { recipients.forEach { put(JSONObject().put("id", it.id)) } })
            put("recipientGroups", JSONArray())
        }
        val paths = if (asDraft) listOf(Config.Routes.messagesV2Drafts, Config.Routes.messagesDrafts)
        else listOf(Config.Routes.messagesV2, Config.Routes.messages)
        val failure = if (asDraft) {
            "Der Entwurf konnte nicht gespeichert werden. Bitte versuche es später erneut."
        } else {
            "Die Nachricht konnte nicht gesendet werden. Bitte versuche es später erneut."
        }

        for (path in paths) {
            val form = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("request", null, meta.toString().toRequestBody(jsonMediaType))
                .apply {
                    attachments.forEach { file ->
                        addFormDataPart(
                            "attachments",
                            file.name,
                            file.bytes.toRequestBody(file.mimeType.toMediaTypeOrNull()),
                        )
                    }
                }
                .build()
            val (text, code) = withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url("${Config.untisBase}$path")
                    .headers(messageWriteHeaders(session, token, schoolYearId))
                    .post(form)
                    .build()
                val client = noCookieClient.newBuilder().callTimeout(60, TimeUnit.SECONDS).build()
                client.newCall(request).execute().use { it.body?.string().orEmpty() to it.code }
            }
            // 404 only means this path doesn't exist on the instance — try the older one.
            if (code == 404) continue
            if (code == 401 || text.firstOrNull() == '<') throw AppError.sessionExpired
            if (code in 200..299) return
            throw AppError(serverMessage(text) ?: failure)
        }
        throw AppError(failure)
    }

    private class MessagePermissions(val recipientOption: String, val maxFileSize: Long, val maxFileCount: Int)

    /** The MessageCenter config for this account: allowed recipient option + attachment limits. */
    private suspend fun messagePermissions(session: UserSession, token: String, schoolYearId: Int?): MessagePermissions {
        val fallback = MessagePermissions(recipientOption = "TEACHER", maxFileSize = 7_000_000L, maxFileCount = 5)
        val (body, code) = runCatching {
            withContext(Dispatchers.IO) {
                val request = Request.Builder()
                    .url("${Config.untisBase}${Config.Routes.messagePermissions}")
                    .headers(messageWriteHeaders(session, token, schoolYearId))
                    .build()
                noCookieClient.newCall(request).execute().use { it.body?.string().orEmpty() to it.code }
            }
        }.getOrNull() ?: return fallback
        if (code != 200 || body.firstOrNull() == '<') return fallback
        val json = JsonDyn.parse(body)
        return MessagePermissions(
            recipientOption = json["recipientOptions"].array.firstOrNull()?.string ?: fallback.recipientOption,
            maxFileSize = json["maxFileSize"].double?.toLong() ?: fallback.maxFileSize,
            maxFileCount = json["maxFileCount"].int ?: fallback.maxFileCount,
        )
    }

    /** Headers for a MessageCenter write — deliberately without `Content-Type`, so OkHttp can
     * set the multipart boundary itself. */
    private fun messageWriteHeaders(session: UserSession, token: String, schoolYearId: Int?): Headers =
        Headers.Builder().apply {
            add("Cookie", cookieHeader(session.sessionId))
            add("Accept", "application/json")
            if (token.isNotEmpty()) add("Authorization", "Bearer $token")
            tenantIdOf(token, session.bearerToken)?.let { add("Tenant-Id", it) }
            schoolYearId?.let { add("X-Webuntis-Api-School-Year-Id", it.toString()) }
        }.build()

    /** WebUntis issues short-lived JWTs and the web client refreshes before every write; falls
     * back to the token stored at login when the refresh is unavailable. */
    private suspend fun freshMessageToken(session: UserSession): String {
        val (body, resp) = runCatching { get("${Config.untisBase}${Config.Routes.token}", session) }.getOrNull()
            ?: return session.bearerToken
        if (resp.code != 200 || body.firstOrNull() == '<') return session.bearerToken
        val trimmed = body.trim()
        if (trimmed.startsWith("{")) {
            val json = JsonDyn.parse(trimmed)
            return json["accessToken"].string ?: json["token"].string ?: json["access_token"].string
                ?: json["jwt"].string ?: session.bearerToken
        }
        return trimmed.trim('"').ifEmpty { session.bearerToken }
    }

    /** The `Tenant-Id` a write needs, read from the bearer token's own `tenant_id` claim (no
     * verification — this only reads a claim the session already carries). */
    private fun tenantIdOf(vararg tokens: String): String? {
        for (token in tokens) {
            val payload = token.split('.').getOrNull(1) ?: continue
            val decoded = runCatching {
                val flags = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
                String(Base64.decode(payload, flags), Charsets.UTF_8)
            }.getOrNull() ?: continue
            val claim = JsonDyn.parse(decoded)["tenant_id"]
            val value = claim.string ?: claim.int?.toString()
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    /** WebUntis' own German error text, so a business rule ("nur an eine einzige Lehrkraft
     * senden") reaches the user instead of a generic failure. */
    private fun serverMessage(body: String): String? {
        val json = JsonDyn.parse(body)
        json["errorMessage"].string?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        return json["validationErrors"].array
            .mapNotNull { it["errorMessage"].string }
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .takeIf { it.isNotBlank() }
    }

    // ── Klassenbuch / Classreg events ────────────────────────────────────────

    /** `year` null = the current school year. */
    suspend fun classregEvents(session: UserSession, studentId: Int, year: Int? = null): List<ClassregEvent> {
        if (session.bearerToken.isEmpty()) return emptyList()
        val y = year ?: currentSchoolYear()
        val url = "${Config.untisBase}${Config.Routes.classregEvents}?startDate=${y}0908&endDate=${y + 1}0612&studentId=$studentId"
        val (body, resp) = get(url, session)
        if (isHtmlOrAuthError(body, resp)) throw AppError.sessionExpired
        if (resp.code != 200) throw AppError("Klassenbuch nicht ladbar (HTTP ${resp.code}).")
        val json = JsonDyn.parse(body)
        return json["data"]["rows"].array.map { r ->
            ClassregEvent(
                id = r["id"].int ?: 0,
                subjectName = r["subjectName"].string ?: "",
                creatorName = r["creatorName"].string ?: "",
                createDate = r["createDate"].int ?: 0,
                eventReasonName = r["eventReasonName"].string ?: "",
                categoryName = r["categoryName"].string ?: "",
                text = r["text"].string ?: "",
            )
        }
    }
}
