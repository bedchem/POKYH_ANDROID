package dev.plattnericus.pokyh.core.i18n

/** The empty, failed and unavailable states (ui/components/States.kt). */
class StateStrings(
    val notStudentTitle: String,
    val noClassTitle: String,
    val failedTitle: String,
    val unavailableTitle: String,
    val notStudent: (feature: String) -> String,
    val noClass: (feature: String) -> String,
    val failed: (feature: String, reason: String) -> String,
    val offline: (feature: String) -> String,
    val needsAccount: (feature: String) -> String,
    val openAccount: String,
    val featureTodos: String,
    val featureReminders: String,
    val featureClassList: String,
)

val StatesDe = StateStrings(
    notStudentTitle = "Nur für Schülerkonten",
    noClassTitle = "Keine Klasse gefunden",
    failedTitle = "Verbindungsfehler",
    unavailableTitle = "Nicht verfügbar",
    notStudent = { "$it ist nur mit einem Schülerkonto verfügbar." },
    noClass = { "Für $it wird deine WebUntis-Klasse benötigt — sie konnte für dein Konto nicht ermittelt werden." },
    failed = { f, r -> "$f konnte nicht geladen werden:\n$r" },
    offline = { "Du bist offline, und $it wurde auf diesem Gerät noch nicht gespeichert. Sobald du wieder Internet hast, wird es geladen." },
    needsAccount = { "$it benötigt ein POKYH-Konto." },
    openAccount = "Profil → POKYH-Konto öffnen",
    featureTodos = "Todos",
    featureReminders = "Erinnerungen",
    featureClassList = "Die Klassenliste",
)

val StatesIt = StateStrings(
    notStudentTitle = "Solo per account studente",
    noClassTitle = "Nessuna classe trovata",
    failedTitle = "Errore di connessione",
    unavailableTitle = "Non disponibile",
    notStudent = { "$it è disponibile solo con un account studente." },
    noClass = { "Per $it serve la tua classe di WebUntis, che non è stato possibile determinare per il tuo account." },
    failed = { f, r -> "Impossibile caricare $f:\n$r" },
    offline = { "Sei offline e $it non è ancora salvato su questo dispositivo. Appena torni online verrà caricato." },
    needsAccount = { "$it richiede un account POKYH." },
    openAccount = "Profilo → apri account POKYH",
    featureTodos = "I to-do",
    featureReminders = "I promemoria",
    featureClassList = "L’elenco della classe",
)

val StatesEn = StateStrings(
    notStudentTitle = "Student accounts only",
    noClassTitle = "No class found",
    failedTitle = "Connection error",
    unavailableTitle = "Not available",
    notStudent = { "$it is only available with a student account." },
    noClass = { "$it needs your WebUntis class, which couldn't be determined for your account." },
    failed = { f, r -> "$f couldn't be loaded:\n$r" },
    offline = { "You are offline, and $it hasn't been saved on this device yet. It will load as soon as you're back online." },
    needsAccount = { "$it needs a POKYH account." },
    openAccount = "Profile → open POKYH account",
    featureTodos = "To-dos",
    featureReminders = "Reminders",
    featureClassList = "The class list",
)
