package io.github.noahhhx.mimos.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalUriHandler
import io.github.noahhhx.mimos.instance.APP_VERSION
import io.github.noahhhx.mimos.instance.RELEASES_URL
import io.github.noahhhx.mimos.instance.Version
import io.github.noahhhx.mimos.ui.Button
import io.github.noahhhx.mimos.ui.ButtonKind
import io.github.noahhhx.mimos.ui.EntryPage
import io.github.noahhhx.mimos.ui.MimosTheme
import io.github.noahhhx.mimos.ui.Rule
import io.github.noahhhx.mimos.ui.Text

/** The connected server no longer supports this app: nothing else works until it is updated. */
@Composable
fun UpdateRequired(
    brandMark: Painter,
    webOrigin: String,
    minAppVersion: Version,
    onChangeInstance: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    EntryPage(brandMark, "Update the app") {
        Text(
            "$webOrigin needs version $minAppVersion of the app or later. " +
                "This is version $APP_VERSION."
        )
        Button(
            "Get the new app",
            onClick = { uriHandler.openUri(RELEASES_URL) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Install it over this one, and you stay signed in.",
            style = MimosTheme.typography.small,
            color = MimosTheme.colors.muted,
        )
        Button(
            "Change instance",
            onClick = onChangeInstance,
            kind = ButtonKind.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** The server runs a newer Mimos than this app, so a newer app is out. */
@Composable
internal fun NewerAppNotice(serverVersion: Version, onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val spacing = MimosTheme.spacing
    Column(
        Modifier.fillMaxWidth()
            .background(MimosTheme.colors.surface)
            .padding(horizontal = spacing.page, vertical = spacing.m),
        verticalArrangement = Arrangement.spacedBy(spacing.s),
    ) {
        Text("A newer app is available.")
        Text(
            "Your server runs Mimos $serverVersion. This app is $APP_VERSION.",
            style = MimosTheme.typography.small,
            color = MimosTheme.colors.muted,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
            Button("Get the new app", onClick = { uriHandler.openUri(RELEASES_URL) }, small = true)
            Button("Not now", onClick = onDismiss, kind = ButtonKind.Secondary, small = true)
        }
    }
    Rule()
}
