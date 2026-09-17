@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.data.model.TimetableEntry
import dev.plattnericus.pokyh.data.untis.MergedSlot
import dev.plattnericus.pokyh.data.untis.SlotKind
import dev.plattnericus.pokyh.ui.components.PokyhBleedCard
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.components.PokyhFittedText
import dev.plattnericus.pokyh.ui.components.PokyhIconButton
import dev.plattnericus.pokyh.ui.components.PokyhLabel
import dev.plattnericus.pokyh.ui.components.PokyhRowSeparator
import dev.plattnericus.pokyh.ui.components.TagChip
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.monospacedDigits
import dev.plattnericus.pokyh.ui.theme.SubjectTone
import dev.plattnericus.pokyh.ui.theme.statusTone
import dev.plattnericus.pokyh.ui.theme.subjectTone
import kotlinx.coroutines.launch

/**
 * The lesson detail, as a bottom sheet.
 *
 * Built like a screen rather than like a dialog: the sheet is the warm [PokyhColors.bg] canvas
 * and everything on it is a card standing on that canvas, so the hero, the Lehrer/Raum list and
 * the note cards separate by the same tone step and radius as the rest of the app. It used to be
 * a card-colored sheet holding card-colored cards — the cards vanished into it and the whole
 * thing read as one flat slab with a photo glued to its top edge.
 *
 * The hero card opens on a **header image of the subject**, served by the POKYH backend and keyed
 * by the subject's long name — the same images the web app's lesson popup uses. The backend has
 * no placeholder for a subject it hasn't got a picture of, so [imageUrl] is null in that case and
 * the header falls back to the subject's monogram on its own pastel — a deliberate design, not an
 * error state.
 *
 * `skipPartiallyExpanded = false` so it can rest at a medium height instead of jumping to full —
 * the content is short, and a half sheet keeps the timetable visible behind it.
 */
@Composable
fun LessonDetailSheet(
    slot: MergedSlot,
    imageUrl: String?,
    imageHeader: Pair<String, String>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val scope = rememberCoroutineScope()

    fun dismiss() {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    ModalBottomSheet(
        onDismissRequest = ::dismiss,
        sheetState = sheetState,
        containerColor = PokyhTheme.colors.bg,
        shape = PokyhShapes.topXxl,
        dragHandle = { SheetHandle() },
    ) {
        LessonDetailContent(slot = slot, imageUrl = imageUrl, imageHeader = imageHeader, onClose = ::dismiss)
    }
}

/** A slim pill — the image now sits inside a card below it, so the handle has plain canvas to
 * sit on and no longer has to be hidden. */
@Composable
private fun SheetHandle() {
    Box(
        Modifier
            .padding(vertical = PokyhSpacing.md)
            .size(width = 36.dp, height = 4.dp)
            .background(PokyhTheme.colors.textTertiary.copy(alpha = 0.35f), PokyhShapes.pill),
    )
}

@Composable
private fun LessonDetailContent(
    slot: MergedSlot,
    imageUrl: String?,
    imageHeader: Pair<String, String>,
    onClose: () -> Unit,
) {
    val colors = PokyhTheme.colors
    val d = slot.display
    val isDark = colors.isDark
    val tone = remember(slot.id, isDark) { detailTone(slot, isDark) }
    val headerName = headerNameFor(slot)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PokyhSpacing.screenH)
            .padding(bottom = PokyhSpacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
    ) {
        PokyhBleedCard {
            LessonHeaderImage(
                imageUrl = imageUrl,
                imageHeader = imageHeader,
                monogram = headerName,
                tone = tone,
                tags = tagsFor(slot),
                onClose = onClose,
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(PokyhSpacing.card),
                verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xs),
            ) {
                PokyhFittedText(
                    text = headerName,
                    style = PokyhType.title2,
                    color = colors.textPrimary,
                    maxLines = 2,
                )
                val short = shortNameFor(slot)
                if (short.isNotEmpty() && !short.equals(headerName, ignoreCase = true)) {
                    Text(short, style = PokyhType.subheadline, color = colors.textSecondary)
                }
                Spacer(Modifier.size(PokyhSpacing.xs))
                TimePill(text = "${Fmt.time(d.startTime)} – ${Fmt.time(d.endTime)}", tone = tone)
            }
        }

        DetailsCard(detailEntryFor(slot), tone)
        slot.replacement?.let { InsteadCard(it) }
        // Every free-text field WebUntis can attach to a period, each under its own heading.
        // `note` and `lessonText` are different things and a server may send either or both;
        // folding them into one card meant whichever lost the coin toss was never shown.
        d.note?.takeIf { it.isNotBlank() }?.let { SectionCard("Notiz", it) }
        d.lessonText?.takeIf { it.isNotBlank() && it != d.note }?.let { SectionCard("Stundentext", it) }
        d.substitutionText?.takeIf { it.isNotBlank() }?.let { SectionCard("Vertretungstext", it) }
        if (d.isExam) d.examDescription?.takeIf { it.isNotEmpty() }?.let { SectionCard("Prüfungsinhalt", it) }
    }
}

