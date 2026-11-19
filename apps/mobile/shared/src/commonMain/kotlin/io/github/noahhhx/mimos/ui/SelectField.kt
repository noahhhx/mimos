package io.github.noahhhx.mimos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

/**
 * The web's `<select>`: a labelled box, styled as a [TextField], showing the chosen option's label.
 * Tapping it lists [options] in a [BottomSheet], the chosen one marked "Selected". Like a
 * [TextField], it carries [description] (or [label]) as its accessibility name.
 */
@Composable
fun <T> SelectField(
    label: String,
    selected: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    val colors = MimosTheme.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var open by remember { mutableStateOf(false) }
    val name = description ?: label
    val shown = optionLabel(selected)
    Column(modifier.fillMaxWidth()) {
        Label(label, Modifier.clearAndSetSemantics {})
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth()
                .semantics {
                    contentDescription = name
                    stateDescription = shown
                }
                .background(if (pressed) colors.hover else colors.surface)
                .border(MimosTheme.spacing.hairline, colors.line)
                .clickable(
                    interactionSource = source,
                    indication = null,
                    enabled = enabled,
                    role = Role.DropdownList,
                    onClick = { open = true },
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(shown, Modifier.weight(1f), style = MimosTheme.typography.input)
            // Drawn, not the "▾" glyph: no font here has it, and the fallback's taller line would
            // make this box taller than a TextField beside it.
            Box(
                Modifier.size(10.dp, 6.dp).drawBehind {
                    drawPath(
                        Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width / 2, size.height)
                            close()
                        },
                        colors.muted,
                    )
                }
            )
        }
    }
    if (open) {
        BottomSheet(onDismiss = { open = false }) {
            Label(label, Modifier.padding(horizontal = MimosTheme.spacing.page, vertical = 12.dp))
            for (option in options) {
                ListRow(
                    optionLabel(option),
                    trailing = if (option == selected) "Selected" else null,
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}
