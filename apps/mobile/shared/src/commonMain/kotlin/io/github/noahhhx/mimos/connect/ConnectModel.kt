package io.github.noahhhx.mimos.connect

import io.github.noahhhx.mimos.instance.Discovery
import io.github.noahhhx.mimos.instance.Instance
import io.github.noahhhx.mimos.instance.InstanceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the connect screen is. */
sealed interface ConnectState {
    /** Not connected: the address form shows [text]. */
    sealed interface Addressing : ConnectState {
        val text: String
    }

    data class Editing(override val text: String) : Addressing

    data class Connecting(override val text: String) : Addressing

    data class Failed(override val text: String, val failure: Discovery.Failure) : Addressing

    data class Connected(val instance: Instance) : ConnectState
}

/** The connect screen: type a web address, connect to it, and disconnect again. */
class ConnectModel(
    private val store: InstanceStore,
    private val discover: suspend (String) -> Discovery,
) {
    private val mutableState =
        MutableStateFlow(store.load()?.let(ConnectState::Connected) ?: ConnectState.Editing(""))
    val state: StateFlow<ConnectState> = mutableState.asStateFlow()

    fun edit(text: String) {
        when (state.value) {
            is ConnectState.Editing,
            is ConnectState.Failed -> mutableState.value = ConnectState.Editing(text)
            is ConnectState.Connecting,
            is ConnectState.Connected -> Unit
        }
    }

    suspend fun connect() {
        val text =
            when (val current = state.value) {
                is ConnectState.Editing -> current.text
                is ConnectState.Failed -> current.text
                is ConnectState.Connecting,
                is ConnectState.Connected -> return
            }
        mutableState.value = ConnectState.Connecting(text)
        mutableState.value =
            when (val outcome = discover(text)) {
                is Discovery.Connected -> {
                    store.save(outcome.instance)
                    ConnectState.Connected(outcome.instance)
                }
                is Discovery.Failure -> ConnectState.Failed(text, outcome)
            }
    }

    fun disconnect() {
        val current = state.value as? ConnectState.Connected ?: return
        store.clear()
        mutableState.value = ConnectState.Editing(current.instance.config.webUrl.origin)
    }
}
