package dev.plattnericus.pokyh.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.plattnericus.pokyh.core.i18n.I18n
import dev.plattnericus.pokyh.core.i18n.strings
import dev.plattnericus.pokyh.core.util.Fmt
import dev.plattnericus.pokyh.core.util.SchoolDates
import dev.plattnericus.pokyh.data.backend.BackendClient
import dev.plattnericus.pokyh.data.model.MessageFolder
import dev.plattnericus.pokyh.data.model.MessagePreview
import dev.plattnericus.pokyh.data.model.SubjectGrades
import dev.plattnericus.pokyh.data.model.TimetableEntry
import dev.plattnericus.pokyh.data.storage.OfflineStore
import dev.plattnericus.pokyh.data.untis.UntisClient
import dev.plattnericus.pokyh.state.AppState
import dev.plattnericus.pokyh.ui.components.PokyhIconButton
import dev.plattnericus.pokyh.ui.components.PokyhLabel
import dev.plattnericus.pokyh.ui.components.PokyhTextField
import dev.plattnericus.pokyh.ui.navigation.PokyhDestinations
import dev.plattnericus.pokyh.ui.theme.DecorativeTone
import dev.plattnericus.pokyh.ui.theme.PokyhDecorative
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhThemeMode
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.appBackground
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import androidx.compose.ui.draw.clip

/** What the search finds inside the pages (web components/app/searchContent.ts). */
data class ContentDoc(
    val id: String,
    val source: Source,
    val title: String,
    val subtitle: String = "",
    val keywords: String = "",
    /** A route, or a timetable jump (date, lesson). */
    val route: String? = null,
    val jump: Pair<Int, Int?>? = null,
)

enum class Source { ClassService, Classreg, Homework, Reminder, Todo, Subject, Grade, Lesson, Message, Dish }

