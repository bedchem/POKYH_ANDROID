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
import dev.plattnericus.pokyh.ui.components.PokyhFittedText
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.plattnericus.pokyh.ui.theme.PokyhDecorative

data class PokyhNavItem(val tab: AppTab, val icon: ImageVector, val label: String, val badge: Int = 0)

/** The web's bar (components/app/shell.module.css, `.bar`): a squircle, 40px at the top. */
private val BarShape = dev.plattnericus.pokyh.ui.theme.TopSmoothCornerShape(40.dp)

/** The web's active pill (`.pill`): a fixed 64 × 32 in the periwinkle tone. */
private val PillWidth = 64.dp
private val PillHeight = 32.dp
private val TabHeight = 56.dp

/**
 * The bottom navigation, as the web's phone bar (components/app/AppShell.tsx, `BottomBar`):
 * the nav tone, its top corners rounded, 8dp around the tabs. The current tab sits on a
 * periwinkle pill that glides from tab to tab (the web's shared `layoutId`), its glyph filled
 * and the glyph and label in periwinkle ink; the others in the quiet ink-3.
 */
@Composable
fun PokyhBottomNav(
    items: List<PokyhNavItem>,
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PokyhTheme.colors
    val tone = PokyhDecorative.periwinkle(colors.isDark)
    val index = items.indexOfFirst { it.tab == selected }
    // The pill's place, in tabs: animated so it glides rather than jumps (the web's GLIDE spring).
    val at by animateFloatAsState(
        targetValue = index.coerceAtLeast(0).toFloat(),
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 520f),
        label = "tabPill",
    )
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .shadow(14.dp, BarShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
            .background(colors.nav, BarShape)
            .navigationBarsPadding()
            .padding(8.dp),
    ) {
        val tabWidth = maxWidth / items.size.coerceAtLeast(1)
        if (index >= 0) {
            // The pill sits where the tab's own pill box is: centred, 3dp from the tab's top.
            Box(
                Modifier
                    .offset(x = tabWidth * at + (tabWidth - PillWidth) / 2, y = (TabHeight - PillHeight - 4.dp - 14.dp) / 2)
                    .size(PillWidth, PillHeight)
                    .background(tone.fill, PokyhShapes.pill),
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            items.forEach { item ->
                BarTab(item = item, isSelected = item.tab == selected, onClick = { onSelect(item.tab) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** One tab of the bar (the web's `.tab`): the 64 × 32 pill box, 4dp, the label; 56dp tall. */
@Composable
private fun BarTab(item: PokyhNavItem, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PokyhTheme.colors
    val tone = PokyhDecorative.periwinkle(colors.isDark)
    val ink by animateColorAsState(
        targetValue = if (isSelected) tone.ink else colors.textTertiary,
        animationSpec = tween(250),
        label = "tabInk",
    )
    Column(
        modifier = modifier
            .height(TabHeight)
            // No rounded clip here: it cut the corners off a label as wide as the tab
            // ("Stundenplan"), and the tab has no ripple for it to shape anyway.
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Box(Modifier.size(PillWidth, PillHeight), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isSelected) PokyhIcons.navSelected(item.icon) else item.icon,
                contentDescription = item.label,
                tint = ink,
                modifier = Modifier.size(22.dp),
            )
            if (item.badge > 0) {
                // The web's badge: its left edge 8dp right of the middle, 2dp from the top, ringed in the bar's tone.
                dev.plattnericus.pokyh.ui.components.UnreadBadge(
                    count = item.badge,
                    ring = colors.nav,
                    modifier = Modifier.align(Alignment.TopStart).offset(x = PillWidth / 2 + 8.dp - 2.dp, y = (-3).dp),
                )
            }
        }
        PokyhFittedText(
            text = item.label,
            style = PokyhType.navLabel.copy(fontSize = 11.5.sp, lineHeight = 14.sp, fontWeight = FontWeight(650), letterSpacing = (-0.005).em),
            color = ink,
            maxLines = 1,
        )
    }
}

/**
 * The tab bar on a mid-size window (a small tablet, a phone on its side): the same bar, floating
 * above the bottom edge and no wider than a phone's — a bar stretched across 800dp spreads five
 * small targets so far apart that reaching from one to the next is a journey.
 */
@Composable
fun PokyhFloatingNav(
    items: List<PokyhNavItem>,
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = PokyhSpacing.md), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .padding(horizontal = PokyhSpacing.lg)
                .floatingSurface(shape = PokyhShapes.xxl, color = PokyhTheme.colors.nav)
                .clip(PokyhShapes.xxl)
                .padding(horizontal = PokyhSpacing.sm, vertical = PokyhSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                PokyhBottomNavItem(item = item, isSelected = item.tab == selected, onClick = { onSelect(item.tab) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * The tabs on a wide window, as the web's side bar shows them folded: a column at the left edge
 * in the bar's own tone, each place an icon over its name, the POKYH mark on top. Content then
 * gets the whole height, which on a tablet on its side is what there is least of.
 */
@Composable
fun PokyhSideNav(
    items: List<PokyhNavItem>,
    selected: AppTab,
    onSelect: (AppTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(96.dp)
            .background(PokyhTheme.colors.nav)
            .systemBarsPadding()
            .padding(vertical = PokyhSpacing.lg, horizontal = PokyhSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
    ) {
        dev.plattnericus.pokyh.ui.components.PokyhMark(size = 40.dp, modifier = Modifier.clip(PokyhShapes.md))
        Spacer(Modifier.height(PokyhSpacing.md))
        items.forEach { item ->
            PokyhBottomNavItem(item = item, isSelected = item.tab == selected, onClick = { onSelect(item.tab) }, modifier = Modifier.fillMaxWidth())
        }
    }
}

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

    val tone = PokyhDecorative.periwinkle(colors.isDark)
    val indicatorColor by animateColorAsState(
        targetValue = if (isSelected) tone.fill else tone.fill.copy(alpha = 0f),
        animationSpec = spec,
        label = "navIndicator",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) tone.ink else colors.textTertiary,
        animationSpec = spec,
        label = "navContent",
    )
    val indicatorWidth = PillWidth

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
            if (item.badge > 0) {
                dev.plattnericus.pokyh.ui.components.UnreadBadge(
                    count = item.badge,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = (-6).dp, y = (-2).dp),
                )
            }
        }
        Spacer(Modifier.size(PokyhSpacing.xs))
        PokyhFittedText(
            text = item.label,
            style = PokyhType.navLabel.copy(fontSize = 11.5.sp, lineHeight = 14.sp, fontWeight = FontWeight(650)),
            color = contentColor,
            maxLines = 1,
        )
    }
}
