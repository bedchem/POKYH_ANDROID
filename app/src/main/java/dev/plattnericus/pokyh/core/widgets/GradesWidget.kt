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
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.android.EntryPointAccessors
import dev.plattnericus.pokyh.MainActivity
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.gradeColorSimple

/** `GradesWidget` (POKYHWidget/GradesWidget.swift) — overall average + the most recently entered
 * grades, read straight from [WidgetDataBridge]'s cached [GradesSnapshot]. */
class GradesWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val bridge = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java).widgetDataBridge()
        val snapshot = bridge.readGrades()
        provideContent {
            GlanceTheme {
                GradesContent(snapshot)
            }
        }
    }
}

class GradesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GradesWidget()
}

@Composable
private fun GradesContent(snapshot: GradesSnapshot) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .cornerRadius(20.dp)
            .padding(14.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Noten",
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorProvider(Brand.accent)),
            )
            Spacer(GlanceModifier.defaultWeight())
            if (snapshot.hasData) {
                Text(
                    Fmt.num(snapshot.average),
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ColorProvider(gradeColorSimple(snapshot.average))),
                )
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        if (!snapshot.hasData) {
            Text(
                "Keine Noten verfügbar",
                style = TextStyle(fontSize = 14.sp, color = GlanceTheme.colors.onSurfaceVariant),
            )
        } else {
            snapshot.recent.take(4).forEachIndexed { index, grade ->
                if (index > 0) Spacer(GlanceModifier.height(6.dp))
                GradeRow(grade)
            }
        }
    }
}

@Composable
private fun GradeRow(grade: GradeItemSnapshot) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            grade.subject,
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(fontSize = 13.sp, color = GlanceTheme.colors.onBackground),
        )
        Text(
            Fmt.num(grade.value),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = ColorProvider(gradeColorSimple(grade.value))),
        )
    }
}
