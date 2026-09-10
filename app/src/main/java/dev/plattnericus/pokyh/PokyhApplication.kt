package dev.plattnericus.pokyh

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Hilt entry point (POKYHApp.swift). No app-wide setup lives here on purpose — the URLCache
 * sizing and background-refresh registration iOS does in `init()` don't have an Android
 * equivalent in this pass (Coil/OkHttp caching and WorkManager scheduling are data-layer
 * concerns); see [dev.plattnericus.pokyh.MainActivity] for the UI-process wiring (splash,
 * edge-to-edge, app-lifecycle → [dev.plattnericus.pokyh.state.AppState] auto-lock).
 */
@HiltAndroidApp
class PokyhApplication : Application()
