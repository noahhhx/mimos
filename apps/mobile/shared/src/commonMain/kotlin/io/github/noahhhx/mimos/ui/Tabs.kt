package io.github.noahhhx.mimos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics

/**
 * The web's `.tabs`: a row of labels over a `line` rule, the selected one in `fg` with a 2dp amber
 * underline. For the choices on one page; the app's own navigation is the tab bar.
 */
@Composable
fun Tabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MimosTheme.colors
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                val active = index == selected
                Box(
                    Modifier.weight(1f)
                        .semantics { this.selected = active }
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        )
                        .padding(vertical = MimosTheme.spacing.m),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MimosTheme.typography.tab,
                        color = if (active) colors.fg else colors.muted,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth()) {
            Rule(Modifier.align(Alignment.BottomCenter))
            Row(Modifier.fillMaxWidth()) {
                labels.indices.forEach { index ->
                    Box(
                        Modifier.weight(1f)
                            .height(MimosTheme.spacing.ruleThickness)
                            .background(if (index == selected) colors.accent else Color.Transparent)
                    )
                }
            }
        }
    }
}
