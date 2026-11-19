package io.github.noahhhx.mimos

import io.github.noahhhx.mimos.api.apis.HouseholdApi
import io.github.noahhhx.mimos.api.apis.PlanningApi
import io.github.noahhhx.mimos.api.apis.RecipePicturesApi
import io.github.noahhhx.mimos.api.apis.RecipesApi
import io.github.noahhhx.mimos.client.MimosHttp
import io.github.noahhhx.mimos.connect.ConnectModel
import io.github.noahhhx.mimos.connect.ConnectState
import io.github.noahhhx.mimos.instance.Instance
import io.github.noahhhx.mimos.plan.PlanModel
import io.github.noahhhx.mimos.plan.PlanRoute
import io.github.noahhhx.mimos.plan.PlanViewStore
import io.github.noahhhx.mimos.recipes.RecipeId
import io.github.noahhhx.mimos.recipes.RecipeRoute
import io.github.noahhhx.mimos.recipes.RecipeSortStore
import io.github.noahhhx.mimos.recipes.RecipesModel
import io.github.noahhhx.mimos.recipes.pictures.PictureShrinker
import io.github.noahhhx.mimos.recipes.pictures.RecipePictures
import io.github.noahhhx.mimos.session.SessionModel
import io.github.noahhhx.mimos.session.SessionState
import io.github.noahhhx.mimos.shell.Destination
import io.github.noahhhx.mimos.shell.ShellModel
import io.github.noahhhx.mimos.ui.ThemeModel
import io.ktor.client.engine.HttpClientEngine
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * What the app keeps while it is open: the theme, the connection, the session of the connected
 * instance with its HTTP client, and where the person is in the app. A platform holds one across
 * screen rotations, so a connect, a sign-in, or a request in flight is not lost. [debug] builds may
 * open the component gallery.
 */
class AppModel(
    private val scope: CoroutineScope,
    val connect: ConnectModel,
    val theme: ThemeModel,
    val debug: Boolean,
    private val engine: HttpClientEngine,
    recipeSortStore: RecipeSortStore,
    planViewStore: PlanViewStore,
    pictureShrinker: PictureShrinker,
    private val createSession: (Instance) -> SessionModel,
    today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
) {
    val shell = ShellModel()

    private class Connection(
        val instance: Instance,
        val session: SessionModel,
        val http: MimosHttp,
        val watch: Job,
    ) {
        fun close() {
            watch.cancel()
            http.close()
        }
    }

    private var current: Connection? = null

    private fun connected(): MimosHttp = (current ?: error("not connected")).http

    val recipes =
        RecipesModel(
            scope,
            recipeSortStore,
            api = { connected().api(::RecipesApi) },
            pictures = RecipePictures({ connected().api(::RecipePicturesApi) }),
            shrinker = pictureShrinker,
        )

    val plan =
        PlanModel(
            scope,
            planViewStore,
            planning = { connected().api(::PlanningApi) },
            household = { connected().api(::HouseholdApi) },
            recipes = { connected().api(::RecipesApi) },
            today = today,
        )

    /** Shows a planned recipe in the Recipes tab. */
    fun openRecipe(recipeId: String) {
        shell.go(Destination.Recipes)
        recipes.show(RecipeId(recipeId))
    }

    /** Whether the system's back button has a step to take inside the app. */
    val canGoBack: Flow<Boolean> =
        combine(shell.destination, recipes.route, plan.route) { destination, recipe, planned ->
                when (destination) {
                    Destination.Recipes -> recipe != RecipeRoute.List
                    Destination.Plan -> planned != PlanRoute.Week
                    else -> false
                }
            }
            .distinctUntilChanged()

    /** Takes that step. False when there is none, and the system should leave the app. */
    fun goBack(): Boolean =
        when (shell.destination.value) {
            Destination.Recipes -> recipes.back()
            Destination.Plan -> plan.back()
            else -> false
        }

    private val galleryOpen = MutableStateFlow(false)
    val gallery: StateFlow<Boolean> = galleryOpen.asStateFlow()

    fun connect() {
        scope.launch { connect.connect() }
    }

    fun session(instance: Instance): SessionModel = connection(instance).session

    /** The HTTP client for [instance], open until the instance changes. */
    fun http(instance: Instance): MimosHttp = connection(instance).http

    private fun connection(instance: Instance): Connection {
        current
            ?.takeIf { it.instance == instance }
            ?.let {
                return it
            }
        current?.close()
        val session = createSession(instance)
        val http = MimosHttp(engine, instance.config.apiUrl, session::accessToken)
        // A session can end without signOut (its refresh token refused), and whoever signs in
        // next must not see what the last person had open.
        val watch = scope.launch {
            session.state.collect { if (it is SessionState.SignedOut) forgetPerson() }
        }
        return Connection(instance, session, http, watch).also { current = it }
    }

    fun signOut() {
        current?.session?.signOut()
        forgetPerson()
    }

    private fun forgetPerson() {
        shell.reset()
        recipes.reset()
        plan.reset()
    }

    /** Signs out of the connected instance, then forgets it so another can be typed in. */
    fun changeInstance() {
        val connected = connect.state.value as? ConnectState.Connected ?: return
        session(connected.instance).signOut()
        current?.close()
        current = null
        forgetPerson()
        connect.disconnect()
    }

    /** Release builds never open it. */
    fun openGallery() {
        if (debug) galleryOpen.value = true
    }

    fun closeGallery() {
        galleryOpen.value = false
    }
}
