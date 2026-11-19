package io.github.noahhhx.mimos.household

import io.github.noahhhx.mimos.api.apis.HouseholdApi
import io.github.noahhhx.mimos.api.models.Household
import io.github.noahhhx.mimos.api.models.HouseholdInvite
import io.github.noahhhx.mimos.api.models.HouseholdInvitePreview
import io.github.noahhhx.mimos.api.models.HouseholdInviteToken
import io.github.noahhhx.mimos.client.ApiProblem
import io.github.noahhhx.mimos.instance.WebAddress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the Household screen is: the household, or an invite the person may join with. */
sealed interface HouseholdRoute {
    data object Page : HouseholdRoute

    data class Join(val link: InviteLink) : HouseholdRoute
}

sealed interface HouseholdState {
    data object Loading : HouseholdState

    data class Loaded(val household: Household) : HouseholdState {
        val shared: Boolean
            get() = household.members.size > 1
    }

    data object Failed : HouseholdState
}

sealed interface InviteState {
    data object None : InviteState

    data object Creating : InviteState

    data class Created(val invite: HouseholdInvite, val link: String) : InviteState

    data object Failed : InviteState
}

sealed interface LeaveState {
    data object Idle : LeaveState

    /** The warnings about what stays and what goes are on screen. */
    data class Confirming(val leaving: Boolean = false, val error: String? = null) : LeaveState
}

/** The invite on the join screen. */
sealed interface JoinState {
    data object Loading : JoinState

    /** The invite is for another instance than the connected one, [connected]. */
    data class OtherInstance(val link: InviteLink, val connected: WebAddress) : JoinState

    /** Unknown, used, or out of date: nothing to join. */
    data class Invalid(val message: String) : JoinState

    /** The invite could not be read, which says nothing about whether it works. */
    data object Failed : JoinState

    data object AlreadyMember : JoinState

    data class Ready(
        val preview: HouseholdInvitePreview,
        val joining: Boolean = false,
        val error: String? = null,
    ) : JoinState
}

/** The paste field for an invite link someone sent. */
data class PasteState(val text: String = "", val error: String? = null)

const val INVALID_INVITE = "This invite link is not valid. Ask for a new one."
const val NOT_AN_INVITE = "That is not an invite link. It looks like https://…/app/join/…"

/**
 * The Household screen, as the web's Household and join pages (ADR-0019): who is in it, an invite
 * link to share, joining with a link someone sent, and leaving. [connected] is the connected
 * instance's web address, which names its invite links. [onChanged] runs after the person joins or
 * leaves, since every recipe and plan the app holds then belongs to another household.
 */