@HiltViewModel
class SearchViewModel @Inject constructor(
    val appState: AppState,
    private val untisClient: UntisClient,
    private val backendClient: BackendClient,
    private val offlineStore: OfflineStore,
) : ViewModel() {
    private val _content = MutableStateFlow<Map<String, List<ContentDoc>>>(emptyMap())
    val content: StateFlow<Map<String, List<ContentDoc>>> = _content.asStateFlow()

    private fun emit(name: String, docs: List<ContentDoc>) = _content.update { it + (name to docs) }

    private fun source(name: String, task: suspend () -> List<ContentDoc>) {
        viewModelScope.launch { emit(name, runCatching { task() }.getOrDefault(emptyList())) }
    }

    private fun day(d: Int, pattern: String = "EEE d. MMM"): String = runCatching {
        val l = I18n.lang.value
        java.time.LocalDate.of(d / 10000, d / 100 % 100, d % 100)
            .format(java.time.format.DateTimeFormatter.ofPattern(if (l == dev.plattnericus.pokyh.core.i18n.Lang.De) pattern else pattern.replace(". ", " "), l.locale))
    }.getOrDefault("")

    private fun strip(s: String) = s.replace(Regex("<[^>]*>"), " ").replace("&nbsp;", " ").replace(Regex("\\s+"), " ").trim()

    init {
        val s = appState.session.value
        if (s != null) {
            if (s.hasUntis) {
                source("grades") {
                    val key = "grades-${s.studentId}-${SchoolDates.currentSchoolYear}"
                    val list = offlineStore.peek(key, ListSerializer(SubjectGrades.serializer()))?.value.orEmpty()
                    list.flatMap { sub ->
                        val route = PokyhDestinations.gradeSubject(sub.id, SchoolDates.currentSchoolYear)
                        listOf(ContentDoc("subject:${sub.id}", Source.Subject, sub.subjectName, sub.teacherName, route = route)) +
                            sub.grades.filter { it.markDisplayValue > 0 }.map { g ->
                                ContentDoc(
                                    "grade:${g.id}", Source.Grade,
                                    "${g.markName.ifEmpty { Fmt.fixed(g.markDisplayValue, 2) }} · ${sub.subjectName}",
                                    listOf(g.examType, g.text, day(g.date)).filter { it.isNotBlank() }.joinToString(" · "),
                                    route = route,
                                )
                            }
                    }
                }
                source("lessons") {
                    val monday = dev.plattnericus.pokyh.core.util.schoolWeekMonday()
                    val list = offlineStore.peek("tt-${s.studentId}-$monday", ListSerializer(TimetableEntry.serializer()))?.value.orEmpty()
                    list.filterNot { it.isCancelled }.distinctBy { "${it.date}-${it.startTime}" }.map { e ->
                        ContentDoc(
                            "lesson:${e.date}-${e.startTime}", Source.Lesson,
                            e.subjectLong.ifEmpty { e.subjectName.ifEmpty { e.note ?: "–" } },
                            listOf("${day(e.date, "EEE")} ${Fmt.time(e.startTime)}", e.roomName, e.teacherLongName ?: e.teacherName).filter { it.isNotBlank() }.joinToString(" · "),
                            keywords = "${e.subjectName} ${e.teacherName} ${e.note.orEmpty()}",
                            jump = e.date to e.id,
                        )
                    }
                }
                source("messages") {
                    val list = offlineStore.peek("messages-${s.username.lowercase()}-${MessageFolder.Inbox.rawValue}", ListSerializer(MessagePreview.serializer()))?.value
                        ?: runCatching { untisClient.messages(s, MessageFolder.Inbox) }.getOrDefault(emptyList())
                    list.map { m -> ContentDoc("msg:${m.id}", Source.Message, m.subject.ifBlank { "–" }, m.senderName, strip(m.contentPreview), route = PokyhDestinations.messageDetail(m.id, reply = true)) }
                }
                source("services") {
                    untisClient.classServices(s).map { c -> ContentDoc("duty:${c.id}-${c.personName}", Source.ClassService, c.duty.ifBlank { "–" }, c.personName, route = PokyhDestinations.CLASSROOM) }
                }
                source("classreg") {
                    untisClient.classregEvents(s, s.studentId).map { e ->
                        ContentDoc(
                            "event:${e.id}", Source.Classreg, e.text.ifBlank { e.categoryName.ifBlank { e.eventReasonName.ifBlank { "–" } } },
                            listOf(e.categoryName, e.subjectName, day(e.createDate)).filter { it.isNotBlank() }.joinToString(" · "),
                            keywords = "${e.eventReasonName} ${e.creatorName}",
                            route = PokyhDestinations.CLASSREG_EVENTS,
                        )
                    }
                }
                source("homework") {
                    val monday = dev.plattnericus.pokyh.core.util.schoolWeekMonday()
                    val from = monday.year * 10000 + monday.monthNumber * 100 + monday.dayOfMonth
                    val end = java.time.LocalDate.of(monday.year, monday.monthNumber, monday.dayOfMonth).plusDays(11)
                    untisClient.homeworks(s, from, end.year * 10000 + end.monthValue * 100 + end.dayOfMonth).filter { it.text.isNotBlank() }.map { h ->
                        ContentDoc("hw:${h.id}", Source.Homework, strip(h.text), listOf(h.subject, day(h.dueDate)).filter { it.isNotBlank() }.joinToString(" · "), route = PokyhDestinations.CLASSROOM)
                    }
                }
            }
            val token = s.apiToken
            val classId = s.classId
            if (token != null && classId != null) source("reminders") {
                backendClient.reminders(classId, token).map { r ->
                    ContentDoc("rem:${r.id}", Source.Reminder, r.title, r.createdByName, r.body, route = PokyhDestinations.reminderDetail(r.id))
                }
            }
            if (token != null) source("todos") {
                backendClient.todos(s.username, token).map { td -> ContentDoc("todo:${td.id}", Source.Todo, td.title, keywords = td.details, route = PokyhDestinations.TODOS) }
            }
            source("mensa") {
                val today = java.time.LocalDate.now().toString()
                backendClient.dishes().filter { it.date.take(10) >= today }.map { d ->
                    val date = d.date.take(10).replace("-", "").toIntOrNull() ?: 0
                    ContentDoc("dish:${d.id}", Source.Dish, d.name, day(date, "EEEE d. MMM"), "${d.category} ${d.tags.joinToString(" ")}", route = PokyhDestinations.dishDetail(d.id))
                }
            }
        }
    }
}

private fun fold(s: String): String = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()

/** The web's scoring: the title's start counts most, then a word in it, then anywhere in it, then the rest. */
private fun score(title: String, rest: String, words: List<String>): Int {
    val t = fold(title)
    val r = fold(rest)
    var total = 0
    for (w in words) {
        total += when {
            t.startsWith(w) -> 4
            t.contains(" $w") || t.contains("-$w") -> 3
            t.contains(w) -> 2
            r.contains(w) -> 1
            else -> return 0
        }
    }
    return total
}

private class PageItem(val id: String, val title: String, val icon: ImageVector, val tone: DecorativeTone?, val keywords: String, val run: () -> Unit)

/**
 * The search, as the web's palette: "Seiten" — the places and actions of the app — and
 * "Inhalte" — what is on those pages, each row naming the page it comes from. Everything is
 * matched on the device, word by word and without accents.
 */
