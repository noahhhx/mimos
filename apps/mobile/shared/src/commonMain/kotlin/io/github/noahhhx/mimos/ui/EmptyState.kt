package io.github.noahhhx.mimos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A `surface` card saying there is nothing here, and what a person can do about it. */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = MimosTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.surface)
            .border(MimosTheme.spacing.hairline, colors.line)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s),
    ) {
        Text(title, style = MimosTheme.typography.heading)
        Text(message, color = colors.muted)
        if (action != null) {
            Column(Modifier.padding(top = MimosTheme.spacing.s)) { action() }
        }
    }
}

/** What a failed load shows: the problem in `danger`, and a way to try again. */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    retryLabel: String = "Try again",
) {
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.m),
    ) {
        Notice(message)
        Button(retryLabel, onRetry, kind = ButtonKind.Secondary)
    }
}
