package dev.plattnericus.pokyh.ui.profile

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.core.i18n.I18n
import dev.plattnericus.pokyh.core.i18n.Lang
import dev.plattnericus.pokyh.core.i18n.strings
import dev.plattnericus.pokyh.ui.components.TabRootAvatarSize
import dev.plattnericus.pokyh.ui.navigation.PokyhDestinations
import dev.plattnericus.pokyh.ui.settings.WebSignInDialogs
import dev.plattnericus.pokyh.ui.settings.rememberWebSignIn
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhDecorative
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhThemeMode
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.semibold
import dev.plattnericus.pokyh.ui.theme.pressHighlight
import kotlinx.coroutines.delay
import dev.plattnericus.pokyh.ui.theme.PokyhType.bold

/** The public website, the menu's "Startseite". */
private const val WebsiteUrl = "https://pokyh.com"

private val RowShape = RoundedCornerShape(12.dp)

/**
 * The account as a picture in a tab's header that opens the account page over the whole screen, as
 * apps do: the account on top, then the profile, the settings, light or dark, scanning the
 * website's QR code to sign in there, signing out (the first tap asks, the second signs out) and
 * the language — the entries of the web's `AccountMenu` (components/app/AppShell.tsx). Back or
 * the close button leave it.
 */
@Composable
fun AccountMenuButton(
    onNavigate: (String) -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val t = strings().nav
    val colors = PokyhTheme.colors
    val context = LocalContext.current
    val session by viewModel.session.collectAsStateWithLifecycle()
    val lang by I18n.lang.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    var avatarOpen by remember { mutableStateOf(false) }
    val webSignIn = rememberWebSignIn(viewModel.appState)

    // Signing out: the first tap asks, the second does it; the question goes away by itself.
    var asking by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    LaunchedEffect(asking, open) {
        if (!open) asking = false
        if (asking) {
            delay(6000)
            asking = false
        }
    }

    val name = session?.personName ?: session?.username.orEmpty()
    val detail = session?.klasseName?.takeIf { it.isNotBlank() } ?: if (session?.isParent == true) t.guardian else t.student

    fun go(route: String) {
        open = false
        onNavigate(route)
    }

    Box {
        val interaction = remember { MutableInteractionSource() }
        Box(
            modifier = Modifier
                .size(TabRootAvatarSize)
                .clip(PokyhShapes.pill)
                .pressHighlight(interaction, PokyhShapes.pill)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClickLabel = t.accountMenu,
                    role = Role.Button,
                    onClick = { open = !open },
                ),
            contentAlignment = Alignment.Center,
        ) {
            CurrentUserAvatar()
        }

        FullScreenPage(open = open, onDismiss = { open = false }, closeLabel = strings().common.close) {
            // The account, large, as the top of an account page.
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Tapped, the picture opens large, as on the profile.
                val avatarInteraction = remember { MutableInteractionSource() }
                Box(
                    Modifier
                        .clip(PokyhShapes.pill)
                        .clickable(
                            interactionSource = avatarInteraction,
                            indication = null,
                            enabled = session != null,
                            role = Role.Image,
                            onClickLabel = name,
                        ) { avatarOpen = true },
                ) {
                    CurrentUserAvatar(size = 84.dp)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    name.ifEmpty { "…" },
                    style = PokyhType.title2.bold(),
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(detail, style = PokyhType.subheadline, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            MenuRow(PokyhIcons.profile, t.profile) { go(PokyhDestinations.PROFILE) }
            MenuRow(PokyhIcons.settings, t.settings) { go(PokyhDestinations.SETTINGS) }
            MenuRow(if (colors.isDark) PokyhIcons.themeLight else PokyhIcons.themeDark, t.actionTheme) {
                viewModel.setThemeMode(if (colors.isDark) PokyhThemeMode.Light else PokyhThemeMode.Dark)
            }
            if (session != null) {
                MenuRow(PokyhIcons.webSignIn, t.scanQr, busy = webSignIn.working) {
                    open = false
                    webSignIn.scan()
                }
                MenuRow(
                    icon = PokyhIcons.signOut,
                    text = if (asking) t.logoutConfirm else t.logout,
                    danger = asking,
                    busy = leaving,
                ) {
                    if (!asking) {
                        asking = true
                    } else if (!leaving) {
                        leaving = true
                        open = false
                        viewModel.logoutCurrentAccount(onDone = { leaving = false })
                    }
                }
            }
            MenuGap()
            LanguageChoice(lang) { choice ->
                if (choice != lang) I18n.choose(context, choice)
            }
        }
    }
    WebSignInDialogs(webSignIn)
    session?.takeIf { avatarOpen }?.let { s -> AvatarViewerDialog(session = s, onDismiss = { avatarOpen = false }) }
}

/**
 * A page over the whole screen (a dialog without the platform's margins), drawn under the status
 * and navigation bars and padded clear of them, with a close button on top. Back closes it.
 */
@Composable
private fun FullScreenPage(open: Boolean, onDismiss: () -> Unit, closeLabel: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    if (!open) return
    val colors = PokyhTheme.colors
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bg)
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    // The same grey button as "back" in the app's headers.
                    dev.plattnericus.pokyh.ui.components.PokyhIconButton(
                        icon = PokyhIcons.close,
                        contentDescription = closeLabel,
                        onClick = onDismiss,
                        tinted = true,
                    )
                }
                content()
            }
            // The app's version at the very bottom, so the page doesn't end in empty space.
            Column(
                Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("v${dev.plattnericus.pokyh.BuildConfig.VERSION_NAME}", style = PokyhType.footnote, color = colors.textTertiary)
                Text("POKYH", style = PokyhType.footnote.semibold(), color = colors.textTertiary)
            }
        }
    }
}

