package dev.plattnericus.pokyh.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlarmOn
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CompareArrows
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.outlined.Circle

/**
 * SF Symbol → Material Symbols mapping table. Named after the exact SF Symbol the iOS source
 * uses at that call site, so a screen port reads as a direct transliteration (`Icon(PokyhIcons.
 * house_fill, ...)` next to iOS's `Image(systemName: "house.fill")`). Extend this file — don't
 * import `Icons.Filled.*` ad hoc in screens — so every icon substitution stays in one place.
 */
object PokyhIcons {
    // Tab bar
    val house_fill = Icons.Filled.Home
    val calendar = Icons.Filled.CalendarMonth
    val graduationcap_fill = Icons.Filled.School
    val chart_bar_fill = Icons.Filled.BarChart
    val fork_knife = Icons.Filled.Restaurant

    // Toolbar / navigation
    val envelope = Icons.Filled.Email
    val person_crop_circle = Icons.Filled.Person
    val chevron_right = Icons.Filled.ChevronRight
    val chevron_left = Icons.Filled.ChevronLeft
    val xmark = Icons.Filled.Close
    val plus = Icons.Filled.Add
    val ellipsis_circle = Icons.Filled.MoreVert
    val arrow_left = Icons.AutoMirrored.Filled.ArrowBack
    val arrow_right = Icons.AutoMirrored.Filled.ArrowForward
    val magnifyingglass = Icons.Filled.Search

    // Status / states
    val exclamationmark_triangle_fill = Icons.Filled.PriorityHigh
    val checkmark_circle_fill = Icons.Filled.CheckCircle
    val checkmark_seal_fill = Icons.Filled.Verified
    val xmark_circle_fill = Icons.Filled.Cancel
    val wifi_slash = Icons.Filled.WifiOff
    val wifi_exclamationmark = Icons.Filled.CloudOff
    val person_fill_xmark = Icons.Filled.PersonOff
    val person_2_slash = Icons.Filled.PersonRemove
    val lock = Icons.Filled.Lock
    val arrow_clockwise = Icons.Filled.Refresh
    val info_circle = Icons.Filled.Info
    val exclamationmark_circle_fill = Icons.Filled.Error // ProfileView backend-status badge
    val questionmark_circle = Icons.Filled.HelpOutline // ConnectionStatusView "unknown" status

    // School domain
    val checklist = Icons.Filled.Checklist
    val bell_fill = Icons.Filled.Notifications
    val book_closed_fill = Icons.AutoMirrored.Filled.MenuBook
    val person_3_fill = Icons.Filled.Groups
    val pencil_and_list_clipboard = Icons.Filled.Description
    val clock_arrow_circlepath = Icons.Filled.History
    val function = Icons.Filled.Functions
    val target = Icons.Filled.PriorityHigh // no direct M3 equivalent; used only on the target-grade calculator
    val calendar_badge = Icons.Filled.CalendarToday
    val doc_text = Icons.Filled.Description
    val arrow_left_arrow_right = Icons.AutoMirrored.Filled.CompareArrows
    val beach_umbrella_fill = Icons.Filled.BeachAccess
    val sun_max_fill = Icons.Filled.WbSunny
    val mappin_circle_fill = Icons.Filled.Place // TimetableView LessonDetailView room row

    // People / auth
    val person_fill = Icons.Filled.Person
    val person_badge_plus = Icons.Filled.PersonAdd
    val faceid = Icons.Filled.Fingerprint // BiometricPrompt covers both face/fingerprint on Android
    val key_fill = Icons.Filled.Key
    val rectangle_portrait_and_arrow_right = Icons.AutoMirrored.Filled.Logout
    val trash = Icons.Filled.Delete
    val pencil = Icons.Filled.Edit
    val shield = Icons.Filled.Shield
    val lock_shield = Icons.Filled.Shield
    val hand_raised_fill = Icons.Filled.PrivacyTip // ProfileView "Datenschutzerklärung" row

    // Mensa / ratings
    val star_fill = Icons.Filled.Star
    val star_outline = Icons.Filled.StarBorder
    val paperclip = Icons.Filled.AttachFile
    val doc_fill = Icons.Filled.InsertDriveFile
    val clock = Icons.Filled.AccessTime
    val clock_badge_checkmark = Icons.Filled.AlarmOn // "Exakt" toggle on AbsencesView (no direct M3 equivalent)
    val bolt_fill = Icons.Filled.Bolt

    // Misc
    val square_and_arrow_up = Icons.Filled.Share
    val paintbrush_fill = Icons.Filled.Palette
    val moon_fill = Icons.Filled.Bedtime
    val circle_lefthalf_filled = Icons.Filled.BrightnessAuto
    val arrow_up_circle_fill = Icons.AutoMirrored.Filled.Send
    val circle_filled = Icons.Filled.Circle
    val circle_outline = Icons.Outlined.Circle
    val event_busy = Icons.Filled.EventBusy

    // Grades (GradesView / GradeSubjectView)
    val chart_xyaxis_line = Icons.Filled.ShowChart
    val arrow_counterclockwise = Icons.Filled.RestartAlt
    val arrow_uturn_backward = Icons.Filled.Undo
    val arrow_up_arrow_down = Icons.Filled.SwapVert
    val checkmark = Icons.Filled.Check
}
