package dev.plattnericus.pokyh.core.i18n

/** Every text of the app in one language; one area per part of the app. */
class Strings(
    val lang: Lang,
    val common: CommonStrings,
    val states: StateStrings,
    val grades: GradesStrings,
    val login: LoginStrings,
    val absences: AbsencesStrings,
    val profile: ProfileStrings,
    val settings: SettingsStrings,
    val bell: BellStrings,
    val nav: NavStrings,
)

val StringsDe = Strings(
    lang = Lang.De,
    common = CommonDe,
    states = StatesDe,
    grades = GradesDe,
    login = LoginDe,
    absences = AbsencesDe,
    profile = ProfileDe,
    settings = SettingsDe,
    bell = BellDe,
    nav = NavDe,
)

val StringsIt = Strings(
    lang = Lang.It,
    common = CommonIt,
    states = StatesIt,
    grades = GradesIt,
    login = LoginIt,
    absences = AbsencesIt,
    profile = ProfileIt,
    settings = SettingsIt,
    bell = BellIt,
    nav = NavIt,
)

val StringsEn = Strings(
    lang = Lang.En,
    common = CommonEn,
    states = StatesEn,
    grades = GradesEn,
    login = LoginEn,
    absences = AbsencesEn,
    profile = ProfileEn,
    settings = SettingsEn,
    bell = BellEn,
    nav = NavEn,
)
