package dev.plattnericus.pokyh.data.untis

import dev.plattnericus.pokyh.core.util.GradeScales
import dev.plattnericus.pokyh.data.model.SubjectGrades
import dev.plattnericus.pokyh.data.model.UserSession
import dev.plattnericus.pokyh.data.storage.OfflineStore
import kotlinx.serialization.builtins.serializer

/**
 * Works out the account's grading scale from the grades just loaded and makes it the one every
 * screen colours by ([GradeScales.current]): the marks, the school's postcode (its address from
 * the school search at sign-in) and the last day of the school year, as WebUntis dates it (kept
 * on the device, so it holds offline too).
 */
suspend fun UntisClient.updateGradeScale(
    session: UserSession,
    subjects: List<SubjectGrades>,
    schoolYear: Int,
    current: Boolean,
    offlineStore: OfflineStore,
) {
    val yearEnd = runCatching {
        offlineStore.load(
            key = "schoolyear-end-${session.schoolRef.loginName.lowercase()}-$schoolYear",
            serializer = Int.serializer(),
        ) { schoolYearEnd(session, if (current) null else schoolYear) ?: error("no school year end") }.value
    }.getOrNull()
    val marks = subjects.flatMap { s -> s.grades.map { it.markDisplayValue to it.markName } }
    GradeScales.current = GradeScales.infer(marks, GradeScales.postcodeDigits(session.school?.address), yearEnd)
}
