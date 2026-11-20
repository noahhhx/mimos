package io.github.noahhhx.mimos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/**
 * One of a set of choices, as the web's radio buttons: a square box in `line`, filled with `accent`
 * when [selected], beside its label. The whole row is the target.
 */
@Composable
fun Choice(label: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MimosTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                interactionSource = null,
                indication = null,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(vertical = MimosTheme.spacing.s),
        horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(18.dp)
                .border(MimosTheme.spacing.hairline, if (selected) colors.accent else colors.line)
                .padding(4.dp)
                .background(if (selected) colors.accent else colors.surface)
        )
        Text(label)
    }
}
