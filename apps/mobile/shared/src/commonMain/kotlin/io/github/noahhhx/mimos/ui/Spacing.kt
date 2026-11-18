package io.github.noahhhx.mimos.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The web's measurements (`globals.css`), in dp. */
data class MimosSpacing(
    val hairline: Dp = 1.dp,
    val ruleWidth: Dp = 64.dp,
    val ruleThickness: Dp = 2.dp,
    val noticeEdge: Dp = 3.dp,
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    /** The gutter either side of a page. */
    val page: Dp = 16.dp,
    val buttonHeight: Dp = 42.dp,
    val buttonHeightSmall: Dp = 30.dp,
    val buttonPadding: Dp = 18.dp,
    val buttonPaddingSmall: Dp = 8.dp,
    val iconToggle: Dp = 34.dp,
    val headerHeight: Dp = 64.dp,
    val tabHeight: Dp = 52.dp,
)
