@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package io.github.noahhhx.mimos.session

import io.github.noahhhx.mimos.ui.MemoryThemeStore
import io.github.noahhhx.mimos.ui.ThemeChoice
import io.github.noahhhx.mimos.ui.ThemeModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private val now = Instant.fromEpochSeconds(1_800_000_000)

private class FixedClock(var at: Instant = now) : kotlin.time.Clock {
    override fun now() = at
}

private fun tokens(
    access: String,
    refresh: String = "refresh-$access",
    expiresIn: Int = 5,
    id: String? = "id-$access",
) = Tokens(access, now + expiresIn.minutes, refresh, id)

private class MemoryTokenStore(var stored: StoredSession = StoredSession.None) : TokenStore {
    override suspend fun load() = stored

    override suspend fun save(session: StoredSession.Present) {
        stored = session
    }

    override suspend fun clear() {
        stored = StoredSession.None
    }
}

private class FakeAuthorizer : Authorizer {
    var authorizeAnswer = CompletableDeferred<AuthorizeResult>()
    var refreshAnswer = CompletableDeferred<RefreshResult>()
    val authorized = mutableListOf<Pair<AuthorizeKind, String>>()
    val refreshed = mutableListOf<String>()
    val endedWith = mutableListOf<String?>()

    override suspend fun authorize(kind: AuthorizeKind, theme: String): AuthorizeResult {
        authorized += kind to theme
        return authorizeAnswer.await()
    }

    override suspend fun refresh(refreshToken: String): RefreshResult {
        refreshed += refreshToken
        return refreshAnswer.await()
    }

    override suspend fun endSession(idToken: String?) {
        endedWith += idToken
    }
}

private class Rig(
    stored: StoredSession = StoredSession.None,
    val authorizer: FakeAuthorizer = FakeAuthorizer(),
    val clock: FixedClock = FixedClock(),
    scope: CoroutineScope,
) {
    val store = MemoryTokenStore(stored)
    val theme = ThemeModel(MemoryThemeStore())
    val model = SessionModel(authorizer, store, { theme.authTheme }, scope, clock)
    val state
        get() = model.state.value
}

class SessionModelTest {
    @Test
    fun `with nothing stored the person is signed out with no notice`() = runTest {
        val rig = Rig(scope = backgroundScope)
        assertEquals(SessionState.Refreshing, rig.state)
        runCurrent()
        assertEquals(SessionState.SignedOut(), rig.state)
        assertEquals(emptyList(), rig.authorizer.refreshed)
    }

    @Test
    fun `signing in shows progress, then the tokens, and keeps only the refresh and id tokens`() =
        runTest {
            val rig = Rig(scope = backgroundScope)
            runCurrent()
            rig.model.signIn()
            runCurrent()
            assertEquals(SessionState.SigningIn, rig.state)
            assertEquals(listOf(AuthorizeKind.SignIn to "light"), rig.authorizer.authorized)
            rig.authorizer.authorizeAnswer.complete(AuthorizeResult.Granted(tokens("a")))
            runCurrent()
            assertEquals(SessionState.SignedIn(tokens("a")), rig.state)
            assertEquals(StoredSession.Present("refresh-a", "id-a"), rig.store.stored)
            assertEquals("a", rig.model.accessToken())
        }

    @Test
    fun `creating an account asks the browser to create one`() = runTest {
        val rig = Rig(scope = backgroundScope)
        runCurrent()
        rig.model.createAccount()
        runCurrent()
        assertEquals(listOf(AuthorizeKind.CreateAccount to "light"), rig.authorizer.authorized)
    }

    @Test
    fun `keycloak's pages are asked for the theme chosen in the app`() = runTest {
        val rig = Rig(scope = backgroundScope)
        runCurrent()
        rig.theme.choose(ThemeChoice.Dark)
        rig.model.createAccount()
        runCurrent()
        assertEquals(listOf(AuthorizeKind.CreateAccount to "dark"), rig.authorizer.authorized)
    }

    @Test
    fun `a cancelled sign-in returns to signed out with no notice and stores nothing`() = runTest {
        val rig = Rig(scope = backgroundScope)
        runCurrent()
        rig.model.signIn()
        runCurrent()
        rig.authorizer.authorizeAnswer.complete(AuthorizeResult.Cancelled)
        runCurrent()
        assertEquals(SessionState.SignedOut(), rig.state)
        assertEquals(StoredSession.None, rig.store.stored)
    }

