package dev.plattnericus.pokyh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType

/** One entry of the `DayKind` config table (`TimetableView.swift`) — icon/color/title/subtitle
 * for a whole special day (holiday, weekend, all-cancelled, all-replacement, full-day event). */
data class SpecialDaySpec(val icon: ImageVector, val color: Color, val title: String, val subtitle: String)

/** Port of `SpecialDayCard` — dashed-outline card shown instead of a lesson list. */
@Composable
fun SpecialDayCard(spec: SpecialDaySpec, modifier: Modifier = Modifier, compact: Boolean = false) {
    val shape = PokyhShapes.r12
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val borderColor = spec.color.copy(alpha = 0.35f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .let { if (!compact) it.height(380.dp) else it }
            .background(spec.color.copy(alpha = 0.08f), shape)
            .drawWithContent {
                drawContent()
                // PokyhShapes always produces Outline.Generic (see SmoothCornerShape) — draw its
                // path directly rather than relying on the (unreliable-to-import) drawOutline API.
                val outline = shape.createOutline(size, layoutDirection, density)
                val path = (outline as? androidx.compose.ui.graphics.Outline.Generic)?.path
                if (path != null) {
                    drawPath(
                        path = path,
                        color = borderColor,
                        style = Stroke(
                            width = 1.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                        ),
                    )
                }
            }
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp)) {
            Box(
                modifier = Modifier.size(if (compact) 28.dp else 48.dp).background(spec.color.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(spec.icon, contentDescription = null, tint = spec.color, modifier = Modifier.size(if (compact) 16.dp else 24.dp))
            }
            Text(
                spec.title,
                style = if (compact) PokyhType.footnote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) else PokyhType.headline,
                color = PokyhTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                spec.subtitle,
                style = if (compact) PokyhType.caption2 else PokyhType.subheadline,
                color = PokyhTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}
