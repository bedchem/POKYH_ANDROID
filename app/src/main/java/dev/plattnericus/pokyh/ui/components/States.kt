package dev.plattnericus.pokyh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.core.i18n.strings
import dev.plattnericus.pokyh.data.model.AppError
import dev.plattnericus.pokyh.data.model.BackendStatus
import dev.plattnericus.pokyh.ui.pixel.PixelArt
import dev.plattnericus.pokyh.ui.pixel.PixelSprite
import dev.plattnericus.pokyh.ui.pixel.stateScene
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.semibold
import dev.plattnericus.pokyh.ui.theme.fadeIn

/** A pixel scene on its own, [width] wide (see [PixelSprite]). */
@Composable
fun PixelSpriteArt(sprite: PixelSprite, modifier: Modifier = Modifier, width: Dp = 160.dp) {
    PixelArt(scene = stateScene(sprite), modifier = modifier.width(width))
}

/**
 * A state where something went wrong or there is nothing to show — the web's `PixelState`: a
 * small pixel scene drawn like the Mensa plate (square pixels, outlines, light from the top left,
 * dithered shadows, animated in steps), the plain sentence below it, a second sentence for
 * people, the technical reason in small type, and at most one action.
 */
@Composable
fun PixelState(
    title: String,
    modifier: Modifier = Modifier,
    sprite: PixelSprite = PixelSprite.Unplugged,
    note: String? = null,
    detail: String? = null,
    compact: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = PokyhTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PokyhSpacing.lg, vertical = if (compact) PokyhSpacing.xl else 40.dp)
            .fadeIn(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PixelSpriteArt(sprite, width = if (compact) 120.dp else 160.dp)
        Spacer(Modifier.size(if (compact) PokyhSpacing.lg else PokyhSpacing.xl))
        Text(
            text = title,
            style = (if (compact) PokyhType.callout else PokyhType.headline).semibold(),
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 340.dp),
        )
        if (note != null) {
            Spacer(Modifier.size(6.dp))
            Text(
                text = note,
                style = PokyhType.callout,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 360.dp),
            )
        }
        if (detail != null) {
            Spacer(Modifier.size(6.dp))
            Text(
                text = detail,
                style = PokyhType.caption.copy(fontFamily = FontFamily.Monospace),
                color = colors.textTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 380.dp),
            )
        }
        if (action != null) {
            Spacer(Modifier.size(PokyhSpacing.xl))
            action()
        }
    }
}

/** A whole screen's state, centred in the space it has (scrolls when the text is long). */
@Composable
private fun StateLayout(
    sprite: PixelSprite,
    title: String,
    note: String?,
    modifier: Modifier = Modifier,
    detail: String? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Box(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        PixelState(
            title = title,
            sprite = sprite,
            note = note,
            detail = detail,
            action = action,
            modifier = Modifier.padding(horizontal = PokyhSpacing.lg),
        )
    }
}

@Composable
private fun RetryButton(onRetry: () -> Unit) {
    PokyhSecondaryButton(text = strings().common.retry, onClick = onRetry, icon = PokyhIcons.refresh)
}

/** Nothing to show, and that's fine. */
@Composable
fun EmptyStateView(
    title: String,
    modifier: Modifier = Modifier,
    sprite: PixelSprite = PixelSprite.Empty,
    subtitle: String? = null,
    action: @Composable (() -> Unit)? = null,
) {
    StateLayout(sprite = sprite, title = title, note = subtitle, modifier = modifier, action = action)
}

private enum class ErrorKind { Session, Offline, Generic }

/** What kind of failure a message describes, the way the web's ErrorView reads it. */
private fun classify(message: String): ErrorKind {
    val m = message.lowercase()
    val session = listOf("session", "sitzung", "sessione", "login", "auth", "unauthorized", "angemeldet", "anmelden", "accedi", "sign in")
    val offline = listOf("network", "fetch", "offline", "verbindung", "connessione", "connection", "timeout", "econnrefused", "internet")
    return when {
        session.any { it in m } -> ErrorKind.Session
        offline.any { it in m } -> ErrorKind.Offline
        else -> ErrorKind.Generic
    }
}

/** The raw reason, without addresses and kept short. */
private fun technicalReason(message: String): String =
    message.replace(Regex("https?://\\S+"), "").replace(Regex("\\s+"), " ").trim().take(140)

