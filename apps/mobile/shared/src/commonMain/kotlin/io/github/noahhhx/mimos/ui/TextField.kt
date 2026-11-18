package io.github.noahhhx.mimos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp

/**
 * A labelled single-line field: a `line` border that turns `accent` while focused, and an optional
 * error under it in `danger`. Keep one call site per field so it keeps its focus when [error] comes
 * and goes.
 */
@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val colors = MimosTheme.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Column(modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f)) {
        Label(label)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = MimosTheme.typography.input.copy(color = colors.fg),
            cursorBrush = SolidColor(colors.accent),
            interactionSource = source,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            modifier =
                Modifier.fillMaxWidth()
                    .background(colors.surface)
                    .border(
                        MimosTheme.spacing.hairline,
                        if (focused) colors.accent else colors.line,
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
        )
        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text(error, style = MimosTheme.typography.small, color = colors.danger)
        }
    }
}
