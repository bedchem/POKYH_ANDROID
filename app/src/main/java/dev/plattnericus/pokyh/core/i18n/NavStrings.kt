package dev.plattnericus.pokyh.core.i18n

/** The tabs, the header actions and the app's frame. */
class NavStrings(
    val home: String,
    val timetable: String,
    val school: String,
    val grades: String,
    val mensa: String,
    val messages: String,
    val messagesUnread: (Int) -> String,
    val profile: String,
    val settings: String,
    val arrangeHome: String,
    val switching: String,
)

val NavDe = NavStrings(
    home = "Home",
    timetable = "Stundenplan",
    school = "Schule",
    grades = "Noten",
    mensa = "Mensa",
    messages = "Nachrichten",
    messagesUnread = { "Nachrichten, $it ungelesen" },
    profile = "Profil",
    settings = "Einstellungen",
    arrangeHome = "Startseite anordnen",
    switching = "Lädt…",
)

val NavIt = NavStrings(
    home = "Home",
    timetable = "Orario",
    school = "Scuola",
    grades = "Voti",
    mensa = "Mensa",
    messages = "Messaggi",
    messagesUnread = { "Messaggi, $it non letti" },
    profile = "Profilo",
    settings = "Impostazioni",
    arrangeHome = "Riordina la home",
    switching = "Caricamento…",
)

val NavEn = NavStrings(
    home = "Home",
    timetable = "Timetable",
    school = "School",
    grades = "Grades",
    mensa = "Canteen",
    messages = "Messages",
    messagesUnread = { "Messages, $it unread" },
    profile = "Profile",
    settings = "Settings",
    arrangeHome = "Arrange home",
    switching = "Loading…",
)
