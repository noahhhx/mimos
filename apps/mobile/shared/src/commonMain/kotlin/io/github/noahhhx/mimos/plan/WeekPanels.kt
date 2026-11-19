package io.github.noahhhx.mimos.plan

import io.github.noahhhx.mimos.api.apis.PluginsApi
import io.github.noahhhx.mimos.api.models.PanelAction
import io.github.noahhhx.mimos.api.models.PanelButton
import io.github.noahhhx.mimos.api.models.WeekPanel
import io.github.noahhhx.mimos.client.ApiProblem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * One plugin's panel as the plan shows it: collapsed until opened, and while a button's press is on
 * its way or the wheel it brought back still turns, its buttons wait.
 */
data class PanelState(
    val panel: WeekPanel,
    val open: Boolean = false,
    val pressing: Boolean = false,
    val spinning: Boolean = false,
    val error: String? = null,
) {
    val buttonsEnabled: Boolean
        get() = !pressing && !spinning
}

/** A week's panels, in the order the server lists them. */
data class WeekPanels(val monday: LocalDate, val panels: List<PanelState>)

/**
 * The plugins' panels for the plan's week (ADR-0017). They load per week, not per change: a meal
 * planned changes nothing in a panel, and reloading one would drop a wheel's landed spin. A newer
 * week replaces an older one, so a slow answer never lands on the wrong week; a week whose panels
 * fail to load has none, as on the web. [onPressed] runs with the week after a button's answer,
 * since a panel's button can change what its plugin suggests.
 */
class WeekPanelsModel(
    private val scope: CoroutineScope,
    private val api: () -> PluginsApi,
    private val onPressed: (LocalDate) -> Unit,
) {
    private val mutableState = MutableStateFlow<WeekPanels?>(null)

    /** Null until the wanted week's panels have answered. */
    val state: StateFlow<WeekPanels?> = mutableState.asStateFlow()

    private var wanted: LocalDate? = null
    private var loadJob: Job? = null
    private val pressJobs = mutableListOf<Job>()

    /** Loads [monday]'s panels unless they are loaded or on their way. */
    fun ensureLoaded(monday: LocalDate) {
        if (wanted != monday || (state.value == null && loadJob?.isActive != true)) load(monday)
    }

    fun load(monday: LocalDate) {
        cancelAll()
        wanted = monday
        mutableState.value = null
        loadJob = scope.launch {
            val panels =
                try {
                    api().getWeekPanels(monday).body().panels.map { PanelState(it) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    emptyList()
                }
            if (wanted == monday) mutableState.value = WeekPanels(monday, panels)
        }
    }

    /** Opens or closes a panel. Closing mid-spin ends it, so reopening shows where it landed. */
    fun toggle(pluginId: String) = edit(pluginId) { it.copy(open = !it.open, spinning = false) }

    fun press(pluginId: String, button: PanelButton) {
        val week = state.value ?: return
        val current = week.panels.find { it.panel.pluginId == pluginId } ?: return
        if (!current.buttonsEnabled) return
        edit(pluginId) { it.copy(pressing = true, error = null) }
        pressJobs += scope.launch {
            val answer =
                try {
                    api()
                        .pressWeekPanelAction(
                            week.monday,
                            pluginId,
                            PanelAction(button.id, button.value),
                        )
                        .body()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    val message =
                        (failure as? ApiProblem)?.detail
                            ?: "${current.panel.pluginName} could not do that. Try again."
                    edit(pluginId) { it.copy(pressing = false, error = message) }
                    return@launch
                }
            edit(pluginId) {
                it.copy(panel = answer, pressing = false, spinning = hasLanding(answer.blocks))
            }
            onPressed(week.monday)
        }
    }

    /** The wheel has stopped: the blocks after it show and the buttons work again. */
    fun spinStopped(pluginId: String) = edit(pluginId) { it.copy(spinning = false) }

    private fun edit(pluginId: String, change: (PanelState) -> PanelState) = mutableState.update {
        it?.copy(
            panels =
                it.panels.map { each ->
                    if (each.panel.pluginId == pluginId) change(each) else each
                }
        )
    }

    private fun cancelAll() {
        loadJob?.cancel()
        pressJobs.forEach { it.cancel() }
        pressJobs.clear()
    }

    /** Forgets everything, for when the person signs out and someone else may sign in. */
    fun reset() {
        cancelAll()
        wanted = null
        mutableState.value = null
    }
}
