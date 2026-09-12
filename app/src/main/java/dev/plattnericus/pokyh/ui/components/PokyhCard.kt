package dev.plattnericus.pokyh.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.ui.theme.DecorativeTone
import dev.plattnericus.pokyh.ui.theme.PokyhIcons
import dev.plattnericus.pokyh.ui.theme.PokyhRadius
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.medium
import dev.plattnericus.pokyh.ui.theme.cardSurface
import dev.plattnericus.pokyh.ui.theme.pressHighlight
import dev.plattnericus.pokyh.ui.theme.pressable

/**
 * The container language. Everything on a screen is one of these five things, and that is the
 * whole vocabulary:
 *
 *  - [PokyhSection]      a titled band of the page. Content sits directly on the canvas.
 *  - [PokyhCard]         a card: one group of related content on a [cardSurface].
 *  - [PokyhListCard]     a card holding a homogeneous list, rows hairline-separated.
 *  - [PokyhTileRow]      a single row standing on the canvas as its own small surface — for
 *                        rows that are independently actionable or swipeable, where a grouped
 *                        list can't work.
 *  - [PokyhFeatureTile]  a color-blocked category tile.
 *
 * The one judgement call a screen has to make is section-vs-card, and the rule is: a card means
 * "these things belong together". A flat list of same-shaped rows is a [PokyhListCard], not
 * twenty cards — which is what made the old screens read as noise.
 */

// ── Sections ────────────────────────────────────────────────────────────────

/**
 * A band of the page: optional title row, then content on the canvas. Owns the gap between its
 * header and its content ([PokyhSpacing.headerContent]) so every section on every screen has
 * the same rhythm; the *gap between sections* belongs to the screen ([PokyhSpacing.section]).
 */
@Composable
fun PokyhSection(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    contentSpacing: Dp = PokyhSpacing.rowGap,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            PokyhSectionHeader(title = title, trailing = trailing)
            Spacer(Modifier.size(PokyhSpacing.headerContent))
        }
        Column(verticalArrangement = Arrangement.spacedBy(contentSpacing)) { content() }
    }
}

/** Section title on the canvas, with an optional trailing control (a sort menu, a "Alle" link). */
@Composable
fun PokyhSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = PokyhType.title2,
            color = PokyhTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) trailing()
    }
}

/**
 * All-caps eyebrow. Above a screen title, or as the label of a block inside a card. Deliberately
 * quiet — it's a signpost, not a heading, and it lets the thing under it be the loud one.
 */
@Composable
fun PokyhLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color = PokyhTheme.colors.textTertiary,
) {
    Text(
        text = text.uppercase(),
        style = PokyhType.label,
        color = color,
        modifier = modifier,
    )
}

// ── Cards ───────────────────────────────────────────────────────────────────

/**
 * A card. One radius ([PokyhShapes.xl]), one padding ([PokyhSpacing.card]), one elevation — pass
 * [onClick] and it also gets the standard press highlight. Override [padding] only when the
 * content genuinely bleeds to the edge (an image header), and in that case pad the text block
 * inside instead.
 */
@Composable
fun PokyhCard(
    modifier: Modifier = Modifier,
    shape: Shape = PokyhShapes.xl,
    padding: Dp = PokyhSpacing.card,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .cardSurface(shape)
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(shape)
                        .pressHighlight(interactionSource, shape)
                        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(padding),
        content = content,
    )
}

/**
 * A card whose content bleeds to its edges — an image header, a full-width chart. Same surface
 * and radius as [PokyhCard], but it clips instead of padding, so the caller pads the parts that
 * need it.
 */
@Composable
fun PokyhBleedCard(
    modifier: Modifier = Modifier,
    shape: Shape = PokyhShapes.xl,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .cardSurface(shape)
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier
                        .pressHighlight(interactionSource, shape)
                        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        content = content,
    )
}

// ── Grouped lists ───────────────────────────────────────────────────────────

/**
 * One card holding a homogeneous list. Rows get a hairline between them and none at the ends —
 * the card edge already closes the group, so a trailing divider is just a stray line.
 *
 * This is the default for any list of same-shaped rows. It reads as a single object, keeps the
 * screen's shadow count at one per group instead of one per row, and gives rows a shared left
 * edge to align to.
 */
@Composable
fun <T> PokyhListCard(
    items: List<T>,
    modifier: Modifier = Modifier,
    shape: Shape = PokyhShapes.xl,
    itemContent: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth().cardSurface(shape).clip(shape)) {
        items.forEachIndexed { index, item ->
            if (index > 0) PokyhRowSeparator()
            itemContent(item)
        }
    }
}

/** Hairline between rows inside a card. Inset so it starts where the text does. */
@Composable
fun PokyhRowSeparator(modifier: Modifier = Modifier, startInset: Dp = PokyhSpacing.card) {
    HorizontalDivider(
        modifier = modifier.padding(start = startInset),
        thickness = 1.dp,
        color = PokyhTheme.colors.separator,
    )
}

