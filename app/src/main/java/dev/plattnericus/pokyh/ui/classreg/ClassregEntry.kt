package dev.plattnericus.pokyh.ui.classreg

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.plattnericus.pokyh.core.i18n.I18n
import dev.plattnericus.pokyh.data.model.ClassregEvent
import dev.plattnericus.pokyh.ui.theme.DecorativeTone
import dev.plattnericus.pokyh.ui.theme.PokyhDecorative
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.SmoothCornerShape

// One class-register (Klassenbuch) entry and its row, as the web's
// components/class/ClassregEntry.tsx: a calendar tile in the tone of the category, what was noted
// and by whom. Used by the Klassenbuch and the class overview.

/** Misconduct in the blush tone, notes in butter, everything else periwinkle (the web's `categoryTone`). */
@Composable
internal fun classregTone(category: String, reason: String = ""): DecorativeTone {
    val l = "$category $reason".lowercase()
    val dark = PokyhTheme.colors.isDark
    return when {
        Regex("täuschung|betrug|disziplin|discipl|verweis|richiamo|cheat").containsMatchIn(l) -> PokyhDecorative.blush(dark)
        "vermerk" in l || "nota" in l || "note" in l -> PokyhDecorative.butter(dark)
        else -> PokyhDecorative.periwinkle(dark)
    }
}

/** WebUntis gives the time as HHMM, sometimes as minutes since midnight (the web's `entryTime`). */
internal fun entryTime(createTime: Int): String {
    if (createTime <= 0) return ""
    val minutes = if (createTime % 100 > 59) createTime else createTime / 100 * 60 + createTime % 100
    return "%02d:%02d".format(minutes / 60, minutes % 60)
}

private fun dateOf(d: Int): java.time.LocalDate? = runCatching { java.time.LocalDate.of(d / 10000, d / 100 % 100, d % 100) }.getOrNull()

/** One entry (the web's `EntryRow`): the 48dp calendar tile, the subject, the note, who and when, the category. */
@Composable
internal fun ClassregEntryRow(ev: ClassregEvent, first: Boolean) {
    val colors = PokyhTheme.colors
    val tone = classregTone(ev.categoryName, ev.eventReasonName)
    val body = ev.text.ifEmpty { ev.eventReasonName }
    val day = dateOf(ev.createDate)
    Column(Modifier.fillMaxWidth()) {
        // The thin rule between entries, inset 14dp, none above the first.
        if (!first) Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(colors.line))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(tone.fill),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "${day?.dayOfMonth ?: ""}",
                    style = PokyhType.headline.copy(fontSize = 18.sp, lineHeight = 18.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.03).em),
                    color = tone.ink,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    day?.month?.getDisplayName(java.time.format.TextStyle.SHORT, I18n.lang.value.locale)?.replace(".", "")?.uppercase().orEmpty(),
                    style = PokyhType.caption2.copy(fontSize = 10.5.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.04.em),
                    color = tone.ink,
                    modifier = Modifier.alpha(0.8f),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    ev.subjectName.ifEmpty { ev.elementName.ifEmpty { ev.categoryName } },
                    style = PokyhType.headline.copy(fontSize = 15.5.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                )
                if (body.isNotEmpty()) {
                    Text(body, style = PokyhType.callout.copy(fontSize = 14.sp, lineHeight = 18.sp), color = colors.textSecondary, modifier = Modifier.padding(top = 2.dp))
                }
                val by = listOf(ev.creatorName, entryTime(ev.createTime)).filter { it.isNotBlank() }.joinToString(" · ")
                if (by.isNotEmpty()) {
                    Text(by, style = PokyhType.caption.copy(fontSize = 12.5.sp, lineHeight = 16.sp), color = colors.textTertiary, modifier = Modifier.padding(top = 4.dp))
                }
                if (ev.categoryName.isNotEmpty() || ev.eventReasonName.isNotEmpty()) {
                    Column(
                        Modifier.padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).background(tone.fill).padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        if (ev.categoryName.isNotEmpty()) {
                            Text(ev.categoryName, style = PokyhType.caption.copy(fontSize = 11.5.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold), color = tone.ink, modifier = Modifier.alpha(0.75f))
                        }
                        if (ev.eventReasonName.isNotEmpty() && ev.eventReasonName != body) {
                            Text(ev.eventReasonName, style = PokyhType.footnote.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold), color = tone.ink)
                        }
                    }
                }
            }
        }
    }
}

/** The entries on one surface (the web's `ul.squircle rounded-[24px] bg-surface p-2`). */
@Composable
internal fun ClassregEntryList(items: List<ClassregEvent>, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(SmoothCornerShape(24.dp)).background(PokyhTheme.colors.card).padding(8.dp),
    ) {
        items.forEachIndexed { i, ev -> ClassregEntryRow(ev, first = i == 0) }
    }
}

/** The web's `Section` (components/app/Page.tsx): the title at 20 extra bold, an action on the right, 14dp to the content. */
@Composable
internal fun WebSection(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    titleIcon: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (titleIcon != null) titleIcon()
            Text(
                title,
                style = PokyhType.title2.copy(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.035).em),
                color = PokyhTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (action != null) action()
        }
        content()
    }
}

/** The web's page header (components/app/Page.tsx, `PageHeader`): the title at 34, the subtitle under it, the actions below. */
@Composable
internal fun WebPageHeader(title: String, subtitle: String?, modifier: Modifier = Modifier, actions: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(top = 16.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column {
            Text(
                title,
                style = PokyhType.largeTitle.copy(fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.05).em),
                color = PokyhTheme.colors.textPrimary,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = PokyhType.body.copy(fontSize = 15.sp, lineHeight = 24.sp), color = PokyhTheme.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
            }
        }
        if (actions != null) actions()
    }
}
