package io.github.noahhhx.mimos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** The web's dialogs and sheets: a `surface` panel with a 2dp amber top edge, over `bg` at 80%. */
@Composable
private fun Panel(
    modifier: Modifier,
    padding: PaddingValues,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MimosTheme.colors
    Column(
        modifier
            // Taps on the panel must not reach the scrim, which dismisses.
            .pointerInput(Unit) { detectTapGestures {} }
            .background(colors.surface)
            .border(MimosTheme.spacing.hairline, colors.line)
            .drawBehind {
                drawRect(colors.accent, Offset.Zero, Size(size.width, 2.dp.toPx()))
            }
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.m),
        content = content,
    )
}

/** A modal over the screen: [title], [content], and [actions] along the bottom, right-aligned. */
@Composable
fun MimosDialog(
    title: String,
    onDismiss: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Overlay(onDismiss, Alignment.Center) {
        Panel(
            Modifier.padding(MimosTheme.spacing.l).widthIn(max = 512.dp).fillMaxWidth(),
            PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        ) {
            Text(title, style = MimosTheme.typography.heading)
            content()
            Row(
                Modifier.fillMaxWidth().padding(top = MimosTheme.spacing.s),
                horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s, Alignment.End),
                content = actions,
            )
        }
    }
}

/**
 * A panel over the bottom of the screen, as the web's inspector is on a phone. Its content runs
 * edge to edge: rows draw their own gutters.
 */
@Composable
fun BottomSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Overlay(onDismiss, Alignment.BottomCenter) {
        Panel(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .fillMaxWidth(),
            PaddingValues(top = 2.dp),
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                content()
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing))
            }
        }
    }
}
