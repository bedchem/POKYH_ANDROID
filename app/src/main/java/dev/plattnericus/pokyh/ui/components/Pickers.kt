package dev.plattnericus.pokyh.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhMotion
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.pressHighlight

/**
 * The trigger for a [androidx.compose.material3.DropdownMenu]: a recessed pill showing the
 * current value with a caret that rotates when the menu opens.
 *
 * Exists because a menu anchored to bare text is invisible as a control — the year pickers on
 * Noten/Abwesenheiten/Klassenbuch and the sort menu on Noten were all plain labels, so nothing
 * said they could be tapped. The recessed fill says "control", the caret says "there are more
 * options", and the rotation confirms the tap landed.
 *
 * Anchor it the usual way — put it and the menu in the same `Box`:
 * ```
 * Box {
 *     PokyhMenuButton(label = "2025/26", expanded = open, onClick = { open = true })
 *     DropdownMenu(expanded = open, onDismissRequest = { open = false }) { … }
 * }
 * ```
 */
@Composable
fun PokyhMenuButton(
    label: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = PokyhTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val caretRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(PokyhMotion.durationFast),
        label = "menuCaret",
    )
    val contentColor = if (enabled) colors.textPrimary else colors.textTertiary

    Row(
        modifier = modifier
            .clip(PokyhShapes.pill)
            .background(colors.cardAlt)
            .pressHighlight(interactionSource, PokyhShapes.pill)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.DropdownList,
                onClick = onClick,
            )
            .defaultMinSize(minHeight = 36.dp)
            .padding(start = PokyhSpacing.md, end = PokyhSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.xs),
    ) {
        if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = contentColor, modifier = Modifier.size(15.dp))
        }
        Text(label, style = PokyhType.caption, color = contentColor)
        Icon(
            imageVector = PokyhIcons.expandMenu,
            contentDescription = null,
            tint = if (enabled) colors.textSecondary else colors.textTertiary,
            modifier = Modifier.size(14.dp).rotate(caretRotation),
        )
    }
}

/**
 * A two-state inline toggle: a recessed pill whose fill and label go accent-tinted when on.
 *
 * For a setting that flips between two equally-valid readings rather than picking from a list —
 * Abwesenheiten's "Gerundet"/"Exakt" being the case that prompted it. That was a bare
 * icon-plus-label with no surface, so it read as a status line rather than something you could
 * tap to change.
 */
@Composable
fun PokyhToggleChip(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val colors = PokyhTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val fill by animateColorAsState(
        targetValue = if (checked) colors.accentTint else colors.cardAlt,
        animationSpec = tween(PokyhMotion.durationFast),
        label = "toggleChipFill",
    )
    val content by animateColorAsState(
        targetValue = if (checked) colors.accentText else colors.textSecondary,
        animationSpec = tween(PokyhMotion.durationFast),
        label = "toggleChipContent",
    )

    Row(
        modifier = modifier
            .clip(PokyhShapes.pill)
            .background(fill)
            .pressHighlight(interactionSource, PokyhShapes.pill)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Switch,
                onClick = onToggle,
            )
            .defaultMinSize(minHeight = 36.dp)
            .padding(horizontal = PokyhSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.xs),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(15.dp))
        }
        Text(label, style = PokyhType.caption, color = content)
    }
}

/** The caret a row uses to say "tapping me opens a menu", for rows that aren't navigation and so
 * shouldn't carry [PokyhIcons.chevronRight]. */
@Composable
fun PokyhRowMenuCaret(expanded: Boolean = false, tint: Color = PokyhTheme.colors.textTertiary) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(PokyhMotion.durationFast),
        label = "rowCaret",
    )
    Icon(
        imageVector = PokyhIcons.expandMenu,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(16.dp).rotate(rotation),
    )
}
