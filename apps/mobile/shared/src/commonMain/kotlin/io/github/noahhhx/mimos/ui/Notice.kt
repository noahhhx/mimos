package io.github.noahhhx.mimos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class NoticeKind {
    /** `danger`: something failed. */
    Error,
    /** `accent`: something worked. */
    Ok,
}

/** The web's `.card.error` and `.card.ok`: a `surface` panel with a 3dp rule at its left. */
@Composable
fun Notice(message: String, modifier: Modifier = Modifier, kind: NoticeKind = NoticeKind.Error) {
    val colors = MimosTheme.colors
    val edge = if (kind == NoticeKind.Error) colors.danger else colors.accent
    val edgeWidth = MimosTheme.spacing.noticeEdge
    Box(
        modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
            .background(colors.surface)
            .drawBehind { drawRect(edge, size = Size(edgeWidth.toPx(), size.height)) }
            .padding(start = edgeWidth + 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
    ) {
        Text(
            message,
            style = MimosTheme.typography.body.copy(fontWeight = FontWeight.Medium),
            color = if (kind == NoticeKind.Error) colors.danger else colors.fg,
        )
    }
}
