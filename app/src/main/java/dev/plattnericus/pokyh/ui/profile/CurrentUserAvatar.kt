package dev.plattnericus.pokyh.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.ui.components.TabRootAvatarSize
import dev.plattnericus.pokyh.ui.components.UntisAvatar
import dev.plattnericus.pokyh.ui.components.UntisImageAuth
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhTheme

/**
 * The signed-in user, as an avatar: their real WebUntis profile picture when they have one, their
 * initial otherwise, and a person glyph only when there's no session at all.
 *
 * Sized to fill its slot ([TabRootAvatarSize] by default) rather than sitting inset on a tinted
 * disc — see [dev.plattnericus.pokyh.ui.components.TabRootActions]. Only the no-session fallback
 * draws a backdrop, because a lone glyph does need one to read as a control.
 *
 * It reads the session itself so that every place showing "the current user" shows the same
 * thing. That's the fix for a visible inconsistency: each tab root used to supply its own
 * `avatarContent`, and only Home had the session in hand — so Home showed an avatar while
 * Stundenplan/Schule/Noten/Mensa showed a generic glyph.
 *
 * Reuses [ProfileViewModel] purely to read the session (same reason [ProfileToolbarAction] does);
 * every instance shares the one [dev.plattnericus.pokyh.state.AppState] singleton underneath, so
 * this is a state read rather than per-screen work.
 */
@Composable
fun CurrentUserAvatar(
    modifier: Modifier = Modifier,
    size: Dp = TabRootAvatarSize,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val s = session

    // Login only captures `imageUrl` when it happened to need app-data for something else, so
    // ask for it here the first time an avatar is actually shown. AppState de-duplicates, so the
    // five tab roots that all render this don't each trigger a fetch.
    LaunchedEffect(s?.username) { viewModel.appState.ensureProfileImageUrl() }

    if (s == null) {
        Box(
            modifier = modifier
                .size(size)
                .background(PokyhTheme.colors.cardAlt, PokyhShapes.pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = PokyhIcons.profile,
                contentDescription = "Profil",
                tint = PokyhTheme.colors.textPrimary,
                modifier = Modifier.size(size * 0.5f),
            )
        }
        return
    }

    UntisAvatar(
        rawImageUrl = s.imageUrl,
        name = s.personName ?: s.username,
        auth = UntisImageAuth.of(s),
        modifier = modifier,
        size = size,
    )
}

/**
 * The unread inbox count, for a tab-root header's Messages badge.
 *
 * A composable read rather than a parameter threaded down from the shell: the five tab roots all
 * want the same number and none of them otherwise touch [dev.plattnericus.pokyh.state.AppState],
 * so reading it here keeps their signatures unchanged — the same reasoning as [CurrentUserAvatar].
 */
@Composable
fun rememberUnreadMessageCount(viewModel: ProfileViewModel = hiltViewModel()): Int {
    val count by viewModel.appState.unreadMessages.collectAsStateWithLifecycle()
    return count
}
