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
    const val GRADE_SUBJECT = "grades/{subjectId}"
    const val MENSA = "mensa"
    const val MENSA_DISH = "mensa/{dishId}"
    const val TODOS = "todos"
    const val REMINDERS = "reminders"
    const val REMINDER_DETAIL = "reminders/{reminderId}"
    const val ABSENCES = "absences"
    const val MESSAGES = "messages"
    const val MESSAGE_DETAIL = "messages/{id}"
    const val CLASSREG_EVENTS = "classreg_events"
    const val CLASSROOM = "classroom"
    const val PROFILE = "profile"
    const val CONNECTION_STATUS = "connection_status"
    const val LOGIN = "login"
    const val LOCK = "lock"

    fun gradeSubject(subjectId: Int): String = "grades/$subjectId"
    fun dishDetail(dishId: String): String = "mensa/$dishId"
    fun reminderDetail(reminderId: String): String = "reminders/$reminderId"
    fun messageDetail(id: Int): String = "messages/$id"
}

/** Store.swift `AppTab` — the 5 bottom-navigation roots; [route] is that tab's NavHost start destination. */
enum class AppTab(val route: String) {
    Home(PokyhDestinations.HOME),
    Timetable(PokyhDestinations.TIMETABLE),
    School(PokyhDestinations.SCHOOL_HUB),
    Grades(PokyhDestinations.GRADES),
    Mensa(PokyhDestinations.MENSA),
}
