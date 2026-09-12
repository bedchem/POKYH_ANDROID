package dev.plattnericus.pokyh.ui.profile

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.ui.components.UntisAvatar
import dev.plattnericus.pokyh.ui.components.UntisImageAuth
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhTheme

/**
 * The signed-in user, as an avatar: their real WebUntis profile picture when they have one, their
 * initial otherwise, and a person glyph only when there's no session at all.
 *
 * It reads the session itself so that every place showing "the current user" shows the same
 * thing. That's the fix for a visible inconsistency: each tab root used to supply its own
 * `avatarContent`, and only Home had the session in hand — so Home showed an avatar while
 * Stundenplan/Schule/Noten/Mensa showed a generic glyph.
 *
 * Reuses [ProfileViewModel] purely to read the session (same reason
 * [ProfileToolbarAction] does); every instance shares the one
 * [dev.plattnericus.pokyh.state.AppState] singleton underneath, so this is a state read rather
 * than per-screen work.
 */
@Composable
fun CurrentUserAvatar(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val s = session

    // Login only captures `imageUrl` when it happened to need app-data for something else, so
    // ask for it here the first time an avatar is actually shown. AppState de-duplicates, so the
    // five tab roots that all render this don't each trigger a fetch.
    LaunchedEffect(s?.username) { viewModel.appState.ensureProfileImageUrl() }

    if (s == null) {
        Icon(
            imageVector = PokyhIcons.profile,
            contentDescription = "Profil",
            tint = PokyhTheme.colors.textPrimary,
            modifier = modifier.size(size),
        )
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