@Composable
fun SearchScreen(onNavigateBack: () -> Unit, onNavigate: (String) -> Unit, viewModel: SearchViewModel = hiltViewModel()) {
    val t = strings().search
    val nav = strings().nav
    val content by viewModel.content.collectAsStateWithLifecycle()
    val session by viewModel.appState.session.collectAsStateWithLifecycle()
    val themeMode by viewModel.appState.themeMode.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val dark = PokyhTheme.colors.isDark
    val untis = session?.hasUntis != false

    fun go(route: String) { onNavigateBack(); onNavigate(route) }
    fun tab(tab: dev.plattnericus.pokyh.ui.navigation.AppTab) { onNavigateBack(); viewModel.appState.selectTab(tab) }

    val pages = buildList {
        val peri = PokyhDecorative.periwinkle(dark)
        add(PageItem("home", nav.home, PokyhIcons.tabHome, peri, "dashboard start übersicht panoramica") { tab(dev.plattnericus.pokyh.ui.navigation.AppTab.Home) })
        if (untis) add(PageItem("tt", nav.timetable, PokyhIcons.tabTimetable, peri, "plan stunden lessons orario") { tab(dev.plattnericus.pokyh.ui.navigation.AppTab.Timetable) })
        if (untis) add(PageItem("grades", nav.grades, PokyhIcons.tabGrades, peri, "voti marks durchschnitt media") { tab(dev.plattnericus.pokyh.ui.navigation.AppTab.Grades) })
        if (untis) add(PageItem("msg", nav.messages, PokyhIcons.messages, peri, "mail post inbox messaggi") { go(PokyhDestinations.MESSAGES) })
        add(PageItem("mensa", nav.mensa, PokyhIcons.tabMensa, PokyhDecorative.butter(dark), "essen menu speiseplan cafeteria") { tab(dev.plattnericus.pokyh.ui.navigation.AppTab.Mensa) })
        if (untis) add(PageItem("abs", strings().school.absences, PokyhIcons.decorAbsences, PokyhDecorative.butter(dark), "fehlstunden assenze") { go(PokyhDestinations.ABSENCES) })
        if (untis) add(PageItem("creg", strings().school.classreg, PokyhIcons.decorClassRegister, PokyhDecorative.butter(dark), "klassenbuch registro einträge vermerk") { go(PokyhDestinations.CLASSREG_EVENTS) })
        add(PageItem("todos", strings().school.todos, PokyhIcons.decorTodos, PokyhDecorative.sage(dark), "aufgaben tasks") { go(PokyhDestinations.TODOS) })
        add(PageItem("rem", strings().school.reminders, PokyhIcons.decorReminders, PokyhDecorative.blush(dark), "termine promemoria") { go(PokyhDestinations.REMINDERS) })
        if (untis) add(PageItem("class", strings().school.klasse, PokyhIcons.decorClassMembers, PokyhDecorative.sage(dark), "mitschüler classe klassendienste dienste hausaufgaben") { go(PokyhDestinations.CLASSROOM) })
        add(PageItem("school", nav.school, PokyhIcons.tabSchool, peri, "hub übersicht") { tab(dev.plattnericus.pokyh.ui.navigation.AppTab.School) })
        add(PageItem("profile", strings().profile.title, PokyhIcons.person, peri, "konto account") { go(PokyhDestinations.PROFILE) })
        add(PageItem("stats", t.profileStats, PokyhIcons.decorSubjects, peri, "aktivität activity online tage anmeldungen") { go(PokyhDestinations.PROFILE_STATS) })
        add(PageItem("settings", strings().settings.title, PokyhIcons.settings, PokyhDecorative.sage(dark), "einstellungen impostazioni darstellung theme") { go(PokyhDestinations.SETTINGS) })
        if (untis) add(PageItem("compose", t.actionCompose, PokyhIcons.compose, null, "nachricht schreiben senden lehrer") { go(PokyhDestinations.messagesCompose()) })
        if (untis) add(PageItem("report", t.actionReport, PokyhIcons.decorAbsences, null, "krank fehlen abwesend") { go(PokyhDestinations.absencesReport()) })
        add(PageItem("theme", t.actionTheme, PokyhIcons.themeDark, null, "dark mode dunkel hell theme farbe") {
            viewModel.appState.setThemeMode(if (dark) PokyhThemeMode.Light else PokyhThemeMode.Dark)
        })
        if (untis) add(PageItem("password", t.actionPassword, PokyhIcons.password, null, "passwort password reset vergessen") { go(PokyhDestinations.SETTINGS) })
        add(PageItem("lang", t.actionLanguage, PokyhIcons.language, null, "sprache lingua deutsch italiano english") { go(PokyhDestinations.SETTINGS) })
    }
    val words = fold(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
    val pageRows = if (words.isEmpty()) pages else pages.map { it to score(it.title, it.keywords, words) }.filter { it.second > 0 }.sortedByDescending { it.second }.take(6).map { it.first }
    val contentRows = if (words.isEmpty()) emptyList() else content.values.flatten()
        .map { it to score(it.title, "${it.subtitle} ${it.keywords} ${sourceLabel(it.source)}", words) }
        .filter { it.second > 0 }.sortedByDescending { it.second }.take(40).map { it.first }

    Scaffold(modifier = Modifier.appBackground(), containerColor = PokyhTheme.colors.bg) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = PokyhSpacing.screenH, vertical = PokyhSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
            ) {
                PokyhIconButton(icon = PokyhIcons.back, contentDescription = strings().common.back, onClick = onNavigateBack)
                PokyhTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = t.placeholder,
                    leadingIcon = PokyhIcons.search,
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = PokyhSpacing.screenH, end = PokyhSpacing.screenH, bottom = PokyhSpacing.xxxl),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (words.isEmpty()) item(key = "hint") { Text(t.empty, style = PokyhType.footnote, color = PokyhTheme.colors.textTertiary, modifier = Modifier.padding(vertical = PokyhSpacing.sm)) }
                if (pageRows.isNotEmpty()) {
                    item(key = "h-pages") { PokyhLabel(t.pages, Modifier.padding(top = PokyhSpacing.md, bottom = PokyhSpacing.xs)) }
                    items(pageRows, key = { "p-" + it.id }) { p -> ResultRow(p.title, null, null, p.icon, p.tone, p.run) }
                }
                if (contentRows.isNotEmpty()) {
                    item(key = "h-content") { PokyhLabel(t.contents, Modifier.padding(top = PokyhSpacing.lg, bottom = PokyhSpacing.xs)) }
                    items(contentRows, key = { "c-" + it.id }) { d ->
                        ResultRow(d.title, d.subtitle, sourceLabel(d.source), sourceIcon(d.source), sourceTone(d.source, dark)) {
                            when {
                                d.jump != null -> { onNavigateBack(); viewModel.appState.openTimetableAt(d.jump.first, d.jump.second) }
                                d.route != null -> go(d.route)
                            }
                        }
                    }
                }
                if (words.isNotEmpty() && pageRows.isEmpty() && contentRows.isEmpty()) {
                    item(key = "none") { Text(t.noResults, style = PokyhType.callout, color = PokyhTheme.colors.textTertiary, modifier = Modifier.padding(vertical = PokyhSpacing.xl)) }
                }
            }
        }
    }
}

