package dev.plattnericus.pokyh.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhMotion
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.insetSurface

/**
 * The app's one text-input style: a recessed [insetSurface] field with no visible outline at
 * rest and a 1.5dp [Brand.accent] ring on focus.
 *
 * Built on [BasicTextField] rather than Material's `OutlinedTextField`, because every screen
 * that used the stock field ended up overriding its border colors to transparent and then
 * drawing its own background anyway — four screens, four slightly different results. A
 * recessed-on-card field is also the only treatment that reads correctly at both layers, which
 * an outlined field does not: an outline inside a card adds a third edge next to the card's own.
 */
@Composable
fun PokyhTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    label: String? = null,
    leadingIcon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    isPassword: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester? = null,
) {
    val colors = PokyhTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val ringColor by animateColorAsState(
        targetValue = if (focused) Brand.accent else Color.Transparent,
        animationSpec = androidx.compose.animation.core.tween(PokyhMotion.durationFast),
        label = "fieldFocusRing",
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(PokyhSpacing.sm)) {
        if (label != null) {
            PokyhLabel(label, color = colors.textSecondary)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .insetSurface(PokyhShapes.md)
                .border(1.5.dp, ringColor, PokyhShapes.md)
                .defaultMinSize(minHeight = 52.dp)
                .padding(horizontal = PokyhSpacing.lg, vertical = PokyhSpacing.md),
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.md),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (focused) colors.accentText else colors.textTertiary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && placeholder != null) {
                    Text(placeholder, style = PokyhType.body, color = colors.textTertiary)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    minLines = minLines,
                    textStyle = PokyhType.body.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(Brand.accent),
                    visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    interactionSource = interactionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
                )
            }
            if (trailing != null) trailing()
        }
    }
}

/**
 * A field with its submit action built into the trailing edge — the comment composer, and
 * anything else where typing and sending are one gesture apart. Same surface as
 * [PokyhTextField]; the send affordance is disabled (and tertiary) until there's something to
 * send, so the control itself says whether the action is available.
 */
@Composable
fun PokyhComposerField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    submitIcon: ImageVector,
    submitDescription: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    PokyhTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        singleLine = false,
        minLines = 1,
        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Send),
        keyboardActions = KeyboardActions(onSend = { if (value.isNotBlank()) onSubmit() }),
        trailing = {
            PokyhIconButton(
                icon = submitIcon,
                contentDescription = submitDescription,
                onClick = onSubmit,
                enabled = value.isNotBlank(),
                tint = PokyhTheme.colors.accentText,
                size = 32.dp,
                iconSize = 22.dp,
            )
        },
    )
}
