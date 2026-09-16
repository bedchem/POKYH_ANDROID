package dev.plattnericus.pokyh.ui.home

import androidx.compose.ui.graphics.vector.ImageVector
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import kotlinx.serialization.Serializable

/**
 * One block of the Home screen.
 *
 * The enum — not the composables — is what Home is built from: the screen renders whatever
 * [HomeLayout.order] lists, in that order. Adding a block to Home is therefore adding a case here
 * plus a branch in `HomeSectionContent`, and nothing else needs to know the order exists.
 *
 * **The name of a case is persisted.** [HomeLayout] is stored by \[name\], so renaming a case
 * silently drops it out of every existing user's saved layout (it no longer parses, and
 * [HomeLayout.sanitized] treats it as unknown). Add and deprecate; don't rename.
 */
enum class HomeSection(
    val title: String,
    val glyph: ImageVector,
) {
    Shortcuts(title = "Schnellzugriff", glyph = PokyhIcons.decorSubjects),
    Exam(title = "Nächste Schularbeit", glyph = PokyhIcons.exam),
    Today(title = "Heute", glyph = PokyhIcons.tabTimetable),
    Mensa(title = "Mensa", glyph = PokyhIcons.mensa),
    Grades(title = "Zuletzt eingetragen", glyph = PokyhIcons.tabGrades),
    ;

    companion object {
        /** The layout a fresh install gets: the day in the order it happens, quick links on top. */
        val defaultOrder: List<HomeSection> = listOf(Shortcuts, Exam, Today, Mensa, Grades)

        fun parse(raw: String): HomeSection? = entries.firstOrNull { it.name == raw }
    }
}

/**
 * The order the Home blocks are in.
 *
 * **Order only — nothing can be switched off.** Hiding blocks was the other half of this and it
 * is gone: it turned Home into a screen you had to configure before it was useful, it could be
 * emptied completely (which needed its own empty state to dig yourself back out of), and the
 * thing people actually wanted was to put the block they look at first at the top. Every block
 * is always present; you choose where.
 */
@Serializable
data class HomeLayout(
    val order: List<String> = HomeSection.defaultOrder.map { it.name },
) {
    /**
     * The layout as the screen should use it: known sections only, every section present exactly
     * once, in the saved order with anything new appended.
     *
     * Everything here is a *forward-compatibility* concern rather than defensive padding. A
     * layout saved by an older build won't mention a section added since, and one saved by a
     * newer build may mention a section this build doesn't have — both have to resolve to
     * something sensible instead of a Home screen with a missing block or a crash. Dropping a
     * section (`NextLesson`) rides on the same path: its name no longer parses and falls out.
     */
    val sanitized: List<HomeSection>
        get() {
            val known = order.mapNotNull { HomeSection.parse(it) }.distinct()
            val missing = HomeSection.entries.filter { it !in known }
            return known + missing
        }

    /** The sections to render, in order. */
    val visible: List<HomeSection> get() = sanitized

    /** [from] and [to] index into [sanitized]. */
    fun moved(from: Int, to: Int): HomeLayout {
        val list = sanitized.toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return this
        list.add(to, list.removeAt(from))
        return copy(order = list.map { it.name })
    }

    fun reset(): HomeLayout = HomeLayout()
}
