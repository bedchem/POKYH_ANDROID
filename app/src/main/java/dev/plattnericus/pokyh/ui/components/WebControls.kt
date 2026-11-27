package dev.plattnericus.pokyh.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.plattnericus.pokyh.ui.theme.PokyhDecorative
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.pressHighlight

// Controls in the web's look (pokyh-frontend components/app/PickMenu.tsx and the Segmented in
// components/app/Page.tsx), for the pages that follow the web one to one.

/**
 * The web's `PickMenu`: a "quiet" button (components/ui/Button.tsx: the inset tone, 44dp, 14dp
 * corners, 15sp semibold) with the chosen option and a caret; the list opens under it, the
 * chosen option on the periwinkle tone.
 */
@Composable
fun <T> WebPickMenu(
    value: T,
    options: List<Pair<T, String>>,
    onChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val colors = PokyhTheme.colors
    var open by remember { mutableStateOf(false) }
    val caret by animateFloatAsState(if (open) 180f else 0f, tween(200), label = "pickCaret")
    val shape = RoundedCornerShape(14.dp)
    val interaction = remember { MutableInteractionSource() }
    Box(modifier) {
        Row(
            Modifier
                .height(44.dp)
                .clip(shape)
                .background(colors.cardAlt)
                .pressHighlight(interaction, shape)
                .clickable(interactionSource = interaction, indication = null, role = Role.DropdownList) { open = !open }
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) Icon(icon, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(17.dp))
            Text(
                options.firstOrNull { it.first == value }?.second.orEmpty(),
                style = PokyhType.callout.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                maxLines = 1,
            )
            Icon(PokyhIcons.expandMenu, contentDescription = null, tint = colors.textPrimary, modifier = Modifier.size(15.dp).rotate(caret))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.widthIn(min = 190.dp)) {
            val tone = PokyhDecorative.periwinkle(colors.isDark)
            options.forEach { (v, label) ->
                val active = v == value
                DropdownMenuItem(
                    text = { Text(label, style = PokyhType.callout.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold), color = if (active) tone.ink else colors.textPrimary) },
                    modifier = Modifier.padding(horizontal = 6.dp).clip(RoundedCornerShape(10.dp)).background(if (active) tone.fill else Color.Transparent),
                    onClick = { onChange(v); open = false },
                )
            }
        }
    }
}
