package io.github.noahhhx.mimos

import io.github.noahhhx.mimos.connect.ConnectModel
import io.github.noahhhx.mimos.connect.ConnectState
import io.github.noahhhx.mimos.instance.Instance
import io.github.noahhhx.mimos.session.SessionModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What the app keeps while it is open: the connection, and the session of the connected instance. A
 * platform holds one across screen rotations, so a connect or a sign-in in flight is not lost.
 */
class AppModel(
    private val scope: CoroutineScope,
    val connect: ConnectModel,
    private val createSession: (Instance) -> SessionModel,
) {
    private var current: Pair<Instance, SessionModel>? = null

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

    /** Signs out of the connected instance, then forgets it so another can be typed in. */
    fun changeInstance() {
        val connected = connect.state.value as? ConnectState.Connected ?: return
        session(connected.instance).signOut()
        current = null
        connect.disconnect()
    }
}
