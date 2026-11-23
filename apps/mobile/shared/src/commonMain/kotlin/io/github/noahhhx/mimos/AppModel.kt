package io.github.noahhhx.mimos

import io.github.noahhhx.mimos.account.AccountModel
import io.github.noahhhx.mimos.account.DocumentStore
import io.github.noahhhx.mimos.api.apis.AccountApi
import io.github.noahhhx.mimos.api.apis.FoodProductsApi
import io.github.noahhhx.mimos.api.apis.FuelApi
import io.github.noahhhx.mimos.api.apis.HouseholdApi
import io.github.noahhhx.mimos.api.apis.IntervalsIcuApi
import io.github.noahhhx.mimos.api.apis.LoggingApi
import io.github.noahhhx.mimos.api.apis.PlanningApi
import io.github.noahhhx.mimos.api.apis.PluginSettingsApi
import io.github.noahhhx.mimos.api.apis.PluginsApi
import io.github.noahhhx.mimos.api.apis.RecipeCollectionsApi
import io.github.noahhhx.mimos.api.apis.RecipePicturesApi
import io.github.noahhhx.mimos.api.apis.RecipesApi
import io.github.noahhhx.mimos.api.apis.ShoppingListsApi
import io.github.noahhhx.mimos.api.models.MealPlanEntry
import io.github.noahhhx.mimos.client.MimosHttp
import io.github.noahhhx.mimos.client.NetworkWatcher
import io.github.noahhhx.mimos.connect.ConnectModel
import io.github.noahhhx.mimos.connect.ConnectState
import io.github.noahhhx.mimos.fuel.FuelModel
import io.github.noahhhx.mimos.household.HouseholdModel
import io.github.noahhhx.mimos.household.HouseholdRoute
import io.github.noahhhx.mimos.household.InviteLink
import io.github.noahhhx.mimos.household.parseInviteLink
import io.github.noahhhx.mimos.instance.Instance
import io.github.noahhhx.mimos.instance.Version
import io.github.noahhhx.mimos.kitchen.KitchenModel
import io.github.noahhhx.mimos.log.LogModel
import io.github.noahhhx.mimos.plan.PlanModel
import io.github.noahhhx.mimos.plan.PlanRoute
import io.github.noahhhx.mimos.plan.PlanViewStore
import io.github.noahhhx.mimos.plugins.PluginsModel
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
import io.github.noahhhx.mimos.shopping.ShoppingModel
import io.github.noahhhx.mimos.shopping.ShoppingStore
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
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

