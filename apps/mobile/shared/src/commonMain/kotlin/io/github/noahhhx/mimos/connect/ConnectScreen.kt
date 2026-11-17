package io.github.noahhhx.mimos.connect

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ConnectScreen(model: ConnectModel, brandMark: Painter, connect: () -> Unit) {
    val state by model.state.collectAsState()
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Decorative, as on the web: the title beside it names the app.
            Image(brandMark, contentDescription = null, modifier = Modifier.size(40.dp))
            BasicText(
                "Connect to Mimos",
                style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
            )
        }
        when (val current = state) {
            is ConnectState.Connected -> Unit
            is ConnectState.Addressing -> {
                // One call site for every state, so the field keeps its focus and cursor
                // when a failure turns back into editing.
                val connecting = current is ConnectState.Connecting
                AddressForm(current.text, model::edit, connect, enabled = !connecting)
                if (connecting) BasicText("Connecting…")
                if (current is ConnectState.Failed) {
                    BasicText(current.failure.message())
                    if (current.failure.needsHttpsLink()) {
                        val uriHandler = LocalUriHandler.current
                        Link("Why Mimos needs HTTPS") { uriHandler.openUri(WHY_HTTPS_URL) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddressForm(
    text: String,
    onEdit: (String) -> Unit,
    onConnect: () -> Unit,
    enabled: Boolean = true,
) {
    BasicText("Web address, as you open Mimos in a browser")
    BasicTextField(
        value = text,
        onValueChange = onEdit,
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(fontSize = 18.sp),
        keyboardOptions =
            KeyboardOptions(
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
            ),
        keyboardActions = KeyboardActions(onGo = { onConnect() }),
        modifier = Modifier.fillMaxWidth().border(1.dp, Color.Black).padding(12.dp),
    )
    Action("Connect", onClick = onConnect, enabled = enabled)
}

@Composable
internal fun Action(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    BasicText(
        label,
        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
        modifier =
            Modifier.clickable(enabled = enabled, onClick = onClick)
                .border(1.dp, Color.Black)
                .padding(12.dp),
    )
}

@Composable
private fun Link(label: String, onClick: () -> Unit) {
    BasicText(
        label,
        style = TextStyle(textDecoration = TextDecoration.Underline),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
