package io.github.noahhhx.mimos.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import io.github.noahhhx.mimos.ui.MimosTheme
import io.github.noahhhx.mimos.ui.Rule
import io.github.noahhhx.mimos.ui.Text

/** The web's app navigation, along the bottom: the current tab in `fg` with a 2dp amber rule. */
@Composable
fun TabBar(selected: Destination, onSelect: (Destination) -> Unit, modifier: Modifier = Modifier) {
    val colors = MimosTheme.colors
    Column(modifier.fillMaxWidth()) {
        Rule()
        Row(Modifier.fillMaxWidth()) {
            for (tab in Destination.tabs) {
                val active = tab == selected
                Box(
                    Modifier.weight(1f)
                        .height(MimosTheme.spacing.tabHeight)
                        .semantics { this.selected = active }
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(tab) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (active) {
                        Box(
                            Modifier.align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .height(MimosTheme.spacing.ruleThickness)
                                .background(colors.accent)
                        )
                    }
                    Text(
                        tab.label,
                        style = MimosTheme.typography.tab,
                        color = if (active) colors.fg else colors.muted,
                    )
                }
            }
        }
    }
}
