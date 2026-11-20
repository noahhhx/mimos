package io.github.noahhhx.mimos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Every page starts with this: an optional eyebrow, the serif title, and the short amber rule, with
 * the page's actions under the rule on a phone.
 */
@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val spacing = MimosTheme.spacing
    Column(modifier.fillMaxWidth().padding(bottom = 28.dp)) {
        if (eyebrow != null) {
            Label(eyebrow)
            Spacer(Modifier.height(spacing.s))
        }
        Text(title, style = MimosTheme.typography.title)
        Spacer(Modifier.height(22.dp))
        Box(
            Modifier.size(spacing.ruleWidth, spacing.ruleThickness)
                .background(MimosTheme.colors.accent)
        )
        if (actions != null) {
            Row(
                Modifier.padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(spacing.s),
                content = actions,
            )
        }
    }
}
