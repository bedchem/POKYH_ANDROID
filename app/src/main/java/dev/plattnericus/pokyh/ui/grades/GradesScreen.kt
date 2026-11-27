@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.grades

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn as animateFadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.core.i18n.I18n
import dev.plattnericus.pokyh.core.i18n.strings
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.core.util.GradeMath
import dev.plattnericus.pokyh.core.util.todayLocalDate
import dev.plattnericus.pokyh.data.model.GradeEntry
import dev.plattnericus.pokyh.data.model.SubjectGrades
import dev.plattnericus.pokyh.ui.components.AsOfLabel
import dev.plattnericus.pokyh.ui.components.ErrorStateView
import dev.plattnericus.pokyh.ui.components.PixelState
import dev.plattnericus.pokyh.ui.components.PokyhCard
import dev.plattnericus.pokyh.ui.components.PokyhFittedText
import dev.plattnericus.pokyh.ui.components.PokyhMenuButton
import dev.plattnericus.pokyh.ui.components.PokyhRowSeparator
import dev.plattnericus.pokyh.ui.components.PokyhSectionHeader
import dev.plattnericus.pokyh.ui.components.PokyhSecondaryButton
import dev.plattnericus.pokyh.ui.components.SkeletonBlock
import dev.plattnericus.pokyh.ui.components.StatCardSkeleton
import dev.plattnericus.pokyh.ui.components.PokyhTextButton
import dev.plattnericus.pokyh.ui.components.PokyhTopBar
import dev.plattnericus.pokyh.ui.components.TabRootActions
import dev.plattnericus.pokyh.ui.components.TopBarNav
import dev.plattnericus.pokyh.ui.navigation.PokyhDestinations
import dev.plattnericus.pokyh.ui.pixel.PixelSprite
import dev.plattnericus.pokyh.ui.profile.CurrentUserAvatar
import dev.plattnericus.pokyh.ui.profile.rememberUnreadMessageCount
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhMotion
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.monospacedDigits
import dev.plattnericus.pokyh.ui.theme.PokyhType.semibold
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.bandColor
import dev.plattnericus.pokyh.ui.theme.fadeIn
import dev.plattnericus.pokyh.ui.theme.pressHighlight
import dev.plattnericus.pokyh.ui.theme.subjectTone

/**
 * Noten — the web app's dashboard, rebuilt for the phone.
 *
 * Structure follows `app/grades/page.tsx` (github.com/bedchem/pokyh-frontend) exactly: the
 * school-year header with its "Stand … · N Fächer · N Noten" line and year picker, the four stat
 * cards, then the Fächer list with a sort menu, an expand-all toggle, and rows that open in place
 * to reveal their individual grades. Numbers and diagram geometry are the same
 * ([buildGradesDashboard]); the surfaces, type and palette are this app's.
 *
 * The dashboard renders even with zero grades — the cards show placeholders rather than being
 * swapped for an empty state, so the screen looks like itself at the start of a school year.
 */
@Composable
fun GradesScreen(
    onSubjectClick: (lessonId: Int, year: Int) -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: GradesViewModel = hiltViewModel(),
) {
    val year by viewModel.year.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val subjects by viewModel.subjects.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val freshness by viewModel.freshness.collectAsStateWithLifecycle()
    var yearMenuOpen by remember { mutableStateOf(false) }

    val g = strings().grades
    val dashboard = remember(subjects, g, dev.plattnericus.pokyh.core.util.GradeScales.current) { buildGradesDashboard(subjects, todayLocalDate()) }
    // Each time the tab is shown: the new-grade notifications go.
    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.onShown() }

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            // Only the web's top bar stays; "Noten" and the year scroll with the page, as there.
            PokyhTopBar(
                fillActions = true,
                title = null,
                nav = TopBarNav.None,
                actions = {
                    TabRootActions(
                        avatarContent = { CurrentUserAvatar() },
                        unreadMessages = rememberUnreadMessageCount(),
                        onMessages = { onNavigate(PokyhDestinations.MESSAGES) },
                        onProfile = { onNavigate(PokyhDestinations.PROFILE) },
                        onNavigate = onNavigate,
                    )
                },
            )
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                loading -> GradesSkeleton()
                error != null -> ErrorStateView(message = error!!, onRetry = viewModel::retry)
                else -> GradesContent(
                    dashboard = dashboard,
                    freshness = freshness,
                    subjects = subjects,
                    sort = sort,
                    year = year,
                    years = viewModel.availableYears,
                    onYearChange = viewModel::selectYear,
                    onSortChange = viewModel::selectSort,
                    onSubjectClick = onSubjectClick,
                )
            }
        }
    }
}

