package io.github.noahhhx.mimos.client

import kotlinx.coroutines.flow.StateFlow

/**
 * Whether the phone has a network at all (ADR-0034). Only a hint: a network does not prove the
 * instance answers, and a home instance on a network with no internet still counts.
 */
interface NetworkWatcher {
    val online: StateFlow<Boolean>
}
