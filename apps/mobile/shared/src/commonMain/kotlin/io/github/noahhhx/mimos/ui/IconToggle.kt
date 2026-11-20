package io.github.noahhhx.mimos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * A 34dp square with a `line` border that turns `accent` while pressed or [active]: the header's
 * tools.
 */
@Composable
fun IconToggle(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    icon: @Composable () -> Unit,
) {
    val colors = MimosTheme.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .size(MimosTheme.spacing.iconToggle)
            .border(
                MimosTheme.spacing.hairline,
                if (pressed || active) colors.accent else colors.line,
            )
            .semantics { this.contentDescription = contentDescription }
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        icon()
    }
}

/** The web's theme glyph: a 12dp square, half filled. */
@Composable
fun ThemeGlyph(color: Color = MimosTheme.colors.fg) {
    Canvas(Modifier.size(12.dp)) {
        val stroke = 1.dp.toPx()
        drawRect(
            color,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            style = Stroke(stroke),
        )
        drawRect(color, topLeft = Offset.Zero, size = Size(size.width / 2, size.height))
    }
}

/** The web's profile glyph: a head and shoulders in 1dp strokes. */
@Composable
fun ProfileGlyph(color: Color = MimosTheme.colors.fg) {
    Canvas(Modifier.size(16.dp)) {
        val unit = size.width / 16f
        val stroke = Stroke(1.dp.toPx())
        drawOval(
            color,
            topLeft = Offset(5 * unit, 2 * unit),
            size = Size(6 * unit, 6 * unit),
            style = stroke,
        )
        val shoulders =
            Path().apply {
                moveTo(2 * unit, 15 * unit)
                cubicTo(2 * unit, 11.7f * unit, 4.7f * unit, 9.5f * unit, 8 * unit, 9.5f * unit)
                cubicTo(11.3f * unit, 9.5f * unit, 14 * unit, 11.7f * unit, 14 * unit, 15 * unit)
            }
        drawPath(shoulders, color, style = stroke)
    }
}
