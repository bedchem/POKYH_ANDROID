@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.components.PokyhLabel
import dev.plattnericus.pokyh.ui.components.PokyhRowSeparator
import dev.plattnericus.pokyh.ui.components.TagChip
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import kotlinx.coroutines.launch

/**
 * The lesson detail, as a bottom sheet. `skipPartiallyExpanded = false` so it can rest at a
 * medium height instead of jumping to full — the content is short, and a half sheet keeps the
 * timetable visible behind it.
 *
 * The sheet's own header is the same shape as a pushed screen's: status chips, then the lesson
 * name at [PokyhType.largeTitle], then the time. So arriving here from the grid feels like
 * arriving at a screen, not at a different kind of surface.
 */
@Composable
fun LessonDetailSheet(slot: MergedSlot, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()

    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = ::dismiss,
        sheetState = sheetState,
        containerColor = PokyhTheme.colors.card,
        shape = PokyhShapes.topXxl,
    ) {
        LessonDetailContent(slot = slot)
    }
}

@Composable
private fun LessonDetailContent(slot: MergedSlot) {
    val colors = PokyhTheme.colors
    val d = slot.display

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PokyhSpacing.screenH)
            .padding(bottom = PokyhSpacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.lg),
    ) {
        TagsRow(slot)
        Text(
            text = headerNameFor(slot),
            style = PokyhType.largeTitle,
            color = colors.textPrimary,
            textDecoration = if (d.isCancelled && slot.replacement == null) {
                TextDecoration.LineThrough
            } else {
                TextDecoration.None
            },
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
        ) {
            Icon(PokyhIcons.clock, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            Text(
                text = "${Fmt.time(d.startTime)} – ${Fmt.time(d.endTime)}",
                style = PokyhType.body,
                color = colors.textSecondary,
            )
        }
        DetailsCard(d)
        if (slot.replacement != null) ReplacementCard(d)
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
        Row(horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm)) {
            tags.forEach { (text, color) -> TagChip(text = text, color = color, large = true) }
        }
    }
}

/** The title favors the active replacement (if any) over the cancelled/original entry that
 * [MergedSlot.display] carries. */
private fun headerNameFor(slot: MergedSlot): String {
    slot.replacement?.let { r -> return r.subjectLong.ifEmpty { r.subjectName.ifEmpty { r.note ?: "" } } }
    val d = slot.display
    return d.subjectLong.ifEmpty { d.subjectName.ifEmpty { d.note ?: "Stunde" } }
}

@Composable
private fun DetailsCard(d: TimetableEntry) {
    val teacher = d.teacherLongName ?: d.teacherName
    val origTeacher = d.originalTeacherLong ?: (d.originalTeacher ?: "")
    val room = d.roomName
    val origRoom = d.originalRoom ?: ""

    val hasTeacher = teacher.isNotEmpty() || origTeacher.isNotEmpty()
    val hasRoom = room.isNotEmpty() || origRoom.isNotEmpty()
    if (!hasTeacher && !hasRoom) return

    PokyhCard(padding = 0.dp) {
        if (hasTeacher) {
            if (origTeacher.isNotEmpty() && origTeacher != teacher) {
                ChangeRow("Lehrer", origTeacher, teacher, PokyhIcons.person)
            } else {
                DetailRow("Lehrer", teacher.ifEmpty { origTeacher }, PokyhIcons.person)
            }
        }
        if (hasTeacher && hasRoom) PokyhRowSeparator()
        if (hasRoom) {
            if (origRoom.isNotEmpty() && origRoom != room) {
                ChangeRow("Raum", origRoom, room, PokyhIcons.room)
            } else {
                DetailRow("Raum", room.ifEmpty { origRoom }, PokyhIcons.room)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(PokyhSpacing.card),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.iconText),
    ) {
        Icon(icon, contentDescription = null, tint = PokyhTheme.colors.accentText, modifier = Modifier.size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xxs)) {
            PokyhLabel(label)
            Text(value, style = PokyhType.body, color = PokyhTheme.colors.textPrimary)
        }
    }
}

@Composable
private fun ChangeRow(label: String, from: String, to: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(PokyhSpacing.card),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.iconText),
    ) {
        Icon(icon, contentDescription = null, tint = PokyhTheme.colors.accentText, modifier = Modifier.size(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xxs)) {
            PokyhLabel(label)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
            ) {
                Text(
                    text = from,
                    style = PokyhType.body,
                    color = Brand.danger,
                    textDecoration = TextDecoration.LineThrough,
                )
                Icon(
                    imageVector = PokyhIcons.forward,
                    contentDescription = null,
                    tint = PokyhTheme.colors.textTertiary,
                    modifier = Modifier.size(13.dp),
                )
                Text(to, style = PokyhType.body, color = Brand.orange)
            }
        }
    }
}

/**
 * The "Statt" card — 1:1 with the original behavior including its quirk: the body always shows
 * the CANCELLED/original entry's subject (`slot.display`, passed in here as [d]), not the
 * replacement. "Statt" is what would normally have been on the timetable.
 */
@Composable
private fun ReplacementCard(d: TimetableEntry) {
    PokyhCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
        ) {
            Icon(
                imageVector = PokyhIcons.replacement,
                contentDescription = null,
                tint = PokyhTheme.colors.textTertiary,
                modifier = Modifier.size(14.dp),
            )
            PokyhLabel("Statt")
        }
        Spacer(Modifier.size(PokyhSpacing.sm))
        Text(
            text = d.subjectLong.ifEmpty { d.subjectName },
            style = PokyhType.headline,
            color = PokyhTheme.colors.textTertiary,
            textDecoration = TextDecoration.LineThrough,
        )
    }
}

@Composable
private fun SectionCard(title: String, body: String) {
    PokyhCard {
        PokyhLabel(title)
        Spacer(Modifier.size(PokyhSpacing.sm))
        Text(body, style = PokyhType.body, color = PokyhTheme.colors.textPrimary)
    }
}