@Composable
private fun GradesContent(
    dashboard: GradesDashboard,
    freshness: GradesViewModel.Freshness,
    subjects: List<SubjectGrades>,
    sort: GradesViewModel.SortMode,
    year: Int,
    years: List<Int>,
    onYearChange: (Int) -> Unit,
    onSortChange: (GradesViewModel.SortMode) -> Unit,
    onSubjectClick: (lessonId: Int, year: Int) -> Unit,
) {
    // Same ordering the web applies, including "Letzte Note" (by each subject's newest date).
    //
    // One addition the web doesn't need: subjects with no grades yet are always sorted LAST,
    // whatever the direction. Their average is 0, so "Schlechtester ⌀" would otherwise put every
    // ungraded subject above a genuine 4 — ranking by a number that doesn't exist.
    val sortedSubjects = remember(subjects, sort) {
        val withMeta = subjects.map { s ->
            val vals = s.grades.map { it.markDisplayValue }.filter { it > 0 }
            Triple(s, GradeMath.averageOf(vals), s.grades.maxOfOrNull { it.date } ?: 0)
        }
        val ungradedLast = compareBy<Triple<SubjectGrades, Double, Int>> { it.second <= 0 }
        when (sort) {
            GradesViewModel.SortMode.NAME ->
                // By the language's alphabet (Ä with A, accents ignored), as the web's localeCompare.
                java.text.Collator.getInstance(dev.plattnericus.pokyh.core.i18n.I18n.lang.value.locale).let { c -> withMeta.sortedWith { a, b -> c.compare(a.first.subjectName, b.first.subjectName) } }
            GradesViewModel.SortMode.AVG_DESC ->
                withMeta.sortedWith(ungradedLast.thenByDescending { it.second })
            GradesViewModel.SortMode.AVG_ASC ->
                withMeta.sortedWith(ungradedLast.thenBy { it.second })
            GradesViewModel.SortMode.RECENT ->
                withMeta.sortedWith(ungradedLast.thenByDescending { it.third })
        }.map { it.first to it.second }
    }

    val g = strings().grades
    var expanded by remember { mutableStateOf(setOf<Int>()) }
    val allExpanded = sortedSubjects.isNotEmpty() && expanded.size == sortedSubjects.size

    val colors = PokyhTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PokyhSpacing.screenH)
            .padding(top = 16.dp, bottom = 80.dp),
    ) {
        // The web's page head (grades/page.tsx, `.page-head`), stacked on phones: "Noten", the
        // school year with the summary under it, the year picker below; 28dp to the cards.
        Column(Modifier.fadeIn(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column {
                Text(
                    strings().nav.grades,
                    style = PokyhType.largeTitle.copy(fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = (-0.05).em),
                    color = colors.textPrimary,
                )
                Spacer(Modifier.size(8.dp))
                // Just the school year: the counts and dates are on the cards below.
                Text(
                    g.schoolYearTitle(year),
                    style = PokyhType.body.copy(fontSize = 15.sp, lineHeight = 22.sp),
                    color = colors.textSecondary,
                )
                AsOfLabel(savedAt = freshness.savedAt, stale = freshness.stale)
            }
            WebSelect(
                label = "$year / ${year + 1}",
                options = years.map { y -> "$y / ${y + 1}" to (y == year) },
                onPick = { i -> onYearChange(years[i]) },
                minWidth = 132.dp,
            )
        }
        Spacer(Modifier.size(28.dp))

        StatCardGrid(dashboard)
        Spacer(Modifier.size(44.dp))

        Column(modifier = Modifier.fadeIn(160)) {
            // `.timeline-head`, stacked on phones: "Fächer" at 22, then the sort and expand buttons.
            Text(
                g.subjects,
                style = PokyhType.title2.copy(fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.035).em),
                color = colors.textPrimary,
            )
            Spacer(Modifier.size(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val modes = GradesViewModel.SortMode.entries
                WebSelect(
                    label = sortLabel(sort),
                    options = modes.map { sortLabel(it) to (it == sort) },
                    onPick = { i -> onSortChange(modes[i]) },
                    minWidth = 150.dp,
                )
                WebButton(
                    text = if (allExpanded) g.collapseAll else g.expandAll,
                    onClick = {
                        expanded = if (allExpanded) emptySet() else sortedSubjects.map { it.first.lessonId }.toSet()
                    },
                )
            }
            Spacer(Modifier.size(14.dp))

            if (sortedSubjects.isEmpty()) {
                // Only reachable when WebUntis returns no lessons at all for the year — a
                // subject with zero grades is still listed (see UntisClient.grades).
                PokyhCard {
                    PixelState(title = g.emptyTitle, note = g.noSubjects, sprite = PixelSprite.Empty, compact = true)
                }
            } else {
                // `.timeline-list`: the surface, 26dp corners, 8dp in; rows of 18dp corners with
                // thin rules between them, inset 14dp.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(26.dp))
                        .background(colors.card)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    sortedSubjects.forEachIndexed { index, (subject, avg) ->
                        if (index > 0) {
                            Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(colors.separator))
                        }
                        SubjectExpandableRow(
                            subject = subject,
                            average = avg,
                            isExpanded = subject.lessonId in expanded,
                            onToggle = {
                                expanded = if (subject.lessonId in expanded) {
                                    expanded - subject.lessonId
                                } else {
                                    expanded + subject.lessonId
                                }
                            },
                            onOpenDetails = { onSubjectClick(subject.lessonId, year) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * The four cards as the pushed 2.1 grid: **square tiles**, two by two, 12dp apart. On a tablet
 * (720dp and wider) all four sit in one row instead, so a tile stays tile-sized there too.
 *
 * Every tile is as tall as it is wide. Only when the content truly needs more (a very large
 * system font) do all four grow together, so nothing is cut and they stay the same size.
 */
@Composable
private fun StatCardGrid(dashboard: GradesDashboard) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = PokyhSpacing.md
        val perRow = if (maxWidth >= 720.dp) 4 else 2
        androidx.compose.runtime.CompositionLocalProvider(LocalStatCompact provides true) {
            androidx.compose.ui.layout.Layout(
                content = {
                    AverageStatCard(dashboard = dashboard, modifier = Modifier.fadeIn())
                    RatioStatCard(dashboard = dashboard, modifier = Modifier.fadeIn(40))
                    DistributionStatCard(dashboard = dashboard, modifier = Modifier.fadeIn(80))
                    RecentStatCard(dashboard = dashboard, modifier = Modifier.fadeIn(120))
                },
            ) { measurables, constraints ->
                val gapPx = gap.roundToPx()
                val tileW = (constraints.maxWidth - gapPx * (perRow - 1)) / perRow
                // The distribution card is left out: its ring sizes itself to whatever height
                // the tile leaves, so it would otherwise ask for its own width again on top.
                val needed = measurables.filterIndexed { i, _ -> i != 2 }.maxOf { it.minIntrinsicHeight(tileW) }
                val tileH = maxOf(tileW, needed)
                val placeables = measurables.map { it.measure(androidx.compose.ui.unit.Constraints.fixed(tileW, tileH)) }
                val rows = (placeables.size + perRow - 1) / perRow
                layout(constraints.maxWidth, rows * tileH + (rows - 1) * gapPx) {
                    placeables.forEachIndexed { i, p ->
                        p.place(x = (i % perRow) * (tileW + gapPx), y = (i / perRow) * (tileH + gapPx))
                    }
                }
            }
        }
    }
}

/** The web's select button (grades/page.tsx, `.sort-select`): 40dp, the surface, 12dp corners, 14sp, a caret. */
@Composable
private fun WebSelect(label: String, options: List<Pair<String, Boolean>>, onPick: (Int) -> Unit, minWidth: androidx.compose.ui.unit.Dp) {
    var open by remember { mutableStateOf(false) }
    val caret by animateFloatAsState(if (open) 180f else 0f, tween(200), label = "selectCaret")
    Box {
        WebButton(
            text = label,
            onClick = { open = true },
            modifier = Modifier.widthIn(min = minWidth),
            trailing = { Icon(PokyhIcons.expandMenu, contentDescription = null, tint = PokyhTheme.colors.textPrimary, modifier = Modifier.size(14.dp).rotate(caret)) },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, (text, selected) ->
                val tone = dev.plattnericus.pokyh.ui.theme.PokyhDecorative.periwinkle(PokyhTheme.colors.isDark)
                DropdownMenuItem(
                    text = { Text(text, style = PokyhType.callout.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = if (selected) tone.ink else PokyhTheme.colors.textPrimary) },
                    // The chosen option on the periwinkle tone, as the web's `.custom-select-item.selected`.
                    modifier = Modifier.padding(horizontal = 6.dp).clip(RoundedCornerShape(10.dp)).background(if (selected) tone.fill else Color.Transparent),
                    onClick = { onPick(i); open = false },
                )
            }
        }
    }
}

/** The web's `.expand-all` / `.details-btn`: 40dp, the surface, 12dp corners, 14sp at 650. */
@Composable
private fun WebButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    fill: Color = PokyhTheme.colors.card,
    trailing: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .background(fill)
            .pressHighlight(interaction, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        // The web's `justify-content: space-between; gap: 8px`.
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text,
            style = PokyhType.callout.copy(fontSize = 14.sp, fontWeight = FontWeight(650)),
            color = PokyhTheme.colors.textPrimary,
            maxLines = 1,
        )
        if (trailing != null) {
            Box(Modifier.padding(start = 8.dp)) { trailing() }
        }
    }
}

/** The tone of a grade band, as the web colours averages (`.timeline-avg.v-*`): sage, butter, blush. */
@Composable
private fun bandTone(band: GradeBand) = when (band) {
    GradeBand.Excellent, GradeBand.Positive -> dev.plattnericus.pokyh.ui.theme.PokyhDecorative.sage(PokyhTheme.colors.isDark)
    GradeBand.Negative -> dev.plattnericus.pokyh.ui.theme.PokyhDecorative.butter(PokyhTheme.colors.isDark)
    GradeBand.Critical -> dev.plattnericus.pokyh.ui.theme.PokyhDecorative.blush(PokyhTheme.colors.isDark)
    GradeBand.Neutral -> dev.plattnericus.pokyh.ui.theme.DecorativeTone(PokyhTheme.colors.cardAlt, PokyhTheme.colors.textPrimary)
}

/** The web's `.grade-value` inks: `--success-ink`, `--warning-ink`, `--danger-ink`. */
@Composable
private fun valueInk(band: GradeBand): Color {
    val dark = PokyhTheme.colors.isDark
    return when (band) {
        GradeBand.Excellent, GradeBand.Positive -> if (dark) Color(0xFF34D399) else Color(0xFF047857)
        GradeBand.Negative -> if (dark) Color(0xFFFBBF24) else Color(0xFFA15C07)
        GradeBand.Critical -> PokyhTheme.colors.dangerText
        GradeBand.Neutral -> PokyhTheme.colors.textPrimary
    }
}

/** The dashboard's loading state: the four cards and the Fächer list, in outline. */
@Composable
private fun GradesSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PokyhSpacing.screenH)
            .padding(top = 16.dp, bottom = PokyhSpacing.xxxl),
        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.section),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SkeletonBlock(height = 36.dp, width = 160.dp)
            SkeletonBlock(height = 14.dp, width = 260.dp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(2) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) {
                    StatCardSkeleton(modifier = Modifier.weight(1f).aspectRatio(1f), tall = true)
                    StatCardSkeleton(modifier = Modifier.weight(1f).aspectRatio(1f), tall = true)
                }
            }
        }
        Column {
            SkeletonBlock(height = 18.dp, width = 90.dp)
            Spacer(Modifier.size(PokyhSpacing.headerContent))
            PokyhCard(padding = 0.dp) {
                repeat(5) { index ->
                    if (index > 0) PokyhRowSeparator(startInset = 0.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = PokyhSpacing.card),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
                    ) {
                        SkeletonBlock(height = 24.dp, width = 84.dp, shape = PokyhShapes.xs)
                        SkeletonBlock(height = 10.dp, width = 52.dp)
                        Spacer(Modifier.weight(1f))
                        SkeletonBlock(height = 16.dp, width = 30.dp)
                    }
                }
            }
        }
    }
}

