package dev.plattnericus.pokyh.core.i18n

/** Schule: the hub of the school's parts. */
class SchoolStrings(
    val gradesInfo: String,
    val absences: String,
    val absencesInfo: String,
    val klasse: String,
    val klasseInfo: String,
    val classreg: String,
    val classregInfo: String,
    val reminders: String,
    val remindersInfo: String,
    val todos: String,
    val todosInfo: String,
    val unread: (Int) -> String,
    val messagesInfo: String,
)

val SchoolDe = SchoolStrings(
    gradesInfo = "Alle Fächer & Bewertungen",
    absences = "Abwesenheiten",
    absencesInfo = "Fehlstunden & Entschuldigungen",
    klasse = "Klasse",
    klasseInfo = "Übersicht, Prüfungen & Mitglieder",
    classreg = "Klassenbuch",
    classregInfo = "Einträge & Vermerke",
    reminders = "Erinnerungen",
    remindersInfo = "Hausaufgaben & Termine der Klasse",
    todos = "Eigene To-dos",
    todosInfo = "Deine persönliche Aufgabenliste",
    unread = { "$it ungelesen" },
    messagesInfo = "Posteingang der Schule",
)

val SchoolIt = SchoolStrings(
    gradesInfo = "Tutte le materie e i voti",
    absences = "Assenze",
    absencesInfo = "Ore di assenza e giustificazioni",
    klasse = "Classe",
    klasseInfo = "Panoramica, verifiche e membri",
    classreg = "Registro di classe",
    classregInfo = "Annotazioni e note",
    reminders = "Promemoria",
    remindersInfo = "Compiti e appuntamenti della classe",
    todos = "I miei to-do",
    todosInfo = "La tua lista personale di compiti",
    unread = { "$it non letti" },
    messagesInfo = "Posta della scuola",
)

val SchoolEn = SchoolStrings(
    gradesInfo = "All subjects & marks",
    absences = "Absences",
    absencesInfo = "Missed lessons & excuses",
    klasse = "Class",
    klasseInfo = "Overview, exams & members",
    classreg = "Class register",
    classregInfo = "Entries & notes",
    reminders = "Reminders",
    remindersInfo = "Homework & class dates",
    todos = "My to-dos",
    todosInfo = "Your personal task list",
    unread = { "$it unread" },
    messagesInfo = "The school’s inbox",
)
