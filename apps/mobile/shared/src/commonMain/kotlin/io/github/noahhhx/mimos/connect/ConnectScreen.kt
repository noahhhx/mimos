package io.github.noahhhx.mimos.connect

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.noahhhx.mimos.APP_NAME
import io.github.noahhhx.mimos.instance.InsecureAddress
import kotlinx.coroutines.launch

@Composable
fun ConnectScreen(model: ConnectModel) {
    val state by model.state.collectAsState()
    val scope = rememberCoroutineScope()
    val connect: () -> Unit = { scope.launch { model.connect() } }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val title = if (state is ConnectState.Connected) APP_NAME else "Connect to Mimos"
        BasicText(title, style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold))
        when (val current = state) {
            is ConnectState.Connected -> {
                BasicText("Connected to ${current.instance.config.webUrl.origin}")
                BasicText("Server version ${current.instance.serverVersion}")
                Action("Disconnect", onClick = model::disconnect)
            }
            is ConnectState.Addressing -> {
                // One call site for every state, so the field keeps its focus and cursor
                // when a failure turns back into editing.
                val connecting = current is ConnectState.Connecting
                AddressForm(current.text, model::edit, connect, enabled = !connecting)
                if (connecting) BasicText("Connecting…")
                if (current is ConnectState.Failed) {
                    BasicText(current.failure.message())
                    if (current.failure == InsecureAddress) {
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
private fun Action(label: String, onClick: () -> Unit, enabled: Boolean = true) {
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
