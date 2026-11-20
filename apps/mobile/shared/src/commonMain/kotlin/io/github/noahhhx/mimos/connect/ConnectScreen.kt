package io.github.noahhhx.mimos.connect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import io.github.noahhhx.mimos.ui.Button
import io.github.noahhhx.mimos.ui.EntryPage
import io.github.noahhhx.mimos.ui.MimosTheme
import io.github.noahhhx.mimos.ui.Text
import io.github.noahhhx.mimos.ui.TextField

@Composable
fun ConnectScreen(model: ConnectModel, brandMark: Painter, connect: () -> Unit) {
    val state by model.state.collectAsState()
    EntryPage(brandMark, "Connect to Mimos") {
        when (val current = state) {
            is ConnectState.Connected -> Unit
            is ConnectState.Addressing -> {
                // One call site for every state, so the field keeps its focus and cursor
                // when a failure turns back into editing.
                val connecting = current is ConnectState.Connecting
                val failure = (current as? ConnectState.Failed)?.failure
                Text(
                    "The address you open Mimos at in a browser, such as mimos.example.com.",
                    color = MimosTheme.colors.muted,
                )
                TextField(
                    value = current.text,
                    onValueChange = model::edit,
                    label = "Web address",
                    error = failure?.message(),
                    enabled = !connecting,
                    keyboardOptions =
                        KeyboardOptions(
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Uri,
                            imeAction = ImeAction.Go,
                        ),
                    keyboardActions = KeyboardActions(onGo = { connect() }),
                )
                if (connecting) Text("Connecting…", color = MimosTheme.colors.muted)
                Button(
                    "Connect",
                    onClick = connect,
                    enabled = !connecting,
                    modifier = Modifier.fillMaxWidth(),
                )
                failure?.helpLink()?.let { help ->
                    val uriHandler = LocalUriHandler.current
                    Text(
                        help.label,
                        style =
                            MimosTheme.typography.body.copy(
                                textDecoration = TextDecoration.Underline
                            ),
                        modifier =
                            Modifier.clickable(role = Role.Button) {
                                uriHandler.openUri(help.url)
                            },
                    )
                }
            }
        }
    }
}
