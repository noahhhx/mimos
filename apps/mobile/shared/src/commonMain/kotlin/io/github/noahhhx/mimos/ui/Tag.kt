package io.github.noahhhx.mimos.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A recipe's tag: small `muted` text in a `line` outline. */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier
            .border(MimosTheme.spacing.hairline, MimosTheme.colors.line)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        style = MimosTheme.typography.small,
        color = MimosTheme.colors.muted,
    )
}
