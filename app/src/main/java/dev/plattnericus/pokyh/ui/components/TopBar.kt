package dev.plattnericus.pokyh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
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
import dev.plattnericus.pokyh.ui.theme.Brand
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
 *
 * Messages is a glyph, so it gets the soft tinted circle that makes it read as a control. The
 * profile slot does NOT: [avatarContent] fills the whole 40dp target edge to edge. It used to
 * render a 28dp avatar centered on the same tinted disc, which put a visible ring of chrome
 * around the user's photo — the photo is already a circle, and framing it in a second one just
 * made it look inset and small.
 */
@Composable
fun RowScope.TabRootActions(
    avatarContent: @Composable () -> Unit,
    onMessages: () -> Unit,
    onProfile: () -> Unit,
    unreadMessages: Int = 0,
) {
    Box {
        PokyhIconButton(
            icon = PokyhIcons.messages,
            contentDescription = if (unreadMessages > 0) {
                "Nachrichten, $unreadMessages ungelesen"
            } else {
                "Nachrichten"
            },
            onClick = onMessages,
            tinted = true,
            iconSize = 19.dp,
        )
        if (unreadMessages > 0) {
            UnreadBadge(
                count = unreadMessages,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp),
            )
        }
    }
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(TabRootAvatarSize)
            .clip(PokyhShapes.pill)
            .pressHighlight(interactionSource, PokyhShapes.pill)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onProfile),
        contentAlignment = Alignment.Center,
    ) {
        avatarContent()
    }
}

/** The tap target the profile avatar fills in a tab-root header — matches
 * [PokyhIconButton]'s default so the two actions sit on the same baseline. */
val TabRootAvatarSize = 40.dp

/**
 * The unread count on the Messages action: an accent pill with a ring of the page background,
 * so it stays legible where it overlaps the icon's own tinted circle.
 *
 * Counts above 9 collapse to "9+" — the exact number stops being actionable past that, and a
 * three-digit badge would be wider than the button it sits on.
 */
@Composable
fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    val colors = PokyhTheme.colors
    Box(
        modifier = modifier
            .background(colors.bg, PokyhShapes.pill)
            .padding(2.dp)
            .background(Brand.accent, PokyhShapes.pill)
            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 9) "9+" else count.toString(),
            style = PokyhType.badgeChip,
            color = Brand.onAccent,
        )
    }
}
