@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.classreg

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.data.model.ClassregEvent
import dev.plattnericus.pokyh.ui.components.EmptyStateView
import dev.plattnericus.pokyh.ui.components.ErrorStateView
import dev.plattnericus.pokyh.ui.components.ListSkeleton
import dev.plattnericus.pokyh.ui.components.PokyhListCard
import dev.plattnericus.pokyh.ui.components.PokyhMenuButton
import dev.plattnericus.pokyh.ui.components.PokyhTopBar
import dev.plattnericus.pokyh.ui.components.TagChip
import dev.plattnericus.pokyh.ui.components.TopBarNav
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.fadeIn
import dev.plattnericus.pokyh.ui.theme.slideInTrailing
import dev.plattnericus.pokyh.core.i18n.strings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import dev.plattnericus.pokyh.ui.theme.PokyhShapes

/** Klassenbuch — entries for the selected school year, newest `createDate` first, as one
 * grouped list. */
@Composable
fun ClassregEventsScreen(onNavigateBack: () -> Unit = {}, viewModel: ClassregViewModel = hiltViewModel()) {
    val year by viewModel.year.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var yearMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            PokyhTopBar(
                title = strings().tasks.classreg,
                eyebrow = strings().common.schoolYear(year),
                nav = TopBarNav.Back(onNavigateBack),
                actions = {
                    Box(modifier = Modifier.slideInTrailing()) {
                        PokyhMenuButton(
                            label = "$year/${(year + 1) % 100}",
                            expanded = yearMenuExpanded,
                            onClick = { yearMenuExpanded = true },
                        )
                        DropdownMenu(expanded = yearMenuExpanded, onDismissRequest = { yearMenuExpanded = false }) {
                            viewModel.availableYears.forEach { y ->
                                DropdownMenuItem(
                                    text = { Text("$y/${(y + 1) % 100}", style = PokyhType.body) },
                                    onClick = {
                                        yearMenuExpanded = false
                                        viewModel.selectYear(y)
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                loading -> ListSkeleton()
                error != null -> ErrorStateView(message = error!!, onRetry = viewModel::retry)
                events.isEmpty() -> EmptyStateView(
                    sprite = dev.plattnericus.pokyh.ui.pixel.PixelSprite.Empty,
                    title = strings().tasks.classregEmpty,
                    subtitle = strings().tasks.classregEmptyText,
                )
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = PokyhSpacing.screenH)
                        .padding(bottom = PokyhSpacing.xxxl),
                    verticalArrangement = Arrangement.spacedBy(PokyhSpacing.section),
                ) {
                    ClassregStats(events, Modifier.fadeIn())
                    ClassregList(events)
                }
            }
        }
    }
}

private enum class ClassregRange { All, Month, Months3 }
private enum class ClassregSort { DateDesc, DateAsc, Subject, Category }

/** The category's tone, as on the web (components/class/ClassregEntry `categoryTone`). */
@Composable
private fun categoryTone(category: String, reason: String = ""): dev.plattnericus.pokyh.ui.theme.DecorativeTone {
    val l = "$category $reason".lowercase()
    val dark = PokyhTheme.colors.isDark
    return when {
        Regex("täuschung|betrug|disziplin|discipl|verweis|richiamo|cheat").containsMatchIn(l) -> dev.plattnericus.pokyh.ui.theme.PokyhDecorative.blush(dark)
        "vermerk" in l || "nota" in l || "note" in l -> dev.plattnericus.pokyh.ui.theme.PokyhDecorative.butter(dark)
        else -> dev.plattnericus.pokyh.ui.theme.PokyhDecorative.periwinkle(dark)
    }
}

private fun classregDate(n: Int, pattern: String): String = runCatching {
    val l = dev.plattnericus.pokyh.core.i18n.I18n.lang.value
    java.time.LocalDate.of(n / 10000, n / 100 % 100, n % 100).format(java.time.format.DateTimeFormatter.ofPattern(pattern, l.locale))
}.getOrDefault("")

/** The year in four figures and the split by category, as on the web. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ClassregStats(events: List<ClassregEvent>, modifier: Modifier = Modifier) {
    val t = strings().tasks
    val colors = PokyhTheme.colors
    val noCategory = t.noCategory
    val subjects = events.map { it.subjectName }.filter { it.isNotBlank() }.toSet().size
    val latest = events.maxOfOrNull { it.createDate } ?: 0
    val categories = events.groupBy { it.categoryName.ifBlank { noCategory } }.map { (name, list) -> Triple(name, list.size, list.first()) }.sortedByDescending { it.second }
    val top = categories.firstOrNull { it.first != noCategory }?.first.orEmpty()
    val figures = buildList {
        add(t.totalEntries to "${events.size}")
        add(t.subjectsLabel to "$subjects")
        add(t.lastEntry to if (latest > 0) classregDate(latest, if (dev.plattnericus.pokyh.core.i18n.I18n.lang.value == dev.plattnericus.pokyh.core.i18n.Lang.De) "d. MMM" else "d MMM") else "-")
        if (top.isNotEmpty()) add(t.topCategory to top)
    }
    Column(
        modifier.fillMaxWidth().background(colors.card, PokyhShapes.xl).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        figures.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEachIndexed { i, (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, style = PokyhType.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = colors.textSecondary)
                        val small = figures.indexOf(label to value) == 3
                        Text(
                            value,
                            style = if (small) PokyhType.title2.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold) else PokyhType.largeTitle.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (categories.size > 1) {
            Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                categories.forEach { (name, n, e) ->
                    Box(Modifier.weight(n.toFloat()).fillMaxHeight().background(categoryTone(if (name == noCategory) "" else name, e.eventReasonName).ink, PokyhShapes.pill))
                }
            }
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.forEach { (name, n, e) ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).background(categoryTone(if (name == noCategory) "" else name, e.eventReasonName).ink, PokyhShapes.pill))
                        Text(name, style = PokyhType.footnote, color = colors.textSecondary)
                        Text("$n", style = PokyhType.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = colors.textPrimary)
                    }
                }
            }
        }
    }
}

/** The entries in a time range, sorted and grouped: by month, or by subject or category. */
@Composable
private fun ClassregList(events: List<ClassregEvent>) {
    val t = strings().tasks
    var range by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(ClassregRange.All) }
    var sort by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(ClassregSort.DateDesc) }
    var sortOpen by remember { androidx.compose.runtime.mutableStateOf(false) }
    val noCategory = t.noCategory
    val groups = remember(events, range, sort, noCategory) {
        val cutoff = when (range) {
            ClassregRange.All -> 0
            ClassregRange.Month -> java.time.LocalDate.now().minusMonths(1).let { it.year * 10000 + it.monthValue * 100 + it.dayOfMonth }
            ClassregRange.Months3 -> java.time.LocalDate.now().minusMonths(3).let { it.year * 10000 + it.monthValue * 100 + it.dayOfMonth }
        }
        val list = events.filter { it.createDate >= cutoff }.let { l -> if (sort == ClassregSort.DateAsc) l.sortedBy { it.createDate } else l.sortedByDescending { it.createDate } }
        val grouped = list.groupBy {
            when (sort) {
                ClassregSort.Subject -> it.subjectName.ifBlank { "-" }
                ClassregSort.Category -> it.categoryName.ifBlank { noCategory }
                else -> (it.createDate / 100).toString()
            }
        }.toList()
        val ordered = if (sort == ClassregSort.Subject || sort == ClassregSort.Category) grouped.sortedBy { it.first.lowercase() } else grouped
        ordered.map { (key, items) ->
            val title = if (sort == ClassregSort.Subject || sort == ClassregSort.Category) key
            else classregDate(items.first().createDate, "LLLL yyyy").replaceFirstChar { it.titlecase(dev.plattnericus.pokyh.core.i18n.I18n.lang.value.locale) }
            Triple(key, title, items)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.section)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm)) {
            dev.plattnericus.pokyh.ui.components.PokyhSegmentedControl(
                options = ClassregRange.entries,
                selected = range,
                onSelect = { range = it },
                label = { when (it) { ClassregRange.All -> t.filterAll; ClassregRange.Month -> t.filter1m; ClassregRange.Months3 -> t.filter3m } },
                modifier = Modifier.weight(1f),
            )
            Box {
                PokyhMenuButton(label = when (sort) { ClassregSort.DateDesc -> t.sortDateDesc; ClassregSort.DateAsc -> t.sortDateAsc; ClassregSort.Subject -> t.sortSubject; ClassregSort.Category -> t.sortCategory }, expanded = sortOpen, onClick = { sortOpen = true })
                DropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
                    ClassregSort.entries.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(when (s) { ClassregSort.DateDesc -> t.sortDateDesc; ClassregSort.DateAsc -> t.sortDateAsc; ClassregSort.Subject -> t.sortSubject; ClassregSort.Category -> t.sortCategory }, style = PokyhType.body) },
                            onClick = { sortOpen = false; sort = s },
                        )
                    }
                }
            }
        }
        if (groups.isEmpty()) {
            Text(t.noEntriesRange, style = PokyhType.callout, color = PokyhTheme.colors.textTertiary, modifier = Modifier.padding(vertical = PokyhSpacing.lg))
        }
        groups.forEach { (key, title, items) ->
            androidx.compose.runtime.key(sort, key) {
                dev.plattnericus.pokyh.ui.components.PokyhSection(
                    title = title,
                    trailing = { dev.plattnericus.pokyh.ui.components.PokyhLabel("${items.size}") },
                ) {
                    PokyhListCard(items = items, modifier = Modifier.fadeIn()) { event -> ClassregRow(event) }
                }
            }
        }
    }
}

