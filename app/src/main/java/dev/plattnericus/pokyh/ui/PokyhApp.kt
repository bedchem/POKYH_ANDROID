package dev.plattnericus.pokyh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.plattnericus.pokyh.core.update.AppUpdater
import dev.plattnericus.pokyh.state.AppState
import dev.plattnericus.pokyh.ui.update.UpdateDialogHost
import dev.plattnericus.pokyh.ui.update.UpdateViewModel
import dev.plattnericus.pokyh.ui.navigation.AppTab
import dev.plattnericus.pokyh.ui.components.AnimatedCheck
import dev.plattnericus.pokyh.ui.components.BrandSpinner
import dev.plattnericus.pokyh.ui.components.OfflineNotice
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
    val serviceStates by appState.serviceStates.collectAsStateWithLifecycle()
    val deviceOnline by appState.deviceOnline.collectAsStateWithLifecycle()

    // Brief, skippable success confirmation on Login/Lock -> Authed, so signing in resolves
    // instead of cutting straight to the tab shell. Purely additive: reacts to the existing
    // phase StateFlow. Naturally skipped on the add-account path (phase stays Authed
    // throughout that flow, so no Authed->Authed "transition" fires here).
    var previousPhase by remember { mutableStateOf(phase) }
    var showSuccessFlash by remember { mutableStateOf(false) }
    LaunchedEffect(phase) {
        if (phase == AppState.Phase.Authed && previousPhase != AppState.Phase.Authed) {
            showSuccessFlash = true
            // Long enough for the tick to finish being drawn and register as finished.
            // At 500ms the stroke was still travelling when the overlay disappeared.
            delay(950)
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

        // Above every screen and its top bar: an outage is a property of the app, not of
        // whichever tab is open, and it has to be seen without hunting for it.
        //
        // **Signed in only.** The Login and Lock screens have their own error line for a
        // failed attempt, and a banner there would be answering a question nobody has asked
        // yet — it belongs where it explains something already on screen: data that may be
        // out of date.
        if (phase == AppState.Phase.Authed) {
            OfflineNotice(
                states = serviceStates,
                isOffline = isOffline,
                deviceOnline = deviceOnline,
                // Only the success check holds it back, and only for the ~1s it is on
                // screen. It used to wait on `busy` as well, which couples the one thing
                // the user has to be told to a flag set in half a dozen places — if any of
                // them leaves it stuck, the banner never appears at all.
                ready = !showSuccessFlash,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .systemBarsPadding()
                    .padding(top = PokyhSpacing.sm),
            )
        }

        if (showSuccessFlash) {
            SuccessFlash()
        }

        // Update prompt from GitHub releases — in every phase, since an outdated app that can't
        // sign in is exactly when it matters.
        val updateViewModel: UpdateViewModel = hiltViewModel()
        val updateState by updateViewModel.updater.state.collectAsStateWithLifecycle()
        UpdateDialogHost(updateViewModel)

        // Admin announcement popups (backend /popups/active). Held back while the sign-in
        // check or the update prompt is on screen so they never stack.
        if (phase == AppState.Phase.Authed && !showSuccessFlash && updateState == AppUpdater.State.Idle) {
            dev.plattnericus.pokyh.ui.popups.AnnouncementPopupHost()
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

    /**
     * The NavHost's start destination, fixed for the lifetime of the shell.
     *
     * It used to be `effectiveTab.route`, which looks harmless and is not: `NavHost` rebuilds
     * its graph whenever `startDestination` changes and assigns it to the controller, and
     * assigning a graph **resets the entire back stack** — every saved tab state and every
     * NavBackStackEntry-scoped ViewModel with it. So each tab switch raced its own
     * `navigate()` below: the freshly created ViewModel could be torn down mid-load (Mensa
     * arriving back from Home with nothing on screen and no request in flight was this), and a
     * pop had no stack left to animate back through.
     *
     * Switching tabs is the `navigate()` call's job alone. The initial value is the persisted
     * tab, so the app still opens where it was left, with no visible transition on launch, and
     * it is saved so a configuration change doesn't reset the graph either.
     */
    val startRoute = rememberSaveable { effectiveTab.route }

    /** The tab whose stack is currently on screen — the one being left when [effectiveTab] changes. */
    var shownTab by rememberSaveable { mutableStateOf(effectiveTab) }

    LaunchedEffect(effectiveTab) {
        val leaving = shownTab
        shownTab = effectiveTab
        if (navController.currentDestination?.route == effectiveTab.route) return@LaunchedEffect
        // Leave the old tab at its root before its state is saved. Otherwise whatever was pushed
        // on top of it (Profil, Nachrichten, a subject) is saved with it and `restoreState`
        // brings it straight back the next time that tab is tapped. The root's own state — its
        // scroll position, its ViewModel — is still saved and restored.
        if (leaving != effectiveTab) navController.popBackStack(leaving.route, inclusive = false)
        navController.navigate(effectiveTab.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // The other direction: the system back button pops the stack without going through the
    // bar, so it could land on another tab's root while the bar still highlighted the old one.
    // Whenever the stack settles on a tab root, that tab becomes the selection. `shownTab` is
    // set first so the effect above sees nothing left to navigate.
    val currentEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(currentEntry) {
        // Read live rather than from `currentEntry`: a tab switch pops to the old root and then
        // navigates in one go, and acting on that in-between root would flip the bar back.
        val route = navController.currentBackStackEntry?.destination?.route ?: return@LaunchedEffect
        val tab = AppTab.entries.firstOrNull { it.route == route } ?: return@LaunchedEffect
        if (tab != effectiveTab) {
            shownTab = tab
            appState.selectTab(tab)
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
            startDestination = startRoute,
            // Consumed, not just applied: the tab screens are Scaffolds of their own, and an
            // inner Scaffold still sees the raw navigation-bar inset and pads for it a second
            // time — a bare strip of canvas between the content and the nav bar.
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding).fillMaxSize(),
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
            AnimatedCheck(color = Brand.onAccent, strokeWidth = 4.dp, modifier = Modifier.size(42.dp))
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
            BrandSpinner(color = Brand.accent, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
            Text(text, style = PokyhType.footnote, color = PokyhTheme.colors.textSecondary)
        }
    }
}