/**
 * A subject row that expands in place to list its grades — the web's `timeline-item`.
 *
 * **The subject's name is the coloured block, and the row is one line.** The name sits on its
 * own [subjectTone] fill — the same tint the Stundenplan gives that subject's cells, so a
 * subject is recognisable by the same colour in both places — with its grade count beside it.
 *
 * Two earlier attempts are worth not repeating. A leading badge held an abbreviation of the name
 * while the full name sat right next to it: the same word twice, once truncated. Stacking the
 * tinted name over its count then left every row with a chip floating above a caption, each chip
 * a different width, and the value and chevron stranded across a wide gap — a list that looked
 * like scattered labels rather than rows. On one line the chips share a baseline, the counts
 * line up behind them, and the row has a single centre.
 *
 * The average is coloured by its [bandColor] band, like every grade value on the screen.
 */
@Composable
private fun SubjectExpandableRow(
    subject: SubjectGrades,
    average: Double,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    val colors = PokyhTheme.colors
    val tone = subjectTone(subject.subjectName, colors.isDark)
    val interactionSource = remember { MutableInteractionSource() }
    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(PokyhMotion.durationFast),
        label = "subjectChevron",
    )

    val rowShape = RoundedCornerShape(18.dp)
    Column(modifier = Modifier.fillMaxWidth()) {
        // `.timeline-row`: 64dp, 12dp in, the subject on its tone, the average in a band-toned pill.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(rowShape)
                .pressHighlight(interactionSource, rowShape)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onToggle)
                .heightIn(min = 64.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.weight(1f)) {
                Text(
                    text = subject.subjectName,
                    style = PokyhType.callout.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Bold),
                    color = tone.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(tone.fill)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            val band = if (average > 0) bandTone(gradeBand(average)) else null
            Box(
                Modifier
                    .widthIn(min = 58.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(band?.fill ?: colors.cardAlt)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (average > 0) Fmt.num(average) else "-",
                    style = PokyhType.headline.copy(fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold).monospacedDigits(),
                    color = band?.ink ?: colors.textSecondary,
                )
            }
            Icon(
                imageVector = PokyhIcons.expandMenu,
                contentDescription = if (isExpanded) strings().grades.collapse else strings().grades.expand,
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp).rotate(chevronRotation),
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = animateFadeIn(tween(PokyhMotion.durationFast)) +
                expandVertically(tween(PokyhMotion.durationStandard)),
            exit = fadeOut(tween(PokyhMotion.durationFast)) +
                shrinkVertically(tween(PokyhMotion.durationStandard)),
        ) {
            // `.timeline-panel`: the inset tone, 18dp corners, the grades and "Details" on the surface.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 8.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.cardAlt)
                    .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp),
            ) {
                if (subject.grades.isEmpty()) {
                    Text(
                        text = strings().grades.noSingleGrades,
                        style = PokyhType.footnote.copy(fontSize = 14.sp),
                        color = colors.textTertiary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    subject.grades.sortedByDescending { it.date }.forEach { grade ->
                        GradeDetailRow(grade)
                    }
                }
                Spacer(Modifier.size(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    WebButton(
                        text = strings().grades.openDetails,
                        onClick = onOpenDetails,
                        trailing = { Icon(PokyhIcons.chevronRight, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(14.dp)) },
                    )
                }
            }
        }
    }
}