/** Something went wrong and retrying is the answer. */
@Composable
fun ErrorStateView(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    // Offline with nothing stored is not a failure — say that it is the connection, not the app.
    if (AppError.isOfflineNotSaved(message)) {
        OfflineStateView(message = message, modifier = modifier, onRetry = onRetry)
        return
    }
    val c = strings().common
    val (sprite, title, detail) = when (classify(message)) {
        ErrorKind.Session -> Triple(PixelSprite.Lock, c.errSessionTitle, c.errSessionDetail)
        ErrorKind.Offline -> Triple(PixelSprite.Unplugged, c.errOfflineTitle, c.errOfflineDetail)
        ErrorKind.Generic -> Triple(PixelSprite.Unplugged, c.errGenericTitle, c.errGenericDetail)
    }
    StateLayout(
        sprite = sprite,
        title = title,
        note = detail,
        detail = technicalReason(message).ifEmpty { null },
        modifier = modifier,
        action = onRetry?.let { { RetryButton(it) } },
    )
}

/** Signed in offline and this was never stored on the device — the connection, not a bug. */
@Composable
fun OfflineStateView(
    message: String,
    modifier: Modifier = Modifier,
    title: String = strings().common.offlineTitle,
    onRetry: (() -> Unit)? = null,
) {
    StateLayout(
        sprite = PixelSprite.Unplugged,
        title = title,
        note = if (AppError.isOfflineNotSaved(message)) strings().common.offlineNotSaved else message,
        modifier = modifier,
        action = onRetry?.let { { RetryButton(it) } },
    )
}

/** First load, with no cached content to show underneath. */
@Composable
fun LoadingStateView(modifier: Modifier = Modifier, label: String? = null) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = Brand.accent, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
        if (label != null) {
            Spacer(Modifier.size(PokyhSpacing.lg))
            Text(label, style = PokyhType.footnote, color = PokyhTheme.colors.textSecondary)
        }
    }
}

/**
 * Why a POKYH-backend feature (Todos, Erinnerungen, Klasse) isn't available for the current
 * [status] — specific per status rather than one generic message, because each of these has a
 * different fix and telling the user which one they're in is the whole point.
 */
@Composable
fun BackendUnavailableView(feature: String, status: BackendStatus, modifier: Modifier = Modifier) {
    val s = strings().states
    val sprite = when (status) {
        is BackendStatus.Failed, is BackendStatus.Offline -> PixelSprite.Unplugged
        is BackendStatus.NoClass -> PixelSprite.Search
        else -> PixelSprite.Lock
    }
    val title = when (status) {
        is BackendStatus.NotStudent -> s.notStudentTitle
        is BackendStatus.NoClass -> s.noClassTitle
        is BackendStatus.Failed -> s.failedTitle
        is BackendStatus.Offline -> strings().common.offlineTitle
        else -> s.unavailableTitle
    }
    val message = when (status) {
        is BackendStatus.NotStudent -> s.notStudent(feature)
        is BackendStatus.NoClass -> s.noClass(feature)
        is BackendStatus.Failed -> s.failed(feature, status.message)
        is BackendStatus.Offline -> s.offline(feature)
        else -> s.needsAccount(feature)
    }

    StateLayout(
        sprite = sprite,
        title = title,
        note = message,
        modifier = modifier,
        action = if (status is BackendStatus.NoClass) {
            {
                Box(
                    modifier = Modifier
                        .background(PokyhTheme.colors.accentTint, PokyhShapes.pill)
                        .padding(horizontal = PokyhSpacing.lg, vertical = PokyhSpacing.sm),
                ) {
                    Text(text = s.openAccount, style = PokyhType.caption, color = PokyhTheme.colors.accentText)
                }
            }
        } else {
            null
        },
    )
}

/**
 * A slim inline notice inside a scrolling page — for a fact worth stating in place rather than
 * a state that replaces the screen ("Heute kein Unterricht"). A tinted [IconTile] plus one line,
 * on the standard row surface, so it sits in the same rhythm as the rows above and below it.
 */
@Composable
fun PokyhInlineNotice(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    tint: Color = Brand.accent,
) {
    PokyhTileRow(modifier = modifier) {
        IconTile(icon = icon, color = tint, size = 38.dp)
        Text(
            text = text,
            style = PokyhType.callout,
            color = PokyhTheme.colors.textSecondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The same notice with a pixel scene beside it instead of an icon: for a block of the home screen
 * that has nothing to show ("Heute kein Unterricht"), so an empty block still looks like POKYH.
 */
@Composable
fun PokyhPixelNotice(
    sprite: PixelSprite,
    text: String,
    modifier: Modifier = Modifier,
) {
    PokyhTileRow(modifier = modifier) {
        PixelSpriteArt(sprite, width = 72.dp)
        Text(
            text = text,
            style = PokyhType.callout,
            color = PokyhTheme.colors.textSecondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
