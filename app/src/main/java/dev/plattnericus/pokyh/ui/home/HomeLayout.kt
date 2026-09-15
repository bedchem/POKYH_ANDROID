package dev.plattnericus.pokyh.ui.home

import androidx.compose.ui.graphics.vector.ImageVector
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import kotlinx.serialization.Serializable

/**
 * One block of the Home screen.
 *
 * The enum — not the composables — is what Home is built from: the screen renders whatever
 * [HomeLayout.order] lists, in that order, and asks this enum what each entry is called. Adding
 * a block to Home is therefore adding a case here plus a branch in `HomeSectionContent`, and
 * nothing else needs to know the order exists.
 *
 * **The name of a case is persisted.** [HomeLayout] is stored by \[name\], so renaming a case
 * silently drops it out of every existing user's saved layout (it no longer parses, and
 * [HomeLayout.sanitized] treats it as unknown). Add and deprecate; don't rename.
 */
enum class HomeSection(
    val title: String,
    /** What this block is, in the editor's own words — the user is choosing between blocks
     * there, not looking at them, so each needs to say what it would show. */
    val editorDescription: String,
    val glyph: ImageVector,
) {
    Shortcuts(
        title = "Schnellzugriff",
        editorDescription = "Noten, Abwesenheiten, Todos und Erinnerungen",
        glyph = PokyhIcons.decorSubjects,
    ),
    NextLesson(
        title = "Nächste Stunde",
        editorDescription = "Die Stunde, die als Nächstes beginnt",
        glyph = PokyhIcons.timetable,
    ),
    Exam(
        title = "Nächste Schularbeit",
        editorDescription = "Die nächste anstehende Prüfung",
        glyph = PokyhIcons.exam,
    ),
    Today(
        title = "Heute",
        editorDescription = "Alle Stunden des heutigen Tages",
        glyph = PokyhIcons.tabTimetable,
    ),
    Mensa(
        title = "Mensa",
        editorDescription = "Der Speiseplan der Woche",
        glyph = PokyhIcons.mensa,
    ),
    Grades(
        title = "Zuletzt eingetragen",
        editorDescription = "Die neuesten Noten",
        glyph = PokyhIcons.tabGrades,
    ),
    ;

    companion object {
        /**
         * The layout a fresh install gets: the day in the order it happens, with the quick
         * links on top.
         *
         * [NextLesson] is deliberately not in it. It overlaps [Today] (which already lists the
         * same lessons), so having both on by default would show the same information twice —
         * it exists for people who would rather have the one next thing than the whole day, and
         * the editor is where they say so.
         */
        val defaultOrder: List<HomeSection> = listOf(Shortcuts, Exam, Today, Mensa, Grades)

        fun parse(raw: String): HomeSection? = entries.firstOrNull { it.name == raw }
    }
}

/**
 * Which Home blocks are shown, and in what order.
 *
 * Order and visibility are stored separately rather than as one "visible list": a hidden block
 * keeps its place, so turning it back on puts it where it was instead of at the bottom. That is
 * the difference between a toggle and a delete, and the editor offers a toggle.
 */
@Serializable
data class HomeLayout(
    val order: List<String> = HomeSection.defaultOrder.map { it.name },
    val hidden: Set<String> = emptySet(),
) {
    /**
     * The layout as the screen should use it: known sections only, every section present
     * exactly once, in the saved order with anything new appended.
     *
     * Everything here is a *forward-compatibility* concern rather than defensive padding. A
     * layout saved by an older build won't mention a section added since, and a layout saved by
     * a newer one may mention a section this build doesn't have — both have to resolve to
     * something sensible instead of a Home screen with a missing block or a crash.
     */
    val sanitized: List<HomeSection>
        get() {
            val known = order.mapNotNull { HomeSection.parse(it) }.distinct()
            val missing = HomeSection.entries.filter { it !in known }
            return known + missing
        }

    /** The sections to render, in order. */
    val visible: List<HomeSection> get() = sanitized.filter { it.name !in hidden }

    fun isVisible(section: HomeSection): Boolean = section.name !in hidden

    fun toggled(section: HomeSection): HomeLayout = copy(
        hidden = if (section.name in hidden) hidden - section.name else hidden + section.name,
        // Persist the full resolved order alongside the change, so a layout written by this
        // build always names every section this build knows about.
        order = sanitized.map { it.name },
    )

    /**
     * [from] and [to] index into [sanitized] — i.e. into the editor's list, which shows hidden
     * sections too. Moving is independent of visibility on purpose: you arrange the shelf, then
     * decide what sits on it.
     */
    fun moved(from: Int, to: Int): HomeLayout {
        val list = sanitized.toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return this
        list.add(to, list.removeAt(from))
        return copy(order = list.map { it.name })
    }

    fun reset(): HomeLayout = HomeLayout()
}
