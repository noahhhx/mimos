package io.github.noahhhx.mimos

import io.github.noahhhx.mimos.connect.ConnectModel
import io.github.noahhhx.mimos.connect.ConnectState
import io.github.noahhhx.mimos.instance.Instance
import io.github.noahhhx.mimos.session.SessionModel
import io.github.noahhhx.mimos.shell.ShellModel
import io.github.noahhhx.mimos.ui.ThemeModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the app keeps while it is open: the theme, the connection, the session of the connected
 * instance, and where the person is in the app. A platform holds one across screen rotations, so a
 * connect or a sign-in in flight is not lost. [debug] builds may open the component gallery.
 */
class AppModel(
    private val scope: CoroutineScope,
    val connect: ConnectModel,
    val theme: ThemeModel,
    val debug: Boolean,
    private val createSession: (Instance) -> SessionModel,
) {
    val shell = ShellModel()

    private var current: Pair<Instance, SessionModel>? = null

    private val galleryOpen = MutableStateFlow(false)
    val gallery: StateFlow<Boolean> = galleryOpen.asStateFlow()

    fun connect() {
        scope.launch { connect.connect() }
    }

    fun session(instance: Instance): SessionModel {
        current
            ?.takeIf { it.first == instance }
            ?.let {
                return it.second
            }
        return createSession(instance).also { current = instance to it }
    }

    fun signOut() {
        current?.second?.signOut()
        shell.reset()
    }

    /** Signs out of the connected instance, then forgets it so another can be typed in. */
    fun changeInstance() {
        val connected = connect.state.value as? ConnectState.Connected ?: return
        session(connected.instance).signOut()
        current = null
        shell.reset()
        connect.disconnect()
    }

    /** Release builds never open it. */
    fun openGallery() {
        if (debug) galleryOpen.value = true
    }

    fun closeGallery() {
        galleryOpen.value = false
    }
}