/**
 * What the app keeps while it is open: the theme, the connection, the session of the connected
 * instance with its HTTP client, and where the person is in the app. A platform holds one across
 * screen rotations, so a connect, a sign-in, or a request in flight is not lost. [debug] builds may
 * open the component gallery, and take invites to `http://localhost`, as they connect to it.
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
    documents: DocumentStore,
    shoppingStore: ShoppingStore,
    network: NetworkWatcher,
    private val createSession: (Instance) -> SessionModel,
    today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
    now: () -> LocalDateTime = {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    },
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
            collections = { connected().api(::RecipeCollectionsApi) },
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
            plugins = { connected().api(::PluginsApi) },
            logging = { connected().api(::LoggingApi) },
            foodProducts = { connected().api(::FoodProductsApi) },
            fuel = { connected().api(::FuelApi) },
            today = today,
        )

    val shopping =
        ShoppingModel(
            scope,
            api = { connected().api(::ShoppingListsApi) },
            today = today,
            store = shoppingStore,
            network = network,
        )

    val log =
        LogModel(
            scope,
            logging = { connected().api(::LoggingApi) },
            recipes = { connected().api(::RecipesApi) },
            foodProducts = { connected().api(::FoodProductsApi) },
            fuel = { connected().api(::FuelApi) },
            today = today,
        )

    val fuel =
        FuelModel(
            scope,
            fuel = { connected().api(::FuelApi) },
            intervalsIcu = { connected().api(::IntervalsIcuApi) },
            onChanged = plan::changedElsewhere,
        )

    val kitchen =
        KitchenModel(
            scope,
            planning = { connected().api(::PlanningApi) },
            plugins = { connected().api(::PluginsApi) },
            recipes = { connected().api(::RecipesApi) },
            shopping = { connected().api(::ShoppingListsApi) },
            now = now,
            onPlanned = plan::changedElsewhere,
        )

    val plugins =
        PluginsModel(
            scope,
            api = { connected().api(::PluginSettingsApi) },
            onChanged = plan::pluginsChanged,
        )

    val household =
        HouseholdModel(
            scope,
            api = { connected().api(::HouseholdApi) },
            connected = { (current ?: error("not connected")).instance.config.webUrl },
            allowLocalhostHttp = debug,
            onChanged = {
                recipes.reset()
                plan.reset()
                kitchen.reset()
                plugins.reset()
                shopping.reset()
                log.reset()
            },
        )

    val account =
        AccountModel(
            scope,
            api = { connected().api(::AccountApi) },
            documents = documents,
            onImported = {
                recipes.reset()
                plan.reset()
                kitchen.reset()
                fuel.reset()
                shopping.reset()
                log.reset()
            },
        )

    init {
        scope.launch {
            network.online.collect { online ->
                val session = current?.session
                if (!online || session == null) return@collect
                when (session.state.value) {
                    SessionState.Unreachable -> session.retry()
                    is SessionState.SignedIn -> shopping.reconnected()
                    else -> {}
                }
            }
        }
    }

    /** An invite opened from a link, held until someone is signed in to see it. */
    private var pendingInvite: InviteLink? = null

    /**
     * Opens an invite from a link to the app. Before an instance is connected, its web address is
     * filled in for the person to connect to; the invite shows once they are signed in.
     */
    fun openInvite(link: String) {
        val invite = parseInviteLink(link, allowLocalhostHttp = debug) ?: return
        pendingInvite = invite
        if (connect.state.value is ConnectState.Addressing) connect.edit(invite.instance.origin)
        if (current?.session?.state?.value is SessionState.SignedIn) showPendingInvite()
    }

    /**
     * Signs out of the connected instance and fills in [invite]'s, to connect and sign in there.
     */
    fun switchInstance(invite: InviteLink) {
        changeInstance()
        pendingInvite = invite
        connect.edit(invite.instance.origin)
    }

    private fun showPendingInvite() {
        val invite = pendingInvite ?: return
        pendingInvite = null
        shell.go(Destination.Household)
        household.openJoin(invite)
    }

    /** Shows a planned recipe in the Recipes tab. */
    fun openRecipe(recipeId: String) {
        shell.go(Destination.Recipes)
        recipes.show(RecipeId(recipeId))
    }

    /** The Plan tab's picker for tonight's dinner. */
    fun planTonight() {
        shell.go(Destination.Plan)
        plan.planTonight()
    }

    /** The Plan tab at [entry], to change or remove it. */
    fun swapMeal(entry: MealPlanEntry) {
        shell.go(Destination.Plan)
        plan.showMeal(entry)
    }

    /** This week's shopping list. */
    fun openShopping() {
        shell.go(Destination.Shopping)
        shopping.thisWeek()
    }

    private val storedShoppingOpen = MutableStateFlow(false)

    /** Whether the No answer page shows the shopping list kept on the phone (ADR-0034). */
    val storedShopping: StateFlow<Boolean> = storedShoppingOpen.asStateFlow()

    fun openStoredShopping() {
        storedShoppingOpen.value = true
    }

    fun closeStoredShopping() {
        storedShoppingOpen.value = false
    }

    /** Whether the system's back button has a step to take inside the app. */
    val canGoBack: Flow<Boolean> =
        combine(shell.destination, recipes.route, plan.route, household.route, storedShopping) {
                destination,
                recipe,
                planned,
                invite,
                stored ->
                if (stored) return@combine true
                when (destination) {
                    Destination.Recipes -> recipe != RecipeRoute.List
                    Destination.Plan -> planned != PlanRoute.Week
                    Destination.Household -> invite != HouseholdRoute.Page
                    else -> false
                }
            }
            .distinctUntilChanged()

    /** Takes that step. False when there is none, and the system should leave the app. */
    fun goBack(): Boolean {
        if (storedShoppingOpen.value) {
            closeStoredShopping()
            return true
        }
        return when (shell.destination.value) {
            Destination.Recipes -> recipes.back()
            Destination.Plan -> plan.back()
            Destination.Household -> household.back()
            else -> false
        }
    }

    private val dismissedNewer = MutableStateFlow<Version?>(null)

    /**
     * The server version whose newer-app notice the person put away, until the app starts again.
     */
    val dismissedNewerApp: StateFlow<Version?> = dismissedNewer.asStateFlow()

    fun dismissNewerApp(serverVersion: Version) {
        dismissedNewer.value = serverVersion
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
            session.state.collect {
                when (it) {
                    is SessionState.SignedOut -> forgetPerson()
                    is SessionState.SignedIn -> {
                        closeStoredShopping()
                        showPendingInvite()
                        shopping.reconnected()
                    }
                    else -> {}
                }
            }
        }
        return Connection(instance, session, http, watch).also { current = it }
    }

    fun signOut() {
        current?.session?.signOut()
        forgetPerson()
    }

    private fun forgetPerson() {
        closeStoredShopping()
        shell.reset()
        recipes.reset()
        plan.reset()
        kitchen.reset()
        plugins.reset()
        shopping.reset()
        log.reset()
        fuel.reset()
        household.reset()
        account.reset()
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
