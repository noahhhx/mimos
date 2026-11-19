package io.github.noahhhx.mimos

import io.github.noahhhx.mimos.connect.ConnectModel
import io.github.noahhhx.mimos.connect.ConnectState
import io.github.noahhhx.mimos.household.HouseholdRoute
import io.github.noahhhx.mimos.household.InviteLink
import io.github.noahhhx.mimos.instance.Instance
import io.github.noahhhx.mimos.instance.InstanceConfig
import io.github.noahhhx.mimos.instance.InstanceStore
import io.github.noahhhx.mimos.instance.SignupPolicy
import io.github.noahhhx.mimos.instance.Version
import io.github.noahhhx.mimos.instance.WebAddress
import io.github.noahhhx.mimos.instance.parseWebAddress
import io.github.noahhhx.mimos.plan.PlanView
import io.github.noahhhx.mimos.plan.PlanViewStore
import io.github.noahhhx.mimos.recipes.RecipeId
import io.github.noahhhx.mimos.recipes.RecipeRoute
import io.github.noahhhx.mimos.recipes.RecipeSort
import io.github.noahhhx.mimos.recipes.RecipeSortStore
import io.github.noahhhx.mimos.recipes.pictures.PickedPicture
import io.github.noahhhx.mimos.recipes.pictures.PictureShrinker
import io.github.noahhhx.mimos.session.AuthorizeKind
import io.github.noahhhx.mimos.session.AuthorizeResult
import io.github.noahhhx.mimos.session.Authorizer
import io.github.noahhhx.mimos.session.RefreshResult
import io.github.noahhhx.mimos.session.SessionModel
import io.github.noahhhx.mimos.session.SessionState
import io.github.noahhhx.mimos.session.StoredSession
import io.github.noahhhx.mimos.session.TokenStore
import io.github.noahhhx.mimos.session.Tokens
import io.github.noahhhx.mimos.shell.Destination
import io.github.noahhhx.mimos.ui.MemoryThemeStore
import io.github.noahhhx.mimos.ui.ThemeModel
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

private class NoInstance : InstanceStore {
    override fun load(): Instance? = null

    override fun save(instance: Instance) = Unit

    override fun clear() = Unit
}

private class NoPlanView : PlanViewStore {
    override fun load(): PlanView? = null

    override fun save(view: PlanView) = Unit
}

private class NoSort : RecipeSortStore {
    override fun load(): RecipeSort? = null

    override fun save(sort: RecipeSort) = Unit
}

private class OneInstance(private val instance: Instance) : InstanceStore {
    override fun load(): Instance = instance

    override fun save(instance: Instance) = Unit

    override fun clear() = Unit
}

private fun model(
    debug: Boolean,
    scope: CoroutineScope,
    store: InstanceStore = NoInstance(),
    createSession: (Instance) -> SessionModel = { error("no session") },
) =
    AppModel(
        scope = scope,
        connect = ConnectModel(store) { error("never connects") },
        theme = ThemeModel(MemoryThemeStore()),
        debug = debug,
        engine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) },
        recipeSortStore = NoSort(),
        planViewStore = NoPlanView(),
        pictureShrinker =
            object : PictureShrinker {
                override suspend fun shrink(picked: PickedPicture) = error("nothing is picked")
            },
        createSession = createSession,
    )

private val instance =
    Instance(
        InstanceConfig(
            webUrl = WebAddress("https://mimos.example.com"),
            apiUrl = "https://api.mimos.example.com",
            oidcAuthority = "https://id.mimos.example.com/realms/mimos",
            oidcClientId = "mimos-web",
            signup = SignupPolicy.OPEN,
        ),
        serverVersion = Version(1, 2, 3),
    )

/** Restores one stored session whose access token is already due, then refuses to renew it. */
private class ExpiringAuthorizer : Authorizer {
    private var refreshes = 0

    override suspend fun authorize(kind: AuthorizeKind, theme: String) = AuthorizeResult.Cancelled

    override suspend fun refresh(refreshToken: String): RefreshResult =
        if (refreshes++ == 0) {
            RefreshResult.Granted(Tokens("access", Instant.fromEpochSeconds(0), "refresh", "id"))
        } else {
            RefreshResult.InvalidGrant
        }

    override suspend fun endSession(idToken: String?) = Unit
}

/** Restores a stored session and renews it whenever asked. */
private class GrantingAuthorizer : Authorizer {
    override suspend fun authorize(kind: AuthorizeKind, theme: String) = AuthorizeResult.Cancelled

    override suspend fun refresh(refreshToken: String): RefreshResult =
        RefreshResult.Granted(
            Tokens("access", Instant.fromEpochSeconds(4_000_000_000), "refresh", "id")
        )

    override suspend fun endSession(idToken: String?) = Unit
}

private const val APP_INVITE =
    "io.github.noahhhx.mimos://join?instance=https%3A%2F%2Fmimos.example.com&token=abc"

private class OneStoredSession : TokenStore {
    private var stored: StoredSession = StoredSession.Present("refresh", "id")

    override suspend fun load() = stored

    override suspend fun save(session: StoredSession.Present) {
        stored = session
    }

    override suspend fun clear() {
        stored = StoredSession.None
    }
}

class AppModelTest {
    @Test
    fun `the app is called Mimos`() {
        assertEquals("Mimos", APP_NAME)
    }

