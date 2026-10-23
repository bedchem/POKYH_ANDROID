package dev.plattnericus.pokyh.ui.navigation

/**
 * Every navigation route in the app, one const per screen (kebab-case, no leading slash).
 * Ported from the set of destinations reachable across RootTabView.swift's five tabs.
 */
object PokyhDestinations {
    const val HOME = "home"
    const val TIMETABLE = "timetable"
    const val SCHOOL_HUB = "school_hub"
    const val GRADES = "grades"
    const val GRADE_SUBJECT = "grades/{subjectId}?year={year}"
    const val MENSA = "mensa"
    const val MENSA_DISH = "mensa/{dishId}"
    const val TODOS = "todos"
    const val REMINDERS = "reminders"
    const val REMINDER_DETAIL = "reminders/{reminderId}"
    const val ABSENCES = "absences"
    const val ABSENCE_CALC = "absences/calc?absent={absent}&total={total}"
    const val SETTINGS = "settings"
    const val MESSAGES = "messages"
    const val MESSAGE_DETAIL = "messages/{id}?reply={reply}"
    const val CLASSREG_EVENTS = "classreg_events"
    const val CLASSROOM = "classroom"
    const val PROFILE = "profile"
    const val PROFILE_STATS = "profile/stats"
    const val CONNECTION_STATUS = "connection_status"
    const val LOGIN = "login"
    const val LOCK = "lock"

    /** The selected school YEAR travels with the route: the detail screen loads its own grades
     * and would otherwise always fetch the current year, so opening a subject from 2024/25 found
     * nothing and showed "Fach nicht gefunden". */
    fun gradeSubject(subjectId: Int, year: Int): String = "grades/$subjectId?year=$year"
    fun dishDetail(dishId: String): String = "mensa/$dishId"
    fun reminderDetail(reminderId: String): String = "reminders/$reminderId"
    /** [reply]: opened from the inbox, so "Antworten" is offered (sent and draft messages have none). */
    fun messageDetail(id: Int, reply: Boolean = false): String = "messages/$id?reply=$reply"

    /** The calculator, filled in with the year's own numbers when there are any (hours). */
    fun absenceCalculator(absentHours: Double? = null, totalHours: Double? = null): String =
        "absences/calc?absent=${absentHours?.let { "%.1f".format(java.util.Locale.ROOT, it) } ?: ""}&total=${totalHours?.let { "%.1f".format(java.util.Locale.ROOT, it) } ?: ""}"
}

/** Store.swift `AppTab` — the 5 bottom-navigation roots; [route] is that tab's NavHost start destination. */
enum class AppTab(val route: String) {
    Home(PokyhDestinations.HOME),
    Timetable(PokyhDestinations.TIMETABLE),
    School(PokyhDestinations.SCHOOL_HUB),
    Grades(PokyhDestinations.GRADES),
    Mensa(PokyhDestinations.MENSA),
}
