package io.github.noahhhx.mimos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp

enum class ButtonKind {
    /** Filled amber: the page's main action. */
    Primary,
    /** Outlined in `line`. */
    Secondary,
    /** Outlined in `danger`, filled when pressed. */
    Danger,
}

/**
 * The web's `.button`. Pressing deepens it, as hovering does on the web; nothing animates.
 * [description] names it for a screen reader when [label] alone is ambiguous ("Remove" on every
 * row). A non-null [pressed] makes it a toggle, as the web's `aria-pressed`: its state is announced
 * and read as checked.
 */
@Composable
fun Button(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    small: Boolean = false,
    enabled: Boolean = true,
    description: String? = null,
    pressed: Boolean? = null,
) {
    val colors = MimosTheme.colors
    val spacing = MimosTheme.spacing
    val source = remember { MutableInteractionSource() }
    val held by source.collectIsPressedAsState()
    val deeper = lerp(colors.accent, colors.fg, 0.18f)
    val look =
        when (kind) {
            ButtonKind.Primary ->
                ButtonLook(
                    if (held) deeper else colors.accent,
                    if (held) deeper else colors.accent,
                    colors.onAccent,
                )
            ButtonKind.Secondary ->
                ButtonLook(
                    Color.Transparent,
                    if (held) colors.accent else colors.line,
                    colors.fg,
                )
            ButtonKind.Danger ->
                ButtonLook(
                    if (held) colors.danger else Color.Transparent,
                    colors.danger,
                    if (held) colors.onAccent else colors.danger,
                )
        }
    Box(
        modifier
            .then(
                if (description == null) Modifier
                else Modifier.semantics { contentDescription = description }
            )
            .alpha(if (enabled) 1f else 0.45f)
            .defaultMinSize(
                minHeight = if (small) spacing.buttonHeightSmall else spacing.buttonHeight,
                minWidth = if (small) spacing.buttonHeightSmall else Dp.Unspecified,
            )
            .background(look.fill)
            .border(spacing.hairline, look.border)
            .then(
                if (pressed == null)
                    Modifier.clickable(
                        interactionSource = source,
                        indication = null,
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                    )
                else
                    Modifier.toggleable(
                        value = pressed,
                        interactionSource = source,
                        indication = null,
                        enabled = enabled,
                        role = Role.Checkbox,
                        onValueChange = { onClick() },
                    )
            )
            .padding(horizontal = if (small) spacing.buttonPaddingSmall else spacing.buttonPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = if (small) MimosTheme.typography.buttonSmall else MimosTheme.typography.button,
            color = look.text,
        )
    }
}

private class ButtonLook(val fill: Color, val border: Color, val text: Color)
