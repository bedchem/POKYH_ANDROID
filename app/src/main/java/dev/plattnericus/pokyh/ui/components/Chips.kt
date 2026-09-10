package dev.plattnericus.pokyh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.smoothCorner

/** Small colored capsule badge (Entfall/Prüfung/Vertretung/Veranstaltung, diet tags, "Standard"…).
 * Matches the recurring iOS chip recipe: `.caption.bold()` at 9sp, 20%-opacity capsule fill. */
@Composable
fun TagChip(text: String, color: Color, modifier: Modifier = Modifier, large: Boolean = false) {
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.2f), androidx.compose.foundation.shape.RoundedCornerShape(50))
            .padding(horizontal = if (large) 10.dp else 6.dp, vertical = if (large) 4.dp else 2.dp),
    ) {
        Text(
            text = text,
            style = if (large) PokyhType.caption.copy(fontWeight = FontWeight.Bold) else PokyhType.badgeChip,
            color = color,
        )
    }
}

/** Icon tile used on Home shortcuts (40dp, r11) and SchoolHub rows (46dp, r12). */
@Composable
fun IconTile(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    cornerRadius: Dp = 11.dp,
    iconSize: Dp = 20.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(color.copy(alpha = 0.15f), smoothCorner(cornerRadius)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(iconSize))
    }
}
