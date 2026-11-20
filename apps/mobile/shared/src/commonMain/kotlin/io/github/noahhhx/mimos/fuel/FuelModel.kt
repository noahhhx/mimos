package io.github.noahhhx.mimos.fuel

import io.github.noahhhx.mimos.api.apis.FuelApi
import io.github.noahhhx.mimos.api.apis.IntervalsIcuApi
import io.github.noahhhx.mimos.api.models.IntervalsIcuConnection
import io.github.noahhhx.mimos.api.models.IntervalsIcuConnectionInput
import io.github.noahhhx.mimos.api.models.IntervalsIcuConnectionStatus
import io.github.noahhhx.mimos.client.ApiProblem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The profile form: what is typed, what to fix, and how the last save went. */
data class ProfileForm(
    val draft: FuelDraft,
    val fieldErrors: List<FuelField> = emptyList(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
)

sealed interface ProfileState {
    data object Loading : ProfileState

    data object Failed : ProfileState

    data class Loaded(val form: ProfileForm) : ProfileState
}

/** The intervals.icu section; [Off] when this instance does not offer it (a 404). */
sealed interface ConnectionState {
    data object Loading : ConnectionState

    data object Off : ConnectionState

    data object Failed : ConnectionState

    data class Loaded(val connection: IntervalsIcuConnection) : ConnectionState
}

/**
 * The connect form. The API key is held here only until it is sent, and never stored on the device;
 * the server keeps it encrypted (ADR-0022).
 */
data class LinkForm(
    val athleteId: String = "",
    val apiKey: String = "",
    val busy: Boolean = false,
    val problem: String? = null,
    val confirmingDisconnect: Boolean = false,
) {
    val canConnect: Boolean
        get() = !busy && athleteId.isNotBlank() && apiKey.isNotBlank()
}

/**
 * The Fuel screen, as the web's Fuel page (ADR-0022): the person's fuel profile, which turns
 * targets on, and their intervals.icu connection. Both load each time the screen opens. [onChanged]
 * runs after anything that moves a day's target or training, since the plan shows them.
 */
class FuelModel(
    private val scope: CoroutineScope,
    private val fuel: () -> FuelApi,
    private val intervalsIcu: () -> IntervalsIcuApi,
    private val onChanged: () -> Unit,
) {
    private val mutableProfile = MutableStateFlow<ProfileState>(ProfileState.Loading)
    val profile: StateFlow<ProfileState> = mutableProfile.asStateFlow()

    private val mutableConnection = MutableStateFlow<ConnectionState>(ConnectionState.Loading)
    val connection: StateFlow<ConnectionState> = mutableConnection.asStateFlow()

    private val mutableLink = MutableStateFlow(LinkForm())
    val link: StateFlow<LinkForm> = mutableLink.asStateFlow()

    private val jobs = mutableListOf<Job>()

    fun load() {
        if ((profile.value as? ProfileState.Loaded)?.form?.saving == true || link.value.busy) return
        launch {
            mutableProfile.value =
                try {
                    ProfileState.Loaded(ProfileForm(fuel().getMyFuelProfile().body().toDraft()))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    ProfileState.Failed
                }
        }
        launch {
            val loaded =
                try {
                    ConnectionState.Loaded(intervalsIcu().getMyIntervalsIcu().body())
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (problem: ApiProblem) {
                    // A 404 means this instance does not offer intervals.icu; training is entered
                    // by hand.
                    if (problem.status == 404) ConnectionState.Off else ConnectionState.Failed
                } catch (_: Exception) {
                    ConnectionState.Failed
                }
            mutableConnection.value = loaded
            if (loaded is ConnectionState.Loaded) {
                mutableLink.value = LinkForm(athleteId = loaded.connection.athleteId ?: "")
            }
        }
    }

    fun edit(change: (FuelDraft) -> FuelDraft) = updateForm {
        it.copy(draft = change(it.draft), saved = false)
    }

    fun use(suggestion: Suggestion) = edit { it.with(suggestion) }

    fun chooseGoal(goal: Goal) = edit { it.withGoal(goal) }

    fun save() {
        val form = (profile.value as? ProfileState.Loaded)?.form ?: return
        if (form.saving) return
        val input =
            when (val checked = form.draft.check()) {
                is CheckedProfile.Invalid -> {
                    updateForm {
                        it.copy(fieldErrors = checked.fields, error = null, saved = false)
                    }
                    return
                }
                is CheckedProfile.Valid -> checked.input
            }
        updateForm {
            it.copy(fieldErrors = emptyList(), error = null, saved = false, saving = true)
        }
        launch {
            try {
                val saved = fuel().updateMyFuelProfile(input).body()
                mutableProfile.value =
                    ProfileState.Loaded(ProfileForm(saved.toDraft(), saved = true))
                onChanged()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                val named = namedProfileFields((failure as? ApiProblem)?.detail)
                updateForm {
                    it.copy(
                        saving = false,
                        fieldErrors = named,
                        error = if (named.isEmpty()) "Could not save your fuel settings." else null,
                    )
                }
            }
        }
    }

    private fun updateForm(change: (ProfileForm) -> ProfileForm) = mutableProfile.update {
        if (it is ProfileState.Loaded) ProfileState.Loaded(change(it.form)) else it
    }

    fun editAthleteId(text: String) = mutableLink.update { it.copy(athleteId = text) }

    fun editApiKey(text: String) = mutableLink.update { it.copy(apiKey = text) }

    /** Connects, forgets the key, and syncs at once, as the web does. */
    fun connect() {
        val form = link.value.takeIf { it.canConnect } ?: return
        run {
            val connected =
                try {
                    intervalsIcu()
                        .connectMyIntervalsIcu(
                            IntervalsIcuConnectionInput(form.athleteId.trim(), form.apiKey.trim())
                        )
                        .body()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    mutableLink.update {
                        it.copy(
                            problem =
                                (failure as? ApiProblem)?.detail ?: IntervalsIcuFailures.CONNECT
                        )
                    }
                    return@run
                }
            mutableLink.update { it.copy(apiKey = "") }
            show(connected)
            onChanged()
            try {
                show(intervalsIcu().syncMyIntervalsIcu().body())
                onChanged()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {}
        }
    }

    fun sync() = run {
        try {
            show(intervalsIcu().syncMyIntervalsIcu().body())
            onChanged()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableLink.update { it.copy(problem = IntervalsIcuFailures.SYNC) }
        }
    }

    fun startDisconnect() = mutableLink.update { it.copy(confirmingDisconnect = true) }

    fun cancelDisconnect() = mutableLink.update { it.copy(confirmingDisconnect = false) }

    /** Training from intervals.icu goes with it; training added by hand stays. */
    fun disconnect() = run {
        try {
            intervalsIcu().disconnectMyIntervalsIcu()
            mutableLink.value = LinkForm()
            show(IntervalsIcuConnection(IntervalsIcuConnectionStatus.DISCONNECTED))
            onChanged()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            mutableLink.update {
                it.copy(confirmingDisconnect = false, problem = IntervalsIcuFailures.DISCONNECT)
            }
        }
    }

    private fun show(connection: IntervalsIcuConnection) {
        mutableConnection.value = ConnectionState.Loaded(connection)
    }

    /** One connection change at a time. */
    private fun run(action: suspend () -> Unit) {
        if (link.value.busy) return
        mutableLink.update { it.copy(busy = true, problem = null) }
        launch {
            try {
                action()
            } finally {
                mutableLink.update { it.copy(busy = false) }
            }
        }
    }

    private fun launch(block: suspend CoroutineScope.() -> Unit) {
        jobs.removeAll { !it.isActive }
        jobs += scope.launch(block = block)
    }

    /** Forgets everything, the typed key included, for when the person signs out. */
    fun reset() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        mutableProfile.value = ProfileState.Loading
        mutableConnection.value = ConnectionState.Loading
        mutableLink.value = LinkForm()
    }
}
