package dev.plattnericus.pokyh.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.core.i18n.strings
import dev.plattnericus.pokyh.data.model.TimetableSource
import dev.plattnericus.pokyh.ui.components.PokyhMenuButton
import dev.plattnericus.pokyh.ui.components.PokyhSecondaryButton
import dev.plattnericus.pokyh.ui.components.PokyhSegmentedControl
import dev.plattnericus.pokyh.ui.components.PokyhTextField
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhDecorative
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType

/**
 * The school year of the shown week, and the list to jump to another — with, as on the web,
 * "Andere Stundenpläne" under the years. [short] writes it as 26/27.
 */
@Composable
internal fun YearMenu(shownYear: Int, years: List<Int>, short: Boolean, onPick: (Int) -> Unit, onOther: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        PokyhMenuButton(
            label = if (short) "${shownYear % 100}/${(shownYear + 1) % 100}" else "$shownYear/${(shownYear + 1) % 100}",
            expanded = open,
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            years.forEach { y ->
                DropdownMenuItem(
                    text = { Text("$y/${(y + 1) % 100}", style = PokyhType.body) },
                    trailingIcon = {
                        if (y == shownYear) Icon(PokyhIcons.check, contentDescription = null, tint = PokyhTheme.colors.accentText, modifier = Modifier.size(16.dp))
                    },
                    onClick = { open = false; onPick(y) },
                )
            }
            HorizontalDivider(color = PokyhTheme.colors.nested)
            DropdownMenuItem(
                text = { Text(strings().timetable.otherTimetables, style = PokyhType.body) },
                leadingIcon = { Icon(PokyhIcons.classMembers, contentDescription = null) },
                onClick = { open = false; onOther() },
            )
        }
    }
}

/** Whose timetable is shown, when it isn't the own one; ✕ goes back to the own. */
@Composable
internal fun SourceSwitch(source: TimetableSource?, onOpen: () -> Unit, onClear: () -> Unit) {
    if (source == null) return
    val tone = PokyhDecorative.periwinkle(PokyhTheme.colors.isDark)
    Row(
        Modifier.clip(PokyhShapes.pill).background(tone.fill).clickable(onClick = onOpen).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(PokyhIcons.classMembers, contentDescription = null, tint = tone.ink, modifier = Modifier.size(15.dp))
        Text(source.name, style = PokyhType.footnote.copy(fontWeight = FontWeight.Bold), color = tone.ink, maxLines = 1)
        Box(Modifier.size(28.dp).clip(PokyhShapes.pill).clickable(onClick = onClear), contentAlignment = Alignment.Center) {
            Icon(PokyhIcons.close, contentDescription = strings().timetable.myTimetable, tint = tone.ink, modifier = Modifier.size(13.dp))
        }
    }
}

/**
 * The classes, teachers and rooms whose timetable the school shows this account, as WebUntis
 * lists them (often only some kinds, or only the own class), and the way back to the own one
 * (web OtherTimetableSheet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OtherTimetableSheet(source: TimetableSource?, viewModel: TimetableViewModel, onPick: (TimetableSource?) -> Unit, onDismiss: () -> Unit) {
    val t = strings().timetable
    val colors = PokyhTheme.colors
    val kinds = listOf("CLASS" to t.kindClass, "TEACHER" to t.kindTeacher, "ROOM" to t.kindRoom)
    var kind by remember { mutableStateOf(source?.type ?: "CLASS") }
    var query by remember { mutableStateOf("") }
    val lists by viewModel.filters.collectAsStateWithLifecycle()
    val failed by viewModel.filtersFailed.collectAsStateWithLifecycle()
    LaunchedEffect(kind) { viewModel.loadFilters(kind) }
    val list = lists[kind]
    val shown = list.orEmpty().filter { query.isBlank() || "${it.name} ${it.longName}".contains(query.trim(), ignoreCase = true) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = colors.card, shape = PokyhShapes.topXxl) {
        Column(Modifier.fillMaxWidth().padding(horizontal = PokyhSpacing.screenH).padding(bottom = PokyhSpacing.xxl), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
            Text(t.otherTimetables, style = PokyhType.title2, color = colors.textPrimary)
            val own = source == null
            Row(
                Modifier.fillMaxWidth().clip(PokyhShapes.lg).background(if (own) Brand.accent.copy(alpha = 0.10f) else colors.nested).clickable { onPick(null) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.iconText),
            ) {
                val tone = PokyhDecorative.periwinkle(colors.isDark)
                Box(Modifier.size(40.dp).background(tone.fill, PokyhShapes.pill), contentAlignment = Alignment.Center) {
                    Icon(PokyhIcons.person, contentDescription = null, tint = tone.ink, modifier = Modifier.size(20.dp))
                }
                Text(t.myTimetable, style = PokyhType.callout.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary, modifier = Modifier.weight(1f))
                Icon(PokyhIcons.chevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(16.dp))
            }
            PokyhSegmentedControl(
                options = kinds.map { it.first },
                selected = kind,
                onSelect = { kind = it; query = "" },
                label = { k -> kinds.first { it.first == k }.second },
                modifier = Modifier.fillMaxWidth(),
            )
            if ((list?.size ?: 0) > 8) {
                PokyhTextField(value = query, onValueChange = { query = it }, placeholder = t.otherSearch, leadingIcon = PokyhIcons.search, modifier = Modifier.fillMaxWidth())
            }
            when {
                kind in failed -> Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm)) {
                    Text(t.otherFailed, style = PokyhType.callout.copy(fontWeight = FontWeight.SemiBold), color = Brand.danger)
                    PokyhSecondaryButton(text = strings().common.retry, icon = PokyhIcons.refresh, onClick = { viewModel.loadFilters(kind, force = true) })
                }
                list == null -> Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = colors.textTertiary)
                }
                shown.isEmpty() -> Text(t.otherNone, style = PokyhType.callout, color = colors.textSecondary, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp))
                else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    items(shown, key = { it.id }) { r ->
                        val active = source?.type == kind && source.id == r.id
                        Row(
                            Modifier.fillMaxWidth().clip(PokyhShapes.lg).then(if (active) Modifier.background(Brand.accent.copy(alpha = 0.10f)) else Modifier)
                                .clickable { onPick(TimetableSource(kind, r.id, r.name)) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(r.name, style = PokyhType.callout.copy(fontWeight = FontWeight.Bold), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (r.longName.isNotEmpty() && r.longName != r.name) {
                                    Text(r.longName, style = PokyhType.footnote, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Icon(PokyhIcons.chevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
