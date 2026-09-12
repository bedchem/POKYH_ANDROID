package dev.plattnericus.pokyh.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

/**
 * POKYH color system.
 *
 * Three layers, and nothing else invents a color:
 *
 *  1. [Brand] — semantic, theme-independent identity colors. [Brand.accent] is THE accent; the
 *     status trio ([Brand.success]/[Brand.warning]/[Brand.danger]) only ever encodes state.
 *     These, plus the hash-based `subjectColor`/`senderColor`/`gradeColor` functions at the
 *     bottom of this file, are shared cross-platform identity — exact hex parity with iOS/Web
 *     matters there, so do not re-derive or "improve" them.
 *  2. [PokyhColors] — the light/dark neutral ramp (canvas -> card -> inset) and text ramp. This
 *     is the app's own UI chrome and evolves independently of iOS/Web.
 *  3. [PokyhDecorative] — four hand-picked accent surfaces for category color-blocking (the
 *     Home shortcut tiles and the School hub tiles). Four, deliberately: a fifth tone makes
 *     those grids read as "assorted pastels", which is what this system exists to prevent.
 *
 * Rule of thumb when adding UI: neutral by default, [Brand.accent] for the one primary
 * action/selected state on screen, a status color only when it means that status, and a
 * [PokyhDecorative] tone only for a stable *category* — never for decoration's own sake.
 */
object Brand {
    /** The accent. Fills (buttons, selected states, active indicators) and large glyphs. */
    val accent = Color(0xFF6366F1)

    /**
     * Darker step of [accent] for accent-colored *text* and small glyphs on a light surface.
     * [accent] itself only clears ~3.9:1 against white — fine behind a 20dp icon or as a filled
     * button, short of 4.5:1 for a 12-14sp label. Same hue, so the two read as one color.
     * Don't reach for this directly: read [PokyhColors.accentText], which resolves back to
     * [accent] in dark mode (where the lighter indigo is already the high-contrast one).
     */
    val accentInk = Color(0xFF4F46E5)

    /** Content color on an [accent] fill. */
    val onAccent = Color.White

    val success = Color(0xFF10B981)
    val warning = Color(0xFFF59E0B)
    val danger = Color(0xFFEF4444)

    /** Rating stars only — the one place a pure yellow is wanted. */
    val star = Color(0xFFF5B301)

    /**
     * Aliases kept for the call sites that still read them. Prefer the semantic names above in
     * new UI: `tint` is [success] under its old name, `orange` is a second warning step (used
     * where "warning" and "changed/replaced" appear side by side and must stay distinct), and
     * `accentSoft` is the violet secondary that stock Material components tint with.
     */
    val tint = success
    val orange = Color(0xFFF97316)
    val accentSoft = Color(0xFF8B5CF6)
}

/**
 * The neutral ramp, resolved for the active theme. Read via `PokyhTheme.colors`.
 *
 * The three fill tokens are a deliberate ladder, and every screen is built from them:
 *
 *   [bg]      page canvas — a warm off-white, never pure white, so that…
 *   [card]    …the card/sheet/nav fill (pure white in light, a lifted gray in dark) reads as a
 *             distinct layer from tone alone, with only a whisper of shadow and no border.
 *   [cardAlt] *inset* fill — one step the other way from [card]. Inputs, segmented tracks,
 *             skeleton blocks, image placeholders: things that sit *inside* a card and should
 *             read as recessed rather than as a second card.
 *
 * [surface] is an alias of [card] (a card is the app's surface) and exists for the handful of
 * call sites that read "surface" for a sheet/nav/overlay fill.
 */
data class PokyhColors(
    val bg: Color,
    val surface: Color,
    val card: Color,
    val cardAlt: Color,
    /** Hairline at the edge of a surface. Used sparingly — tone does the separating. */
    val border: Color,
    /** Hairline *between rows inside* a card. Lighter than [border]. */
    val separator: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    /** Soft [Brand.accent] wash for accent-tinted chrome (selected pill, icon tile, badge). */
    val accentTint: Color,
    /** [Brand.accent] at a weight readable as small text in this theme — see [Brand.accentInk]. */
    val accentText: Color,
    /** True for [DarkPokyhColors] — lets shadow/elevation helpers branch without a second
     * `isSystemInDarkTheme()` read in every leaf composable. */
    val isDark: Boolean,
)

val LightPokyhColors = PokyhColors(
    bg = Color(0xFFF5F2ED),
    surface = Color(0xFFFFFFFF),
    card = Color(0xFFFFFFFF),
    cardAlt = Color(0xFFEEEAE3),
    border = Color(0xFFE7E2D9),
    separator = Color(0xFFF0ECE5),
    textPrimary = Color(0xFF1C1A17),
    textSecondary = Color(0xFF6C655C),
    textTertiary = Color(0xFFA39B90),
    accentTint = Color(0xFFEDECFD),
    accentText = Brand.accentInk,
    isDark = false,
)

