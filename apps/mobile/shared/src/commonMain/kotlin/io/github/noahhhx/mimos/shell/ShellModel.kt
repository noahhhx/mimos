package io.github.noahhhx.mimos.shell

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where the signed-in person is: the destination on screen, and whether the profile menu is open.
 */
class ShellModel {
    private val mutableDestination = MutableStateFlow(Destination.home)
    val destination: StateFlow<Destination> = mutableDestination.asStateFlow()

    private val mutableProfileOpen = MutableStateFlow(false)
    val profileOpen: StateFlow<Boolean> = mutableProfileOpen.asStateFlow()

    /** Goes to [to], closing the profile menu if that is where the person chose it. */
    fun go(to: Destination) {
        mutableDestination.value = to
        mutableProfileOpen.value = false
    }

    fun openProfile() {
        mutableProfileOpen.value = true
    }

    fun closeProfile() {
        mutableProfileOpen.value = false
    }

    /** Back to the start, for when the person signs out and someone else may sign in. */
    fun reset() {
        mutableDestination.value = Destination.home
        mutableProfileOpen.value = false
    }
}
