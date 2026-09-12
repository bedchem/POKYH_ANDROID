package dev.plattnericus.pokyh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType

/**
 * Small labelled markers. Two sizes and nothing in between:
 *
 *  - [TagChip]    the default. A status or category on a row or card ("Entfall", "VEGAN").
 *  - [MiniBadge]  the compact form, for a marker riding *inside* a line of text.
 *
 * Both take the semantic color of the thing they mark and derive their own fill from it at a
 * fixed alpha, so a chip is never independently colored — it always agrees with the icon or text
 * it accompanies.
 */
private const val ChipFillAlpha = 0.13f

@Composable
fun TagChip(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    large: Boolean = false,
) {
    Row(
        modifier = modifier
            .background(color.copy(alpha = ChipFillAlpha), PokyhShapes.pill)
            .padding(
                horizontal = if (large) PokyhSpacing.md else PokyhSpacing.sm,
                vertical = if (large) 6.dp else 3.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.xs),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(if (large) 14.dp else 11.dp))
        }
        Text(
            text = text,
            style = if (large) PokyhType.caption else PokyhType.badgeChip,
            color = color,
        )
    }
}

/** The compact chip, for a marker sitting inside a line of text (an account's "Standard" flag). */
@Composable
fun MiniBadge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .background(color.copy(alpha = ChipFillAlpha), PokyhShapes.pill)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(9.dp))
        }
        Text(text, style = PokyhType.badgeChip, color = color)
    }
}

/**
 * A rounded glyph tile: the leading slot of a row, or the icon of a stat card. The fill is the
 * icon's own color at a fixed alpha, which is what keeps a column of tiles reading as one
 * family even when each one is a different semantic color.
 *
 * The radius scales with the tile so the corner character stays constant — [PokyhShapes.sm] at
 * 40dp, [PokyhShapes.md] at 48dp and up.
 */
@Composable
fun IconTile(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    shape: Shape? = null,
    iconSize: Dp = size * 0.48f,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color.copy(alpha = 0.13f), shape ?: if (size <= 40.dp) PokyhShapes.sm else PokyhShapes.md),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(iconSize))
    }
}

/** A neutral [IconTile] — for a row whose icon carries no status meaning. */
@Composable
fun NeutralIconTile(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val colors = PokyhTheme.colors
    Box(
        modifier = modifier
            .size(size)
            .background(colors.cardAlt, if (size <= 40.dp) PokyhShapes.sm else PokyhShapes.md),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(size * 0.48f))
    }
}

/**
 * A status line: a colored glyph plus its label, at caption size. The one pattern for
 * "entschuldigt / offen", "verbunden / Fehler" and similar — so that state never shows up as a
 * bare colored dot in one place and an icon+text pair in another.
 */
@Composable
fun StatusLabel(
    text: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.xs),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        Text(text, style = PokyhType.caption, color = color)
    }
}
