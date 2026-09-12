package dev.plattnericus.pokyh.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.plattnericus.pokyh.R

/**
 * Plus Jakarta Sans — free (OFL, google/fonts), geometric, with softly rounded terminals. One
 * variable-font file (`res/font/plus_jakarta_sans_variable.ttf`) instantiated at each weight via
 * [FontVariation.weight] rather than shipping separate static files.
 */
private fun pokyhFont(weight: FontWeight) = Font(
    resId = R.font.plus_jakarta_sans_variable,
    weight = weight,
    style = FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val PokyhFontFamily = FontFamily(
    pokyhFont(FontWeight.Normal),
    pokyhFont(FontWeight.Medium),
    pokyhFont(FontWeight.SemiBold),
    pokyhFont(FontWeight.Bold),
    pokyhFont(FontWeight.ExtraBold),
)

private fun style(
    size: Int,
    lineHeight: Int,
    weight: FontWeight,
    tracking: Float = 0f,
) = TextStyle(
    fontFamily = PokyhFontFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.em,
)

/**
 * The type scale. Eleven steps, each with one job — the hierarchy is the *weight and size step*
 * between neighbours, so a screen never has to invent an in-between size to show that one thing
 * matters more than another.
 *
 * The shape of it: display sizes carry personality (extra-bold, negatively tracked, tight
 * leading — the big confident headings), and everything from [body] down stays plain and
 * generously leaded so it just reads. Weight is part of the token: don't restyle a token's
 * weight at a call site, pick the token that already has it.
 *
 * The only `.copy()`-style modifiers that should appear in screen code are [medium]/[semibold]/
 * [bold] (a token at one step more emphasis) and [monospacedDigits] (tabular figures). Anything
 * else — a new size, a new tracking — belongs here as a token.
 */
object PokyhType {
    /** Screen hero. One per screen, at the top, and nothing else on the screen competes. */
    val display = style(36, 40, FontWeight.ExtraBold, tracking = -0.022f)

    /** Screen title in a [dev.plattnericus.pokyh.ui.components.PokyhTopBar] large header. */
    val largeTitle = style(30, 36, FontWeight.ExtraBold, tracking = -0.02f)

    /** Title of a pushed detail screen; the biggest thing inside a hero card. */
    val title1 = style(24, 30, FontWeight.Bold, tracking = -0.015f)

    /** Section title on the canvas ("Heute", "Fächer"), card title in a hero card. */
    val title2 = style(20, 26, FontWeight.Bold, tracking = -0.01f)

    /** Card title, group header. */
    val title3 = style(17, 23, FontWeight.SemiBold, tracking = -0.005f)

    /** The emphasized line of a list row — a subject name, a person's name. */
    val headline = style(16, 21, FontWeight.SemiBold)

    /** Running text and row primary text. */
    val body = style(16, 23, FontWeight.Normal)

    /** Slightly tightened body for dense blocks. */
    val callout = style(15, 21, FontWeight.Normal)

    /** Row secondary text, supporting copy. */
    val subheadline = style(14, 19, FontWeight.Normal)

    /** Metadata under a row, helper text under a control. */
    val footnote = style(13, 18, FontWeight.Normal)

    /** Timestamps, counts, chip text. */
    val caption = style(12, 16, FontWeight.Medium)

    /** The smallest readable step: badge text, axis labels, footers. */
    val caption2 = style(11, 14, FontWeight.Medium)

    /** All-caps eyebrow above a title, and in-card section labels. Always paired with a
     * [PokyhColors.textTertiary]/[PokyhColors.textSecondary] color and `.uppercase()` text. */
    val label = style(11, 14, FontWeight.Bold, tracking = 0.09f)

    /** Button labels. */
    val button = style(16, 20, FontWeight.SemiBold, tracking = -0.005f)

    /** Bottom-nav item labels. */
    val navLabel = style(10, 12, FontWeight.SemiBold, tracking = 0.005f)

    // ── Emphasis modifiers ───────────────────────────────────────────────────

    fun TextStyle.medium() = copy(fontWeight = FontWeight.Medium)
    fun TextStyle.semibold() = copy(fontWeight = FontWeight.SemiBold)
    fun TextStyle.bold() = copy(fontWeight = FontWeight.Bold)
    fun TextStyle.extraBold() = copy(fontWeight = FontWeight.ExtraBold)

    /** `.monospacedDigit()` — tabular figures via the `tnum` OpenType feature, so numbers in a
     * column (grade averages, countdowns, timetable times) don't shift width as they change. */
    fun TextStyle.monospacedDigits() = copy(fontFeatureSettings = "tnum")

    // ── Numerals ─────────────────────────────────────────────────────────────

    /**
     * The one escape hatch for a size not on the scale: a big number that *is* the content
     * (a grade average, a percentage). Tabular by default, since these always change in place.
     */
    fun numeral(size: TextUnit, weight: FontWeight = FontWeight.ExtraBold) = TextStyle(
        fontFamily = PokyhFontFamily,
        fontWeight = weight,
        fontSize = size,
        letterSpacing = (-0.03f).em,
        fontFeatureSettings = "tnum",
    )

    /** Hero statistic — the single number a screen is about. */
    val statLarge = numeral(46.sp)

    /** Secondary statistic, sharing a row with [statLarge] or sitting in its own small card. */
    val statMedium = numeral(28.sp)

    /** Inline statistic in a compact stat row. */
    val statSmall = numeral(19.sp, FontWeight.Bold)

    /** "POKYH" on Login/Lock. */
    val wordmark = numeral(32.sp)

    // ── Dense, fixed-size UI ─────────────────────────────────────────────────
    // The timetable week grid packs 6 days x ~10 periods onto one phone screen; these sizes are
    // driven by the cell geometry, not by the scale, and deliberately stay put.

    val gridCellSubject = style(10, 12, FontWeight.Bold)
    val gridCellTime = TextStyle(fontFamily = PokyhFontFamily, fontWeight = FontWeight.Medium, fontSize = 7.5.sp)
    val gridCellRoom = TextStyle(fontFamily = PokyhFontFamily, fontWeight = FontWeight.Normal, fontSize = 8.sp)
    val timeAxisPeriod = style(9, 11, FontWeight.Normal)
    val timeAxisBoundary = style(9, 11, FontWeight.SemiBold)
    val badgeChip = style(9, 11, FontWeight.Bold, tracking = 0.04f)

    // ── Widgets / Live Activity ──────────────────────────────────────────────
    // Rendered by Glance outside the app's own theme.

    val widgetHeader = style(10, 12, FontWeight.SemiBold, tracking = 0.06f)
    val widgetBigNumber = numeral(38.sp)
    val liveActivityCountdown = numeral(28.sp)
}

/** Material3 Typography bridge — lets stock M3 components (TextField labels, menu items,
 * DatePicker chrome) render in the same typeface, even though app code addresses [PokyhType]. */
val PokyhMaterialTypography = Typography(
    displayLarge = PokyhType.display,
    displayMedium = PokyhType.largeTitle,
    displaySmall = PokyhType.title1,
    headlineLarge = PokyhType.title1,
    headlineMedium = PokyhType.title2,
    headlineSmall = PokyhType.title3,
    titleLarge = PokyhType.title2,
    titleMedium = PokyhType.title3,
    titleSmall = PokyhType.headline,
    bodyLarge = PokyhType.body,
    bodyMedium = PokyhType.subheadline,
    bodySmall = PokyhType.footnote,
    labelLarge = PokyhType.button,
    labelMedium = PokyhType.caption,
    labelSmall = PokyhType.caption2,
)
