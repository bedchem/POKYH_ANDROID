package dev.plattnericus.pokyh.ui.home

import androidx.compose.ui.graphics.vector.ImageVector
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import kotlinx.serialization.Serializable
import dev.plattnericus.pokyh.core.i18n.I18n

/**
 * One block (widget) of the Home screen.
 *
 * The enum — not the composables — is what Home is built from: the screen renders whatever
 * [HomeLayout.visible] lists, in that order. Adding a widget is adding a case here plus a branch
 * in `HomeSectionContent`, and nothing else needs to know the order exists.
 *
 * **The name of a case is persisted.** [HomeLayout] is stored by \[name\], so renaming a case
 * silently drops it out of every existing user's saved layout (it no longer parses, and
 * [HomeLayout.sanitized] treats it as unknown). Add and deprecate; don't rename. `NextLesson`
 * was used once and dropped — don't reuse it.
 *
 * [defaultVisible] decides what a widget does the first time a layout meets it: the original five
 * are on for everyone, anything added later starts in "Widgets hinzufügen" so an update never
 * rearranges somebody's Home on its own.
 */
enum class HomeSection(
    val glyph: ImageVector,
    val defaultVisible: Boolean,
) {
    // The web's dashboard, in its order: quick links, the next exam, the latest grades, the
    // messages, the Mensa.
    Shortcuts(PokyhIcons.decorSubjects, true),
    Exam(PokyhIcons.exam, true),
    Grades(PokyhIcons.tabGrades, true),
    Messages(PokyhIcons.messages, true),
    Mensa(PokyhIcons.mensa, true),
    // No longer one of the standard widgets; it stays in "Widgets hinzufügen".
    Today(PokyhIcons.tabTimetable, false),
    NowNext(PokyhIcons.clock, false),
    Changes(PokyhIcons.absences, false),
    Average(PokyhIcons.gradeTrend, false),
    OpenTodos(PokyhIcons.todos, false),
    UpcomingReminders(PokyhIcons.reminders, false),
    ;

    /** The widget's name, in the language in use. */
    val title: String
        get() = I18n.s.home.let { t ->
            when (this) {
                Shortcuts -> t.sectionShortcuts
                Exam -> t.sectionExam
                Today -> t.sectionToday
                Mensa -> t.sectionMensa
                Grades -> t.sectionGrades
                Messages -> t.sectionMessages
                NowNext -> t.sectionNowNext
                Changes -> t.sectionChanges
                Average -> t.sectionAverage
                OpenTodos -> t.sectionTodos
                UpcomingReminders -> t.sectionReminders
            }
        }

    /** One line for the "Widgets hinzufügen" list — what the widget is *for*. */
    val description: String
        get() = I18n.s.home.let { t ->
            when (this) {
                Shortcuts -> t.sectionShortcutsInfo
                Exam -> t.sectionExamInfo
                Today -> t.sectionTodayInfo
                Mensa -> t.sectionMensaInfo
                Grades -> t.sectionGradesInfo
                Messages -> t.sectionMessagesInfo
                NowNext -> t.sectionNowNextInfo
                Changes -> t.sectionChangesInfo
                Average -> t.sectionAverageInfo
                OpenTodos -> t.sectionTodosInfo
                UpcomingReminders -> t.sectionRemindersInfo
            }
        }

    companion object {
        /** The layout a fresh install gets: the day in the order it happens, quick links on top. */
        val defaultOrder: List<HomeSection> = entries.filter { it.defaultVisible }

        fun parse(raw: String): HomeSection? = entries.firstOrNull { it.name == raw }
    }
}

/**
 * Which Home widgets are shown, and in what order.
 *
 * [order] is the arrangement of the shown ones; [hidden] is what the user took off. A widget in
 * neither is one this layout has never seen — see [HomeSection.defaultVisible]. Both lists are
 * stored by name, and [hidden] defaults to empty, so a layout saved before widgets could be
 * removed still reads back as "everything shown, in that order".
 */
@Serializable
data class HomeLayout(
    val order: List<String> = HomeSection.defaultOrder.map { it.name },
    val hidden: List<String> = emptyList(),
) {
    private val hiddenSections: Set<HomeSection>
        get() = hidden.mapNotNull { HomeSection.parse(it) }.toSet()

    /**
     * Every widget exactly once, in the saved order: saved ones first, then ones this layout has
     * never seen. Unknown names (from an older or newer build) fall out.
     */
    val sanitized: List<HomeSection>
        get() {
            val known = order.mapNotNull { HomeSection.parse(it) }.distinct()
            val missing = HomeSection.entries.filter { it !in known }
            return known + missing
        }

    /** The widgets to render, in order. */
    val visible: List<HomeSection>
        get() {
            val placed = order.mapNotNull { HomeSection.parse(it) }.toSet()
            val off = hiddenSections
            return sanitized.filter { it !in off && (it in placed || it.defaultVisible) }
        }

    /** What the "Widgets hinzufügen" list offers — everything not on Home, in enum order. */
    val available: List<HomeSection>
        get() = HomeSection.entries.filter { it !in visible }

    /** A new arrangement of the shown widgets; what is hidden stays hidden. */
    fun withOrder(shown: List<HomeSection>): HomeLayout = copy(order = shown.map { it.name })

    fun removed(section: HomeSection): HomeLayout = HomeLayout(
        order = visible.filter { it != section }.map { it.name },
        hidden = (hiddenSections + section).map { it.name },
    )

    /** Added at the end — the one place the user is looking when they tap it. */
    fun added(section: HomeSection): HomeLayout = HomeLayout(
        order = (visible.filter { it != section } + section).map { it.name },
        hidden = (hiddenSections - section).map { it.name },
    )

    /** [from] and [to] index into [visible]. */
    fun moved(from: Int, to: Int): HomeLayout {
        val list = visible.toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return this
        list.add(to, list.removeAt(from))
        return withOrder(list)
    }

    fun reset(): HomeLayout = HomeLayout()

    /**
     * "Heute" stopped being a standard widget. A layout still exactly on the old standard set
     * (never arranged by hand) loses it like a fresh install; a Home someone arranged keeps it.
     */
    fun withoutOldDefaultToday(): HomeLayout = when {
        hidden.isEmpty() && order == OldDefaultOrder -> HomeLayout()
        // Never rearranged since the last standard: it becomes the web's dashboard.
        hidden.isEmpty() && order == PreviousDefaultOrder -> HomeLayout()
        else -> this
    }
}

/** The standard widgets while "Heute" was one of them. */
private val OldDefaultOrder = listOf("Shortcuts", "Exam", "Today", "Mensa", "Grades")

/** The standard widgets before the messages joined them. */
private val PreviousDefaultOrder = listOf("Shortcuts", "Exam", "Mensa", "Grades")