    @Test
    fun `a failed sign-in says why, and the person can try again`() = runTest {
        val rig = Rig(scope = backgroundScope)
        runCurrent()
        rig.model.signIn()
        runCurrent()
        rig.authorizer.authorizeAnswer.complete(AuthorizeResult.Failed("no browser"))
        runCurrent()
        assertEquals(SessionState.SignedOut(SignedOutNotice.SignInFailed("no browser")), rig.state)

        rig.authorizer.authorizeAnswer = CompletableDeferred()
        rig.model.signIn()
        runCurrent()
        assertEquals(SessionState.SigningIn, rig.state)
    }

    @Test
    fun `a second sign-in while one is in flight does not start another`() = runTest {
        val rig = Rig(scope = backgroundScope)
        runCurrent()
        rig.model.signIn()
        rig.model.signIn()
        rig.model.createAccount()
        runCurrent()
        assertEquals(1, rig.authorizer.authorized.size)
    }

    @Test
    fun `a stored session is refreshed on launch, and the renewed tokens are kept`() = runTest {
        val rig = Rig(StoredSession.Present("refresh-old", "id-old"), scope = backgroundScope)
        runCurrent()
        assertEquals(SessionState.Refreshing, rig.state)
        assertEquals(listOf("refresh-old"), rig.authorizer.refreshed)
        rig.authorizer.refreshAnswer.complete(
            RefreshResult.Granted(tokens("b", refresh = "refresh-new", id = null))
        )
        runCurrent()
        assertEquals(
            SessionState.SignedIn(tokens("b", refresh = "refresh-new", id = "id-old")),
            rig.state,
        )
        assertEquals(StoredSession.Present("refresh-new", "id-old"), rig.store.stored)
    }

    @Test
    fun `a refresh token Keycloak refuses signs the person out with the expired notice`() =
        runTest {
            val rig = Rig(StoredSession.Present("refresh-old", null), scope = backgroundScope)
            rig.authorizer.refreshAnswer.complete(RefreshResult.InvalidGrant)
            runCurrent()
            assertEquals(SessionState.SignedOut(SignedOutNotice.Expired), rig.state)
            assertEquals(StoredSession.None, rig.store.stored)
        }

    @Test
    fun `a stored token that cannot be read signs the person out with its own notice`() = runTest {
        val rig = Rig(StoredSession.Unreadable, scope = backgroundScope)
        runCurrent()
        assertEquals(SessionState.SignedOut(SignedOutNotice.Unreadable), rig.state)
        assertEquals(StoredSession.None, rig.store.stored)
        assertEquals(emptyList(), rig.authorizer.refreshed)
    }

    @Test
    fun `no answer on launch keeps the stored session, and retrying restores it`() = runTest {
        val rig = Rig(StoredSession.Present("refresh-old", "id-old"), scope = backgroundScope)
        rig.authorizer.refreshAnswer.complete(RefreshResult.Unavailable)
        runCurrent()
        assertEquals(SessionState.Unreachable, rig.state)
        assertEquals(StoredSession.Present("refresh-old", "id-old"), rig.store.stored)

        rig.authorizer.refreshAnswer = CompletableDeferred(RefreshResult.Granted(tokens("c")))
        rig.model.retry()
        runCurrent()
        assertEquals(SessionState.SignedIn(tokens("c")), rig.state)
    }

    @Test
    fun `a token with time left is used as it is`() = runTest {
        val rig = Rig(scope = backgroundScope)
        runCurrent()
        rig.model.signIn()
        runCurrent()
        rig.authorizer.authorizeAnswer.complete(AuthorizeResult.Granted(tokens("a")))
        runCurrent()
        assertEquals("a", rig.model.accessToken())
        assertEquals(emptyList(), rig.authorizer.refreshed)
    }

    @Test
    fun `an expired token is refreshed before it is handed out`() = runTest {
        val rig = signedIn(tokens("a"))
        rig.clock.at = now + 10.minutes
        rig.authorizer.refreshAnswer =
            CompletableDeferred(RefreshResult.Granted(tokens("b", expiresIn = 20)))
        assertEquals("b", rig.model.accessToken())
        assertEquals(listOf("refresh-a"), rig.authorizer.refreshed)
        assertEquals(StoredSession.Present("refresh-b", "id-b"), rig.store.stored)
    }

