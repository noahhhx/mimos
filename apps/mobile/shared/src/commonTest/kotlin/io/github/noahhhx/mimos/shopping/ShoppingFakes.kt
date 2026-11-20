package io.github.noahhhx.mimos.shopping

import io.github.noahhhx.mimos.client.NetworkWatcher
import kotlinx.coroutines.flow.MutableStateFlow

/** What a phone keeps between launches, in memory, so a second model can read the first's. */
class MemoryShoppingStore : ShoppingStore {
    val entries = mutableMapOf<ShoppingStore.Entry, String>()

    override fun load(entry: ShoppingStore.Entry): String? = entries[entry]

    override fun save(entry: ShoppingStore.Entry, value: String?) {
        if (value == null) entries.remove(entry) else entries[entry] = value
    }
}

class FakeNetwork(online: Boolean = true) : NetworkWatcher {
    override val online = MutableStateFlow(online)
}
