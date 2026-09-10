package dev.plattnericus.pokyh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.plattnericus.pokyh.data.model.UserSession
import dev.plattnericus.pokyh.state.AppState
import dev.plattnericus.pokyh.ui.components.PokyhAvatar
import dev.plattnericus.pokyh.ui.navigation.AppTab
import dev.plattnericus.pokyh.ui.navigation.PokyhDestinations
import dev.plattnericus.pokyh.ui.navigation.PokyhNavHost
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.cardSurface

/**
 * Root composable (ContentView.swift + RootTabView.swift). Switches between the Login/Lock
 * placeholders and the authed 5-tab shell by [AppState.phase], and layers the switching/offline
 * overlays on top of everything — the Compose analog of ContentView's two `.overlay` modifiers.
 *
 * [appState] is field-injected by Hilt in `MainActivity` (it's a plain singleton, not a
 * `@HiltViewModel`, so it can't be fetched with `hiltViewModel()` from inside a composable) and
 * threaded down as a parameter from there.
 */
@Composable
fun PokyhApp(appState: AppState) {
    val phase by appState.phase.collectAsStateWithLifecycle()
    val busy by appState.busy.collectAsStateWithLifecycle()
    val isOffline by appState.isOffline.collectAsStateWithLifecycle()
    val statusText by appState.statusText.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize().background(PokyhTheme.colors.bg)) {
        when (phase) {
            AppState.Phase.Login -> dev.plattnericus.pokyh.ui.login.LoginScreen()
            AppState.Phase.Lock -> dev.plattnericus.pokyh.ui.lock.LockScreen()
            AppState.Phase.Authed -> AuthedShell(appState)
        }

        if (busy && phase == AppState.Phase.Authed) {
            SwitchingOverlay(statusText.ifEmpty { "Lädt…" })
        }

        if (isOffline && phase == AppState.Phase.Authed) {
            OfflineBanner(Modifier.align(Alignment.TopCenter).systemBarsPadding().padding(top = 8.dp))
        }

        // ProfileScreen's "Konto hinzufügen" row (ProfileViewModel.addAccount -> AppState.
        // addAccount) only flips `AppState.showAddAccount` — LockScreen hosts its own copy of
        // this sheet for the Lock phase, so this one only needs to cover Authed. `showAddAccount`
        // has no public "close" setter (unlike SwiftUI's two-way `Binding`), so the dialog's own
        // open flag is local, opened on the true edge and closed by LoginScreen's `onCancel`.
        if (phase == AppState.Phase.Authed) {
            val showAddAccount by appState.showAddAccount.collectAsStateWithLifecycle()
            var addAccountSheetOpen by remember { mutableStateOf(false) }
            LaunchedEffect(showAddAccount) { if (showAddAccount) addAccountSheetOpen = true }
            if (addAccountSheetOpen) {
                Dialog(
                    onDismissRequest = { addAccountSheetOpen = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                ) {
                    dev.plattnericus.pokyh.ui.login.LoginScreen(
                        isAdditional = true,
                        onCancel = { addAccountSheetOpen = false },
                    )
                }
            }
        }
    }
}

/**
 * RootTabView.swift, ported. iOS gives each tab its own `NavigationStack` (an independent back
 * stack that survives switching tabs). This pass uses ONE shared [PokyhNavHost] whose start
 * destination follows the selected tab — the standard Compose "single NavHost behind a bottom
 * bar" pattern, simplest-correct for this piece. KNOWN SIMPLIFICATION: a screens-phase agent may
 * split this into 5 independent back stacks later if losing per-tab navigation history on tab
 * switch turns out to matter.
 */
