package dev.plattnericus.pokyh.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.plattnericus.pokyh.ui.pixel.PixelArt
import dev.plattnericus.pokyh.ui.pixel.PixelIconName
import dev.plattnericus.pokyh.ui.pixel.iconScene
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.DecorativeTone
import dev.plattnericus.pokyh.ui.theme.PokyhDecorative
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.pressHighlight

// The settings and the profile as the web sets them out (components/app/SettingsRows.tsx,
// components/account/SecurityRow.tsx, components/app/Page.tsx): each group's name above its
// rows, the rows on the canvas under thin rules — no cards around them — with the control
// below the text on phones and beside it from 640dp on, as the web's `sm:` breakpoint. The
// buttons, the switch and the segmented control are the web's recipes, not Material's.

/** The web's `sm:` breakpoint: title and control side by side from here on. */
@Composable
private fun wide(): Boolean = LocalConfiguration.current.screenWidthDp >= 640

/** The type of these pages, in the web's sizes. */
object SettingsType {
    /** PageHeader's h1: 34px, extra bold, tight. */
    val pageTitle: TextStyle = PokyhType.body.copy(fontSize = 34.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.05).em)
    val pageLead: TextStyle = PokyhType.body.copy(fontSize = 15.sp, lineHeight = 24.sp)
    /** A group's name: 22px, extra bold. */
    val groupTitle: TextStyle = PokyhType.body.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.035).em)
    val rowTitle: TextStyle = PokyhType.body.copy(fontSize = 16.5.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.015).em)
    val rowHint: TextStyle = PokyhType.body.copy(fontSize = 14.5.sp, lineHeight = 22.5.sp)
    val rowValue: TextStyle = PokyhType.body.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold)
}

/** The page's title and what it is for, at the top of its content (the web's `PageHeader`). */
@Composable
fun SettingsPageHeader(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 16.dp, bottom = 28.dp)) {
        Text(title, style = SettingsType.pageTitle, color = PokyhTheme.colors.textPrimary)
        if (subtitle != null) {
            Spacer(Modifier.height(8.dp))
            Text(subtitle, style = SettingsType.pageLead, color = PokyhTheme.colors.textSecondary)
        }
    }
}

/** A group: its name, then its rows, each under a thin rule. */
@Composable
fun SettingsGroup(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        Text(title, style = SettingsType.groupTitle, color = PokyhTheme.colors.textPrimary)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

/** The rule above every row. */
@Composable
fun SettingsRule(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(PokyhTheme.colors.line))
}

/**
 * A setting: its title (red for signing out), a hint under it, and its control — below the text
 * on phones, beside it on wide screens; [inline] keeps a short value beside the title on phones
 * too (an ID). [alert] is a line in red under the hint (a save that failed).
 */
@Composable
fun SettingsItem(
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    danger: Boolean = false,
    inline: Boolean = false,
    alert: String? = null,
    control: (@Composable () -> Unit)? = null,
) {
    val colors = PokyhTheme.colors
    val text: @Composable (Modifier) -> Unit = { m ->
        Column(m) {
            Text(title, style = SettingsType.rowTitle, color = if (danger) colors.dangerText else colors.textPrimary)
            if (hint != null) {
                Spacer(Modifier.height(4.dp))
                Text(hint, style = SettingsType.rowHint, color = colors.textSecondary)
            }
            if (alert != null) {
                Spacer(Modifier.height(4.dp))
                Text(alert, style = SettingsType.rowHint.copy(fontWeight = FontWeight.SemiBold), color = colors.dangerText)
            }
        }
    }
    Column(modifier.fillMaxWidth()) {
        SettingsRule()
        if (control == null) {
            text(Modifier.fillMaxWidth().padding(vertical = 20.dp))
        } else if (inline || wide()) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (inline && !wide()) 16.dp else 32.dp),
            ) {
                text(if (inline) Modifier else Modifier.weight(1f))
                Box(if (inline) Modifier.weight(1f, fill = false).fillMaxWidth().wrapEnd() else Modifier) { control() }
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                text(Modifier.fillMaxWidth())
                control()
            }
        }
    }
}

