package dev.plattnericus.pokyh.core.widgets

import kotlinx.serialization.Serializable

/**
 * Compact, disk-cacheable slices of app data for the home-screen widgets — Android port of iOS'
 * `SharedStore`/`WidgetBridge` snapshot structs (SharedKit.swift). iOS needs these because a
 * widget extension runs in a separate sandboxed process reachable only via an App-Group file;
 * an Android widget's `GlanceAppWidgetReceiver` runs in this app's OWN process, so the snapshot
 * here is purely about keeping the widget's data read cheap and network-free — not process
 * isolation — read straight back out of [dev.plattnericus.pokyh.data.storage.DiskCache].
 */

@Serializable
data class LessonSnapshot(
    val id: Int,
    val subject: String,
    val room: String,
    val teacher: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val isCancelled: Boolean,
    val isExam: Boolean,
)

@Serializable
data class TimetableSnapshot(
    val generatedAtEpochMs: Long = 0L,
    val lessons: List<LessonSnapshot> = emptyList(),
) {
    companion object {
        val EMPTY = TimetableSnapshot()
    }
}

@Serializable
data class GradeItemSnapshot(
    val id: Int,
    val subject: String,
    val value: Double,
    val dateNum: Int,
)

@Serializable
data class GradesSnapshot(
    val generatedAtEpochMs: Long = 0L,
    val average: Double = 0.0,
    val positive: Int = 0,
    val negative: Int = 0,
    val recent: List<GradeItemSnapshot> = emptyList(),
) {
    val hasData: Boolean get() = recent.isNotEmpty()

    companion object {
        val EMPTY = GradesSnapshot()
    }
}
