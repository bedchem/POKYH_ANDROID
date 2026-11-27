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
import dev.plattnericus.pokyh.ui.components.WebPickMenu
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em

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
        topBar = { PokyhTopBar(title = null, nav = TopBarNav.Back(onNavigateBack)) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PokyhSpacing.screenH)
                .padding(bottom = 80.dp),
        ) {
            // The web's page header: "Klassenbuch", the year's summary, the year picker.
            WebPageHeader(
                title = strings().tasks.classreg,
                subtitle = if (!loading && error == null) classregSummary(events) else strings().common.schoolYear(year),
                modifier = Modifier.fadeIn(),
                actions = {
                    WebPickMenu(
                        value = year,
                        options = viewModel.availableYears.map { y -> y to "$y/${(y + 1) % 100}" },
                        onChange = viewModel::selectYear,
                    )
                },
            )
            when {
                loading -> ListSkeleton()
                error != null -> ErrorStateView(message = error!!, onRetry = viewModel::retry)
                events.isEmpty() -> EmptyStateView(
                    sprite = dev.plattnericus.pokyh.ui.pixel.PixelSprite.Empty,
                    title = strings().tasks.classregEmpty,
                    subtitle = strings().tasks.classregEmptyText,
                )
                else -> Column(verticalArrangement = Arrangement.spacedBy(36.dp)) {
                    ClassregStats(events, Modifier.fadeIn())
                    ClassregList(events)
                }
            }
        }
    }
}

/** "12 Einträge · 5 Fächer · Stand 3. November", as the web's subtitle. */
private fun classregSummary(events: List<ClassregEvent>): String {
    val lang = dev.plattnericus.pokyh.core.i18n.I18n.lang.value
    val n = events.size
    val subjects = events.map { it.subjectName }.filter { it.isNotBlank() }.toSet().size
    val latest = events.maxOfOrNull { it.createDate } ?: 0
    val entries = when (lang) {
        dev.plattnericus.pokyh.core.i18n.Lang.De -> if (n == 1) "1 Eintrag" else "$n Einträge"
        dev.plattnericus.pokyh.core.i18n.Lang.It -> if (n == 1) "1 annotazione" else "$n annotazioni"
        dev.plattnericus.pokyh.core.i18n.Lang.En -> if (n == 1) "1 entry" else "$n entries"
    }
    val subj = when (lang) {
        dev.plattnericus.pokyh.core.i18n.Lang.De -> if (subjects == 1) "1 Fach" else "$subjects Fächer"
        dev.plattnericus.pokyh.core.i18n.Lang.It -> if (subjects == 1) "1 materia" else "$subjects materie"
        dev.plattnericus.pokyh.core.i18n.Lang.En -> if (subjects == 1) "1 subject" else "$subjects subjects"
    }
    val asOf = if (latest > 0) {
        val d = classregDate(latest, if (lang == dev.plattnericus.pokyh.core.i18n.Lang.De) "d. MMMM" else "d MMMM")
        when (lang) {
            dev.plattnericus.pokyh.core.i18n.Lang.De -> " · Stand $d"
            dev.plattnericus.pokyh.core.i18n.Lang.It -> " · Aggiornato al $d"
            dev.plattnericus.pokyh.core.i18n.Lang.En -> " · As of $d"
        }
    } else ""
    return "$entries · $subj$asOf"
}

private enum class ClassregRange { All, Month, Months3 }
private enum class ClassregSort { DateDesc, DateAsc, Subject, Category }

@Composable
private fun categoryTone(category: String, reason: String = "") = classregTone(category, reason)

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
        modifier.fillMaxWidth().background(colors.card, dev.plattnericus.pokyh.ui.theme.SmoothCornerShape(24.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        figures.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                row.forEachIndexed { i, (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, style = PokyhType.footnote.copy(fontSize = 13.5.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = colors.textSecondary)
                        val small = figures.indexOf(label to value) == 3
                        Text(
                            value,
                            style = if (small) PokyhType.title2.copy(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, letterSpacing = (-0.04).em) else PokyhType.largeTitle.copy(fontSize = 34.sp, lineHeight = 34.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, letterSpacing = (-0.04).em),
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
                        Text(name, style = PokyhType.footnote.copy(fontSize = 13.sp), color = colors.textSecondary)
                        Text("$n", style = PokyhType.footnote.copy(fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = colors.textPrimary)
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
        val list = events.filter { it.createDate >= cutoff }.let { l -> if (sort == ClassregSort.DateAsc) l.sortedWith(compareBy<ClassregEvent>({ it.createDate }, { it.createTime })) else l.sortedWith(compareByDescending<ClassregEvent> { it.createDate }.thenByDescending { it.createTime }) }
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
    Column(verticalArrangement = Arrangement.spacedBy(36.dp)) {
        androidx.compose.foundation.layout.FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            dev.plattnericus.pokyh.ui.components.WebSegmented(
                value = range,
                options = listOf(ClassregRange.All to t.filterAll, ClassregRange.Month to t.filter1m, ClassregRange.Months3 to t.filter3m),
                onChange = { range = it },
            )
            WebPickMenu(
                value = sort,
                options = listOf(ClassregSort.DateDesc to t.sortDateDesc, ClassregSort.DateAsc to t.sortDateAsc, ClassregSort.Subject to t.sortSubject, ClassregSort.Category to t.sortCategory),
                onChange = { sort = it },
                icon = PokyhIcons.sort,
            )
        }
        if (groups.isEmpty()) {
            Text(t.noEntriesRange, style = PokyhType.callout, color = PokyhTheme.colors.textTertiary, modifier = Modifier.padding(vertical = PokyhSpacing.lg))
        }
        groups.forEach { (key, title, items) ->
            androidx.compose.runtime.key(sort, key) {
                WebSection(
                    title = title,
                    modifier = Modifier.fadeIn(),
                    action = { Text("${items.size}", style = PokyhType.callout.copy(fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = PokyhTheme.colors.textSecondary) },
                ) {
                    ClassregEntryList(items)
                }
            }
        }
    }
}

