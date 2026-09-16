@file:OptIn(ExperimentalMaterial3Api::class)

package dev.plattnericus.pokyh.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.fadeIn

/**
 * A Todo or Erinnerung that was created without a connection and is waiting in the outbox.
 *
 * Same tile as a sent row, so the list does not jump when it goes out — only the leading icon
 * and the amber "Wartet auf Internet" line mark it as not yet on the server. Swiping it away
 * removes it from the queue, which is the one thing that can be done with an unsent item.
 */
@Composable
fun PendingSyncRow(
    title: String,
    text: String,
    dateLabel: String?,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onDelete()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier.fadeIn(),
        backgroundContent = { SwipeToDeleteBackground(dismissState.dismissDirection) },
    ) {
        val colors = PokyhTheme.colors
        PokyhTileRow(verticalAlignment = Alignment.Top) {
            IconTile(icon = PokyhIcons.pendingSync, color = Brand.warning, size = 32.dp)
            Column(
                modifier = Modifier.weight(1f).padding(top = PokyhSpacing.xxs),
                verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xs),
            ) {
                Text(title, style = PokyhType.body, color = colors.textPrimary)
                if (text.isNotBlank()) {
                    Text(text, style = PokyhType.footnote, color = colors.textSecondary)
                }
                if (dateLabel != null) {
                    StatusLabel(text = dateLabel, color = Brand.orange, icon = PokyhIcons.timetable)
                }
                StatusLabel(text = "Wartet auf Internet", color = Brand.warning, icon = PokyhIcons.offline)
            }
        }
    }
}
