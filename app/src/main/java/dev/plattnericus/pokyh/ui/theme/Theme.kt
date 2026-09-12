package dev.plattnericus.pokyh.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

/** `ThemeMode` (Theme.swift / ProfileView) — persisted in DataStore, not derived from anything else. */
enum class PokyhThemeMode { System, Light, Dark }

/** Resolves [PokyhThemeMode] against the system setting, mirroring `AppState.colorScheme`. */
@Composable
fun PokyhThemeMode.resolveIsDark(): Boolean = when (this) {
    PokyhThemeMode.System -> isSystemInDarkTheme()
    PokyhThemeMode.Light -> false
    PokyhThemeMode.Dark -> true
}

/**
 * Root theme wrapper.
 *
 * Dynamic Color (Material You) is deliberately OFF — the POKYH palette is brand identity shared
 * with iOS/Web, not something a wallpaper should override.
 *
 * App code reads [PokyhTheme.colors] / [Brand] / [PokyhType] directly and never
 * `MaterialTheme.colorScheme`. The full M3 [androidx.compose.material3.ColorScheme] is populated
 * here anyway, because the stock M3 components the app *does* use (TextField, Switch,
 * DropdownMenu, ModalBottomSheet, DatePicker, AlertDialog) read those roles internally — leaving
 * any of them at the baseline would show up as generic Material purple inside an otherwise
 * POKYH-colored screen. Neutral-axis roles come straight from [PokyhColors]; the container and
 * inverse roles are a hand-picked complementary set built from [Brand.accent]/[Brand.accentSoft].
 *
 * Switching "Erscheinungsbild" at runtime crossfades the neutral ramp over
 * [ThemeCrossfadeDurationMillis] rather than hard-cutting — see [animatedColors].
 */
@Composable
fun PokyhAppTheme(themeMode: PokyhThemeMode, content: @Composable () -> Unit) {
    val isDark = themeMode.resolveIsDark()
    val target = if (isDark) DarkPokyhColors else LightPokyhColors
    val inverse = if (isDark) LightPokyhColors else DarkPokyhColors
    val pokyhColors = animatedColors(target)

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = Brand.accent,
            onPrimary = Brand.onAccent,
            primaryContainer = target.accentTint,
            onPrimaryContainer = Color(0xFFD8D5FA),
            secondary = Brand.accentSoft,
            onSecondary = Brand.onAccent,
            secondaryContainer = Color(0xFF332A52),
            onSecondaryContainer = Color(0xFFE6DCFF),
            tertiary = Brand.accentSoft,
            onTertiary = Brand.onAccent,
            tertiaryContainer = target.accentTint,
            onTertiaryContainer = Color(0xFFD8D5FA),
            background = target.bg,
            onBackground = target.textPrimary,
            surface = target.surface,
            onSurface = target.textPrimary,
            surfaceVariant = target.cardAlt,
            onSurfaceVariant = target.textSecondary,
            surfaceTint = Brand.accent,
            surfaceBright = target.cardAlt,
            surfaceDim = target.bg,
            surfaceContainerLowest = target.bg,
            surfaceContainerLow = target.surface,
            surfaceContainer = target.card,
            surfaceContainerHigh = target.cardAlt,
            surfaceContainerHighest = target.cardAlt,
            error = Brand.danger,
            onError = Color.White,
            errorContainer = Color(0xFF551A17),
            onErrorContainer = Color(0xFFFBD6D3),
            outline = target.border,
            outlineVariant = target.separator,
            inversePrimary = Brand.accent,
            inverseSurface = inverse.surface,
            inverseOnSurface = inverse.textPrimary,
            scrim = Color.Black,
        )
    } else {
        lightColorScheme(
            primary = Brand.accent,
            onPrimary = Brand.onAccent,
            primaryContainer = target.accentTint,
            onPrimaryContainer = Color(0xFF2A2570),
            secondary = Brand.accentSoft,
            onSecondary = Brand.onAccent,
            secondaryContainer = Color(0xFFEDE6FF),
            onSecondaryContainer = Color(0xFF3A2D5C),
            tertiary = Brand.accentSoft,
            onTertiary = Brand.onAccent,
            tertiaryContainer = target.accentTint,
            onTertiaryContainer = Color(0xFF2A2570),
            background = target.bg,
            onBackground = target.textPrimary,
            surface = target.surface,
            onSurface = target.textPrimary,
            surfaceVariant = target.cardAlt,
            onSurfaceVariant = target.textSecondary,
            surfaceTint = Brand.accent,
            surfaceBright = target.surface,
            surfaceDim = target.cardAlt,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = target.surface,
            surfaceContainer = target.card,
            surfaceContainerHigh = target.cardAlt,
            surfaceContainerHighest = target.cardAlt,
            error = Brand.danger,
            onError = Color.White,
            errorContainer = Color(0xFFFBE2E1),
            onErrorContainer = Color(0xFF7A1712),
            outline = target.border,
            outlineVariant = target.separator,
            inversePrimary = Color(0xFFC4C2FF),
            inverseSurface = inverse.surface,
            inverseOnSurface = inverse.textPrimary,
            scrim = Color.Black,
        )
    }

    ProvidePokyhColors(pokyhColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = PokyhMaterialTypography,
            content = content,
        )
    }
}

/**
 * Crossfades the neutral ramp toward [target] so flipping the theme at runtime reads as the page
 * changing tone rather than as a hard cut. Only the neutral tokens animate — [Brand] colors are
 * theme-independent, and `isDark` flips immediately (it drives shadow math, not a color).
 */
@Composable
private fun animatedColors(target: PokyhColors): PokyhColors {
    val spec = tween<Color>(durationMillis = ThemeCrossfadeDurationMillis)

    @Composable
    fun fade(color: Color, label: String): Color {
        val animated by animateColorAsState(targetValue = color, animationSpec = spec, label = label)
        return animated
    }

    return target.copy(
        bg = fade(target.bg, "bg"),
        surface = fade(target.surface, "surface"),
        card = fade(target.card, "card"),
        cardAlt = fade(target.cardAlt, "cardAlt"),
        border = fade(target.border, "border"),
        separator = fade(target.separator, "separator"),
        textPrimary = fade(target.textPrimary, "textPrimary"),
        textSecondary = fade(target.textSecondary, "textSecondary"),
        textTertiary = fade(target.textTertiary, "textTertiary"),
        accentTint = fade(target.accentTint, "accentTint"),
    )
}
