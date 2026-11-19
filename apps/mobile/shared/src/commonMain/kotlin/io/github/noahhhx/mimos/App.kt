package io.github.noahhhx.mimos

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import io.github.noahhhx.mimos.api.apis.InstanceApi
import io.github.noahhhx.mimos.api.apis.MeApi
import io.github.noahhhx.mimos.connect.ConnectScreen
import io.github.noahhhx.mimos.connect.ConnectState
import io.github.noahhhx.mimos.gallery.Gallery
import io.github.noahhhx.mimos.instance.AppStanding
import io.github.noahhhx.mimos.instance.InstanceStatus
import io.github.noahhhx.mimos.instance.checkInstance
import io.github.noahhhx.mimos.session.SessionScreen
import io.github.noahhhx.mimos.settings.OpenSource
import io.github.noahhhx.mimos.shell.AppShell
import io.github.noahhhx.mimos.shell.Profile
import io.github.noahhhx.mimos.shell.ShellActions
import io.github.noahhhx.mimos.shell.UpdateRequired
import io.github.noahhhx.mimos.ui.MimosTheme
import kotlinx.coroutines.CancellationException

const val APP_NAME = "Mimos"

/**
 * [brandMark] is the chef duck, the web app's icon, drawn beside the app's name. The platform
 * supplies it from its own resources, [share] opens its share sheet with a text, and [licences]
 * lists what the app is built from.
 */
@Composable
fun App(
    model: AppModel,
    brandMark: Painter,
    share: (String) -> Unit,
    licences: List<OpenSource>,
) {
    val theme by model.theme.state.collectAsState()
    val gallery by model.gallery.collectAsState()
    MimosTheme(theme) {
        Box(Modifier.fillMaxSize().background(MimosTheme.colors.bg)) {
            if (gallery) {
                Gallery(model.theme, brandMark, onClose = model::closeGallery)
            } else {
                Connection(model, brandMark, share, licences)
            }
        }
    }
}

@Composable
private fun Connection(
    model: AppModel,
    brandMark: Painter,
    share: (String) -> Unit,
    licences: List<OpenSource>,
) {
    when (val connected = model.connect.state.collectAsState().value) {
        is ConnectState.Addressing -> ConnectScreen(model.connect, brandMark, model::connect)
        is ConnectState.Connected -> {
            val instance = connected.instance
            val session = remember(instance) { model.session(instance) }
            val http = remember(instance) { model.http(instance) }
            // What the app knew from connecting, until the instance says again: it may have been
            // upgraded since.
            val status by
                produceState(InstanceStatus.remembered(instance), instance) {
                    checkInstance(http.api(::InstanceApi))?.let { value = it }
                }
            val standing = status.standing
            if (standing is AppStanding.Unsupported) {
                UpdateRequired(
                    brandMark,
                    instance.config.webUrl.origin,
                    standing.minAppVersion,
                    onChangeInstance = model::changeInstance,
                )
                return
            }
            val dismissed by model.dismissedNewerApp.collectAsState()
            val newerApp =
                (standing as? AppStanding.NewerAvailable)?.serverVersion?.takeIf { it != dismissed }
            SessionScreen(
                session,
                instance,
                brandMark,
                onSignOut = model::signOut,
                onChangeInstance = model::changeInstance,
            ) {
                // Loaded here, not in a model: a rotation asks again, which costs one small call
                // and can never show the last person's name to the next.
                val profile by
                    produceState<Profile>(Profile.Loading, instance) {
                        value =
                            try {
                                val me = http.api(::MeApi).getMe().body()
                                Profile.Known(me.displayName, me.createdAt)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                Profile.Failed(failure.message ?: "no answer")
                            }
                    }
                AppShell(
                    shell = model.shell,
                    recipes = model.recipes,
                    plan = model.plan,
                    household = model.household,
                    account = model.account,
                    theme = model.theme,
                    profile = profile,
                    webOrigin = instance.config.webUrl.origin,
                    serverVersion = status.serverVersion,
                    newerApp = newerApp,
                    licences = licences,
                    brandMark = brandMark,
                    actions =
                        ShellActions(
                            onSignOut = model::signOut,
                            onChangeInstance = model::changeInstance,
                            onOpenRecipe = model::openRecipe,
                            onShare = share,
                            onSwitchInstance = model::switchInstance,
                            onDismissNewerApp = { newerApp?.let(model::dismissNewerApp) },
                            onOpenGallery = if (model.debug) model::openGallery else null,
                        ),
                )
            }
        }
    }
}
