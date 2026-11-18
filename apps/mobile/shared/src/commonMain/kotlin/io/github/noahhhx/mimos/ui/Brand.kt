package io.github.noahhhx.mimos.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import io.github.noahhhx.mimos.APP_NAME

/** The chef duck, the web app's icon, beside the name in the serif. */
@Composable
fun Brand(mark: Painter, modifier: Modifier = Modifier) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Decorative, as on the web: the name beside it says what it is.
        Image(mark, contentDescription = null, modifier = Modifier.size(32.dp))
        Text(APP_NAME, style = MimosTheme.typography.brand)
    }
}