/**
 * Blue-gray dark. The neutrals carry a deliberately *small* cool cast — blue is 3-7 points above
 * red/green, roughly a third of the tint this ramp originally had — and the base sits at 0x11
 * rather than near-black.
 *
 * Both halves of that matter, and each fixes a different failure:
 *
 *  - Too much blue (the original, matching the web frontend's `.dark` block) fights
 *    [Brand.accent]: the indigo has nothing to sit against, so accented text and the selected
 *    nav tab stop reading as "the accent" and start reading as "a slightly different blue".
 *  - Warm instead of cool tints the card and nav surfaces visibly brown/red at these levels,
 *    which is worse than the blue was.
 *  - Pure neutral black is technically correct and visually dead — a flat void with cards
 *    floating on it.
 *
 * A slight cool cast on a lifted base reads as a designed dark gray while still leaving the
 * accent as the only real color in the chrome. [accentTint] is the one deliberate exception.
 *
 * Steps are spaced wider than the light ramp so the [bg]/[card]/[cardAlt] ladder stays legible
 * without shadows, which barely register against a dark background.
 */
val DarkPokyhColors = PokyhColors(
    bg = Color(0xFF111114),
    surface = Color(0xFF1B1B1F),
    card = Color(0xFF1B1B1F),
    cardAlt = Color(0xFF26262B),
    border = Color(0xFF303036),
    separator = Color(0xFF232327),
    textPrimary = Color(0xFFF2F2F4),
    textSecondary = Color(0xFF9C9CA2),
    textTertiary = Color(0xFF67676E),
    // The one intentionally colored token in the dark ramp — it's the accent's quiet form (the
    // selected nav pill, a tinted badge), so it stays indigo rather than following the neutrals.
    accentTint = Color(0xFF20203A),
    accentText = Brand.accent,
    isDark = true,
)

/**
 * One accent surface: [fill] for the block of color, [ink] for text/glyphs drawn on top of it.
 * The pair is contrast-checked in both themes, so a caller never has to decide what is legible
 * on a given tone — use [ink] and it works.
 */
data class DecorativeTone(val fill: Color, val ink: Color)

/**
 * The four category surfaces. Hand-picked (not hash-derived like [subjectColor], which has to
 * stay cross-platform-consistent) and intentionally few — see the file header.
 *
 * Light: pastel fill + a deep shade of the same hue as ink.
 * Dark: inverted — a deep saturated fill with a light tint of the same hue as ink, since a
 * washed-out pastel on near-black reads muddy and kills the ink contrast.
 */
object PokyhDecorative {
    /** Indigo family — sits closest to [Brand.accent], so it leads any cycle. */
    fun periwinkle(isDark: Boolean) = if (isDark) {
        DecorativeTone(Color(0xFF24244C), Color(0xFFBAB8F4))
    } else {
        DecorativeTone(Color(0xFFDEDDFB), Color(0xFF4A46A6))
    }

    fun butter(isDark: Boolean) = if (isDark) {
        DecorativeTone(Color(0xFF473B16), Color(0xFFEBD79A))
    } else {
        DecorativeTone(Color(0xFFF7E7AC), Color(0xFF85641A))
    }

    fun sage(isDark: Boolean) = if (isDark) {
        DecorativeTone(Color(0xFF23351E), Color(0xFFB4D0AD))
    } else {
        DecorativeTone(Color(0xFFD5E4D0), Color(0xFF4B6E45))
    }

    fun blush(isDark: Boolean) = if (isDark) {
        DecorativeTone(Color(0xFF46241F), Color(0xFFEFC1B8))
    } else {
        DecorativeTone(Color(0xFFF7D9D3), Color(0xFF9F5347))
    }

    /** The tones in canonical order. Grids cycle this by index, so a given tile keeps its color
     * across launches and the repeat reads as a pattern rather than as randomness. */
    fun all(isDark: Boolean) = listOf(periwinkle(isDark), butter(isDark), sage(isDark), blush(isDark))

    /** The tone for position [index] of a category grid/list. */
    fun cycle(index: Int, isDark: Boolean): DecorativeTone = all(isDark).let { it[index.mod(it.size)] }
}

/** Provided by PokyhTheme; read via `PokyhTheme.colors` inside any `@Composable`. */
val LocalPokyhColors = staticCompositionLocalOf { LightPokyhColors }

/** Fixed per-subject colors (Theme.swift `subjectColors`), keyed by the exact WebUntis
 * subject short name. Anything not in this map falls back to [subjectColor]'s hash. */
private val fixedSubjectColors: Map<String, Color> = mapOf(
    "D" to Color(0xFF5AA0E8),
    "M" to Color(0xFF4ED87A),
    "IT" to Color(0xFFE73BDF),
    "Bew.Sport" to Color(0xFFAA8EE0),
    "ENGL" to Color(0xFF3DC4CE),
    "R" to Color(0xFFC6E84A),
    "M5-M7" to Color(0xFFE08899),
    "M8" to Color(0xFFE89E6E),
    "Re-Wiku" to Color(0xFF6AB87A),
)