    @Test
    fun `a token that is about to expire is refreshed`() = runTest {
        val rig = signedIn(tokens("a"))
        rig.clock.at = now + 5.minutes - kotlin.time.Duration.parse("10s")
        rig.authorizer.refreshAnswer =
            CompletableDeferred(RefreshResult.Granted(tokens("b", expiresIn = 20)))
        assertEquals("b", rig.model.accessToken())
    }

    @Test
    fun `callers that arrive during a refresh share it`() = runTest {
        val rig = signedIn(tokens("a"))
        rig.clock.at = now + 10.minutes
        val calls = List(3) { async { rig.model.accessToken() } }
        runCurrent()
        assertEquals(listOf("refresh-a"), rig.authorizer.refreshed)
        rig.authorizer.refreshAnswer.complete(RefreshResult.Granted(tokens("b", expiresIn = 20)))
        assertEquals(listOf("b", "b", "b"), calls.map { it.await() })
        assertEquals(1, rig.authorizer.refreshed.size)
    }

    @Test
    fun `a refresh refused during a session signs the person out with the expired notice`() =
        runTest {
            val rig = signedIn(tokens("a"))
            rig.clock.at = now + 10.minutes
            rig.authorizer.refreshAnswer = CompletableDeferred(RefreshResult.InvalidGrant)
            assertNull(rig.model.accessToken())
            assertEquals(SessionState.SignedOut(SignedOutNotice.Expired), rig.state)
            assertEquals(StoredSession.None, rig.store.stored)
        }

    @Test
    fun `no answer during a session keeps the person signed in and sends the old token`() =
        runTest {
            val rig = signedIn(tokens("a"))
            rig.clock.at = now + 10.minutes
            rig.authorizer.refreshAnswer = CompletableDeferred(RefreshResult.Unavailable)
            assertEquals("a", rig.model.accessToken())
            assertEquals(SessionState.SignedIn(tokens("a")), rig.state)
        }

    @Test
    fun `nobody signed in has no token`() = runTest {
        val rig = Rig(scope = backgroundScope)
        runCurrent()
        assertNull(rig.model.accessToken())
    }

    @Test
    fun `signing out ends the Keycloak session with the id token and forgets the tokens`() =
        runTest {
            val rig = signedIn(tokens("a"))
            rig.model.signOut()
            assertEquals(SessionState.SignedOut(), rig.state)
            runCurrent()
            assertEquals(listOf<String?>("id-a"), rig.authorizer.endedWith)
            assertEquals(StoredSession.None, rig.store.stored)
            assertNull(rig.model.accessToken())
        }

    @Test
    fun `a refresh that finishes after signing out does not sign the person back in`() = runTest {
        val rig = signedIn(tokens("a"))
        rig.clock.at = now + 10.minutes
        val call = async { rig.model.accessToken() }
        runCurrent()
        rig.model.signOut()
        rig.authorizer.refreshAnswer.complete(RefreshResult.Granted(tokens("b", expiresIn = 20)))
        runCurrent()
        assertNull(call.await())
        assertEquals(SessionState.SignedOut(), rig.state)
        assertEquals(StoredSession.None, rig.store.stored)
    }

    @Test
    fun `signing out of a restored but unreachable session still ends it`() = runTest {
        val rig = Rig(StoredSession.Present("refresh-old", "id-old"), scope = backgroundScope)
        rig.authorizer.refreshAnswer.complete(RefreshResult.Unavailable)
        runCurrent()
        rig.model.signOut()
        runCurrent()
        assertEquals(listOf<String?>("id-old"), rig.authorizer.endedWith)
        assertEquals(StoredSession.None, rig.store.stored)
        assertEquals(SessionState.SignedOut(), rig.state)
    }

    @Test
    fun `leaving an instance with nobody signed in opens no browser`() = runTest {
        val rig = Rig(scope = backgroundScope)
        runCurrent()
        rig.model.signOut()
        runCurrent()
        assertEquals(emptyList(), rig.authorizer.endedWith)
    }

    private suspend fun TestScope.signedIn(initial: Tokens): Rig {
        val rig = Rig(scope = backgroundScope)
        runCurrent()
        rig.model.signIn()
        runCurrent()
        rig.authorizer.authorizeAnswer.complete(AuthorizeResult.Granted(initial))
        runCurrent()
        assertEquals(SessionState.SignedIn(initial), rig.state)
        return rig
    }
}
