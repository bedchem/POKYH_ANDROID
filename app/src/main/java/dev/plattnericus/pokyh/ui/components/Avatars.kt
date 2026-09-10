package dev.plattnericus.pokyh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.plattnericus.pokyh.ui.theme.InterFontFamily
import dev.plattnericus.pokyh.ui.theme.senderColor

/**
 * Port of `InitialAvatar` — a colored-gradient circle with the name's first letter.
 * [colorSeed] (e.g. a username) picks a persisted-random profile color via [avatarColor];
 * without one, falls back to a name-hash color (message senders, comment authors).
 */
@Composable
fun InitialAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    color: Color = senderColor(name),
) {
    Box(
        modifier = modifier
            .size(size)
            .background(
                Brush.linearGradient(listOf(color, color.copy(alpha = 0.7f))),
                CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().take(1).uppercase().ifEmpty { "?" },
            color = Color.White,
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}

/**
 * Port of `AvatarView` — [InitialAvatar] placeholder that crossfades to the network image once
 * loaded (Coil handles the memory/disk cache + crossfade natively here, replacing iOS's
 * hand-rolled `ImageCache`).
 */
@Composable
fun PokyhAvatar(
    url: String?,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    color: Color = senderColor(name),
) {
    Box(modifier = modifier.size(size).clip(CircleShape)) {
        InitialAvatar(name = name, size = size, color = color)
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        }
    }
}
