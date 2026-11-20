package io.github.noahhhx.mimos

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import io.github.noahhhx.mimos.client.NetworkWatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Online while the phone has a default network. Not `NET_CAPABILITY_VALIDATED`: a home instance on
 * a network with no internet must count as online.
 */
class AndroidNetworkWatcher(context: Context) : NetworkWatcher {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    private val mutableOnline = MutableStateFlow(connectivity.activeNetwork != null)
    override val online: StateFlow<Boolean> = mutableOnline.asStateFlow()

    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                mutableOnline.value = true
            }

            override fun onLost(network: Network) {
                mutableOnline.value = false
            }
        }

    init {
        connectivity.registerDefaultNetworkCallback(callback)
    }

    fun close() = connectivity.unregisterNetworkCallback(callback)
}
