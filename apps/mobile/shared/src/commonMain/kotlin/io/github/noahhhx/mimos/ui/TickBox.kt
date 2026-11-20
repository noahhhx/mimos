package io.github.noahhhx.mimos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * A checkbox in Evening Kitchen's terms: a straight-edged square, amber with a tick when checked.
 * It draws only; the row around it is the toggle.
 */
@Composable
fun TickBox(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = MimosTheme.colors
    Canvas(
        modifier
            .size(24.dp)
            .background(if (checked) colors.accent else colors.surface)
            .border(1.5.dp, if (checked) colors.accent else colors.line)
    ) {
        if (checked) {
            val tick =
                Path().apply {
                    moveTo(size.width * 0.24f, size.height * 0.52f)
                    lineTo(size.width * 0.43f, size.height * 0.70f)
                    lineTo(size.width * 0.76f, size.height * 0.30f)
                }
            drawPath(tick, colors.onAccent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Square))
        }
    }
}
