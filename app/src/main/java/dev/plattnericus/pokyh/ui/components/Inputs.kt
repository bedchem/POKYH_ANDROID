package dev.plattnericus.pokyh.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
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

/** The web's field corners (components/ui/Field.tsx: `rounded-[14px]`). */
private val FieldShape = RoundedCornerShape(14.dp)
private val LargeFieldShape = RoundedCornerShape(16.dp)

/**
 * The app's one text-input style, as the web's `TextField` (components/ui/Field.tsx): the label
 * above in 14sp semibold ink, a 48dp field in the inset tone with 14dp corners and 16sp text.
 * Focused, it turns to the surface tone with a 2dp accent ring around it (outside, as the web's
 * box-shadow); with an [error] the ring is red and the message under it.
 *
 * Built on [BasicTextField] rather than Material's `OutlinedTextField`, because every screen
 * that used the stock field ended up overriding its border colors to transparent and then
 * drawing its own background anyway — four screens, four slightly different results.
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
    /** With [isPassword]: show the password in plain text (the eye button beside it). */
    passwordVisible: Boolean = false,
    /** Told when the field gains or loses the focus. */
    onFocusChange: ((Boolean) -> Unit)? = null,
    /** Shown under the field in the danger ink, the ring red (the web's `error`). */
    error: String? = null,
    /** The web's sign-in field (login/LoginForm.tsx, `.login-field`): 54dp, 16dp corners, the
     * surface tone throughout, 16sp medium, the ring in the accent ink. */
    large: Boolean = false,
) {
    val colors = PokyhTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    if (onFocusChange != null) {
        androidx.compose.runtime.LaunchedEffect(focused) { onFocusChange(focused) }
    }
    val ringColor by animateColorAsState(
        targetValue = when {
            error != null -> Brand.danger
            focused -> if (large) colors.accentText else Brand.accent
            else -> Color.Transparent
        },
        animationSpec = androidx.compose.animation.core.tween(PokyhMotion.durationFast),
        label = "fieldFocusRing",
    )
    val fill by animateColorAsState(
        targetValue = if (focused || large) colors.card else colors.cardAlt,
        animationSpec = androidx.compose.animation.core.tween(PokyhMotion.durationFast),
        label = "fieldFill",
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (label != null) {
            Text(label, style = PokyhType.callout.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    // The ring outside the field, as the web's `box-shadow: 0 0 0 2px`.
                    if (ringColor.alpha > 0f) {
                        val r = 2.dp.toPx()
                        drawRoundRect(
                            color = ringColor,
                            topLeft = Offset(-r, -r),
                            size = Size(size.width + 2 * r, size.height + 2 * r),
                            cornerRadius = CornerRadius((if (large) 16.dp else 14.dp).toPx() + r),
                        )
                    }
                }
                .background(fill, if (large) LargeFieldShape else FieldShape)
                .defaultMinSize(minHeight = if (large) 54.dp else 48.dp)
                .padding(horizontal = 16.dp, vertical = if (singleLine) 10.dp else 12.dp),
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
                    Text(placeholder, style = PokyhType.body.copy(fontSize = 16.sp, fontWeight = if (large) FontWeight.Medium else FontWeight.Normal), color = colors.textTertiary, maxLines = if (singleLine) 1 else Int.MAX_VALUE, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    minLines = minLines,
                    textStyle = PokyhType.body.copy(color = colors.textPrimary, fontWeight = if (large) FontWeight.Medium else FontWeight.Normal),
                    cursorBrush = SolidColor(Brand.accent),
                    visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
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
        if (error != null) {
            Text(error, style = PokyhType.footnote.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium), color = colors.dangerText)
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