@Composable
private fun AuthedShell(appState: AppState) {
    val session by appState.session.collectAsStateWithLifecycle()
    val selectedTab by appState.selectedTab.collectAsStateWithLifecycle()
    val hasUntis = session?.hasUntis ?: true

    // A hidden tab can still be the persisted selection (e.g. right after switching to a
    // POKYH-only account) — fall back to Home instead of an empty bar (RootTabView's
    // `tabSelection` binding getter).
    val effectiveTab = if (!hasUntis && (selectedTab == AppTab.Timetable || selectedTab == AppTab.Grades)) {
        AppTab.Home
    } else {
        selectedTab
    }

    val navController = rememberNavController()
    LaunchedEffect(effectiveTab) {
        navController.navigate(effectiveTab.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // ProfileToolbar.swift — shown on every main/section screen, hidden on detail pushes
    // (grade subject, dish, message/reminder detail) and on Profile/Messages themselves.
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val showHeaderBar = currentRoute in HEADER_BAR_ROUTES

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        bottomBar = {
            NavigationBar(containerColor = PokyhTheme.colors.surface) {
                NavigationBarItem(
                    selected = effectiveTab == AppTab.Home,
                    onClick = { appState.selectTab(AppTab.Home) },
                    icon = { Icon(PokyhIcons.house_fill, contentDescription = "Home") },
                    label = { Text("Home") },
                    colors = tabColors(),
                )
                if (hasUntis) {
                    NavigationBarItem(
                        selected = effectiveTab == AppTab.Timetable,
                        onClick = { appState.selectTab(AppTab.Timetable) },
                        icon = { Icon(PokyhIcons.calendar, contentDescription = "Stundenplan") },
                        label = { Text("Stundenplan") },
                        colors = tabColors(),
                    )
                }
                NavigationBarItem(
                    selected = effectiveTab == AppTab.School,
                    onClick = { appState.selectTab(AppTab.School) },
                    icon = { Icon(PokyhIcons.graduationcap_fill, contentDescription = "Schule") },
                    label = { Text("Schule") },
                    colors = tabColors(),
                )
                if (hasUntis) {
                    NavigationBarItem(
                        selected = effectiveTab == AppTab.Grades,
                        onClick = { appState.selectTab(AppTab.Grades) },
                        icon = { Icon(PokyhIcons.chart_bar_fill, contentDescription = "Noten") },
                        label = { Text("Noten") },
                        colors = tabColors(),
                    )
                }
                NavigationBarItem(
                    selected = effectiveTab == AppTab.Mensa,
                    onClick = { appState.selectTab(AppTab.Mensa) },
                    icon = { Icon(PokyhIcons.fork_knife, contentDescription = "Mensa") },
                    label = { Text("Mensa") },
                    colors = tabColors(),
                )
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (showHeaderBar) {
                HeaderBar(
                    session = session,
                    onMessages = { navController.navigate(PokyhDestinations.MESSAGES) },
                    onProfile = { navController.navigate(PokyhDestinations.PROFILE) },
                )
            }
            PokyhNavHost(
                navController = navController,
                startDestination = effectiveTab.route,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Routes that carry [HeaderBar] — the five tab roots plus the section screens pushed from them,
 * mirroring exactly which iOS screens call `.profileToolbar()`. Deliberately excludes detail
 * pushes (grade subject, dish, message/reminder detail) and Profile/Messages themselves. */
private val HEADER_BAR_ROUTES = setOf(
    PokyhDestinations.HOME,
    PokyhDestinations.TIMETABLE,
    PokyhDestinations.SCHOOL_HUB,
    PokyhDestinations.GRADES,
    PokyhDestinations.MENSA,
    PokyhDestinations.ABSENCES,
    PokyhDestinations.CLASSROOM,
    PokyhDestinations.CLASSREG_EVENTS,
    PokyhDestinations.REMINDERS,
    PokyhDestinations.TODOS,
)

/** ProfileToolbar.swift — top-right header actions: Nachrichten (envelope) + Profil (avatar).
 * A plain Row rather than a Material [androidx.compose.material3.TopAppBar] since the app's flat,
 * bar-less design language (see [HomeScreen]'s own inline header) has no top-bar chrome elsewhere. */
@Composable
private fun HeaderBar(
    session: UserSession?,
    onMessages: () -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMessages) {
            Icon(PokyhIcons.envelope, contentDescription = "Nachrichten", tint = PokyhTheme.colors.textPrimary)
        }
        IconButton(onClick = onProfile) {
            if (session != null) {
                PokyhAvatar(url = session.imageUrl, name = session.personName ?: session.username, size = 28.dp)
            } else {
                Icon(PokyhIcons.person_crop_circle, contentDescription = "Profil", tint = PokyhTheme.colors.textPrimary)
            }
        }
    }
}

@Composable
private fun tabColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Brand.accent,
    selectedTextColor = Brand.accent,
    indicatorColor = Brand.accent.copy(alpha = 0.15f),
    unselectedIconColor = PokyhTheme.colors.textSecondary,
    unselectedTextColor = PokyhTheme.colors.textSecondary,
)

/** ContentView.swift `SwitchingOverlay` — full-screen dim + centered spinner card, e.g. while
 * switching accounts. Uses [cardSurface] (flat fill + hairline border) in place of iOS's
 * `.glassEffect`, per the design system's "no glass, no shadows except the offline banner" rule. */
@Composable
private fun SwitchingOverlay(text: String) {
    Box(
        Modifier.fillMaxSize().background(PokyhTheme.colors.bg.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.cardSurface(radius = 20.dp).padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator(color = Brand.accent)
            Text(
                text = text,
                style = PokyhType.subheadline.copy(fontWeight = FontWeight.Medium),
                color = PokyhTheme.colors.textSecondary,
            )
        }
    }
}

/** ContentView.swift `OfflineBanner` — a slim top capsule. The ONE shadow in the whole app. */
@Composable
private fun OfflineBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .shadow(elevation = 8.dp, shape = CircleShape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.18f))
            .background(Brand.warning, CircleShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PokyhIcons.wifi_slash, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Offline – zeige gespeicherte Daten",
            style = PokyhType.footnote.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
        )
    }
}
