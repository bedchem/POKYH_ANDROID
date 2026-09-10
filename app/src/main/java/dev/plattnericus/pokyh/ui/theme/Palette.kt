package dev.plattnericus.pokyh.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * POKYH design system — ported 1:1 from the iOS app's `Theme.swift` (itself a port of the
 * web app's `app/globals.css`). Every literal color below is copied verbatim; do not
 * "improve" or re-derive them — visual parity across iOS/Web/Android depends on exact hex
 * matches, including the ones that look slightly inconsistent (e.g. dark-mode accent colors
 * are NOT re-tinted, same as web/iOS).
 */
object Brand {
    val accent = Color(0xFF6366F1)      // indigo
    val accentSoft = Color(0xFF8B5CF6)  // violet
    val tint = Color(0xFF10B981)        // emerald
    val success = tint
    val warning = Color(0xFFF59E0B)
    val danger = Color(0xFFEF4444)
    val orange = Color(0xFFF97316)
    val star = Color(0xFFFFD60A)
}

/** Dynamic (light/dark) tokens — mirrors `Palette.dyn(light:dark:)` in Theme.swift. */
data class PokyhColors(
    val bg: Color,
    val surface: Color,
    val card: Color,
    val cardAlt: Color,
    val border: Color,
    val separator: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
)

val LightPokyhColors = PokyhColors(
    bg = Color(0xFFF1F0F8),
    surface = Color(0xFFFAFAFA),
    card = Color(0xFFF5F4FC),
    cardAlt = Color(0xFFECEAF6),
    border = Color(0xFFE0DEEE),
    separator = Color(0xFFD8D6EA),
    textPrimary = Color(0xFF0D0C1A),
    textSecondary = Color(0xFF5A5870),
    textTertiary = Color(0xFF9A98B0),
)

val DarkPokyhColors = PokyhColors(
    bg = Color(0xFF09090C),
    surface = Color(0xFF111116),
    card = Color(0xFF18181E),
    cardAlt = Color(0xFF20202A),
    border = Color(0xFF222230),
    separator = Color(0xFF1C1C28),
    textPrimary = Color(0xFFF0F0F8),
    textSecondary = Color(0xFF8A8A9C),
    textTertiary = Color(0xFF52525F),
)

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
fun gradeColorSimple(value: Double): Color = if (value >= 6.0) Brand.tint else Brand.danger

/** Convenience accessor mirroring the iOS call-site style `Palette.accent`, `PokyhTheme.colors.bg`. */
object PokyhTheme {
    val colors: PokyhColors
        @Composable get() = LocalPokyhColors.current
}

@Composable
fun ProvidePokyhColors(colors: PokyhColors, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPokyhColors provides colors, content = content)
}

// min/max kept for readability in call sites that port Swift's `max(0, min(1, t))` idiom.
private fun clamp01(t: Double) = max(0.0, min(1.0, t))
