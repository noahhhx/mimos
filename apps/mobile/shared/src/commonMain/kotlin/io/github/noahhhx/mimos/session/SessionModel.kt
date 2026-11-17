package io.github.noahhhx.mimos.session

import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Who is signed in to one instance, and every move between the states. The work runs in [scope], so
 * a sign-in in flight outlives the screen that asked for it. A result applies only if the state it
 * began from still holds, so a sign-out during a refresh wins.
 */
class SessionModel(
    private val authorizer: Authorizer,
    private val store: TokenStore,
    private val scope: CoroutineScope,
    private val clock: Clock = Clock.System,
) {
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Refreshing)
    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    private val refreshLock = Mutex()

    init {
        scope.launch { restore() }
    }

    fun retry() {
        if (!mutableState.compareAndSet(SessionState.Unreachable, SessionState.Refreshing)) return
        scope.launch { restore() }
    }

    fun signIn() = authorize(AuthorizeKind.SignIn)

    fun createAccount() = authorize(AuthorizeKind.CreateAccount)

    fun signOut() {
        val idToken =
            when (val current = mutableState.value) {
                is SessionState.SignedIn -> current.tokens.idToken
                else -> null
            }
        val endsSession = mutableState.value is SessionState.SignedIn
        mutableState.value = SessionState.SignedOut()
        scope.launch {
            val stored = store.load()
            store.clear()
            if (endsSession || stored is StoredSession.Present) {
                authorizer.endSession(idToken ?: (stored as? StoredSession.Present)?.idToken)
            }
        }
    }

    /**
     * The bearer token for a call: the current one, or a fresh one when it is expired or close to
     * it. One refresh at a time; callers that arrive meanwhile share its result. Null when nobody
     * is signed in. When the server cannot be asked, the old token goes out and the call fails on
     * its own terms.
     */
    suspend fun accessToken(): String? {
        val first = mutableState.value as? SessionState.SignedIn ?: return null
        if (!first.tokens.needsRefresh(clock.now())) return first.tokens.accessToken
        return refreshLock.withLock {
            val current = mutableState.value as? SessionState.SignedIn ?: return@withLock null
            if (!current.tokens.needsRefresh(clock.now()))
                return@withLock current.tokens.accessToken
            when (val result = authorizer.refresh(current.tokens.refreshToken)) {
                is RefreshResult.Granted -> {
                    val renewed = result.tokens.keepingIdToken(current.tokens.idToken)
                    val applied = SessionState.SignedIn(renewed)
                    if (mutableState.compareAndSet(current, applied)) {
                        store.save(StoredSession.Present(renewed.refreshToken, renewed.idToken))
                        renewed.accessToken
                    } else null
                }
                RefreshResult.InvalidGrant -> {
                    if (
                        mutableState.compareAndSet(
                            current,
                            SessionState.SignedOut(SignedOutNotice.Expired),
                        )
                    ) {
                        store.clear()
                    }
                    null
                }
                RefreshResult.Unavailable -> current.tokens.accessToken
            }
        }
    }

    private suspend fun restore() {
        when (val stored = store.load()) {
            StoredSession.None -> settle(SessionState.SignedOut())
            StoredSession.Unreadable -> {
                store.clear()
                settle(SessionState.SignedOut(SignedOutNotice.Unreadable))
            }
            is StoredSession.Present ->
                when (val result = authorizer.refresh(stored.refreshToken)) {
                    is RefreshResult.Granted -> {
                        val renewed = result.tokens.keepingIdToken(stored.idToken)
                        if (settle(SessionState.SignedIn(renewed))) {
                            store.save(StoredSession.Present(renewed.refreshToken, renewed.idToken))
                        }
                    }
                    RefreshResult.InvalidGrant -> {
                        if (settle(SessionState.SignedOut(SignedOutNotice.Expired))) store.clear()
                    }
                    RefreshResult.Unavailable -> settle(SessionState.Unreachable)
                }
        }
    }

    private fun authorize(kind: AuthorizeKind) {
        val from = mutableState.value
        if (from !is SessionState.SignedOut) return
        if (!mutableState.compareAndSet(from, SessionState.SigningIn)) return
        scope.launch {
            when (val result = authorizer.authorize(kind, AUTH_THEME)) {
                is AuthorizeResult.Granted -> {
                    if (
                        settle(SessionState.SignedIn(result.tokens), from = SessionState.SigningIn)
                    ) {
                        store.save(
                            StoredSession.Present(result.tokens.refreshToken, result.tokens.idToken)
                        )
                    }
                }
                AuthorizeResult.Cancelled ->
                    settle(SessionState.SignedOut(), from = SessionState.SigningIn)
                is AuthorizeResult.Failed ->
                    settle(
                        SessionState.SignedOut(SignedOutNotice.SignInFailed(result.reason)),
                        from = SessionState.SigningIn,
                    )
            }
        }
    }

    private fun settle(
        next: SessionState,
        from: SessionState = SessionState.Refreshing,
    ): Boolean = mutableState.compareAndSet(from, next)
}

private fun Tokens.keepingIdToken(previous: String?) =
    if (idToken == null) copy(idToken = previous) else this
