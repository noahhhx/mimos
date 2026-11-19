package io.github.noahhhx.mimos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * A labelled field: a `line` border that turns `accent` while focused, a muted [placeholder] while
 * empty, and an optional error under it in `danger`. The field itself carries [description] (or
 * [label]) as its accessibility name, so a screen reader or a flow finds it by name, which matters
 * when several fields share a visible label ("Amount" on every ingredient). Keep one call site per
 * field so it keeps its focus when [error] comes and goes. [minLines] above 1 makes it multi-line;
 * [maxLength] stops typing at that many characters. [password] hides what is typed and keeps the
 * keyboard from learning it.
 */
@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    placeholder: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    minLines: Int = 1,
    maxLength: Int? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    password: Boolean = false,
) {
    val colors = MimosTheme.colors
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val name = description ?: label
    Column(modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f)) {
        Label(label, Modifier.clearAndSetSemantics {})
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(if (maxLength == null) it else it.take(maxLength)) },
            enabled = enabled,
            singleLine = minLines == 1,
            minLines = minLines,
            textStyle = MimosTheme.typography.input.copy(color = colors.fg),
            cursorBrush = SolidColor(colors.accent),
            interactionSource = source,
            visualTransformation =
                if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions =
                if (password) {
                    keyboardOptions.copy(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false,
                    )
                } else keyboardOptions,
            keyboardActions = keyboardActions,
            modifier =
                Modifier.fillMaxWidth()
                    .semantics { contentDescription = name }
                    .background(colors.surface)
                    .border(
                        MimosTheme.spacing.hairline,
                        if (focused) colors.accent else colors.line,
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            decorationBox = { field ->
                Box {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            placeholder,
                            Modifier.clearAndSetSemantics {},
                            style = MimosTheme.typography.input,
                            color = colors.muted,
                        )
                    }
                    field()
                }
            },
        )
        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text(error, style = MimosTheme.typography.small, color = colors.danger)
        }
    }
}
