package dev.plattnericus.pokyh.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.plattnericus.pokyh.R

/**
 * Inter — same family the web app uses (`next/font/google` Inter) and visually the closest
 * match to iOS's system font (SF Pro) available for bundling. Shipped as ONE variable-font
 * file (`res/font/inter_variable.ttf`, OFL-licensed from google/fonts) instantiated at each
 * weight via [FontVariation.weight] rather than four separate static files.
 */
private fun interFont(weight: FontWeight) = Font(
    resId = R.font.inter_variable,
    weight = weight,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val InterFontFamily = FontFamily(
    interFont(FontWeight.Normal),
    interFont(FontWeight.Medium),
    interFont(FontWeight.SemiBold),
    interFont(FontWeight.Bold),
    interFont(FontWeight.ExtraBold),
)

/**
 * iOS semantic text styles (SwiftUI `Font.largeTitle` … `.caption2`), named to match 1:1 so
 * screen code can be a direct transliteration of the Swift (`.font(.headline)` -> `PokyhType.headline`).
 * Point sizes are Apple's default (non-Dynamic-Type) sizes at the "Large" content size, which is
 * what the iOS app effectively renders at in all the screenshots this port is built against.
 */
object PokyhType {
    val largeTitle = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 41.sp)
    val title1 = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp)
    val title2 = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp)
    val title3 = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 25.sp)
    val headline = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp)
    val body = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 22.sp)
    val callout = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 21.sp)
    val subheadline = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp)
    val footnote = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp)
    val caption = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
    val caption2 = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 13.sp)

    // Weight variants used throughout (`.headline` == semibold already; these cover the
    // `.subheadline.semibold`, `.caption.bold()` etc. call sites screens need).
    fun TextStyle.medium() = copy(fontWeight = FontWeight.Medium)
    fun TextStyle.semibold() = copy(fontWeight = FontWeight.SemiBold)
    fun TextStyle.bold() = copy(fontWeight = FontWeight.Bold)

    /** `design: .rounded` numeric displays — Inter has no rounded optical variant, so the
     * distinguishing trait we can reproduce is the weight + tabular figures; call sites that
     * used `.rounded` on iOS should use these together with [monospacedDigits]. */
    fun rounded(size: androidx.compose.ui.unit.TextUnit, weight: FontWeight = FontWeight.Bold) =
        TextStyle(fontFamily = InterFontFamily, fontWeight = weight, fontSize = size)

    /** `.monospacedDigit()` — tabular figures via the `tnum` OpenType feature, so numbers in a
     * column (grade averages, countdowns, timetable times) don't shift width as they change. */
    fun TextStyle.monospacedDigits() = copy(
        fontFeatureSettings = "tnum",
    )

    // Explicit point sizes called out in the iOS source (Theme "3.5 Typography" in the design
    // notes) that don't correspond to a semantic SwiftUI font — screens reference these directly.
    val wordmark = rounded(30.sp, FontWeight.Bold)           // "POKYH" on Login/Lock
    val gradeAverage = rounded(44.sp, FontWeight.Bold)        // GradesView overall average
    val subjectAverage = rounded(48.sp, FontWeight.Bold)      // GradeSubjectView average
    val absencesTotal = rounded(30.sp, FontWeight.Bold)       // AbsencesView total
    val absencesMini = rounded(20.sp, FontWeight.Bold)        // AbsencesView mini stats
    val gridCellSubject = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 10.sp)
    val gridCellTime = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Medium, fontSize = 7.5.sp)
    val gridCellRoom = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 8.sp)
    val timeAxisPeriod = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Normal, fontSize = 9.sp)
    val timeAxisBoundary = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 9.sp)
    val badgeChip = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.Bold, fontSize = 9.sp)
    val widgetHeader = TextStyle(fontFamily = InterFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
    val widgetBigNumber = rounded(42.sp, FontWeight.Bold)
    val liveActivityCountdown = rounded(30.sp, FontWeight.Bold)
}

/** Material3 Typography bridge — lets stock M3 components (TextField labels, TopAppBar title,
 * etc.) render in Inter too, even though most screens address [PokyhType] directly. */
val PokyhMaterialTypography = Typography(
    displayLarge = PokyhType.largeTitle,
    displayMedium = PokyhType.title1,
    displaySmall = PokyhType.title2,
    headlineLarge = PokyhType.title2,
    headlineMedium = PokyhType.title3,
    headlineSmall = PokyhType.headline,
    titleLarge = PokyhType.title3,
    titleMedium = PokyhType.headline,
    titleSmall = PokyhType.subheadline,
    bodyLarge = PokyhType.body,
    bodyMedium = PokyhType.subheadline,
    bodySmall = PokyhType.footnote,
    labelLarge = PokyhType.subheadline,
    labelMedium = PokyhType.caption,
    labelSmall = PokyhType.caption2,
)
