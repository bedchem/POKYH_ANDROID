package dev.plattnericus.pokyh.data.model

import kotlinx.serialization.Serializable

/**
 * Domain models mirroring `Models.swift`. These are built manually from [dev.plattnericus.
 * pokyh.data.json.JsonDyn] by the WebUntis client (matching the messy real-world WebUntis
 * JSON) — deliberately NOT `@Serializable`. Backend DTOs (safe to decode directly) live in
 * `BackendDtos.kt`.
 */

// ── Session ──────────────────────────────────────────────────────────────

// UserSession is our own clean, already-normalized domain object (not decoded directly from
// WebUntis's wire JSON — UntisClient builds it manually field-by-field), so it's safe to make
// @Serializable purely for AppState's own offline DiskCache round-trip.
@Serializable
data class UserSession(
    var sessionId: String,
    var bearerToken: String,
    var studentId: Int,
    var klasseId: Int,
    var klasseName: String,
    var username: String,
    var personName: String? = null,
    var personType: Int? = null,
    var isParent: Boolean,
    /** POKYH backend tokens (for todos / reminders / class). */
    var apiToken: String? = null,
    var apiRefresh: String? = null,
    var stableUid: String? = null,
    var classId: String? = null,
    /** Profile image URL from the WebUntis app data (`user.person.imageUrl`). */
    var imageUrl: String? = null,
) {
    /** Does this session have a linked WebUntis account (timetable/grades)?
     * Pure POKYH backend accounts (login fallback) have `studentId == 0`. Also applies
     * to the token-less offline session (timetable then comes from cache). */
    val hasUntis: Boolean get() = studentId > 0

    /** WebUntis `personType` 5 = student. Parents are excluded. If `personType` is absent,
     * an own class context (klasseId > 0, not a parent) counts as a student indicator. */
    val isStudent: Boolean
        get() {
            if (isParent) return false
            personType?.let { return it == 5 }
            return klasseId > 0
        }
}

// ── Saved accounts (multi-user + Face ID) ───────────────────────────────

@Serializable
data class SavedAccount(
    var username: String, // serves as ID & keystore key
    var displayName: String, // class name or username
    var nickname: String? = null, // optional, locally assigned nickname
    var imageUrl: String? = null, // profile image URL (WebUntis) for the account list
) {
    val id: String get() = username

    /** Primary display name: nickname if present, otherwise username. */
    val title: String
        get() {
            val n = nickname?.trim()
            if (!n.isNullOrEmpty()) return n
            return username
        }
}

// ── Timetable ────────────────────────────────────────────────────────────

data class TimetableEntry(
    var id: Int,
    var lessonId: Int,
    var date: Int, // YYYYMMDD
    var startTime: Int, // HHMM
    var endTime: Int, // HHMM
    var subjectName: String,
    var subjectLong: String,
    var teacherName: String,
    var teacherLongName: String? = null,
    var roomName: String,
    var isExam: Boolean,
    var isCancelled: Boolean,
    var isSubstitution: Boolean,
    var isAdditional: Boolean,
    var originalSubject: String? = null,
    var originalSubjectLong: String? = null,
    var originalTeacher: String? = null,
    var originalTeacherLong: String? = null,
    var originalRoom: String? = null,
    var note: String? = null,
    var examDescription: String? = null,
)

// ── Grades ───────────────────────────────────────────────────────────────

data class GradeEntry(
    var id: Int,
    var text: String,
    var date: Int,
    var markName: String,
    var markValue: Double,
    var markDisplayValue: Double,
    var examType: String,
)

data class SubjectGrades(
    var lessonId: Int,
    var subjectName: String,
    var teacherName: String,
    var grades: List<GradeEntry>,
    var average: Double,
    var positiveCount: Int,
    var negativeCount: Int,
) {
    val id: Int get() = lessonId
}

// ── Absences ─────────────────────────────────────────────────────────────

data class AbsenceEntry(
    var id: Int,
    var startDate: Int,
    var endDate: Int,
    var startTime: Int,
    var endTime: Int,
    var isExcused: Boolean,
    var reasonName: String? = null,
    var absenceType: String? = null,
    var hours: Int,
    var note: String? = null,
    var excuseNote: String? = null,
    var teacherName: String? = null,
    var subjectName: String? = null,
)

// ── Messages ─────────────────────────────────────────────────────────────

data class MessagePreview(
    var id: Int,
    var subject: String,
    var contentPreview: String,
    var senderName: String,
    var senderId: Int,
    var sentDate: String,
    var isRead: Boolean,
    var hasAttachments: Boolean,
)

data class MessageDetail(
    var id: Int,
    var subject: String,
    var senderName: String,
    var sentDate: String,
    var body: String,
    var attachments: List<MessageAttachment>,
)

data class MessageAttachment(
    var id: String,
    var name: String,
    var size: Int,
)

enum class MessageFolder(val rawValue: String) {
    Inbox("inbox"), Sent("sent"), Drafts("drafts");

    val id: String get() = rawValue

    val label: String
        get() = when (this) {
            Inbox -> "Posteingang"
            Sent -> "Gesendet"
            Drafts -> "Entwürfe"
        }
}

// ── Cafeteria ────────────────────────────────────────────────────────────

data class Dish(
    var id: String,
    var name: String,
    var description: String? = null,
    var category: String,
    var date: String,
    var imageUrl: String? = null,
    var price: Double? = null,
    var allergens: List<String>,
    var tags: List<String>,
    var calories: Double? = null,
    var protein: Double? = null,
    var carbs: Double? = null,
    var fat: Double? = null,
)

data class DishRatingsData(
    var ratings: Map<String, Double>, // votes per entry
    var myRating: Int? = null,
) {
    val average: Double
        get() {
            val vals = ratings.values
            return if (vals.isEmpty()) 0.0 else vals.sum() / vals.size
        }

    val count: Int get() = ratings.size
}

// ── Classregister ────────────────────────────────────────────────────────

data class ClassregEvent(
    var id: Int,
    var subjectName: String,
    var creatorName: String,
    var createDate: Int, // YYYYMMDD
    var eventReasonName: String,
    var categoryName: String,
    var text: String,
)
