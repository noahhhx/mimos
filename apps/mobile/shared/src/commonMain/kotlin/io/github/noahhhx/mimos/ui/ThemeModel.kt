package io.github.noahhhx.mimos.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Keeps the chosen theme on this install. Never the system's setting (ADR-0021). */
interface ThemeStore {
    fun load(): ThemeChoice?

    fun save(choice: ThemeChoice)
}

/** The theme in force: the stored choice, else light. Every change is stored. */
class ThemeModel(private val store: ThemeStore) {
    private val mutableState = MutableStateFlow(store.load() ?: ThemeChoice.Light)
    val state: StateFlow<ThemeChoice> = mutableState.asStateFlow()

    /** The theme sent to Keycloak's pages as `mimos_theme`, so they match the app. */
    val authTheme: String
        get() = state.value.key

    fun toggle() = choose(state.value.toggled())

    fun choose(choice: ThemeChoice) {
        mutableState.value = choice
        store.save(choice)
    }
}