class HouseholdModel(
    private val scope: CoroutineScope,
    private val api: () -> HouseholdApi,
    private val connected: () -> WebAddress,
    private val allowLocalhostHttp: Boolean,
    private val onChanged: () -> Unit,
) {
    private val mutableRoute = MutableStateFlow<HouseholdRoute>(HouseholdRoute.Page)
    val route: StateFlow<HouseholdRoute> = mutableRoute.asStateFlow()

    private val mutableHousehold = MutableStateFlow<HouseholdState>(HouseholdState.Loading)
    val household: StateFlow<HouseholdState> = mutableHousehold.asStateFlow()

    private val mutableInvite = MutableStateFlow<InviteState>(InviteState.None)
    val invite: StateFlow<InviteState> = mutableInvite.asStateFlow()

    private val mutableLeave = MutableStateFlow<LeaveState>(LeaveState.Idle)
    val leave: StateFlow<LeaveState> = mutableLeave.asStateFlow()

    private val mutableJoin = MutableStateFlow<JoinState>(JoinState.Loading)
    val join: StateFlow<JoinState> = mutableJoin.asStateFlow()

    private val mutablePaste = MutableStateFlow(PasteState())
    val paste: StateFlow<PasteState> = mutablePaste.asStateFlow()

    private var householdJob: Job? = null
    private var previewJob: Job? = null

    fun ensureLoaded() {
        if (household.value !is HouseholdState.Loaded && householdJob?.isActive != true) load()
    }

    fun retry() = load()

    private fun load() {
        householdJob?.cancel()
        householdJob = scope.launch {
            mutableHousehold.value = HouseholdState.Loading
            mutableHousehold.value =
                attempt { HouseholdState.Loaded(api().getHousehold().body()) }
                    ?: HouseholdState.Failed
        }
    }

    fun createInvite() {
        if (invite.value == InviteState.Creating) return
        mutableInvite.value = InviteState.Creating
        scope.launch {
            mutableInvite.value =
                attempt {
                    val created = api().createHouseholdInvite().body()
                    InviteState.Created(created, webInviteLink(connected(), created.token))
                } ?: InviteState.Failed
        }
    }

    fun editPaste(text: String) {
        mutablePaste.value = PasteState(text)
    }

    /** Opens the pasted link's invite, or says why it is not one. */
    fun openPasted() {
        val link = parseInviteLink(paste.value.text, allowLocalhostHttp)
        if (link == null) {
            mutablePaste.update { it.copy(error = NOT_AN_INVITE) }
            return
        }
        mutablePaste.value = PasteState()
        openJoin(link)
    }

    /** Shows [link]'s invite: what joining does, or why it cannot be used here. */
    fun openJoin(link: InviteLink) {
        mutableRoute.value = HouseholdRoute.Join(link)
        preview(link)
    }

    fun retryJoin() {
        (route.value as? HouseholdRoute.Join)?.let { preview(it.link) }
    }

    private fun preview(link: InviteLink) {
        previewJob?.cancel()
        val here = connected()
        if (link.instance != here) {
            mutableJoin.value = JoinState.OtherInstance(link, here)
            return
        }
        mutableJoin.value = JoinState.Loading
        previewJob = scope.launch {
            mutableJoin.value =
                try {
                    val preview =
                        api().previewHouseholdInvite(HouseholdInviteToken(link.token)).body()
                    if (preview.alreadyMember) JoinState.AlreadyMember else JoinState.Ready(preview)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (problem: ApiProblem) {
                    when (problem.status) {
                        410 -> JoinState.Invalid(problem.detail ?: INVALID_INVITE)
                        400,
                        404 -> JoinState.Invalid(INVALID_INVITE)
                        else -> JoinState.Failed
                    }
                } catch (_: Exception) {
                    JoinState.Failed
                }
        }
    }

    fun confirmJoin() {
        val link = (route.value as? HouseholdRoute.Join)?.link ?: return
        val ready = join.value as? JoinState.Ready ?: return
        if (ready.joining) return
        mutableJoin.value = ready.copy(joining = true, error = null)
        scope.launch {
            try {
                val joined = api().joinHousehold(HouseholdInviteToken(link.token)).body()
                changedTo(joined)
                mutableRoute.value = HouseholdRoute.Page
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableJoin.value =
                    ready.copy(error = failure.detail() ?: "Could not join the household.")
            }
        }
    }

    fun startLeaving() {
        mutableLeave.value = LeaveState.Confirming()
    }

    fun stay() {
        if ((leave.value as? LeaveState.Confirming)?.leaving == true) return
        mutableLeave.value = LeaveState.Idle
    }

    fun confirmLeave() {
        val confirming = leave.value as? LeaveState.Confirming ?: return
        if (confirming.leaving) return
        mutableLeave.value = LeaveState.Confirming(leaving = true)
        scope.launch {
            try {
                changedTo(api().leaveHousehold().body())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                mutableLeave.value =
                    LeaveState.Confirming(
                        error = failure.detail() ?: "Could not leave the household."
                    )
            }
        }
    }

    private fun changedTo(household: Household) {
        householdJob?.cancel()
        mutableHousehold.value = HouseholdState.Loaded(household)
        mutableInvite.value = InviteState.None
        mutableLeave.value = LeaveState.Idle
        onChanged()
    }

    /** Back to the household from an invite. False when already there. */
    fun back(): Boolean {
        if (route.value == HouseholdRoute.Page) return false
        previewJob?.cancel()
        mutableRoute.value = HouseholdRoute.Page
        return true
    }

    /** Back to the start, for when the person signs out and someone else may sign in. */
    fun reset() {
        householdJob?.cancel()
        previewJob?.cancel()
        mutableRoute.value = HouseholdRoute.Page
        mutableHousehold.value = HouseholdState.Loading
        mutableInvite.value = InviteState.None
        mutableLeave.value = LeaveState.Idle
        mutableJoin.value = JoinState.Loading
        mutablePaste.value = PasteState()
    }

    private suspend fun <T> attempt(read: suspend () -> T): T? =
        try {
            read()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
}

private fun Exception.detail(): String? = (this as? ApiProblem)?.detail