private fun sourceLabel(s: Source): String = I18n.s.search.let { t ->
    when (s) {
        Source.ClassService -> t.srcClassService
        Source.Classreg -> t.srcClassreg
        Source.Homework -> t.srcHomework
        Source.Reminder -> t.srcReminder
        Source.Todo -> t.srcTodo
        Source.Subject -> t.srcSubject
        Source.Grade -> t.srcGrade
        Source.Lesson -> t.srcLesson
        Source.Message -> t.srcMessage
        Source.Dish -> t.srcDish
    }
}

private fun sourceIcon(s: Source): ImageVector = when (s) {
    Source.ClassService, Source.Homework -> PokyhIcons.decorClassMembers
    Source.Classreg -> PokyhIcons.decorClassRegister
    Source.Reminder -> PokyhIcons.decorReminders
    Source.Todo -> PokyhIcons.decorTodos
    Source.Subject, Source.Grade -> PokyhIcons.tabGrades
    Source.Lesson -> PokyhIcons.tabTimetable
    Source.Message -> PokyhIcons.messages
    Source.Dish -> PokyhIcons.tabMensa
}

private fun sourceTone(s: Source, dark: Boolean): DecorativeTone = when (s) {
    Source.ClassService, Source.Homework, Source.Todo -> PokyhDecorative.sage(dark)
    Source.Classreg, Source.Dish -> PokyhDecorative.butter(dark)
    Source.Reminder -> PokyhDecorative.blush(dark)
    else -> PokyhDecorative.periwinkle(dark)
}

@Composable
private fun ResultRow(title: String, subtitle: String?, tag: String?, icon: ImageVector, tone: DecorativeTone?, onClick: () -> Unit) {
    val colors = PokyhTheme.colors
    Row(
        Modifier.fillMaxWidth().clip(PokyhShapes.lg).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.iconText),
    ) {
        Box(Modifier.size(38.dp).background(tone?.fill ?: colors.nested, PokyhShapes.md), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tone?.ink ?: colors.textPrimary, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = PokyhType.callout.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = PokyhType.footnote, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (tag != null) {
            Text(tag, style = PokyhType.caption2.copy(fontWeight = FontWeight.SemiBold), color = colors.textTertiary)
        }
    }
}

