package io.github.noahhhx.mimos.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment

/**
 * A window over the whole screen with no enter or exit animation (the design has no motion), and
 * [content] placed at [alignment]. [onDismiss] runs for the back button. Dialogs and sheets build
 * on it; the platform provides the window.
 */
@Composable
internal expect fun Overlay(
    onDismiss: () -> Unit,
    alignment: Alignment,
    content: @Composable BoxScope.() -> Unit,
)
