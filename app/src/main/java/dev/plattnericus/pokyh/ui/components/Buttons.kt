package dev.plattnericus.pokyh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.floatingSurface
import dev.plattnericus.pokyh.ui.theme.pressHighlight
import dev.plattnericus.pokyh.ui.theme.pressable

/**
 * Five buttons, in descending emphasis, and a screen should almost never show two of the same
 * kind at once:
 *
 *  - [PokyhPrimaryButton]      solid [Brand.accent]. The one thing the screen wants you to do.
 *  - [PokyhSecondaryButton]    accent-tinted. An alternative to the primary action.
 *  - [PokyhDestructiveButton]  danger-tinted. Delete / sign out.
 *  - [PokyhTextButton]         label only. Dismissals, inline links.
 *  - [PokyhIconButton]         a bare glyph with a real tap target. Toolbar affordances.
 *
 * All capsules, two heights, built on [Row] + [clickable] rather than Material's `Button` — the
 * stock component brings its own min-width, content padding, elevation and ripple, every one of
 * which had to be overridden at each call site.
 */
private val ButtonHeight = 52.dp
private val CompactButtonHeight = 42.dp

@Composable
fun PokyhPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    compact: Boolean = false,
) {
    val active = enabled && !loading
    ButtonShell(
        onClick = onClick,
        enabled = active,
        modifier = modifier,
        container = if (active) Brand.accent else Brand.accent.copy(alpha = 0.45f),
        contentColor = Brand.onAccent.copy(alpha = if (active) 1f else 0.85f),
        compact = compact,
        loading = loading,
        icon = icon,
        text = text,
    )
}

@Composable
fun PokyhSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    compact: Boolean = false,
) {
    val colors = PokyhTheme.colors
    ButtonShell(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier,
        container = colors.accentTint,
        contentColor = if (enabled) colors.accentText else colors.textTertiary,
        compact = compact,
        loading = loading,
        icon = icon,
        text = text,
    )
}

/**
 * A destructive action as a *tinted* button rather than a solid red one — a solid danger fill is
 * loud enough to read as the screen's primary action, which "Abmelden" and "Cache löschen"
 * aren't. The red text carries the meaning; the fill only makes it a button.
 */
@Composable
fun PokyhDestructiveButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    compact: Boolean = false,
) {
    ButtonShell(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier,
        container = Brand.danger.copy(alpha = if (PokyhTheme.colors.isDark) 0.18f else 0.09f),
        contentColor = Brand.danger,
        compact = compact,
        loading = loading,
        icon = icon,
        text = text,
    )
}

@Composable
private fun ButtonShell(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier,
    container: Color,
    contentColor: Color,
    compact: Boolean,
    loading: Boolean,
    icon: ImageVector?,
    text: String,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .height(if (compact) CompactButtonHeight else ButtonHeight)
            .defaultMinSize(minWidth = if (compact) 88.dp else 120.dp)
            .pressable(interactionSource)
            .clip(PokyhShapes.pill)
            .background(container)
            .pressHighlight(interactionSource, PokyhShapes.pill)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = if (compact) PokyhSpacing.lg else PokyhSpacing.xxl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm, Alignment.CenterHorizontally),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = contentColor, strokeWidth = 2.dp)
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
        }
        Text(text, style = if (compact) PokyhType.headline else PokyhType.button, color = contentColor)
    }
}

/** Label-only action. Dialog dismissals, inline links, "mehr anzeigen". */
@Composable
fun PokyhTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = PokyhTheme.colors.accentText,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .clip(PokyhShapes.pill)
            .pressHighlight(interactionSource, PokyhShapes.pill)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = PokyhSpacing.md, vertical = PokyhSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) color else PokyhTheme.colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(text, style = PokyhType.headline, color = if (enabled) color else PokyhTheme.colors.textTertiary)
    }
}

/**
 * A glyph with a 40dp tap target. [tinted] adds the soft circular backdrop the tab-root top bars
 * use, so a toolbar action reads as a designed control rather than a floating icon;
 * [containerColor] overrides that fill for the rare action that is the screen's primary one
 * (the accent circle that opens the message composer).
 */
@Composable
fun PokyhIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = PokyhTheme.colors.textPrimary,
    tinted: Boolean = false,
    containerColor: Color? = null,
    enabled: Boolean = true,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val colors = PokyhTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .clip(PokyhShapes.pill)
            .then(
                when {
                    containerColor != null -> Modifier.background(containerColor)
                    tinted -> Modifier.background(colors.cardAlt)
                    else -> Modifier
                },
            )
            .pressHighlight(interactionSource, PokyhShapes.pill)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else colors.textTertiary,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * The "add" affordance on Todos/Erinnerungen. A *labelled* accent capsule rather than a circular
 * Material FAB: it says what it does, and a capsule keeps it in the button family instead of
 * introducing the app's only floating circle.
 */
@Composable
fun PokyhFab(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .height(50.dp)
            .pressable(interactionSource)
            .floatingSurface(shape = PokyhShapes.pill, color = Brand.accent)
            .clip(PokyhShapes.pill)
            .pressHighlight(interactionSource, PokyhShapes.pill)
            .clickable(interactionSource = interactionSource, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = PokyhSpacing.xl),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
    ) {
        Icon(icon, contentDescription = null, tint = Brand.onAccent, modifier = Modifier.size(20.dp))
        Text(text, style = PokyhType.headline, color = Brand.onAccent)
    }
}