/** Puts a narrower child at the end of the space it has (an inline value, right-aligned). */
private fun Modifier.wrapEnd(): Modifier = this.then(Modifier.layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0))
    val w = if (constraints.hasBoundedWidth) constraints.maxWidth else p.width
    layout(w, p.height) { p.place(w - p.width, 0) }
})

/** Reaches [by] beyond both sides of the column, as the web's `-mx-3`: a fill can go past the text it frames. */
private fun Modifier.bleed(by: Dp): Modifier = this.then(Modifier.layout { measurable, constraints ->
    val extra = by.roundToPx() * 2
    val p = measurable.measure(
        constraints.copy(
            minWidth = (constraints.minWidth + extra).coerceAtMost(if (constraints.hasBoundedWidth) constraints.maxWidth + extra else Int.MAX_VALUE),
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth,
        ),
    )
    val w = (p.width - extra).coerceAtLeast(0)
    layout(w, p.height) { p.place(-extra / 2, 0) }
})

/**
 * A row that leads somewhere: its title and hint, a caret (or an arrow out, for a page outside
 * the app). Pressed, a soft fill comes up behind it, rounded, wider than the text.
 */
@Composable
fun SettingsLinkItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    external: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = PokyhTheme.colors
    val source = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.fillMaxWidth()) {
        SettingsRule()
        Row(
            Modifier
                .padding(vertical = 6.dp)
                .bleed(12.dp)
                .clip(shape)
                .pressHighlight(source, shape)
                .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = SettingsType.rowTitle, color = colors.textPrimary)
                if (hint != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(hint, style = SettingsType.rowHint, color = colors.textSecondary)
                }
            }
            if (trailing != null) trailing()
            Icon(
                if (external) PokyhIcons.openTile else PokyhIcons.chevronRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** A small pixel icon at three times its size (the web's `PixelIcon`, 48px). */
@Composable
fun PixelIcon(name: PixelIconName, modifier: Modifier = Modifier, size: Dp = 48.dp) {
    PixelArt(scene = iconScene(name), modifier = modifier.size(size), animate = false)
}

/** A state in a pill of one of the four tones: "Fehlt", "Empfohlen", "An". */
@Composable
fun StatusPill(text: String, tone: DecorativeTone, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Row(
        modifier.height(28.dp).clip(CircleShape).background(tone.fill).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tone.ink, modifier = Modifier.size(14.dp))
        Text(text, style = PokyhType.body.copy(fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold), color = tone.ink, maxLines = 1)
    }
}

/** "Eingeschaltet", "Gelöscht": a done state where the button was, in sage with a tick (14px type). */
@Composable
fun DonePill(text: String, modifier: Modifier = Modifier) {
    val tone = PokyhDecorative.sage(PokyhTheme.colors.isDark)
    Row(
        modifier.clip(CircleShape).background(tone.fill).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(PokyhIcons.check, contentDescription = null, tint = tone.ink, modifier = Modifier.size(15.dp))
        Text(text, style = PokyhType.body.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold), color = tone.ink, maxLines = 1)
    }
}

/**
 * One of the rows at the top of Konto & Sicherheit: what the account has (its e-mail address,
 * password, two-factor sign-in, devices), its pixel icon on the left, the actions under the text
 * on phones and beside it on wide screens. A row where something wants doing ([highlight]: no
 * e-mail address, two-factor sign-in off) keeps the web's accent outline.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SecurityRow(
    icon: PixelIconName,
    title: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    hint: String? = null,
    highlight: Boolean = false,
    status: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = PokyhTheme.colors
    val shape = RoundedCornerShape(22.dp)
    Column(modifier.fillMaxWidth()) {
        SettingsRule()
        Row(
            Modifier
                .padding(vertical = if (highlight) 12.dp else 20.dp)
                .then(
                    if (highlight) {
                        Modifier.bleed(12.dp).border(2.dp, Brand.accent, shape).padding(horizontal = 12.dp, vertical = 16.dp)
                    } else {
                        Modifier.fillMaxWidth()
                    },
                ),
            horizontalArrangement = Arrangement.spacedBy(if (wide()) 20.dp else 16.dp),
        ) {
            PixelIcon(icon)
            val body: @Composable (Modifier) -> Unit = { m ->
                Column(m) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(title, style = SettingsType.rowTitle, color = colors.textPrimary)
                        if (status != null) status()
                    }
                    if (value != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(value, style = SettingsType.rowValue, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (hint != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(hint, style = SettingsType.rowHint, color = colors.textSecondary)
                    }
                }
            }
            val buttons: @Composable () -> Unit = {
                if (actions != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
                    }
                }
            }
            if (wide()) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    body(Modifier.weight(1f))
                    buttons()
                }
            } else {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    body(Modifier.fillMaxWidth())
                    if (actions != null) buttons()
                }
            }
        }
    }
}

/** The web's button recipes (components/ui/Button.tsx). */
enum class WebButtonVariant { Primary, Tone, Ink, Quiet, Danger }