/**
 * Bit-exact port of the JS-style 32-bit string hash used by both the web app and iOS
 * (`Theme.swift` `hashString`). Must reproduce this EXACTLY — the fallback colors for
 * subjects/senders/avatars depend on it, and any drift makes them differ from iOS/Web.
 *
 *   hash = (hash << 5) - hash + charCode   // per UTF-16 code unit, truncated to Int32
 */
private fun jsHash(s: String): Int {
    var hash = 0
    for (ch in s) {
        hash = (hash shl 5) - hash + ch.code
        // Truncate to signed 32-bit, matching Swift's `Int32(truncatingIfNeeded:)`.
        hash = hash.toInt()
    }
    return abs(hash)
}

/** HSV → RGB, matching SwiftUI's `Color(hue:saturation:brightness:)` (brightness == HSV's V). */
private fun hsv(hueDeg: Float, saturation: Float, value: Float): Color {
    val h = ((hueDeg % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    val c = v * s
    val hPrime = h / 60f
    val x = c * (1 - abs(hPrime % 2 - 1))
    val (r1, g1, b1) = when {
        hPrime < 1f -> Triple(c, x, 0f)
        hPrime < 2f -> Triple(x, c, 0f)
        hPrime < 3f -> Triple(0f, c, x)
        hPrime < 4f -> Triple(0f, x, c)
        hPrime < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = v - c
    return Color(r1 + m, g1 + m, b1 + m)
}

/** Subject color — fixed map first, else a hashed hue (Theme.swift `Palette.subject(_:)`). */
fun subjectColor(name: String): Color {
    fixedSubjectColors[name]?.let { return it }
    val hue = (jsHash(name) % 360).toFloat()
    return hsv(hue, 0.50f, 0.62f)
}

/** Sender color for message/comment avatars without a persisted seed (`Palette.sender(_:)`). */
fun senderColor(name: String): Color {
    val hue = (jsHash(name) % 360).toFloat()
    return hsv(hue, 0.60f, 0.55f)
}

/** Profile-avatar color from a persisted random hue (`Palette.color(for:)` + [AvatarColorStore]).
 * `hue01` is in `[0, 1)`, as stored by [AvatarColorStore]. */
fun avatarColorFromHue(hue01: Double): Color = hsv((hue01 * 360.0).toFloat(), 0.60f, 0.55f)

/**
 * Grade color ramp, Italian 1–10 scale, positive at/above 6 (Theme.swift `Palette.grade(_:)`).
 *   value >= 6 : lerp RGB(134,239,172) -> RGB(21,128,61)  over t = (v-6)/4
 *   value <  6 : lerp RGB(239,68,68)   -> RGB(127,29,29)  over t = (6-v)/5
 */
fun gradeColor(value: Double): Color {
    fun lerp(a: Double, b: Double, t: Double): Double = a + (b - a) * t.coerceIn(0.0, 1.0)
    fun mix(c1: Triple<Double, Double, Double>, c2: Triple<Double, Double, Double>, t: Double): Color =
        Color(
            red = (lerp(c1.first, c2.first, t) / 255).toFloat(),
            green = (lerp(c1.second, c2.second, t) / 255).toFloat(),
            blue = (lerp(c1.third, c2.third, t) / 255).toFloat(),
        )
    val greenLight = Triple(134.0, 239.0, 172.0)
    val greenDark = Triple(21.0, 128.0, 61.0)
    val redNormal = Triple(239.0, 68.0, 68.0)
    val redDark = Triple(127.0, 29.0, 29.0)
    return if (value >= 6.0) {
        mix(greenLight, greenDark, (value - 6.0) / (10.0 - 6.0))
    } else {
        mix(redNormal, redDark, (6.0 - value) / (6.0 - 1.0))
    }
}

/** Simplified binary grade color used by widgets (Phase 3) — `Brand.grade` on iOS. */
fun gradeColorSimple(value: Double): Color = if (value >= 6.0) Brand.success else Brand.danger

/**
 * Rendering-layer treatment, NOT a data-color change (unlike everything above this line, this
 * is safe to adjust freely). Softens a fully-saturated subject/status color toward the card
 * fill for solid decorative use (e.g. a timetable cell's accent bar) against the warm backdrop:
 * same hue, same relative distinctness between subjects, gentler than the raw hex. Do NOT use
 * it for badges/dots/icons that rely on fast color-scanning (cancelled/exam status) — those
 * stay at full saturation everywhere.
 */
fun Color.softenedFill(): Color = androidx.compose.ui.graphics.lerp(this, Color.White, 0.18f)

/** Convenience accessor mirroring the iOS call-site style `Palette.accent`, `PokyhTheme.colors.bg`. */
object PokyhTheme {
    val colors: PokyhColors
        @Composable get() = LocalPokyhColors.current
}

@Composable
fun ProvidePokyhColors(colors: PokyhColors, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPokyhColors provides colors, content = content)
}
