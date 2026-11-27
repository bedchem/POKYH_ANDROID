package dev.plattnericus.pokyh.data.backend

import dev.plattnericus.pokyh.BuildConfig
import dev.plattnericus.pokyh.core.i18n.I18n
import dev.plattnericus.pokyh.core.i18n.Lang
import dev.plattnericus.pokyh.data.json.JsonDyn
import dev.plattnericus.pokyh.data.model.AbsenceEntry
import dev.plattnericus.pokyh.data.model.AbsenceMeta
import dev.plattnericus.pokyh.data.model.AbsenceReason
import dev.plattnericus.pokyh.data.model.AppError
import dev.plattnericus.pokyh.data.model.UserSession
import dev.plattnericus.pokyh.data.network.Config
import dev.plattnericus.pokyh.data.untis.UntisClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The absence features whose rules live in pokyh-backend, the same for the website and the apps
 * (`/webuntis/absences/…`): excusing to the class teacher alone, never the deputy; reporting; the
 * sent excuses; the report dialog's reasons; the excuse letter. The app hands over its own
 * WebUntis session (`X-Untis-Session`), which the backend checks with WebUntis
 * (webuntis/appSession.ts): no password leaves the phone, and no second WebUntis session is made.
 *
 * A backend without that support answers in ways that can't have done anything (see
 * [Unsupported]); the caller then does it on the device, as before.
 */
@Singleton
class UntisViaBackend @Inject constructor(private val okHttpClient: OkHttpClient) {

    /** The backend can't do this for the app (an older one, or it couldn't check the session): nothing was done. */
    class Unsupported(reason: String) : Exception(reason)

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private class Answer(val code: Int, val type: String, val bytes: ByteArray) {
        val text: String get() = bytes.toString(Charsets.UTF_8)
        val json: JsonDyn get() = runCatching { JsonDyn.parse(text) }.getOrElse { JsonDyn.parse("{}") }
    }

    private suspend fun call(session: UserSession, token: String, path: String, body: JSONObject? = null): Answer = withContext(Dispatchers.IO) {
        if (session.sessionId.isBlank()) throw Unsupported("no_untis_session")
        val school = session.schoolRef
        val request = Request.Builder()
            .url(Config.backendURL + "/webuntis" + path)
            .header("X-API-Key", BuildConfig.BACKEND_API_KEY)
            .header("Authorization", "Bearer $token")
            .header("X-Untis-Session", session.sessionId)
            .header("X-Untis-School", school.loginName)
            .header("X-Untis-Server", school.server)
            .apply { if (body != null) post(body.toString().toRequestBody(jsonType)) }
            .build()
        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (e: UnknownHostException) {
            throw Unsupported("unreachable") // Never sent.
        } catch (e: ConnectException) {
            throw Unsupported("unreachable")
        } catch (e: IOException) {
            // Sent, maybe: a write must not be made a second time on the device.
            throw AppError.noConnection()
        }
        response.use { Answer(it.code, it.header("Content-Type").orEmpty(), it.body?.bytes() ?: ByteArray(0)) }
    }

    /**
     * The errors every route shares. A lapsed POKYH token is an [AppError] with code 401 (the
     * caller renews it and asks again); a lapsed WebUntis session is [AppError.sessionExpired].
     */
    private fun common(a: Answer) {
        val json = a.json
        val error = json["error"].string
        val code = json["code"].string
        when {
            // A backend from before app sessions looks for the website's cookie: not for the app.
            a.code == 401 && error == "session_expired" && code != "untis_session_invalid" && code != "untis_identity_unverified" ->
                throw Unsupported("no_app_sessions")
            a.code == 401 && error == "session_expired" -> throw AppError.sessionExpired
            a.code == 401 -> throw AppError(error ?: "HTTP 401", httpCode = 401)
            a.code == 404 && (code == null || code == "app_route") -> throw Unsupported("no_route")
            // The backend couldn't check the session with WebUntis (nothing was sent there).
            a.code == 503 && code == "webuntis_unavailable" -> throw Unsupported("unverified")
            a.code == 400 && code == "school_invalid" -> throw Unsupported("school_invalid")
        }
    }

    /** WebUntis sends times as HHMM (750) or as minutes since midnight (470): always HHMM (as [UntisClient]). */
    private fun hhmmOf(n: Int): Int {
        val minutes = if (n > 2359 || n % 100 > 59) n else (n / 100) * 60 + n % 100
        return (minutes / 60) * 100 + minutes % 60
    }

    suspend fun absenceMeta(session: UserSession, token: String): AbsenceMeta {
        val a = call(session, token, "/absences/meta")
        common(a)
        if (a.code != 200) throw AppError(I18n.s.errors.reasons, httpCode = a.code)
        val d = a.json
        return AbsenceMeta(
            reasons = d["reasons"].array.mapNotNull { r ->
                val id = r["id"].int ?: return@mapNotNull null
                r["name"].string?.trim()?.takeIf { it.isNotEmpty() }?.let { AbsenceReason(id, it) }
            },
            defaultReasonId = d["defaultReasonId"].int,
            defaultDate = d["defaultDate"].int,
            defaultStartTime = d["defaultStartTime"].int?.let(::hhmmOf),
            defaultEndTime = d["defaultEndTime"].int?.let(::hhmmOf),
        )
    }