/** Category tint: only the two categories that actually mean "problem" get a status color; the
 * rest stay neutral, so a page of ordinary entries isn't a page of red and orange. */
private fun accentFor(event: ClassregEvent): Color {
    val l = event.categoryName.lowercase()
    return when {
        l.contains("täuschung") || l.contains("betrug") -> Brand.danger
        l.contains("vermerk") -> Brand.warning
        else -> Brand.accent
    }
}

@Composable
private fun ClassregRow(event: ClassregEvent) {
    val colors = PokyhTheme.colors
    val accent = accentFor(event)

    Row(
        modifier = Modifier.fillMaxWidth().padding(PokyhSpacing.card),
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.iconText),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = Fmt.dateShort(event.createDate),
            style = PokyhType.caption,
            color = colors.textTertiary,
            modifier = Modifier.width(52.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
            ) {
                if (event.subjectName.isNotEmpty()) {
                    Text(
                        text = event.subjectName,
                        style = PokyhType.headline,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Box(modifier = Modifier.weight(1f))
                }
                if (event.categoryName.isNotEmpty()) {
                    TagChip(text = event.categoryName, color = accent)
                }
            }
            val bodyText = event.text.ifEmpty { event.eventReasonName }
            if (bodyText.isNotEmpty()) {
                Text(bodyText, style = PokyhType.callout, color = colors.textSecondary)
            }
            if (event.creatorName.isNotEmpty()) {
                Text(event.creatorName, style = PokyhType.caption2, color = colors.textTertiary)
            }
        }
    }
}