/** One grade inside an expanded subject: date, what it was, value. */
@Composable
private fun GradeDetailRow(grade: GradeEntry) {
    val colors = PokyhTheme.colors
    // `.grade-row` on phones: 60dp date, the name, the mark; 12dp apart, 9dp above and below.
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = Fmt.dateShort(grade.date),
            style = PokyhType.caption.copy(fontSize = 13.sp).monospacedDigits(),
            color = colors.textSecondary,
            modifier = Modifier.width(60.dp),
        )
        Text(
            text = gradeDisplay(grade),
            style = PokyhType.footnote.copy(fontSize = 14.5.sp, fontWeight = FontWeight(550)),
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = Fmt.num(grade.markDisplayValue),
            style = PokyhType.headline.copy(fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold).monospacedDigits(),
            color = valueInk(gradeBand(grade.markDisplayValue)),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            modifier = Modifier.widthIn(min = 48.dp),
        )
    }
}

@Composable
private fun sortLabel(mode: GradesViewModel.SortMode): String {
    val g = strings().grades
    return when (mode) {
        GradesViewModel.SortMode.NAME -> g.sortName
        GradesViewModel.SortMode.AVG_DESC -> g.sortBest
        GradesViewModel.SortMode.AVG_ASC -> g.sortWorst
        GradesViewModel.SortMode.RECENT -> g.sortRecent
    }
}

/**
 * `gradeDisplay(_:)` — falls back through text -> markName -> examType -> "Prüfung", and treats a
 * label that's really just the mark again ("7", "6.5", "7/10") as no label at all, matching the
 * web's `isGradeLike` guard.
 */
fun gradeDisplay(g: GradeEntry): String {
    val raw = g.text.ifEmpty { g.markName.ifEmpty { g.examType } }.trim()
    if (raw.isEmpty()) return I18n.s.grades.exam
    val normalized = raw.replace(',', '.').replace(Regex("[−–—]"), "-").trim()
    val gradeLike = Regex("""^(?:\d{1,2}(?:\.\d{1,2})?[+\-]?|\d{1,2}/\d{1,2})$""")
    return if (gradeLike.matches(normalized)) I18n.s.grades.exam else raw
}
