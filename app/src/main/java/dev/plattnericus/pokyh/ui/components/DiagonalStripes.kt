package dev.plattnericus.pokyh.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * Port of `DiagonalStripes: Shape` — the diagonal-hatch fill behind fully-cancelled timetable
 * cells. iOS spaces bars every 7pt with bar width = spacing/2; reproduced as a repeating stroke
 * pattern across the cell bounds.
 */
@Composable
fun DiagonalStripes(color: Color, modifier: Modifier = Modifier, spacing: Float = 7f) {
    Canvas(modifier = modifier) {
        val strokeWidth = spacing / 2f
        var x = -size.height
        while (x < size.width) {
            drawLine(
                color = color,
                start = Offset(x, size.height),
                end = Offset(x + size.height, 0f),
                strokeWidth = strokeWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Butt,
            )
            x += spacing
        }
    }
}
