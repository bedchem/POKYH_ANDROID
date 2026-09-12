package dev.plattnericus.pokyh.core.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.GlanceTheme
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.android.EntryPointAccessors
import dev.plattnericus.pokyh.MainActivity
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.DarkPokyhColors
import dev.plattnericus.pokyh.ui.theme.subjectColor
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** `NextLessonWidget`/`TodayScheduleWidget` (POKYHWidget/NextLessonWidget.swift), merged into one
 * — current/next lesson plus a short lookahead, read straight from [WidgetDataBridge]'s cached
 * [TimetableSnapshot]. Tapping the widget opens the app ([MainActivity]), same as iOS's default
 * widget tap target. */
class NextLessonWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val bridge = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java).widgetDataBridge()
        val snapshot = bridge.readTimetable()
        provideContent {
            GlanceTheme {
                NextLessonContent(snapshot)
            }
        }
    }
}

class NextLessonWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextLessonWidget()
}

@Composable
private fun NextLessonContent(snapshot: TimetableSnapshot) {
    val now = Clock.System.now().toEpochMilliseconds()
    val upcoming = snapshot.lessons.filter { it.endEpochMs > now }.sortedBy { it.startEpochMs }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .cornerRadius(20.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text(
            "Nächste Stunde",
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorProvider(Brand.accent)),
        )
        Spacer(GlanceModifier.height(8.dp))
        if (upcoming.isEmpty()) {
            Text(
                "Keine weiteren Stunden",
                style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurfaceVariant),
            )
        } else {
            upcoming.take(3).forEachIndexed { index, lesson ->
                if (index > 0) Spacer(GlanceModifier.height(8.dp))
                LessonRow(lesson, current = index == 0 && lesson.startEpochMs <= now)
            }
        }
    }
}

@Composable
private fun LessonRow(lesson: LessonSnapshot, current: Boolean) {
    // A widget renders outside the app's theme, so it can't read PokyhTheme.colors and has to
    // pick a fixed neutral for "cancelled" — DarkPokyhColors.textTertiary, which is legible on
    // both the light and dark widget backgrounds Glance may give us.
    val barColor = when {
        lesson.isCancelled -> DarkPokyhColors.textTertiary
        lesson.isExam -> Brand.warning
        else -> subjectColor(lesson.subject)
    }
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        Column(modifier = GlanceModifier.width(4.dp).height(34.dp).background(barColor).cornerRadius(2.dp)) {}
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                lesson.subject.ifEmpty { "Veranstaltung" },
                maxLines = 1,
                style = TextStyle(
                    fontSize = if (current) 16.sp else 14.sp,
                    fontWeight = if (current) FontWeight.Bold else FontWeight.Medium,
                    color = GlanceTheme.colors.onBackground,
                ),
            )
            val subtitle = listOf(lesson.teacher, lesson.room).filter { it.isNotEmpty() }.joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(subtitle, maxLines = 1, style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant))
            }
        }
        Text(
            "${epochTime(lesson.startEpochMs)}–${epochTime(lesson.endEpochMs)}",
            style = TextStyle(fontSize = 11.sp, color = GlanceTheme.colors.onSurfaceVariant),
        )
    }
}

private fun epochTime(epochMs: Long): String {
    val local = Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.currentSystemDefault())
    return Fmt.time(local.hour * 100 + local.minute)
}
