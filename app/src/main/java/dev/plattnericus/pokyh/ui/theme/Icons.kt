package dev.plattnericus.pokyh.ui.theme

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Every icon in the app, in one place, drawn from one family — Phosphor (see [Ph]).
 *
 * The rule, and the whole reason this file exists: **[Ph] regular for everything**. The [PhFill]
 * weight appears in exactly three situations, each of which is a state and not a style choice:
 *
 *  - the selected bottom-nav tab ([navSelected])
 *  - a status badge whose colored disc *is* the signal (ok / error / warning / verified)
 *  - a filled rating star
 *
 * That replaces the previous arrangement, where screens imported `Icons.Filled.*`,
 * `Icons.Outlined.*` and `Icons.AutoMirrored.Filled.*` ad hoc, so a filled 24dp Material glyph
 * could sit next to an outlined one in the same row.
 *
 * Names are grouped by what the icon is *for*, not by what it looks like, so picking one is a
 * question about the UI rather than about the icon set. Never import [Ph]/[PhFill] from a
 * screen — add a semantic name here instead, and the substitution stays in one file.
 */
object PokyhIcons {

    // ── Navigation: tabs ─────────────────────────────────────────────────────

    val tabHome = Ph.House
    val tabTimetable = Ph.CalendarBlank
    val tabSchool = Ph.GraduationCap
    val tabGrades = Ph.ChartBar
    val tabMensa = Ph.ForkKnife

    /** The [PhFill] twin of a tab icon, for the selected tab only. */
    fun navSelected(icon: ImageVector): ImageVector = when (icon) {
        Ph.House -> PhFill.House
        Ph.CalendarBlank -> PhFill.CalendarBlank
        Ph.GraduationCap -> PhFill.GraduationCap
        Ph.ChartBar -> PhFill.ChartBar
        Ph.ForkKnife -> PhFill.ForkKnife
        else -> icon
    }

    // ── Navigation: chrome ───────────────────────────────────────────────────

    val back = Ph.ArrowLeft
    val forward = Ph.ArrowRight
    val chevronRight = Ph.CaretRight
    val chevronLeft = Ph.CaretLeft

    /** The caret on a menu trigger — rotates 180° when the menu is open. */
    val expandMenu = Ph.CaretDown
    val close = Ph.X
    val add = Ph.Plus
    val more = Ph.DotsThreeVertical
    val search = Ph.MagnifyingGlass
    val share = Ph.Export
    val send = Ph.ArrowCircleUp

    /** The diagonal "open this" arrow in the corner of a feature tile. */
    val openTile = Ph.ArrowUpRight

    // ── Status ───────────────────────────────────────────────────────────────

    val ok = PhFill.CheckCircle
    val verified = PhFill.SealCheck
    val failed = PhFill.XCircle
    val warningBadge = PhFill.WarningCircle
    val warningSign = Ph.Warning
    val info = Ph.Info
    val unknown = Ph.Question
    val check = Ph.Check
    val refresh = Ph.ArrowClockwise
    val offline = Ph.WifiSlash
    val serverUnreachable = Ph.CloudSlash
    val notAStudent = Ph.UserMinus
    val noClass = Ph.UsersThree
    val locked = Ph.LockSimple

    // ── School domain ────────────────────────────────────────────────────────

    val timetable = Ph.CalendarBlank
    val calendarDay = Ph.CalendarDots
    val exam = Ph.ClipboardText
    val noExam = PhFill.SealCheck
    val grades = Ph.ChartBar
    val gradeTrend = Ph.ChartLineUp
    val todos = Ph.ListChecks
    val reminders = Ph.Bell
    val classRegister = Ph.BookBookmark
    val classMembers = Ph.UsersThree
    val absences = Ph.CalendarX
    val subjects = Ph.Books
    val school = Ph.GraduationCap
    val mensa = Ph.ForkKnife
    val dish = Ph.Pizza
    val holiday = Ph.Umbrella
    val weekend = Ph.Sun
    val room = Ph.MapPin
    val replacement = Ph.ArrowsLeftRight
    val recent = Ph.ClockCounterClockwise
    val calculator = Ph.Function
    val target = Ph.Target
    val document = Ph.FileText
    val live = PhFill.Lightning

    // ── People / auth ────────────────────────────────────────────────────────

    val person = Ph.User
    val profile = Ph.UserCircle
    val addAccount = Ph.UserPlus
    val biometrics = Ph.Fingerprint
    val password = Ph.Key
    val signOut = Ph.SignOut
    val delete = Ph.Trash
    val edit = Ph.PencilSimple
    val privacy = Ph.HandPalm
    val security = Ph.ShieldCheck
    val shield = Ph.Shield

    // ── Messages ─────────────────────────────────────────────────────────────

    val messages = Ph.Envelope
    val attachment = Ph.Paperclip
    val file = Ph.File
    val emptyFolder = Ph.Tray
    val compose = Ph.PencilSimple
    val sendMessage = Ph.PaperPlaneTilt
    val markAllRead = Ph.Checks
    val download = Ph.DownloadSimple
    val filePdf = Ph.FilePdf
    val fileImage = Ph.FileImage

    // ── Time ─────────────────────────────────────────────────────────────────

    val clock = Ph.Clock
    val clockExact = Ph.Alarm

    // ── Ratings ──────────────────────────────────────────────────────────────

    val starFilled = PhFill.Star
    val starEmpty = Ph.Star

    // ── Settings ─────────────────────────────────────────────────────────────

    val appearance = Ph.PaintBrush
    val themeSystem = Ph.CircleHalf
    val themeLight = Ph.Sun
    val themeDark = Ph.Moon

    // ── Controls ─────────────────────────────────────────────────────────────

    val sort = Ph.ArrowsDownUp
    val reset = Ph.ArrowsCounterClockwise
    val undo = Ph.ArrowUUpLeft
    val radioOn = PhFill.CheckCircle
    val radioOff = Ph.Circle
    val dot = PhFill.Circle

    // ── Decorative ───────────────────────────────────────────────────────────
    // Large, low-opacity glyphs used as the illustration on a color-blocked category tile.
    // Same family as every functional icon, so a tile reads as part of the app rather than as
    // pasted-in artwork — which is what the PNG doodles they replaced looked like.

    val decorSubjects = Ph.Books
    val decorAbsences = Ph.CalendarX
    val decorTodos = Ph.PencilRuler
    val decorReminders = Ph.Bell
    val decorClassRegister = Ph.BookBookmark
    val decorClassMembers = Ph.UsersThree
    val decorDish = Ph.Pizza
    val decorCelebrate = Ph.Confetti
    val decorMood = Ph.Smiley
    val decorBackpack = Ph.Backpack
}
