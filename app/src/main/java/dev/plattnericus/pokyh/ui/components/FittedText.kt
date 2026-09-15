package dev.plattnericus.pokyh.ui.components

import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import dev.plattnericus.pokyh.ui.theme.PokyhTheme

/**
 * Text that **shrinks rather than breaking a word apart**.
 *
 * German compounds are long — "Durchschnittsnote", "Notenverteilung", "Fehlstunden &
 * Entschuldigungen" — and the app has places where one has to live in a narrow column: a stat
 * card at half width, a category tile in a 2-up grid, a badge. When a single word is wider than
 * its box, Android's line breaker has one last resort and takes it: it breaks mid-word, which is
 * how "Durchschnittsno / te" happens.
 *
 * Nothing about line breaking can prevent that. [androidx.compose.ui.text.style.LineBreak] and
 * [androidx.compose.ui.text.style.Hyphens] (both set app-wide in
 * [dev.plattnericus.pokyh.ui.theme.PokyhType]) decide *where among the legal break points* to
 * break — and when a word doesn't fit on a line of its own, there are none. The only real fixes
 * are a wider box or smaller type, so this takes the second: [TextAutoSize] steps the size down
 * until the text fits, and stops at [minScale] of the style's size so it can never shrink into
 * illegibility. If even the smallest step doesn't fit, it ellipsizes — a trimmed word still
 * reads as one word, where a split one reads as two.
 *
 * Use it where the box is tight and the content is real prose. Ordinary full-width text should
 * stay a plain `Text`: type that silently changes size from screen to screen is its own problem,
 * and this exists to stop a specific, visible defect rather than as a default.
 */
@Composable
fun PokyhFittedText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = PokyhTheme.colors.textPrimary,
    maxLines: Int = Int.MAX_VALUE,
    /** The floor, as a fraction of the style's own size. 0.78 is about two type steps down —
     * far enough to rescue a long compound, close enough that the text still reads as its
     * token. */
    minScale: Float = 0.78f,
) {
    val base: TextUnit = if (style.fontSize.isSpecified) style.fontSize else 14.sp
    val autoSize = remember(base, minScale) {
        TextAutoSize.StepBased(
            minFontSize = base * minScale,
            maxFontSize = base,
            stepSize = 0.5.sp,
        )
    }
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = Color.Unspecified),
        color = { color },
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        autoSize = autoSize,
    )
}

