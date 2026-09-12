package dev.plattnericus.pokyh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType

/** One entry of the `DayKind` config table — icon/color/title/subtitle for a whole special day
 * (holiday, weekend, all-cancelled, all-replacement, full-day event). */
data class SpecialDaySpec(val icon: ImageVector, val color: Color, val title: String, val subtitle: String)

/**
 * Shown instead of a lesson list when a whole day has no normal lessons.
 *
 * Deliberately the same layout as an empty state (tinted glyph disc, title, one line) on a
 * flat tinted panel rather than a card: it isn't content, it's the absence of content, and
 * giving it a real card's elevation would make an empty day look like the most important object
 * on the screen. It used to be a dashed outline, which was the only dashed border in the app.
 */
@Composable
fun SpecialDayCard(spec: SpecialDaySpec, modifier: Modifier = Modifier, compact: Boolean = false) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .let { if (!compact) it.height(340.dp) else it }
            .background(spec.color.copy(alpha = 0.07f), PokyhShapes.xl)
            .padding(PokyhSpacing.card),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) PokyhSpacing.sm else PokyhSpacing.lg),
        ) {
            Box(
                modifier = Modifier
                    .size(if (compact) 30.dp else 72.dp)
                    .background(spec.color.copy(alpha = 0.14f), PokyhShapes.pill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = spec.icon,
                    contentDescription = null,
                    tint = spec.color,
                    modifier = Modifier.size(if (compact) 16.dp else 32.dp),
                )
            }
            Text(
                text = spec.title,
                style = if (compact) PokyhType.caption else PokyhType.title2,
                color = PokyhTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = spec.subtitle,
                style = if (compact) PokyhType.caption2 else PokyhType.callout,
                color = PokyhTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