/** The menu's groups stand apart by space, not by a rule. */
@Composable
private fun MenuGap() = Spacer(Modifier.height(14.dp))

@Composable
private fun MenuRow(
    icon: ImageVector,
    text: String,
    danger: Boolean = false,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = PokyhTheme.colors
    val fill by animateColorAsState(if (danger) Brand.danger.copy(alpha = 0.13f) else Color.Transparent, label = "menuRowFill")
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RowShape)
            .background(fill)
            .pressHighlight(interaction, RowShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = colors.textSecondary)
        } else {
            Icon(icon, contentDescription = null, tint = if (danger) Brand.danger else colors.textSecondary, modifier = Modifier.size(21.dp))
        }
        Text(
            text,
            style = PokyhType.body.semibold(),
            color = if (danger) Brand.danger else colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The three languages side by side, each with its flag; the chosen one in periwinkle. */
@Composable
private fun LanguageChoice(current: Lang, onChoose: (Lang) -> Unit) {
    val colors = PokyhTheme.colors
    val tone = PokyhDecorative.periwinkle(colors.isDark)
    Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Lang.entries.forEach { l ->
            val selected = l == current
            val interaction = remember { MutableInteractionSource() }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (selected) tone.fill else Color.Transparent)
                    .pressHighlight(interaction, RoundedCornerShape(11.dp))
                    .clickable(interactionSource = interaction, indication = null, role = Role.RadioButton, onClickLabel = l.nativeName) { onChoose(l) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                RoundFlag(l)
                Spacer(Modifier.width(8.dp))
                Text(
                    l.code.uppercase(),
                    style = PokyhType.footnote.bold(),
                    color = if (selected) tone.ink else colors.textSecondary,
                )
            }
        }
    }
}

/** A language's flag in a circle (the web's `RoundFlag`). */
@Composable
fun RoundFlag(lang: Lang, modifier: Modifier = Modifier) {
    Canvas(modifier.size(18.dp).clip(PokyhShapes.pill)) {
        val w = size.width
        val h = size.height
        when (lang) {
            Lang.De -> {
                drawRect(Color(0xFF000000), size = androidx.compose.ui.geometry.Size(w, h / 3f))
                drawRect(Color(0xFFDD0000), topLeft = Offset(0f, h / 3f), size = androidx.compose.ui.geometry.Size(w, h / 3f))
                drawRect(Color(0xFFFFCE00), topLeft = Offset(0f, h * 2f / 3f), size = androidx.compose.ui.geometry.Size(w, h / 3f))
            }
            Lang.It -> {
                drawRect(Color(0xFF009246), size = androidx.compose.ui.geometry.Size(w / 3f, h))
                drawRect(Color(0xFFFFFFFF), topLeft = Offset(w / 3f, 0f), size = androidx.compose.ui.geometry.Size(w / 3f, h))
                drawRect(Color(0xFFCE2B37), topLeft = Offset(w * 2f / 3f, 0f), size = androidx.compose.ui.geometry.Size(w / 3f, h))
            }
            Lang.En -> {
                // The web's 24 × 16 flag, centred in the circle.
                val u = h / 16f
                val ox = (w - 24f * u) / 2f
                fun p(x: Float, y: Float) = Offset(ox + x * u, y * u)
                drawRect(Color(0xFF00247D))
                drawLine(Color.White, p(0f, 0f), p(24f, 16f), 3.2f * u)
                drawLine(Color.White, p(24f, 0f), p(0f, 16f), 3.2f * u)
                drawLine(Color(0xFFCF142B), p(0f, 0f), p(24f, 16f), 1.3f * u)
                drawLine(Color(0xFFCF142B), p(24f, 0f), p(0f, 16f), 1.3f * u)
                drawLine(Color.White, p(12f, 0f), p(12f, 16f), 5.4f * u)
                drawLine(Color.White, p(0f, 8f), p(24f, 8f), 5.4f * u)
                drawLine(Color(0xFFCF142B), p(12f, 0f), p(12f, 16f), 2.1f * u)
                drawLine(Color(0xFFCF142B), p(0f, 8f), p(24f, 8f), 2.1f * u)
            }
        }
    }
}