/**
 * A button as the web draws one: flat, solid, slightly squared; pressing deepens the colour a
 * little (nothing shrinks or bounces). [small] is the web's `sm` (36dp high), else `md` (44dp).
 */
@Composable
fun WebButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: WebButtonVariant = WebButtonVariant.Primary,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    loading: Boolean = false,
    enabled: Boolean = true,
    small: Boolean = false,
) {
    val colors = PokyhTheme.colors
    val dark = colors.isDark
    val (container, content) = when (variant) {
        WebButtonVariant.Primary -> Brand.accent to Brand.onAccent
        WebButtonVariant.Tone -> PokyhDecorative.periwinkle(dark).let { it.fill to it.ink }
        WebButtonVariant.Ink -> if (dark) Brand.accent to Brand.onAccent else colors.textPrimary to colors.bg
        WebButtonVariant.Quiet -> colors.cardAlt to colors.textPrimary
        WebButtonVariant.Danger -> Brand.danger.copy(alpha = 0.13f) to colors.dangerText
    }
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val shape = RoundedCornerShape(if (small) 11.dp else 14.dp)
    // active:brightness-[0.88], in the dark theme 1.25.
    val press = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.12f)
    Row(
        modifier
            .height(if (small) 36.dp else 44.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(container)
            .drawWithContent {
                drawContent()
                if (pressed) drawRect(press)
            }
            .clickable(interactionSource = source, indication = null, enabled = enabled && !loading, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (small) 14.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (loading) {
            BrandSpinner(color = content, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(17.dp))
        }
        Text(
            text,
            style = PokyhType.body.copy(fontSize = if (small) 14.sp else 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em),
            color = content,
            maxLines = 1,
        )
        if (trailingIcon != null) Icon(trailingIcon, contentDescription = null, tint = content, modifier = Modifier.size(17.dp))
    }
}

/** The web's switch: 50 × 30, the accent when on, a white knob that slides. */
@Composable
fun WebSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = PokyhTheme.colors
    val track by animateColorAsState(if (checked) Brand.accent else colors.textPrimary.copy(alpha = 0.18f), tween(200), label = "switchTrack")
    val knob by animateDpAsState(if (checked) 20.dp else 0.dp, tween(200), label = "switchKnob")
    Box(
        modifier
            .size(width = 50.dp, height = 30.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(CircleShape)
            .background(track)
            .clickable(enabled = enabled, role = Role.Switch, interactionSource = remember { MutableInteractionSource() }, indication = null) { onCheckedChange(!checked) },
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .offset(x = knob)
                .size(24.dp)
                .shadow(1.5.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

/** One of a few choices as a row of segments on the inset tone (the web's `Segmented`). */
@Composable
fun <T> WebSegmented(value: T, options: List<Pair<T, String>>, onChange: (T) -> Unit, modifier: Modifier = Modifier) {
    val colors = PokyhTheme.colors
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(colors.cardAlt).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (option, label) ->
            val active = option == value
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .then(if (active) Modifier.shadow(0.5.dp, shape).background(colors.card, shape) else Modifier)
                    .clip(shape)
                    .clickable(role = Role.RadioButton) { onChange(option) }
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                PokyhFittedText(
                    label,
                    style = PokyhType.body.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
                    color = if (active) colors.textPrimary else colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Space between the groups of a page, as the web's `gap-14`. */
val SettingsGroupGap: Dp = 56.dp
