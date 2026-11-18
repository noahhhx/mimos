package io.github.noahhhx.mimos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp

/** Text in the theme's body style and `fg`; pass a style and colour for anything else. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MimosTheme.typography.body,
    color: Color = MimosTheme.colors.fg,
    textAlign: TextAlign? = null,
) {
    BasicText(
        text,
        modifier,
        style = style.copy(color = color, textAlign = textAlign ?: style.textAlign),
    )
}

/** Eyebrows, section headings and field labels: small, spaced, in capitals and `muted`. */
@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, MimosTheme.typography.label, MimosTheme.colors.muted)
}

/** A line across the width, in `line`: the rule between rows and under the header. */
@Composable
fun Rule(
    modifier: Modifier = Modifier,
    color: Color = MimosTheme.colors.line,
    thickness: Dp = MimosTheme.spacing.hairline,
) {
    Box(modifier.fillMaxWidth().height(thickness).background(color))
}
