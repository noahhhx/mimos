package io.github.noahhhx.mimos.ui

class MemoryThemeStore(var stored: ThemeChoice? = null) : ThemeStore {
    override fun load() = stored

    override fun save(choice: ThemeChoice) {
        stored = choice
    }
}
