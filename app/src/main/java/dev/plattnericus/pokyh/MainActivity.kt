package dev.plattnericus.pokyh
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
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
class MainActivity : ComponentActivity() {

    @Inject lateinit var appState: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 12+ (API 31+) already shows the themed splash (Theme.Pokyh.Splash /
        // windowSplashScreenBackground) purely from the manifest/theme, no code needed. Below
        // that, the activity's own windowBackground (same color) covers the gap. Either way, we
        // additionally hold a plain colored screen in Compose itself — not the core-splashscreen
        // library — until the persisted theme preference has loaded, so the very first *composed*
        // frame never flashes System-default before switching to the real theme.
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
                    // Compose-level splash hold — see the onCreate() comment above.
                    Box(androidx.compose.ui.Modifier.fillMaxSize().background(dev.plattnericus.pokyh.ui.theme.PokyhTheme.colors.bg))
                }
            }
        }
    }
}
