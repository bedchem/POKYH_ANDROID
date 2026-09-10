package dev.plattnericus.pokyh.ui.theme

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

/**
 * iOS gates its responsive layout on `horizontalSizeClass == .regular` (iPad / large windows),
 * not a device check. The closest Android analog without threading an Activity/WindowSizeClass
 * down through every leaf composable is Material's own 600dp width breakpoint (the same
 * threshold `WindowWidthSizeClass.Medium` uses) — compact phones are always false here, exactly
 * like iPhone in the iOS app; large tablets/foldables/desktop windows are true, like iPad.
 */
private const val RegularWidthBreakpointDp = 600

@Composable
private fun isRegularWidthClass(): Boolean = LocalConfiguration.current.screenWidthDp >= RegularWidthBreakpointDp

/**
 * `ReadableContent` (Theme.swift) — caps content to 760dp and centers it, but ONLY in the
 * regular size class; a no-op on phones (max width there always exceeds phone screen width).
 * Applied once at the screen-background level, not per-card.
 */
@Composable
fun Modifier.readableContent(): Modifier = if (isRegularWidthClass()) {
    this.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = 760.dp)
} else this

/** `centeredForm(_:)` — same idea at 480dp, used for the login/lock forms specifically. Unlike
 * [readableContent] this applies on phones too (SwiftUI's frame(maxWidth:) is always a cap,
 * never a forced minimum — on a narrower phone it's simply a no-op since the screen is already
 * under the cap). */
@Composable
fun Modifier.centeredForm(maxWidth: androidx.compose.ui.unit.Dp = 480.dp): Modifier =
    this.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = maxWidth)

/** `appBackground()` — background fill + [readableContent], applied once per tab root. */
@Composable
fun Modifier.appBackground(): Modifier = composed {
    this.readableContent()
}
