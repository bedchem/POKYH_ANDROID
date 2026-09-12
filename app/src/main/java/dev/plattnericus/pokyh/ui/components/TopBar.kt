package dev.plattnericus.pokyh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.pressHighlight

/** How [PokyhTopBar] presents its leading (left) slot. */
sealed interface TopBarNav {
    data object None : TopBarNav
    data class Back(val onClick: () -> Unit) : TopBarNav
    data class Close(val onClick: () -> Unit, val label: String = "Abbrechen") : TopBarNav
}

/**
 * The tap target of a circular control is wider than its visible edge, so a 40dp button whose
 * glyph should sit on the screen's text margin has to be pulled back by the difference.
 * Without it, every screen with a back button looks indented against every screen without one.
 */
private val ControlOpticalInset = 10.dp

/**
 * Every screen's header, and the single strongest thing tying the app together visually: a
 * control row (back / actions), then the screen title set large on the page canvas.
 *
 * Transparent by design — it is part of the page rather than a bar laid over it, so nothing
 * competes with the content or with the bottom nav. The title uses [PokyhType.largeTitle], the
 * "large confident heading" the rest of the type scale is calibrated against; an optional
 * all-caps [eyebrow] above it carries context (a school year, a count) that used to get crammed
 * into the title string.
 *
 * This replaced a two-row stack — a custom icon row carrying the Messages/Profile shortcuts,
 * *plus* each screen's own Material `TopAppBar` — so a screen now has exactly one header.
 */
@Composable
fun PokyhTopBar(
    title: String?,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    nav: TopBarNav = TopBarNav.None,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = PokyhTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = PokyhSpacing.screenH,
                end = PokyhSpacing.screenH,
                top = PokyhSpacing.sm,
                bottom = PokyhSpacing.md,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
        ) {
            when (nav) {
                is TopBarNav.None -> Unit
                is TopBarNav.Back -> PokyhIconButton(
                    icon = PokyhIcons.back,
                    contentDescription = "Zurück",
                    onClick = nav.onClick,
                    tinted = true,
                    modifier = Modifier.offset(x = -ControlOpticalInset),
                )
                is TopBarNav.Close -> PokyhTextButton(
                    text = nav.label,
                    onClick = nav.onClick,
                    color = colors.textSecondary,
                    modifier = Modifier.offset(x = -PokyhSpacing.md),
                )
            }
            Spacer(Modifier.weight(1f))
            actions()
        }

        if (title != null) {
            Spacer(Modifier.size(PokyhSpacing.md))
            if (eyebrow != null) {
                PokyhLabel(eyebrow)
                Spacer(Modifier.size(PokyhSpacing.xs))
            }
            Text(
                text = title,
                style = PokyhType.largeTitle,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The Messages/Profile shortcut pair every tab-root screen puts in its header's actions slot.
 * Each sits in its own soft tinted circle rather than as a bare glyph, matching the rounded,
 * color-blocked language used everywhere else instead of reading as toolbar chrome.
 */
@Composable
fun RowScope.TabRootActions(
    avatarContent: @Composable () -> Unit,
    onMessages: () -> Unit,
    onProfile: () -> Unit,
) {
    PokyhIconButton(
        icon = PokyhIcons.messages,
        contentDescription = "Nachrichten",
        onClick = onMessages,
        tinted = true,
        iconSize = 19.dp,
    )
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(PokyhShapes.pill)
            .background(PokyhTheme.colors.cardAlt)
            .pressHighlight(interactionSource, PokyhShapes.pill)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onProfile),
        contentAlignment = Alignment.Center,
    ) {
        avatarContent()
    }
}
