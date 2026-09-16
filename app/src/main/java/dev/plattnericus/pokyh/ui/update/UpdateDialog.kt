package dev.plattnericus.pokyh.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.plattnericus.pokyh.BuildConfig
import dev.plattnericus.pokyh.core.update.AppUpdater
import dev.plattnericus.pokyh.ui.components.PokyhTextButton
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(val updater: AppUpdater) : ViewModel()

/**
 * The update prompt, above every screen (including Login/Lock — an outdated app that can't sign
 * in is exactly when an update matters). Checks once on launch; the Profile screen's
 * "Nach Updates suchen" feeds the same dialog.
 */
@Composable
fun UpdateDialogHost(viewModel: UpdateViewModel = hiltViewModel()) {
    val updater = viewModel.updater
    val state by updater.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { updater.checkOnLaunch() }
    LifecycleResumeEffect(Unit) {
        updater.onResume()
        onPauseOrDispose { }
    }

    when (val s = state) {
        AppUpdater.State.Idle -> Unit

        is AppUpdater.State.Available -> UpdateDialog(
            title = "Update verfügbar",
            confirmLabel = "Aktualisieren",
            onConfirm = updater::startUpdate,
            dismissLabel = "Morgen",
            onDismiss = updater::snooze,
        ) {
            Text(
                text = "Version ${s.release.version} ist verfügbar (installiert: ${BuildConfig.VERSION_NAME}).",
                style = PokyhType.callout,
                color = PokyhTheme.colors.textSecondary,
            )
            if (s.release.notes.isNotEmpty()) {
                Text(
                    text = s.release.notes,
                    style = PokyhType.footnote,
                    color = PokyhTheme.colors.textSecondary,
                    modifier = Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                )
            }
        }

        is AppUpdater.State.Downloading -> UpdateDialog(
            title = "Wird heruntergeladen…",
            dismissLabel = "Abbrechen",
            onDismiss = updater::cancel,
        ) {
            val progress = s.progress
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress },
                    color = Brand.accent,
                    trackColor = PokyhTheme.colors.textTertiary.copy(alpha = 0.2f),
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
            } else {
                LinearProgressIndicator(
                    color = Brand.accent,
                    trackColor = PokyhTheme.colors.textTertiary.copy(alpha = 0.2f),
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                )
            }
            Text(
                text = if (progress != null) "${(progress * 100).toInt()} %" else "Verbinde…",
                style = PokyhType.footnote,
                color = PokyhTheme.colors.textSecondary,
            )
        }

        is AppUpdater.State.NeedsPermission -> UpdateDialog(
            title = "Installation erlauben",
            confirmLabel = "Einstellungen öffnen",
            onConfirm = updater::openInstallPermissionSettings,
            dismissLabel = "Abbrechen",
            onDismiss = updater::cancel,
        ) {
            Text(
                text = "Das Update ist heruntergeladen. Erlaube POKYH einmalig, Apps zu installieren " +
                    "(„Aus dieser Quelle zulassen“), und komm dann zurück — die Installation startet automatisch.",
                style = PokyhType.callout,
                color = PokyhTheme.colors.textSecondary,
            )
        }

        is AppUpdater.State.Failed -> UpdateDialog(
            title = "Update fehlgeschlagen",
            confirmLabel = "Erneut versuchen",
            onConfirm = updater::startUpdate,
            dismissLabel = "Morgen",
            onDismiss = updater::snooze,
        ) {
            Text(s.message, style = PokyhType.callout, color = PokyhTheme.colors.textSecondary)
        }
    }
}

/** [dev.plattnericus.pokyh.ui.profile.PokyhDialog]'s look, with free content and no dismiss by
 * tapping outside — a half-finished download shouldn't vanish on a stray tap. */
@Composable
private fun UpdateDialog(
    title: String,
    dismissLabel: String,
    onDismiss: () -> Unit,
    confirmLabel: String? = null,
    onConfirm: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        shape = PokyhShapes.xl,
        containerColor = PokyhTheme.colors.card,
        title = { Text(title, style = PokyhType.title3, color = PokyhTheme.colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.md)) { content() }
        },
        confirmButton = {
            if (confirmLabel != null) PokyhTextButton(text = confirmLabel, onClick = onConfirm)
        },
        dismissButton = {
            Row {
                PokyhTextButton(text = dismissLabel, color = PokyhTheme.colors.textSecondary, onClick = onDismiss)
            }
        },
    )
}
