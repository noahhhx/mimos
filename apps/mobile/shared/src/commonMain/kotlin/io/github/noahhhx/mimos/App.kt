package io.github.noahhhx.mimos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import io.github.noahhhx.mimos.api.apis.MeApi
import io.github.noahhhx.mimos.client.MimosHttp
import io.github.noahhhx.mimos.connect.ConnectScreen
import io.github.noahhhx.mimos.connect.ConnectState
import io.github.noahhhx.mimos.session.SessionScreen
import io.ktor.client.engine.HttpClientEngine

const val APP_NAME = "Mimos"

/**
 * [brandMark] is the chef duck, the web app's icon, drawn beside the app's name. The platform
 * supplies it from its own resources.
 */
@Composable
fun App(model: AppModel, engine: HttpClientEngine, brandMark: Painter) {
    when (val connected = model.connect.state.collectAsState().value) {
        is ConnectState.Addressing -> ConnectScreen(model.connect, brandMark, model::connect)
        is ConnectState.Connected -> {
            val instance = connected.instance
            val session = remember(instance) { model.session(instance) }
            val http =
                remember(instance) {
                    MimosHttp(engine, instance.config.apiUrl, session::accessToken)
                }
            DisposableEffect(http) { onDispose(http::close) }
            SessionScreen(
                session,
                instance,
                brandMark,
                whoAmI = { http.api(::MeApi).getMe().body().displayName },
                onChangeInstance = model::changeInstance,
            )
        }
    }
}
