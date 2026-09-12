package dev.plattnericus.pokyh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.rememberNavController
import dev.plattnericus.pokyh.state.AppState
import dev.plattnericus.pokyh.ui.navigation.AppTab
import dev.plattnericus.pokyh.ui.navigation.PokyhBottomNav
import dev.plattnericus.pokyh.ui.navigation.PokyhNavHost
import dev.plattnericus.pokyh.ui.navigation.PokyhNavItem
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhElevation
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.appBackground
import dev.plattnericus.pokyh.ui.theme.floatingSurface
import dev.plattnericus.pokyh.ui.theme.popIn
import kotlinx.coroutines.delay

/**
 * Root composable. Switches between the Login/Lock screens and the authed 5-tab shell by
 * [AppState.phase], and layers the switching/offline overlays above everything.
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

    // Brief, skippable success confirmation on Login/Lock -> Authed, so signing in resolves
    // instead of cutting straight to the tab shell. Purely additive: reacts to the existing
    // phase StateFlow. Naturally skipped on the add-account path (phase stays Authed
    // throughout that flow, so no Authed->Authed "transition" fires here).
    var previousPhase by remember { mutableStateOf(phase) }
    var showSuccessFlash by remember { mutableStateOf(false) }
    LaunchedEffect(phase) {
        if (phase == AppState.Phase.Authed && previousPhase != AppState.Phase.Authed) {
            showSuccessFlash = true
            delay(500)
            showSuccessFlash = false
        }
        previousPhase = phase
    }

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
            OfflineBanner(
                Modifier
                    .align(Alignment.TopCenter)
                    .systemBarsPadding()
                    .padding(top = PokyhSpacing.sm),
            )
        }

        if (showSuccessFlash) {
            SuccessFlash()
        }

        // ProfileScreen's "Konto hinzufügen" row (ProfileViewModel.addAccount -> AppState.
        // addAccount) only flips `AppState.showAddAccount` — LockScreen hosts its own copy of
        // this sheet for the Lock phase, so this one only needs to cover Authed.
        // `showAddAccount` has no public "close" setter (unlike SwiftUI's two-way `Binding`),
        // so the dialog's own open flag is local, opened on the true edge and closed by
        // LoginScreen's `onCancel`.
        if (phase == AppState.Phase.Authed) {
            val showAddAccount by appState.showAddAccount.collectAsStateWithLifecycle()
            var addAccountSheetOpen by remember { mutableStateOf(false) }
            LaunchedEffect(showAddAccount) { if (showAddAccount) addAccountSheetOpen = true }
            if (addAccountSheetOpen) {
                dev.plattnericus.pokyh.ui.login.AddAccountDialog(onDismiss = { addAccountSheetOpen = false })
            }
        }
    }
}

/**
 * The authed tab shell. One shared [PokyhNavHost] whose start destination follows the selected
 * tab — the standard Compose "single NavHost behind a bottom bar" pattern.
 *
 * KNOWN SIMPLIFICATION: iOS gives each tab its own `NavigationStack` (an independent back stack
 * that survives switching tabs). This could be split into 5 stacks later if losing per-tab
 * navigation history on tab switch turns out to matter.
 */
@Composable
private fun AuthedShell(appState: AppState) {
    val session by appState.session.collectAsStateWithLifecycle()
    val selectedTab by appState.selectedTab.collectAsStateWithLifecycle()
    val hasUntis = session?.hasUntis ?: true

    // A hidden tab can still be the persisted selection (e.g. right after switching to a
    // POKYH-only account) — fall back to Home instead of an empty bar.
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

    Scaffold(
        modifier = Modifier.appBackground(),
        containerColor = PokyhTheme.colors.bg,
        bottomBar = {
            PokyhBottomNav(
                items = buildList {
                    add(PokyhNavItem(AppTab.Home, PokyhIcons.tabHome, "Home"))
                    if (hasUntis) add(PokyhNavItem(AppTab.Timetable, PokyhIcons.tabTimetable, "Stundenplan"))
                    add(PokyhNavItem(AppTab.School, PokyhIcons.tabSchool, "Schule"))
                    if (hasUntis) add(PokyhNavItem(AppTab.Grades, PokyhIcons.tabGrades, "Noten"))
                    add(PokyhNavItem(AppTab.Mensa, PokyhIcons.tabMensa, "Mensa"))
                },
                selected = effectiveTab,
                onSelect = { appState.selectTab(it) },
            )
        },
    ) { innerPadding ->
        PokyhNavHost(
            navController = navController,
            startDestination = effectiveTab.route,
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
        )
    }
}

/** Brief checkmark badge on a genuine Login/Lock -> Authed transition — see the
 * `showSuccessFlash` LaunchedEffect in [PokyhApp] for exactly when this fires. */
@Composable
private fun SuccessFlash() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .floatingSurface(shape = PokyhShapes.pill, color = Brand.accent, elevation = PokyhElevation.level4)
                .popIn(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(PokyhIcons.check, contentDescription = null, tint = Brand.onAccent, modifier = Modifier.size(38.dp))
        }
    }
}

/** Full-screen dim plus a centered spinner card, e.g. while switching accounts. The dimmed
 * backdrop already separates the card, so it sits at card elevation rather than adding a
 * heavier shadow on top of a scrim. */
@Composable
private fun SwitchingOverlay(text: String) {
    Box(
        Modifier.fillMaxSize().background(PokyhTheme.colors.bg.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .floatingSurface(shape = PokyhShapes.xl, elevation = PokyhElevation.level4)
                .padding(PokyhSpacing.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(PokyhSpacing.lg),
        ) {
            CircularProgressIndicator(color = Brand.accent, strokeWidth = 3.dp, modifier = Modifier.size(30.dp))
            Text(text, style = PokyhType.footnote, color = PokyhTheme.colors.textSecondary)
        }
    }
}

/** A slim top capsule. Floating chrome like the bottom nav, so it keeps a visible shadow rather
 * than the near-flat card default. */
@Composable
private fun OfflineBanner(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .floatingSurface(shape = PokyhShapes.pill, color = Brand.warning)
            .padding(horizontal = PokyhSpacing.lg, vertical = PokyhSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(PokyhIcons.offline, contentDescription = null, tint = Brand.onAccent, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(PokyhSpacing.sm))
        Text(
            text = "Offline – gespeicherte Daten",
            style = PokyhType.caption,
            color = Brand.onAccent,
        )
    }
}
