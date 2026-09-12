package dev.plattnericus.pokyh.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhMotion
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.floatingSurface

data class PokyhNavItem(val tab: AppTab, val icon: ImageVector, val label: String)

/**
 * The bottom navigation.
 *
 * It's the same [PokyhColors.card] fill as every card in the app, anchored to the bottom edge
 * with only its top corners rounded ([PokyhShapes.topXxl]) and lifted at
 * [PokyhElevation.level3] — so it reads as a surface the page slides *under*, which is exactly
 * what it is. Card-colored rather than a separate tone is the point: it belongs to the same
 * family as the content, and the warm canvas behind the content is what separates them.
 *
 * Selection is shown three ways at once, which is deliberate — a tab bar is the one control in
 * the app where "where am I" must be readable at a glance and without color vision:
 *
 *   1. the glyph swaps from Phosphor regular to its filled twin ([PokyhIcons.navSelected]),
 *   2. an accent-tinted tile appears behind it,
 *   3. the label goes accent-colored and its width animates a touch wider.
 *
 * Labels stay visible on every tab rather than only the selected one: with five destinations,
 * icon-only tabs are a guessing game, and a row of labels reads as a designed bar instead of a
 * row of loose glyphs.
 */
@Composable
fun PokyhBottomNav(
    items: List<PokyhNavItem>,
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .floatingSurface(shape = PokyhShapes.topXxl)
            .clip(PokyhShapes.topXxl)
            .navigationBarsPadding()
            .padding(horizontal = PokyhSpacing.sm, vertical = PokyhSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            PokyhBottomNavItem(
                item = item,
                isSelected = item.tab == selected,
                onClick = { onSelect(item.tab) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private val IndicatorWidthUnselected = 44.dp
private val IndicatorWidthSelected = 58.dp

@Composable
private fun PokyhBottomNavItem(
    item: PokyhNavItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PokyhTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val spec = tween<Color>(PokyhMotion.durationStandard)

    val indicatorColor by animateColorAsState(
        targetValue = if (isSelected) colors.accentTint else Color.Transparent,
        animationSpec = spec,
        label = "navIndicator",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) colors.accentText else colors.textTertiary,
        animationSpec = spec,
        label = "navContent",
    )
    val indicatorWidth by animateDpAsState(
        targetValue = if (isSelected) IndicatorWidthSelected else IndicatorWidthUnselected,
        animationSpec = tween(PokyhMotion.durationStandard),
        label = "navIndicatorWidth",
    )

    Column(
        modifier = modifier
            .clip(PokyhShapes.md)
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = interactionSource,
                indication = null,
            )
            .padding(vertical = PokyhSpacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(indicatorWidth)
                .height(32.dp)
                .background(indicatorColor, PokyhShapes.pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isSelected) PokyhIcons.navSelected(item.icon) else item.icon,
                contentDescription = item.label,
                tint = contentColor,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.size(PokyhSpacing.xs))
        Text(
            text = item.label,
            style = PokyhType.navLabel,
            color = contentColor,
            maxLines = 1,
        )
    }
}