/** The lesson's time on the subject's own pastel — ties the card body to its header. */
@Composable
private fun TimePill(text: String, tone: SubjectTone) {
    Row(
        modifier = Modifier
            .background(tone.fill, PokyhShapes.pill)
            .padding(horizontal = PokyhSpacing.md, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.xs),
    ) {
        Icon(
            imageVector = PokyhIcons.clock,
            contentDescription = null,
            tint = tone.ink,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = text,
            style = PokyhType.footnote.monospacedDigits(),
            color = tone.ink,
            maxLines = 1,
        )
    }
}

// ── Header ──────────────────────────────────────────────────────────────────

/** Tall enough to be a picture rather than a band, short enough that the facts under it are
 * still on screen when the sheet rests at its medium detent. */
private val HeaderHeight = 148.dp

/**
 * The subject photo, or its monogram on the subject's pastel — the top of the hero card, which
 * clips it to the card's radius.
 *
 * **The dark gradient only exists over a photo.** It is there to make the status chips legible
 * against whatever the picture happens to contain — over the flat pastel fallback there is
 * nothing to fight, and laying a black wash over a pale tint just dirties it.
 *
 * Either way the layout is identical, so nothing below shifts when an image finishes loading,
 * and the monogram is what shows while a photo is still on its way — the header is never blank
 * and never pops from grey to picture.
 */
@Composable
private fun LessonHeaderImage(
    imageUrl: String?,
    imageHeader: Pair<String, String>,
    monogram: String,
    tone: SubjectTone,
    tags: List<Pair<String, Color>>,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val colors = PokyhTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HeaderHeight)
            .background(tone.fill),
    ) {
        if (imageUrl != null) {
            // The API key goes on the request as a header — see BackendClient.subjectImageUrl.
            val request = remember(imageUrl, imageHeader) {
                ImageRequest.Builder(context)
                    .data(imageUrl)
                    .httpHeaders(NetworkHeaders.Builder().set(imageHeader.first, imageHeader.second).build())
                    .build()
            }
            SubcomposeAsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { MonogramFallback(monogram, tone) },
                error = { MonogramFallback(monogram, tone) },
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.55f),
                        ),
                    ),
            )
        } else {
            MonogramFallback(monogram, tone)
        }

        PokyhIconButton(
            icon = PokyhIcons.close,
            contentDescription = "Schließen",
            onClick = onClose,
            tint = colors.textPrimary,
            containerColor = colors.card.copy(alpha = 0.92f),
            size = 34.dp,
            iconSize = 16.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(PokyhSpacing.md),
        )

        if (tags.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(PokyhSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
            ) {
                // A card-colored backing under each tinted chip, so it reads the same over a
                // photo, the dark gradient or the pastel fallback.
                tags.forEach { (text, color) ->
                    Box(Modifier.background(colors.card, PokyhShapes.pill)) {
                        TagChip(text = text, color = color, large = true)
                    }
                }
            }
        }
    }
}

/** Big enough to fill the header, at the same numeral weight the app's hero figures use. It is
 * an oversized graphic mark rather than a word, so it sits outside the type scale — the one
 * escape hatch [PokyhType.numeral] exists for. */
private val MonogramStyle = PokyhType.numeral(72.sp)

/** Two letters of the subject, oversized and low-contrast on its own pastel — the header when
 * there is no photo. */
@Composable
private fun MonogramFallback(name: String, tone: SubjectTone) {
    Box(
        modifier = Modifier.fillMaxSize().background(tone.fill),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().take(2).uppercase().ifEmpty { "?" },
            style = MonogramStyle,
            color = tone.ink.copy(alpha = 0.30f),
            maxLines = 1,
        )
    }
}

// ── Content ─────────────────────────────────────────────────────────────────

private fun tagsFor(slot: MergedSlot): List<Pair<String, Color>> {
    val d = slot.display
    return buildList {
        if (d.isCancelled) add("Entfall" to Brand.danger)
        if (slot.kind == SlotKind.REPLACEMENT) add("Vertretung" to Brand.orange)
        if (d.isExam) add("Prüfung" to Brand.warning)
        if (d.isSubstitution && !d.isCancelled && slot.kind != SlotKind.REPLACEMENT) {
            add("Vertretung" to Brand.orange)
        }
        if (slot.kind == SlotKind.EVENT) add("Veranstaltung" to Brand.accent)
    }
}

