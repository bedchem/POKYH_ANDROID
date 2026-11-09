package dev.plattnericus.pokyh.core.i18n

/** The empty, failed and unavailable states (ui/components/States.kt). */
class StateStrings(
    val notStudentTitle: String,
    val noClassTitle: String,
    val failedTitle: String,
    val unavailableTitle: String,
    val notStudent: (feature: String) -> String,
    val noClass: (feature: String) -> String,
    /** The class waits for its check at the next sign-in. */
    val noClassPending: (feature: String) -> String,
    /** WebUntis names no class for the account. */
    val noClassInUntis: (feature: String) -> String,
    /** Another account signs in as the same WebUntis pupil; an admin decides. */
    val noClassShared: (feature: String) -> String,
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
    noClassPending = { "Für $it wird deine Klasse gerade neu bei WebUntis geprüft. Das passiert bei der nächsten Anmeldung von selbst." },
    noClassInUntis = { "Für $it wird deine Klasse benötigt, aber dein WebUntis-Konto nennt keine. Wenn das nicht stimmt, wende dich an deine Schule." },
    noClassShared = { "Für $it wird deine Klasse benötigt. Ein anderes Konto meldet sich auf demselben WebUntis-Schüler an; ein Admin ordnet das bald zu." },
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
    noClassPending = { "Per $it stiamo verificando di nuovo la tua classe con WebUntis. Succede da solo al prossimo accesso." },
    noClassInUntis = { "Per $it serve la tua classe, ma il tuo account WebUntis non ne indica nessuna. Se non è corretto, rivolgiti alla tua scuola." },
    noClassShared = { "Per $it serve la tua classe. Un altro account accede con lo stesso studente WebUntis; un amministratore lo assegnerà a breve." },
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
    noClassPending = { "For $it, your class is being checked with WebUntis again. It happens on its own at your next sign-in." },
    noClassInUntis = { "$it needs your class, but your WebUntis account doesn't name one. If that's wrong, contact your school." },
    noClassShared = { "$it needs your class. Another account signs in as the same WebUntis student; an admin will sort it out soon." },
    failed = { f, r -> "$f couldn't be loaded:\n$r" },
    offline = { "You are offline, and $it hasn't been saved on this device yet. It will load as soon as you're back online." },
    needsAccount = { "$it needs a POKYH account." },
    openAccount = "Profile → open POKYH account",
    featureTodos = "To-dos",
    featureReminders = "Reminders",
    featureClassList = "The class list",
)
