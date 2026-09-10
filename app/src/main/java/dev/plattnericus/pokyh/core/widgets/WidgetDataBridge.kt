package dev.plattnericus.pokyh.core.widgets

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.plattnericus.pokyh.core.util.toLocalDate
import dev.plattnericus.pokyh.data.model.SubjectGrades
import dev.plattnericus.pokyh.data.model.TimetableEntry
import dev.plattnericus.pokyh.data.storage.DiskCache
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant

/**
 * `WidgetBridge.swift`, ported — publishes a trimmed snapshot for the home-screen widgets to
 * [DiskCache] and immediately nudges Glance to redraw, so a widget reflects what's on screen
 * within the app rather than waiting for its own periodic update tick.
 */
@Singleton
class WidgetDataBridge @Inject constructor(
    @ApplicationContext private val context: Context,
    private val diskCache: DiskCache,
) {

    /** Called from [dev.plattnericus.pokyh.ui.timetable.TimetableViewModel] whenever the current
     * (offset 0) week reloads — `WidgetBridge.publish`: keeps only lessons ending in the future,
     * capped at 12. */
    suspend fun publishTimetable(entries: List<TimetableEntry>) {
        val now = Clock.System.now().toEpochMilliseconds()
        val lessons = entries
            .map {
                LessonSnapshot(
                    id = it.id,
                    subject = it.subjectName,
                    room = it.roomName,
                    teacher = it.teacherName,
                    startEpochMs = epochMillis(it.date, it.startTime),
                    endEpochMs = epochMillis(it.date, it.endTime),
                    isCancelled = it.isCancelled,
                    isExam = it.isExam,
                )
            }
            .filter { it.endEpochMs > now }
            .sortedBy { it.startEpochMs }
            .take(12)
        diskCache.write(TIMETABLE_KEY, TimetableSnapshot(now, lessons), TimetableSnapshot.serializer())
        NextLessonWidget().updateAll(context)
    }

    /** Called from [dev.plattnericus.pokyh.ui.grades.GradesViewModel] whenever grades reload —
     * `WidgetBridge.publishGrades`: overall average across every raw grade value (not a mean of
     * per-subject averages) + the 8 most recently entered grades. */
    suspend fun publishGrades(subjects: List<SubjectGrades>) {
        val now = Clock.System.now().toEpochMilliseconds()
        val allValues = subjects.flatMap { s -> s.grades.map { it.markDisplayValue } }.filter { it > 0 }
        val average = if (allValues.isEmpty()) 0.0 else (allValues.sum() / allValues.size * 100).roundToInt() / 100.0
        val recent = subjects
            .flatMap { s -> s.grades.filter { it.markDisplayValue > 0 }.map { g -> s.subjectName to g } }
            .sortedByDescending { (_, g) -> g.id }
            .take(8)
            .map { (subject, g) -> GradeItemSnapshot(g.id, subject, g.markDisplayValue, g.date) }
        val snapshot = GradesSnapshot(
            generatedAtEpochMs = now,
            average = average,
            positive = allValues.count { it >= 6.0 },
            negative = allValues.count { it < 6.0 },
            recent = recent,
        )
        diskCache.write(GRADES_KEY, snapshot, GradesSnapshot.serializer())
        GradesWidget().updateAll(context)
    }

    suspend fun readTimetable(): TimetableSnapshot = diskCache.read(TIMETABLE_KEY, TimetableSnapshot.serializer()) ?: TimetableSnapshot.EMPTY
    suspend fun readGrades(): GradesSnapshot = diskCache.read(GRADES_KEY, GradesSnapshot.serializer()) ?: GradesSnapshot.EMPTY

    private fun epochMillis(dateNum: Int, hhmm: Int): Long {
        val date = dateNum.toLocalDate()
        val ldt = LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, hhmm / 100, hhmm % 100)
        return ldt.toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    }

    private companion object {
        const val TIMETABLE_KEY = "widget-timetable"
        const val GRADES_KEY = "widget-grades"
    }
}