    @Test
    fun `a debug build opens and closes the gallery`() {
        val app = model(debug = true, TestScope())

        app.openGallery()
        assertTrue(app.gallery.value)
        app.closeGallery()
        assertFalse(app.gallery.value)
    }

    @Test
    fun `a release build never opens the gallery`() {
        val app = model(debug = false, TestScope())

        app.openGallery()

        assertFalse(app.gallery.value)
    }

    @Test
    fun `signing out sends the shell back to the kitchen`() {
        val app = model(debug = false, TestScope())
        app.shell.go(Destination.Log)

        app.signOut()

        assertEquals(Destination.Kitchen, app.shell.destination.value)
    }

    @Test
    fun `signing out sends the recipes tab back to a fresh list`() {
        val app = model(debug = false, TestScope())
        app.recipes.open(RecipeId("r1"))

        app.signOut()

        assertEquals(RecipeRoute.List, app.recipes.route.value)
    }

    @Test
    fun `a session that expires on its own forgets what the person had open`() = runTest {
        val app =
            model(debug = false, backgroundScope) { _ ->
                SessionModel(ExpiringAuthorizer(), OneStoredSession(), { "light" }, backgroundScope)
            }
        val session = app.session(instance)
        runCurrent()
        assertTrue(session.state.value is SessionState.SignedIn)
        app.shell.go(Destination.Recipes)
        app.recipes.open(RecipeId("r1"))
        app.recipes.cookBook.visit(RecipeId("r1"), "Soup")

        session.accessToken()
        runCurrent()

        assertTrue(session.state.value is SessionState.SignedOut)
        assertEquals(Destination.Kitchen, app.shell.destination.value)
        assertEquals(RecipeRoute.List, app.recipes.route.value)
        assertEquals(null, app.recipes.cookBook.title(RecipeId("r1")))
    }

    @Test
    fun `the system back button steps through a recipe trail only on the recipes tab`() = runTest {
        val app = model(debug = false, TestScope())
        assertFalse(app.goBack())

        app.shell.go(Destination.Recipes)
        app.recipes.open(RecipeId("r1"))
        assertTrue(app.canGoBack.first())

        app.shell.go(Destination.Plan)
        assertFalse(app.canGoBack.first())
        assertFalse(app.goBack())

        app.shell.go(Destination.Recipes)
        assertTrue(app.goBack())
        assertEquals(RecipeRoute.List, app.recipes.route.value)
        assertFalse(app.canGoBack.first())
    }

    @Test
    fun `an invite opened before connecting fills in its instance, then shows once signed in`() =
        runTest {
            val app =
                model(debug = false, backgroundScope) { _ ->
                    SessionModel(
                        GrantingAuthorizer(),
                        OneStoredSession(),
                        { "light" },
                        backgroundScope,
                    )
                }

            app.openInvite(APP_INVITE)

            assertEquals(ConnectState.Editing("https://mimos.example.com"), app.connect.state.value)
            assertEquals(Destination.Kitchen, app.shell.destination.value)

            app.session(instance)
            runCurrent()

            assertEquals(Destination.Household, app.shell.destination.value)
            assertEquals(
                HouseholdRoute.Join(InviteLink(instance.config.webUrl, "abc")),
                app.household.route.value,
            )
            assertTrue(app.canGoBack.first())
            assertTrue(app.goBack())
            assertEquals(HouseholdRoute.Page, app.household.route.value)
        }

    @Test
    fun `an invite opened while signed in shows at once, and only once`() = runTest {
        val app =
            model(debug = false, backgroundScope, OneInstance(instance)) { _ ->
                SessionModel(GrantingAuthorizer(), OneStoredSession(), { "light" }, backgroundScope)
            }
        val session = app.session(instance)
        runCurrent()
        assertTrue(session.state.value is SessionState.SignedIn)

        app.openInvite(" https://mimos.example.com/app/join/xyz ")

        assertEquals(Destination.Household, app.shell.destination.value)
        assertEquals(
            HouseholdRoute.Join(InviteLink(instance.config.webUrl, "xyz")),
            app.household.route.value,
        )
        app.goBack()
        session.accessToken()
        runCurrent()
        assertEquals(HouseholdRoute.Page, app.household.route.value)
    }

    @Test
    fun `switching to an invite's instance signs out and fills in its address`() = runTest {
        val app =
            model(debug = false, backgroundScope, OneInstance(instance)) { _ ->
                SessionModel(GrantingAuthorizer(), OneStoredSession(), { "light" }, backgroundScope)
            }
        app.session(instance)
        runCurrent()
        app.shell.go(Destination.Household)

        val other =
            InviteLink(
                parseWebAddress("https://other.example", false) as WebAddress,
                "theirs",
            )
        app.switchInstance(other)

        assertEquals(ConnectState.Editing("https://other.example"), app.connect.state.value)
        assertEquals(Destination.Kitchen, app.shell.destination.value)
    }

    @Test
    fun `a link that is not an invite, or one to plain http in a release build, does nothing`() {
        val app = model(debug = false, TestScope())

        app.openInvite("io.github.noahhhx.mimos://join?token=abc")
        app.openInvite(
            "io.github.noahhhx.mimos://join?instance=http%3A%2F%2Flocalhost%3A3000&token=a"
        )

        assertEquals(ConnectState.Editing(""), app.connect.state.value)
    }
}
