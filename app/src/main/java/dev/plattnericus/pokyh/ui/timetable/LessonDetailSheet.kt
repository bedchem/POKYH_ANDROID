@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.data.model.TimetableEntry
import dev.plattnericus.pokyh.data.untis.MergedSlot
import dev.plattnericus.pokyh.data.untis.SlotKind
import dev.plattnericus.pokyh.ui.components.TagChip
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.bold
import dev.plattnericus.pokyh.ui.theme.PokyhType.medium
import dev.plattnericus.pokyh.ui.theme.cardSurface
import kotlinx.coroutines.launch

/**
 * `LessonDetailView` (TimetableView.swift), ported as a bottom sheet — `skipPartiallyExpanded =
 * false` so it can rest at a "medium" partial height like iOS's `.presentationDetents([.medium,
 * .large])`, instead of always jumping to full/large like most other sheets in this app.
 */
@Composable
fun LessonDetailSheet(slot: MergedSlot, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()

    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(onDismissRequest = ::dismiss, sheetState = sheetState) {
        LessonDetailContent(slot = slot, onDone = ::dismiss)
    }
}

@Composable
private fun LessonDetailContent(slot: MergedSlot, onDone: () -> Unit) {
    val d = slot.display
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDone) { Text("Fertig", style = PokyhType.body, color = Brand.accent) }
        }

        TagsRow(slot)

        Text(
            headerNameFor(slot),
            style = PokyhType.largeTitle.bold(),
            color = PokyhTheme.colors.textPrimary,
            textDecoration = if (d.isCancelled && slot.replacement == null) TextDecoration.LineThrough else TextDecoration.None,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(PokyhIcons.clock, contentDescription = null, tint = PokyhTheme.colors.textSecondary, modifier = Modifier.size(15.dp))
            Text(
                "${Fmt.time(d.startTime)} – ${Fmt.time(d.endTime)}",
                style = PokyhType.body,
                color = PokyhTheme.colors.textSecondary,
            )
        }

        DetailsCard(d)
        slot.replacement?.let { ReplacementCard(d) }
        d.note?.takeIf { it.isNotEmpty() }?.let { SectionCard("Notiz", it) }
        if (d.isExam) d.examDescription?.takeIf { it.isNotEmpty() }?.let { SectionCard("Prüfungsinhalt", it) }
    }
}

@Composable
private fun TagsRow(slot: MergedSlot) {
    val d = slot.display
    val tags = buildList {
        if (d.isCancelled && slot.replacement == null) add("Entfall" to Brand.danger)
        if (slot.replacement != null) add("Vertretung" to Brand.orange)
        if (d.isExam) add("Prüfung" to Brand.warning)
        if (d.isSubstitution && !d.isCancelled && slot.replacement == null) add("Vertretung" to Brand.orange)
        if (slot.kind == SlotKind.EVENT) add("Veranstaltung" to Brand.accent)
    }
    if (tags.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { (text, color) -> TagChip(text = text, color = color, large = true) }
        }
    }
}

/** `LessonDetailView.headerName` — title favors the active replacement (if any) over the
 * cancelled/original entry [MergedSlot.display] carries. */
private fun headerNameFor(slot: MergedSlot): String {
    slot.replacement?.let { r -> return r.subjectLong.ifEmpty { r.subjectName.ifEmpty { r.note ?: "" } } }
    val d = slot.display
    return d.subjectLong.ifEmpty { d.subjectName.ifEmpty { d.note ?: "Stunde" } }
}

@Composable
private fun DetailsCard(d: TimetableEntry) {
    Column(
        modifier = Modifier.fillMaxWidth().cardSurface(radius = 16.dp).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val teacher = d.teacherLongName ?: d.teacherName
        val origTeacher = d.originalTeacherLong ?: (d.originalTeacher ?: "")
        if (teacher.isNotEmpty() || origTeacher.isNotEmpty()) {
            if (origTeacher.isNotEmpty() && origTeacher != teacher) {
                ChangeRow("Lehrer", origTeacher, teacher, PokyhIcons.person_fill)
            } else {
                DetailRow("Lehrer", teacher.ifEmpty { origTeacher }, PokyhIcons.person_fill)
            }
        }
        val room = d.roomName
        val origRoom = d.originalRoom ?: ""
        if (room.isNotEmpty() || origRoom.isNotEmpty()) {
            if (origRoom.isNotEmpty() && origRoom != room) {
                ChangeRow("Raum", origRoom, room, PokyhIcons.mappin_circle_fill)
            } else {
                DetailRow("Raum", room.ifEmpty { origRoom }, PokyhIcons.mappin_circle_fill)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = Brand.accent, modifier = Modifier.size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label.uppercase(), style = PokyhType.caption2, color = PokyhTheme.colors.textTertiary)
            Text(value, style = PokyhType.subheadline.medium(), color = PokyhTheme.colors.textPrimary)
        }
    }
}

@Composable
private fun ChangeRow(label: String, from: String, to: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, contentDescription = null, tint = Brand.accent, modifier = Modifier.size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label.uppercase(), style = PokyhType.caption2, color = PokyhTheme.colors.textTertiary)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(from, style = PokyhType.subheadline.medium(), color = Brand.danger, textDecoration = TextDecoration.LineThrough)
                Icon(PokyhIcons.arrow_right, contentDescription = null, tint = PokyhTheme.colors.textTertiary, modifier = Modifier.size(12.dp))
                Text(to, style = PokyhType.subheadline.medium(), color = Brand.orange)
            }
        }
    }
}

/** `LessonDetailView.replacementCard` — 1:1 port including its quirk: the card's body always
 * shows the CANCELLED/original entry's subject (`slot.display`, passed in here as `d`), not the
 * replacement — "Statt" ("instead of") is what would normally have been on the timetable. */
@Composable
private fun ReplacementCard(d: TimetableEntry) {
    Column(
        modifier = Modifier.fillMaxWidth().cardSurface(radius = 16.dp).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(PokyhIcons.arrow_left_arrow_right, contentDescription = null, tint = PokyhTheme.colors.textSecondary, modifier = Modifier.size(13.dp))
            Text("Statt", style = PokyhType.caption.bold(), color = PokyhTheme.colors.textSecondary)
        }
        Text(
            d.subjectLong.ifEmpty { d.subjectName },
            style = PokyhType.subheadline.bold(),
            color = PokyhTheme.colors.textTertiary,
            textDecoration = TextDecoration.LineThrough,
        )
    }
}

@Composable
private fun SectionCard(title: String, body: String) {
    Column(
        modifier = Modifier.fillMaxWidth().cardSurface(radius = 16.dp).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title.uppercase(), style = PokyhType.caption2.bold(), color = PokyhTheme.colors.textSecondary)
        Text(body, style = PokyhType.subheadline, color = PokyhTheme.colors.textPrimary)
    }
}