/**
 * The sheet's accent.
 *
 * Note this reads [MergedSlot.display] and not the replacement: since the week grid gives each
 * half of a substitution its own slot, `display` is always the lesson that was actually tapped.
 */
private fun detailTone(slot: MergedSlot, isDark: Boolean): SubjectTone {
    val d = slot.display
    return when {
        d.isCancelled -> statusTone(Brand.danger, isDark)
        d.isExam -> statusTone(Brand.warning, isDark)
        slot.kind == SlotKind.EVENT -> statusTone(Brand.accentSoft, isDark)
        d.subjectName.isEmpty() -> statusTone(Brand.accent, isDark)
        else -> subjectTone(d.subjectName, isDark)
    }
}

/** The long name where WebUntis has one, else the short name, else the note. */
private fun headerNameFor(slot: MergedSlot): String {
    val d = slot.display
    return d.subjectLong.ifEmpty { d.subjectName.ifEmpty { d.note ?: "Stunde" } }
}

/** The short name, shown under the title only when it adds something the title doesn't. */
private fun shortNameFor(slot: MergedSlot): String = slot.display.subjectName

private fun detailEntryFor(slot: MergedSlot): TimetableEntry = slot.display

@Composable
private fun DetailsCard(d: TimetableEntry, tone: SubjectTone) {
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
                ChangeRow("Lehrer", origTeacher, teacher, PokyhIcons.person, tone)
            } else {
                DetailRow("Lehrer", teacher.ifEmpty { origTeacher }, PokyhIcons.person, tone)
            }
        }
        if (hasTeacher && hasRoom) PokyhRowSeparator(startInset = DetailTextInset)
        if (hasRoom) {
            if (origRoom.isNotEmpty() && origRoom != room) {
                ChangeRow("Raum", origRoom, room, PokyhIcons.room, tone)
            } else {
                DetailRow("Raum", room.ifEmpty { origRoom }, PokyhIcons.room, tone)
            }
        }
    }
}

private val DetailTileSize = 40.dp

/** Where a row's text starts — the hairline is inset to it, as in the app's other list cards. */
private val DetailTextInset = PokyhSpacing.card + DetailTileSize + PokyhSpacing.iconText

/** The row's glyph on the subject's pastel, so the list belongs to the lesson it describes. */
@Composable
private fun DetailTile(icon: ImageVector, tone: SubjectTone) {
    Box(
        modifier = Modifier.size(DetailTileSize).background(tone.fill, PokyhShapes.sm),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tone.ink, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String, icon: ImageVector, tone: SubjectTone) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PokyhSpacing.card, vertical = PokyhSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.iconText),
    ) {
        DetailTile(icon, tone)
        Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xxs)) {
            PokyhLabel(label)
            Text(value, style = PokyhType.body, color = PokyhTheme.colors.textPrimary)
        }
    }
}

@Composable
private fun ChangeRow(label: String, from: String, to: String, icon: ImageVector, tone: SubjectTone) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PokyhSpacing.card, vertical = PokyhSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.iconText),
    ) {
        DetailTile(icon, tone)
        Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xxs)) {
            PokyhLabel(label)
            // Old and new on their own lines: side by side, two full teacher names ran out of
            // width and wrapped mid-name.
            Text(
                text = from,
                style = PokyhType.body,
                color = Brand.danger,
                textDecoration = TextDecoration.LineThrough,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
            ) {
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
 * "Stattdessen" — the other half of a substitution.
 *
 * Which lesson that is depends on which cell was tapped, and the card says so either way: from
 * the cancelled original it points at the lesson that replaced it, and from the replacement it
 * points back at what was called off.
 */
@Composable
private fun InsteadCard(other: TimetableEntry) {
    val colors = PokyhTheme.colors
    val cancelled = other.isCancelled
    PokyhCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
        ) {
            Icon(
                imageVector = PokyhIcons.replacement,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(14.dp),
            )
            PokyhLabel(if (cancelled) "Statt" else "Stattdessen")
        }
        Spacer(Modifier.size(PokyhSpacing.sm))
        Text(
            text = other.subjectLong.ifEmpty { other.subjectName.ifEmpty { other.note ?: "—" } },
            style = PokyhType.headline,
            color = if (cancelled) colors.textTertiary else colors.textPrimary,
            textDecoration = if (cancelled) TextDecoration.LineThrough else TextDecoration.None,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val meta = listOf(other.teacherName, other.roomName).filter { it.isNotEmpty() }.joinToString(" · ")
        if (meta.isNotEmpty()) {
            Spacer(Modifier.size(PokyhSpacing.xxs))
            Text(meta, style = PokyhType.footnote, color = colors.textSecondary)
        }
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