    suspend fun reportAbsence(
        session: UserSession,
        token: String,
        startDate: Int,
        startTime: Int,
        endDate: Int,
        endTime: Int,
        reasonId: Int,
        text: String,
    ) {
        val body = JSONObject()
            .put("startDate", startDate.toString())
            .put("endDate", endDate.toString())
            .put("startTime", startTime.toString())
            .put("endTime", endTime.toString())
            .put("reasonId", reasonId.toString())
            .put("text", text.take(1000))
        val a = call(session, token, "/absences/create", body)
        common(a)
        if (a.code == 200) return
        val e = I18n.s.errors
        val json = a.json
        throw AppError(
            when (json["code"].string) {
                "end_before_start" -> e.endAfterStart
                "not_allowed" -> e.reportNotAllowed
                "conflict" -> (listOf(e.absenceExists) + json["conflicts"].array.mapNotNull { it.string }.take(3)).joinToString(" ")
                // WebUntis' own reason, as it gave it; else ours.
                "create_failed" -> json["error"].string ?: e.reportFailed
                else -> e.reportFailed
            },
            httpCode = a.code,
        )
    }

    private fun sentExcuseOf(node: JsonDyn): UntisClient.SentExcuse? {
        val start = node["start"].double?.toLong() ?: return null
        val end = node["end"].double?.toLong() ?: return null
        return UntisClient.SentExcuse(
            start = start,
            end = end,
            sentAt = node["sentAt"].string.orEmpty(),
            recipients = node["recipients"].array.mapNotNull { it.string },
            messageId = node["messageId"].int?.takeIf { it > 0 },
        )
    }

    /**
     * "Entschuldigen" for absences that are over: one message to the class teacher, never the
     * deputy, in the app's language; for an account that may write to no teacher, the reason and
     * note saved on the absences instead. The backend reads the absences again from WebUntis.
     */
    suspend fun excuse(
        session: UserSession,
        token: String,
        absences: List<AbsenceEntry>,
        reason: AbsenceReason,
        note: String,
        lang: Lang,
    ): UntisClient.ExcuseResult {
        val t = I18n.s.absences
        val open = absences.filter { !it.isExcused }
        if (open.isEmpty()) throw AppError(t.exAlready)
        val body = JSONObject()
            .put("absenceIds", JSONArray(open.map { it.id }))
            .put("reasonId", reason.id.toString())
            .put("text", note.take(1000))
            .put("locale", lang.code)
            .put("startDate", open.minOf { it.startDate }.toString())
            .put("endDate", open.maxOf { it.endDate }.toString())
        val a = call(session, token, "/absences/excuse", body)
        common(a)
        val json = a.json
        if (a.code == 200 && json["ok"].bool == true) {
            return UntisClient.ExcuseResult(
                viaMessage = json["via"].string != "webuntis",
                sentTo = json["sentTo"].array.mapNotNull { it.string },
                failed = json["failed"].array.mapNotNull { it.string },
                excuse = sentExcuseOf(json["excuse"]),
            )
        }
        throw AppError(
            when (json["code"].string) {
                "not_allowed" -> t.exNotAllowed
                "already_excused" -> t.exAlready
                "messages_forbidden" -> t.exForbidden
                "no_class_teacher" -> t.exNoTeacher
                "class_teacher_unclear" -> t.exTeacherUnclear
                "not_found" -> I18n.s.errors.absenceNotFound
                "rate_limited" -> json["error"].string ?: t.exSendFailed
                else -> t.exSendFailed
            },
            httpCode = a.code,
        )
    }

    /**
     * Deletes absences reported by mistake. The backend reads them again from WebUntis and allows
     * only those that have not begun and are not excused.
     */
    suspend fun deleteAbsences(session: UserSession, token: String, absenceIds: List<Int>) {
        val a = call(session, token, "/absences/delete", JSONObject().put("absenceIds", JSONArray(absenceIds)))
        common(a)
        if (a.code == 200) return
        val t = I18n.s.absences
        throw AppError(if (a.json["code"].string == "not_deletable") t.delNotAllowed else t.delFailed, httpCode = a.code)
    }

    /** The excuses sent so far, found again in "Gesendet" by their subject. */
    suspend fun sentExcuses(session: UserSession, token: String): List<UntisClient.SentExcuse> {
        val a = call(session, token, "/absences/excuses")
        common(a)
        if (a.code != 200) throw AppError(I18n.s.common.failedLater, httpCode = a.code)
        return a.json["excuses"].array.mapNotNull(::sentExcuseOf)
    }

    /** The excuse letter WebUntis prints for these absences, as a PDF. */
    suspend fun excuseLetter(session: UserSession, token: String, startDate: Int, endDate: Int, absenceIds: List<Int>, fileName: String): UntisClient.DownloadedAttachment {
        val ids = absenceIds.take(100).joinToString(",")
        val a = call(session, token, "/absences/excuse-letter?start=$startDate&end=$endDate&group=1" + (if (ids.isNotEmpty()) "&ids=$ids" else ""))
        common(a)
        val e = I18n.s.errors
        return when {
            a.code == 202 -> throw AppError(e.letterBusy, httpCode = 202)
            a.code == 200 && a.bytes.isNotEmpty() && !a.type.contains("json") && !a.type.contains("html") ->
                UntisClient.DownloadedAttachment(fileName, a.type.substringBefore(';').ifBlank { "application/pdf" }, a.bytes)
            a.code == 200 -> throw AppError(e.letterLoadFailed, httpCode = a.code)
            else -> throw AppError(e.letterFailed, httpCode = a.code)
        }
    }
}
