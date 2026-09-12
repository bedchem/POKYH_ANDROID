package dev.plattnericus.pokyh.data.network

import android.util.Base64
import dev.plattnericus.pokyh.BuildConfig

/**
 * Central configuration — mirrors `Config.swift`. All URLs and API routes are bundled here
 * in one place. Secrets (API/server key) live in [BuildConfig], populated from
 * `local.properties` at build time.
 */
object Config {
    val backendURL: String = BuildConfig.BACKEND_BASE_URL

    val untisBase: String = BuildConfig.UNTIS_BASE_URL
    val school: String = BuildConfig.UNTIS_SCHOOL
    const val untisClient: String = "pockyh"

    /** schoolname cookie: "_" + base64(school) */
    val schoolCookie: String
        get() = "_" + Base64.encodeToString(school.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    object Routes {
        // WebUntis
        const val jsonRpc = "/jsonrpc.do"
        const val token = "/api/token/new"
        const val timetable = "/api/rest/view/v1/timetable/entries"
        const val gradeList = "/api/classreg/grade/grading/list"
        const val gradeLesson = "/api/classreg/grade/grading/lesson"
        const val absences = "/api/classreg/absences/students"
        const val messages = "/api/rest/view/v1/messages"
        const val messagesSent = "/api/rest/view/v1/messages/sent"
        const val messagesDrafts = "/api/rest/view/v1/messages/drafts"
        const val classregEvents = "/api/classreg/classregevents"

        // MessageCenter writes go to the v2 collection (multipart), with v1 kept as a fallback
        // for older WebUntis instances — mirrors the web frontend's candidate order.
        const val messagesV2 = "/api/rest/view/v2/messages"
        const val messagesV2Drafts = "/api/rest/view/v2/messages/drafts"
        const val messageRecipients = "/api/rest/view/v1/messages/recipients/static/persons"
        const val messagePermissions = "/api/rest/view/v1/messages/permissions"

        val appDataCandidates: List<String> = listOf(
            "/api/rest/view/v1/app/data",
            "/api/app/data",
            "/api/rest/view/v1/users/me/data",
            "/api/rest/view/v2/app/data",
        )

        fun messageDetail(id: Int): String = "$messages/$id"
        fun messageMarkRead(id: Int): String = "$messages/$id/markasread"
        fun messageAttachments(id: Int): String = "$messages/$id/attachments"

        /** Keyed on the attachment's storage UUID, not on the message id. */
        fun attachmentStorageUrl(storageId: String): String = "$messages/$storageId/attachmentstorageurl"

        /** Direct-download candidates, in the order the web frontend tries them. */
        fun attachmentCandidates(messageId: Int, storageId: String, attachmentId: Int): List<String> = buildList {
            if (storageId.isNotEmpty()) {
                add("$messages/$messageId/attachments/$storageId")
                add("$messages/$messageId/attachments/$storageId/content")
                add("$messages/$messageId/storage/$storageId")
            }
            if (attachmentId > 0) {
                add("$messages/$messageId/attachments/$attachmentId")
                add("$messages/$messageId/attachments/$attachmentId/content")
            }
        }
        fun messageFolder(folder: String): String = when (folder) {
            "sent" -> messagesSent
            "drafts" -> messagesDrafts
            else -> messages
        }

        // POKYH Backend
        const val authLogin = "/auth/login"
        const val authRegister = "/auth/register"
        const val authMe = "/auth/me"
        const val dishes = "/dishes"
        const val dishRatings = "/dish-ratings" // + /{id} | /batch
        const val dishComments = "/dish-comments" // + /{id}
        const val classesMine = "/classes/mine"

        fun todos(username: String): String = "/users/$username/todos"
        fun reminders(classId: String): String = "/classes/$classId/reminders"

        // POKYH Backend — SSE (not in Config.swift; backend additionally exposes these)
        const val sseTodos = "/sse/todos"
        fun sseReminders(classId: String): String = "/sse/reminders/$classId"
        fun sseReminderComments(reminderId: String): String = "/sse/reminder-comments/$reminderId"
        fun sseDishRatings(dishId: String): String = "/sse/dish-ratings/$dishId"
        fun sseDishComments(dishId: String): String = "/sse/dish-comments/$dishId"
    }
}
