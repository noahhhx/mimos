package io.github.noahhhx.mimos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import io.github.noahhhx.mimos.connect.ConnectModel
import io.github.noahhhx.mimos.connect.ConnectScreen
import io.github.noahhhx.mimos.instance.InstanceStore
import io.github.noahhhx.mimos.instance.discover
import io.ktor.client.engine.HttpClientEngine

const val APP_NAME = "Mimos"

/**
 * [brandMark] is the chef duck, the web app's icon, drawn beside the app's name. The platform
 * supplies it from its own resources.
 */
@Composable
fun App(
    store: InstanceStore,
    engine: HttpClientEngine,
    allowLocalhostHttp: Boolean,
    brandMark: Painter,
) {
    val model = remember { ConnectModel(store) { discover(it, engine, allowLocalhostHttp) } }
    ConnectScreen(model, brandMark)
}
