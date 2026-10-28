@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.school

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.plattnericus.pokyh.ui.components.PokyhFeatureTile
import dev.plattnericus.pokyh.ui.components.PokyhTopBar
import dev.plattnericus.pokyh.ui.components.TabRootActions
import dev.plattnericus.pokyh.ui.components.TopBarNav
import dev.plattnericus.pokyh.ui.navigation.PokyhDestinations
import dev.plattnericus.pokyh.ui.profile.CurrentUserAvatar
import dev.plattnericus.pokyh.ui.profile.rememberUnreadMessageCount
import dev.plattnericus.pokyh.ui.theme.PokyhDecorative
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.fadeIn
import dev.plattnericus.pokyh.core.i18n.I18n
import dev.plattnericus.pokyh.core.i18n.strings

/**
 * One tile of the hub — either navigates to [route], or (when [route] is null, i.e. "Noten")
 * switches the selected bottom tab instead.
 */
private data class HubItem(
    val title: String,
    val subtitle: String,
    val glyph: ImageVector,
    val route: String?,
)

/**
 * The school hub: a 2-column grid of color-blocked [PokyhFeatureTile]s, one per area.
 *
 * Tones come from [PokyhDecorative.cycle] by position rather than being assigned per item, so
 * the grid repeats a four-tone pattern every two rows instead of introducing a fifth and sixth
 * pastel. Nachrichten/Profil live in the header actions, like every other tab root.
 */
@Composable
fun SchoolHubScreen(
    onNavigate: (String) -> Unit,
    viewModel: SchoolHubViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark = PokyhTheme.colors.isDark

    // Ordered by how often it's actually opened, school-wide before personal: grades first,
    // then the two things you look up about yourself, then the class, then what the class owes,
    // and your own private list last.
    val lang = strings().lang
    val unread = rememberUnreadMessageCount()
    // What has no tab of its own, in the web's order (components/app/nav.ts HUB_ITEMS); the
    // WebUntis pages only for an account with WebUntis.
    val items = remember(state.hasUntis, lang, unread) {
        buildList {
            val t = I18n.s.school
            if (state.hasUntis) {
                add(HubItem(I18n.s.nav.messages, if (unread > 0) t.unread(unread) else t.messagesInfo, PokyhIcons.decorMessages, route = PokyhDestinations.MESSAGES))
                add(HubItem(t.absences, t.absencesInfo, PokyhIcons.decorAbsences, route = PokyhDestinations.ABSENCES))
            }
            add(HubItem(t.todos, t.todosInfo, PokyhIcons.decorTodos, route = PokyhDestinations.TODOS))
            add(HubItem(t.reminders, t.remindersInfo, PokyhIcons.decorReminders, route = PokyhDestinations.REMINDERS))
            if (state.hasUntis) {
                add(HubItem(t.classreg, t.classregInfo, PokyhIcons.decorClassRegister, route = PokyhDestinations.CLASSREG_EVENTS))
                add(HubItem(t.klasse, t.klasseInfo, PokyhIcons.decorClassMembers, route = PokyhDestinations.CLASSROOM))
            }
        }
    }

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        topBar = {
            PokyhTopBar(
                title = strings().nav.school,
                eyebrow = state.subtitle.ifEmpty { null },
                nav = TopBarNav.None,
                actions = {
                    TabRootActions(
                        avatarContent = { CurrentUserAvatar() },
                        unreadMessages = unread,
                        onMessages = { onNavigate(PokyhDestinations.MESSAGES) },
                        onProfile = { onNavigate(PokyhDestinations.PROFILE) },
                        onNavigate = onNavigate,
                    )
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PokyhSpacing.screenH)
                .padding(bottom = PokyhSpacing.xxxl),
            verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
        ) {
            items.chunked(2).forEachIndexed { rowIdx, row ->
                Row(
                    modifier = Modifier.fillMaxWidth().fadeIn(rowIdx * 50),
                    horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
                ) {
                    row.forEachIndexed { colIdx, item ->
                        PokyhFeatureTile(
                            title = item.title,
                            subtitle = item.subtitle,
                            tone = PokyhDecorative.cycle(rowIdx * 2 + colIdx, isDark),
                            glyph = item.glyph,
                            onClick = {
                                if (item.route != null) onNavigate(item.route) else viewModel.selectGradesTab()
                            },
                            height = 168.dp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}
