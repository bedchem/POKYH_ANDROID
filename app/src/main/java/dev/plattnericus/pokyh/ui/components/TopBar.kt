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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.plattnericus.pokyh.ui.theme.SmoothCornerShape
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** How [PokyhTopBar] presents its leading (left) slot. */
sealed interface TopBarNav {
    data object None : TopBarNav
    data class Back(val onClick: () -> Unit) : TopBarNav
    data class Close(val onClick: () -> Unit, val label: String = dev.plattnericus.pokyh.core.i18n.I18n.s.common.cancel) : TopBarNav
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
    /** The actions fill the row themselves (a tab root's search field): no spacer before them. */
    fillActions: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = PokyhTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = PokyhSpacing.screenH,
                end = PokyhSpacing.screenH,
                top = if (fillActions) 0.dp else PokyhSpacing.sm,
                bottom = if (title != null) 28.dp else PokyhSpacing.md,
            ),
    ) {
        Row(
            // A tab root's row is the web's top bar: 64dp, its controls 10dp apart.
            modifier = if (fillActions) Modifier.fillMaxWidth().height(64.dp) else Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (fillActions) 10.dp else PokyhSpacing.sm),
        ) {
            when (nav) {
                is TopBarNav.None -> Unit
                is TopBarNav.Back -> PokyhIconButton(
                    icon = PokyhIcons.back,
                    contentDescription = dev.plattnericus.pokyh.core.i18n.strings().common.back,
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
            if (!fillActions) Spacer(Modifier.weight(1f))
            actions()
        }

        if (title != null) {
            // The web's page header (components/app/Page.tsx, PageHeader): the title at 34px,
            // extra bold and tight, the subtitle under it in the second ink.
            Spacer(Modifier.size(16.dp))
            Text(
                text = title,
                style = PokyhType.largeTitle.copy(fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = (-0.05).em),
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (eyebrow != null) {
                Spacer(Modifier.size(8.dp))
                Text(eyebrow, style = PokyhType.body.copy(fontSize = 15.sp, lineHeight = 22.sp), color = colors.textSecondary)
            }
        }
    }
}

/** The web's top-bar controls (shell.module.css, `.iconBtn`): 46dp circles in the surface tone. */
val TopBarControlSize = 46.dp

/**
 * The web's search field in the top bar (`.search`): as wide as the row allows up to 560dp, 46dp
 * tall, the surface tone, the magnifier and "Suchen" in the quiet ink. Opens the search.
 */
@Composable
fun RowScope.TopBarSearchField(onClick: () -> Unit) {
    val colors = PokyhTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .weight(1f)
            .widthIn(max = 560.dp)
            .height(TopBarControlSize)
            .clip(SmoothCornerShape(22.dp))
            .background(colors.card)
            .pressHighlight(interaction, SmoothCornerShape(22.dp))
            .clickable(interactionSource = interaction, indication = null, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        androidx.compose.material3.Icon(PokyhIcons.search, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(19.dp))
        Text(
            dev.plattnericus.pokyh.core.i18n.strings().search.title,
            style = PokyhType.body.copy(fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight(550)),
            color = colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
    /** Where the bell's items and the account menu lead; with it, the row is the web's top bar:
     * the search field, the recap, the messages, the bell and the picture with the account menu.
     * Its [PokyhTopBar] then needs `fillActions = true`. */
    onNavigate: ((String) -> Unit)? = null,
    /** A screen's own control (a menu), right after the search field. */
    extra: @Composable RowScope.() -> Unit = {},
) {
    if (onNavigate != null) {
        TopBarSearchField(onClick = { onNavigate(dev.plattnericus.pokyh.ui.navigation.PokyhDestinations.SEARCH) })
        extra()
        // The recap beside the messages, while there is one (on phones the web shows only its circle).
        dev.plattnericus.pokyh.ui.recap.RecapHeaderButton()
    }
    // WebUntis keeps the messages: an account without it has no envelope, as on the web.
    val hasUntis = if (onNavigate != null) {
        androidx.hilt.navigation.compose.hiltViewModel<dev.plattnericus.pokyh.ui.profile.ProfileViewModel>()
            .session.collectAsStateWithLifecycle().value?.hasUntis != false
    } else {
        true
    }
    if (hasUntis) Box {
        PokyhIconButton(
            icon = PokyhIcons.messages,
            contentDescription = if (unreadMessages > 0) {
                dev.plattnericus.pokyh.core.i18n.strings().nav.messagesUnread(unreadMessages)
            } else {
                dev.plattnericus.pokyh.core.i18n.strings().nav.messages
            },
            onClick = onMessages,
            containerColor = if (onNavigate != null) PokyhTheme.colors.card else null,
            tinted = onNavigate == null,
            size = if (onNavigate != null) TopBarControlSize else 40.dp,
            iconSize = 20.dp,
        )
        if (unreadMessages > 0) {
            UnreadBadge(
                count = unreadMessages,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp),
            )
        }
    }
    // The picture opens the account menu, dropping down from it as on the web.
    if (onNavigate != null) {
        dev.plattnericus.pokyh.ui.bell.NotificationBell(onNavigate = onNavigate)
        dev.plattnericus.pokyh.ui.profile.AccountMenuButton(onNavigate = onNavigate)
        return
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
val TabRootAvatarSize = 46.dp

/**
 * The unread count on the Messages action: an accent pill with a ring of the page background,
 * so it stays legible where it overlaps the icon's own tinted circle.
 *
 * Counts above 9 collapse to "9+" — the exact number stops being actionable past that, and a
 * three-digit badge would be wider than the button it sits on.
 */
@Composable
fun UnreadBadge(count: Int, modifier: Modifier = Modifier, ring: androidx.compose.ui.graphics.Color = PokyhTheme.colors.bg) {
    if (count <= 0) return
    Box(
        modifier = modifier
            .background(ring, PokyhShapes.pill)
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
