package io.github.noahhhx.mimos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.noahhhx.mimos.connect.ConnectModel
import io.github.noahhhx.mimos.connect.ConnectScreen
import io.github.noahhhx.mimos.instance.InstanceStore
import io.github.noahhhx.mimos.instance.discover
import io.ktor.client.engine.HttpClientEngine

const val APP_NAME = "Mimos"

@Composable
fun App(store: InstanceStore, engine: HttpClientEngine, allowLocalhostHttp: Boolean) {
    val model = remember { ConnectModel(store) { discover(it, engine, allowLocalhostHttp) } }
    ConnectScreen(model)
}