/**
 * The standard row: optional leading slot, title + optional subtitle, optional trailing slot,
 * and a chevron when it navigates. Sized for a comfortable 56dp+ tap target with
 * [PokyhSpacing.card] horizontal padding, so rows line up with the padding of a [PokyhCard]
 * above or below them.
 */
@Composable
fun PokyhRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleColor: androidx.compose.ui.graphics.Color = PokyhTheme.colors.textPrimary,
    /** Struck through for something excluded from a calculation (a grade taken out of the
     * calculator) or done (a completed task) — the one decoration a row applies to its title. */
    titleDecoration: androidx.compose.ui.text.style.TextDecoration? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    showChevron: Boolean = onClick != null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier
                        .pressHighlight(interactionSource, shape = null)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            enabled = enabled,
                            onClick = onClick,
                        )
                } else {
                    Modifier
                },
            )
            .heightIn(min = 56.dp)
            .padding(horizontal = PokyhSpacing.card, vertical = PokyhSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.iconText),
    ) {
        if (leading != null) leading()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xxs)) {
            Text(
                text = title,
                style = PokyhType.body,
                color = titleColor,
                textDecoration = titleDecoration,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = PokyhType.footnote,
                    color = PokyhTheme.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) trailing()
        if (showChevron) {
            Icon(
                imageVector = PokyhIcons.chevronRight,
                contentDescription = null,
                tint = PokyhTheme.colors.textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * A row that stands on the canvas as its own surface, at one radius step below a card
 * ([PokyhRadius.lg] vs [PokyhRadius.xl]) so the nesting reads correctly if it ever sits next to
 * one. For rows that can't live in a [PokyhListCard]: swipe-to-delete items, and rows whose
 * order changes independently.
 */
@Composable
fun PokyhTileRow(
    modifier: Modifier = Modifier,
    shape: Shape = PokyhShapes.lg,
    padding: Dp = PokyhSpacing.row,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .cardSurface(shape)
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(shape)
                        .pressHighlight(interactionSource, shape)
                        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(padding),
        verticalAlignment = verticalAlignment,
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.iconText),
        content = content,
    )
}

// ── Feature tiles ───────────────────────────────────────────────────────────

/**
 * A color-blocked category tile — the one place the app uses a [DecorativeTone] fill instead of
 * a neutral card. Title (and optional subtitle) at the top, then a row holding the big
 * low-opacity glyph and the "open" arrow at the bottom.
 *
 * The glyph is drawn at 20% of the tone's ink, which is the level where it reads as texture
 * rather than as a second piece of content competing with the title.
 *
 * Laid out as a real [Column] with [Arrangement.SpaceBetween] rather than as glyph/text/arrow
 * absolutely positioned in a [Box]: with a subtitle, two lines of title plus two of subtitle ran
 * straight into a bottom-anchored glyph, because neither knew the other's height. In flow the
 * text always pushes the glyph row down, and the tile grows past [minHeight] if it has to.
 */
@Composable
fun PokyhFeatureTile(
    title: String,
    tone: DecorativeTone,
    glyph: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    minHeight: Dp = 148.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .heightIn(min = minHeight)
            .pressable(interactionSource)
            .clip(PokyhShapes.xxl)
            .then(Modifier.cardSurface(shape = PokyhShapes.xxl, color = tone.fill))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(PokyhSpacing.row),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xs)) {
            Text(
                text = title,
                style = PokyhType.title3,
                color = tone.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = PokyhType.caption,
                    color = tone.ink.copy(alpha = 0.78f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = PokyhSpacing.md),
            verticalAlignment = Alignment.Bottom,
        ) {
            Icon(
                imageVector = glyph,
                contentDescription = null,
                tint = tone.ink.copy(alpha = 0.20f),
                modifier = Modifier.size(52.dp),
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(tone.ink.copy(alpha = 0.14f), PokyhShapes.pill),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = PokyhIcons.openTile,
                    contentDescription = null,
                    tint = tone.ink,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

// ── Statistics ──────────────────────────────────────────────────────────────

/**
 * A labelled number. [PokyhType.statSmall]-sized by default — the compact form that sits two or
 * three to a row inside a card. Screens showing one hero number style it directly with
 * [PokyhType.statLarge] rather than passing a size in here.
 */
@Composable
fun PokyhStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    valueColor: androidx.compose.ui.graphics.Color = PokyhTheme.colors.textPrimary,
    alignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = alignment,
        verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xxs),
    ) {
        Text(value, style = PokyhType.statSmall, color = valueColor)
        Text(label, style = PokyhType.caption.medium(), color = PokyhTheme.colors.textSecondary)
    }
}
