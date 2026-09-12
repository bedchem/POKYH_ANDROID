package dev.plattnericus.pokyh
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.plattnericus.pokyh.state.AppState
import dev.plattnericus.pokyh.ui.PokyhApp
import dev.plattnericus.pokyh.ui.theme.PokyhAppTheme
import javax.inject.Inject

/**
 * Single-activity host (POKYHApp.swift's `WindowGroup`) — the whole UI lives in [PokyhApp].
 * [AppState] is a plain Hilt singleton (not a `@HiltViewModel`), so it's field-injected here and
 * threaded down as a parameter, rather than fetched from inside a composable with `hiltViewModel()`.
 */
@AndroidEntryPoint
// A FragmentActivity, not a plain ComponentActivity: androidx.biometric hosts its prompt in a
// fragment, so the Lock screen's `LocalContext as FragmentActivity` lookup — and with it the
// whole fingerprint unlock — silently did nothing while this was a ComponentActivity.
class MainActivity : FragmentActivity() {

    @Inject lateinit var appState: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Now actually wired up (AndroidManifest.xml launches into Theme.Pokyh.Splash, which
        // installSplashScreen() below reads): keeps the themed system splash on screen until
        // AppState.isReady, covering the pre-Compose gap the in-Compose Box below never could.
        // Theme-invariant splash background (see Theme.Pokyh.Splash's doc) means this can't
        // flash a mismatched color against the in-app dark-mode override either. Called after
        // super.onCreate() (not before, despite most installSplashScreen() samples) because
        // Hilt's field injection into `appState` happens inside that super call.
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !appState.isReady.value }

        enableEdgeToEdge()

        // App-wide (not per-Activity) background/foreground signal for auto-lock — the Android
        // analog of POKYHApp.swift's `.onChange(of: scenePhase)` on `.background`/`.active`.
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> appState.onAppBackgrounded()
                    Lifecycle.Event.ON_START -> appState.onAppForegrounded()
                    else -> Unit
                }
            },
        )

        setContent {
            val themeMode by appState.themeMode.collectAsStateWithLifecycle()
            val isReady by appState.isReady.collectAsStateWithLifecycle()

            // Store.swift `requestNotificationPermissionIfNeeded()` — one system prompt, right
            // after the first successful login (AppState.onAuthenticated flips this true once).
            val requestNotifPermission by appState.requestNotifPermission.collectAsStateWithLifecycle()
            val notifPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { /* granted or not — iOS doesn't branch on the outcome either, just asks once */ }
            LaunchedEffect(requestNotifPermission) {
                if (requestNotifPermission) {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    appState.notifPermissionRequested()
                }
            }

            PokyhAppTheme(themeMode) {
                if (isReady) {
                    PokyhApp(appState)
                } else {
                    // Belt-and-suspenders only — the real splash (kept via
                    // setKeepOnScreenCondition above) already covers this gap visually.
                    Box(androidx.compose.ui.Modifier.fillMaxSize().background(dev.plattnericus.pokyh.ui.theme.PokyhTheme.colors.bg))
                }
            }
        }
    }
}
