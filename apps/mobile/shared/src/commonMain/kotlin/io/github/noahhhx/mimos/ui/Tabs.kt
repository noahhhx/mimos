package io.github.noahhhx.mimos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The web's `.tabs`: a row of labels over a `line` rule, the selected one in `fg` with a 2dp amber
 * underline. For the choices on one page; the app's own navigation is the tab bar.
 */
@Composable
fun Tabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MimosTheme.colors
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { index, label ->
                val active = index == selected
                Box(
                    Modifier.weight(1f)
                        .semantics { this.selected = active }
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        )
                        .padding(vertical = MimosTheme.spacing.m),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MimosTheme.typography.tab,
                        color = if (active) colors.fg else colors.muted,
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth()) {
            Rule(Modifier.align(Alignment.BottomCenter))
            Row(Modifier.fillMaxWidth()) {
                labels.indices.forEach { index ->
                    Box(
                        Modifier.weight(1f)
                            .height(MimosTheme.spacing.ruleThickness)
                            .background(if (index == selected) colors.accent else Color.Transparent)
                    )
                }
            }
        }
    }
}

/**
 * [Tabs] for more labels than fit a phone: each as wide as its label, the row scrolling sideways
 * inside [contentPadding], the rule under the whole width. The selected label is scrolled into view
 * at once, without motion (ADR-0008).
 */
@Composable
fun ScrollingTabs(
    labels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: Dp = 0.dp,
) {
    val colors = MimosTheme.colors
    val scroll = rememberScrollState()
    val starts = remember { mutableStateMapOf<Int, Int>() }
    val inset = with(LocalDensity.current) { contentPadding.roundToPx() }
    LaunchedEffect(selected, starts[selected]) {
        starts[selected]?.let { scroll.scrollTo((it - inset).coerceAtLeast(0)) }
    }
    Box(modifier.fillMaxWidth()) {
        Rule(Modifier.align(Alignment.BottomCenter))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = contentPadding),
            horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.xl),
        ) {
            labels.forEachIndexed { index, label ->
                val active = index == selected
                Column(
                    Modifier.width(IntrinsicSize.Max)
                        .onPlaced { starts[index] = it.positionInParent().x.roundToInt() }
                        .semantics { this.selected = active }
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        )
                ) {
                    Text(
                        label,
                        Modifier.padding(vertical = MimosTheme.spacing.m),
                        style = MimosTheme.typography.tab,
                        color = if (active) colors.fg else colors.muted,
                        maxLines = 1,
                    )
                    Box(
                        Modifier.fillMaxWidth()
                            .height(MimosTheme.spacing.ruleThickness)
                            .background(if (active) colors.accent else Color.Transparent)
                    )
                }
            }
        }
    }
}
