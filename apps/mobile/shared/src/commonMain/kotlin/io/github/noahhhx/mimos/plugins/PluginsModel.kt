package io.github.noahhhx.mimos.plugins

import io.github.noahhhx.mimos.api.apis.PluginSettingsApi
import io.github.noahhhx.mimos.api.models.UserPlugin
import io.github.noahhhx.mimos.api.models.UserPluginSetting
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PluginsState {
    data object Loading : PluginsState

    data class Loaded(val plugins: List<UserPlugin>) : PluginsState

    data object Failed : PluginsState
}

/**
 * The Plugins screen, as the web's Plugins page (ADR-0013, ADR-0019): the instance's plugins, each
 * off until someone in the household turns it on here. [onChanged] runs after a plugin is turned on
 * or off, since the plan's panels and suggestions then differ.
 */
class PluginsModel(
    private val scope: CoroutineScope,
    private val api: () -> PluginSettingsApi,
    private val onChanged: () -> Unit,
) {
    private val mutableState = MutableStateFlow<PluginsState>(PluginsState.Loading)
    val state: StateFlow<PluginsState> = mutableState.asStateFlow()

    /** The plugin whose choice is being saved. */
    private val mutableSaving = MutableStateFlow<String?>(null)
    val saving: StateFlow<String?> = mutableSaving.asStateFlow()

    private val mutableError = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = mutableError.asStateFlow()

    private var job: Job? = null

    /** Loads the list each time the screen opens, as the web page does. */
    fun load() {
        if (job?.isActive == true) return
        mutableError.value = null
        job = scope.launch {
            mutableState.value =
                try {
                    PluginsState.Loaded(api().listMyPlugins().body())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    PluginsState.Failed
                }
        }
    }

    /** The switch follows the tap at once and goes back if the save fails. */
    fun setEnabled(plugin: UserPlugin, enabled: Boolean) {
        if (saving.value != null) return
        show(plugin.id, enabled)
        mutableSaving.value = plugin.id
        mutableError.value = null
        job = scope.launch {
            try {
                api().updateMyPlugin(plugin.id, UserPluginSetting(enabled))
                onChanged()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                show(plugin.id, !enabled)
                mutableError.value =
                    "Could not turn ${plugin.name} ${if (enabled) "on" else "off"}."
            } finally {
                mutableSaving.value = null
            }
        }
    }

    private fun show(pluginId: String, enabled: Boolean) = mutableState.update { state ->
        if (state !is PluginsState.Loaded) state
        else
            PluginsState.Loaded(
                state.plugins.map { if (it.id == pluginId) it.copy(enabled = enabled) else it }
            )
    }

    /** Forgets everything, for when the person signs out and someone else may sign in. */
    fun reset() {
        job?.cancel()
        mutableState.value = PluginsState.Loading
        mutableSaving.value = null
        mutableError.value = null
    }
}

/**
 * A plugin's homepage as a link to open, or null. The API already drops anything but http(s) from
 * manifests (ADR-0013); this keeps the app safe on its own, since the value comes from a plugin.
 */
fun pluginHomepage(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val parsed = runCatching { Url(url) }.getOrNull() ?: return null
    val scheme = url.substringBefore(':', "").lowercase()
    return if ((scheme == "http" || scheme == "https") && parsed.host.isNotEmpty()) url else null
}
