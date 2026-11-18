package io.github.noahhhx.mimos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * A row of a list: a title, an optional muted line under it, and an optional muted note at the
 * right, closed by a `line` rule. A row with [onClick] fills with `hover` while pressed.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    trailing: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = MimosTheme.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .background(if (pressed) colors.hover else Color.Transparent)
                .then(
                    if (onClick == null) Modifier
                    else
                        Modifier.clickable(
                            interactionSource = source,
                            indication = null,
                            role = Role.Button,
                            onClick = onClick,
                        )
                )
                .padding(horizontal = MimosTheme.spacing.page, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title)
                if (detail != null) {
                    Text(detail, style = MimosTheme.typography.small, color = colors.muted)
                }
            }
            if (trailing != null) {
                Text(trailing, style = MimosTheme.typography.small, color = colors.muted)
            }
        }
        Rule()
    }
}
