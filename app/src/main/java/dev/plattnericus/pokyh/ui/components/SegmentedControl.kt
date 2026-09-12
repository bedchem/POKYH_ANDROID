package dev.plattnericus.pokyh.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.plattnericus.pokyh.ui.theme.Brand
import dev.plattnericus.pokyh.ui.theme.PokyhMotion
import dev.plattnericus.pokyh.ui.theme.PokyhShapes
import dev.plattnericus.pokyh.ui.theme.PokyhSpacing
import dev.plattnericus.pokyh.ui.theme.PokyhTheme
import dev.plattnericus.pokyh.ui.theme.PokyhType
import dev.plattnericus.pokyh.ui.theme.PokyhType.semibold
import dev.plattnericus.pokyh.ui.theme.insetSurface

/**
 * The one segmented control: a recessed pill track with the selected segment lifted out of it in
 * [PokyhColors.card], the way a physical switch sits proud of its groove.
 *
 * Selection is marked by *surface*, not by an accent fill — a solid accent segment reads as the
 * primary action of the screen, which a view switcher isn't, and it fights whatever real primary
 * action the screen has. The accent stays in the label.
 *
 * This unified two divergent implementations: Timetable's stock Material `SegmentedButtonRow`
 * and Messages' hand-rolled folder switcher.
 */
@Composable
fun <T> PokyhSegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val colors = PokyhTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .insetSurface(PokyhShapes.pill)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val interactionSource = remember { MutableInteractionSource() }
            val segmentFill by animateColorAsState(
                targetValue = if (isSelected) colors.card else colors.card.copy(alpha = 0f),
                animationSpec = tween(PokyhMotion.durationFast),
                label = "segmentFill",
            )
            val labelColor by animateColorAsState(
                targetValue = if (isSelected) colors.accentText else colors.textSecondary,
                animationSpec = tween(PokyhMotion.durationFast),
                label = "segmentLabel",
            )
            Box(
                // fillMaxHeight matters: without it the Box wraps its label, so the selected
                // segment's fill was only as tall as the text and sat as a short pill floating
                // inside the track instead of filling it.
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(PokyhShapes.pill)
                    .background(segmentFill)
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelect(option) },
                        role = Role.Tab,
                        interactionSource = interactionSource,
                        indication = null,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label(option),
                    style = if (isSelected) PokyhType.footnote.semibold() else PokyhType.footnote,
                    color = labelColor,
                )
            }
        }
    }
}

/**
 * A horizontal row of day pills — the Timetable day picker. Related to
 * [PokyhSegmentedControl] but not the same control: these are equal-weight date targets rather
 * than mutually exclusive views of one thing, so the selected one *does* take the solid accent
 * fill, and "today" is marked separately in the accent text color.
 */
@Composable
fun PokyhDayPills(
    count: Int,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    weekdayLabel: (Int) -> String,
    dayNumberLabel: (Int) -> String,
    isToday: (Int) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = PokyhTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PokyhSpacing.sm),
    ) {
        for (index in 0 until count) {
            val isSelected = index == selectedIndex
            val today = isToday(index)
            val interactionSource = remember { MutableInteractionSource() }
            val fill by animateColorAsState(
                targetValue = when {
                    isSelected -> Brand.accent
                    today -> colors.accentTint
                    else -> colors.card
                },
                animationSpec = tween(PokyhMotion.durationFast),
                label = "dayPillFill",
            )
            val foreground = when {
                isSelected -> Brand.onAccent
                today -> colors.accentText
                else -> colors.textPrimary
            }
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(PokyhShapes.sm)
                    .background(fill)
                    .selectable(
                        selected = isSelected,
                        onClick = { onSelect(index) },
                        role = Role.Tab,
                        interactionSource = interactionSource,
                        indication = null,
                    )
                    .padding(vertical = PokyhSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PokyhSpacing.xxs),
            ) {
                Text(weekdayLabel(index), style = PokyhType.caption2, color = foreground.copy(alpha = 0.8f))
                Text(dayNumberLabel(index), style = PokyhType.headline, color = foreground)
            }
        }
    }
}
