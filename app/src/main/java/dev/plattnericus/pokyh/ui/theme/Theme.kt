package dev.plattnericus.pokyh.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

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
 * Root theme wrapper. Dynamic Color (Material You) is deliberately OFF — see the plan: the
 * POKYH palette is brand identity shared with iOS/Web, not something a wallpaper should
 * override. Material3's ColorScheme is populated from [PokyhColors]/[Brand] purely so stock
 * M3 components (TextField, Switch, Menu, ModalBottomSheet chrome) tint correctly; screens
 * needing exact iOS parity read [PokyhTheme.colors] / [Brand] directly, not MaterialTheme.colorScheme.
 */
@Composable
fun PokyhAppTheme(themeMode: PokyhThemeMode, content: @Composable () -> Unit) {
    val isDark = themeMode.resolveIsDark()
    val pokyhColors = if (isDark) DarkPokyhColors else LightPokyhColors

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = Brand.accent,
            onPrimary = androidx.compose.ui.graphics.Color.White,
            secondary = Brand.accentSoft,
            background = pokyhColors.bg,
            onBackground = pokyhColors.textPrimary,
            surface = pokyhColors.surface,
            onSurface = pokyhColors.textPrimary,
            surfaceVariant = pokyhColors.cardAlt,
            onSurfaceVariant = pokyhColors.textSecondary,
            error = Brand.danger,
            outline = pokyhColors.border,
            outlineVariant = pokyhColors.separator,
        )
    } else {
        lightColorScheme(
            primary = Brand.accent,
            onPrimary = androidx.compose.ui.graphics.Color.White,
            secondary = Brand.accentSoft,
            background = pokyhColors.bg,
            onBackground = pokyhColors.textPrimary,
            surface = pokyhColors.surface,
            onSurface = pokyhColors.textPrimary,
            surfaceVariant = pokyhColors.cardAlt,
            onSurfaceVariant = pokyhColors.textSecondary,
            error = Brand.danger,
            outline = pokyhColors.border,
            outlineVariant = pokyhColors.separator,
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
