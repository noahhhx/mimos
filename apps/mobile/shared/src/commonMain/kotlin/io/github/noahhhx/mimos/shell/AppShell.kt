package io.github.noahhhx.mimos.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import io.github.noahhhx.mimos.account.AccountModel
import io.github.noahhhx.mimos.account.AccountScreen
import io.github.noahhhx.mimos.api.models.MealPlanEntry
import io.github.noahhhx.mimos.fuel.FuelModel
import io.github.noahhhx.mimos.fuel.FuelScreen
import io.github.noahhhx.mimos.household.HouseholdModel
import io.github.noahhhx.mimos.household.HouseholdScreen
import io.github.noahhhx.mimos.household.InviteLink
import io.github.noahhhx.mimos.instance.Version
import io.github.noahhhx.mimos.kitchen.KitchenActions
import io.github.noahhhx.mimos.kitchen.KitchenModel
import io.github.noahhhx.mimos.kitchen.KitchenScreen
import io.github.noahhhx.mimos.log.LogModel
import io.github.noahhhx.mimos.log.LogScreen
import io.github.noahhhx.mimos.plan.PlanModel
import io.github.noahhhx.mimos.plan.PlanScreen
import io.github.noahhhx.mimos.plugins.PluginsModel
import io.github.noahhhx.mimos.plugins.PluginsScreen
import io.github.noahhhx.mimos.recipes.RecipesModel
import io.github.noahhhx.mimos.recipes.RecipesScreen
import io.github.noahhhx.mimos.settings.OpenSource
import io.github.noahhhx.mimos.settings.SettingsScreen
import io.github.noahhhx.mimos.shopping.ShoppingModel
import io.github.noahhhx.mimos.shopping.ShoppingScreen
import io.github.noahhhx.mimos.ui.BottomSheet
import io.github.noahhhx.mimos.ui.Brand
import io.github.noahhhx.mimos.ui.IconToggle
import io.github.noahhhx.mimos.ui.ListRow
import io.github.noahhhx.mimos.ui.MimosTheme
import io.github.noahhhx.mimos.ui.ProfileGlyph
import io.github.noahhhx.mimos.ui.Rule
import io.github.noahhhx.mimos.ui.Text
import io.github.noahhhx.mimos.ui.ThemeGlyph
import io.github.noahhhx.mimos.ui.ThemeModel

/** What the profile menu does besides going to a destination. */
class ShellActions(
    val onSignOut: () -> Unit,
    val onChangeInstance: () -> Unit,
    /** Shows a planned recipe in the Recipes tab. */
    val onOpenRecipe: (String) -> Unit,
    /** The Plan tab's picker for tonight's dinner. */
    val onPlanTonight: () -> Unit,
    /** The Plan tab at a planned meal. */
    val onSwapMeal: (MealPlanEntry) -> Unit,
    /** This week's shopping list. */
    val onOpenShopping: () -> Unit,
    /** Hands a link to the phone's share sheet. */
    val onShare: (String) -> Unit,
    /** Signs out and connects to the instance an invite is for. */
    val onSwitchInstance: (InviteLink) -> Unit,
    /** Puts the newer-app notice away until the app starts again. */
    val onDismissNewerApp: () -> Unit,
    /** Debug builds only: the component gallery. Null hides its entry. */
    val onOpenGallery: (() -> Unit)? = null,
)

/**
 * The signed-in app: a header with the brand and the two tools, the destination on screen, and the
 * tab bar along the bottom (a phone's thumb reaches it; the web's tabs sit under its header).
 * [webOrigin] is the instance's web address, which recipes and Settings name, and [serverVersion]
 * the version it runs. [newerApp] is that version while it is newer than the app's and the person
 * has not put the notice away.
 */
@Composable
fun AppShell(
    shell: ShellModel,
    recipes: RecipesModel,
    plan: PlanModel,
    household: HouseholdModel,
    account: AccountModel,
    plugins: PluginsModel,
    shopping: ShoppingModel,
    log: LogModel,
    fuel: FuelModel,
    kitchen: KitchenModel,
    theme: ThemeModel,
    profile: Profile,
    webOrigin: String,
    serverVersion: Version,
    newerApp: Version?,
    licences: List<OpenSource>,
    brandMark: Painter,
    actions: ShellActions,
) {
    val destination by shell.destination.collectAsState()
    val profileOpen by shell.profileOpen.collectAsState()
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        ShellHeader(
            brandMark,
            onToggleTheme = theme::toggle,
            onProfile = shell::openProfile,
            profileOpen,
        )
        Rule()
        newerApp?.let { NewerAppNotice(it, actions.onDismissNewerApp) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (destination) {
                Destination.Kitchen ->
                    KitchenScreen(
                        kitchen,
                        KitchenActions(
                            actions.onOpenRecipe,
                            actions.onPlanTonight,
                            actions.onSwapMeal,
                            actions.onOpenShopping,
                        ),
                    )
                Destination.Recipes -> RecipesScreen(recipes, webOrigin)
                Destination.Plan -> PlanScreen(plan, actions.onOpenRecipe)
                Destination.Shopping -> ShoppingScreen(shopping)
                Destination.Log -> LogScreen(log)
                Destination.Household ->
                    HouseholdScreen(household, actions.onShare, actions.onSwitchInstance)
                Destination.YourData -> AccountScreen(account)
                Destination.Plugins -> PluginsScreen(plugins)
                Destination.Fuel -> FuelScreen(fuel)
                Destination.Settings ->
                    SettingsScreen(
                        theme,
                        webOrigin,
                        serverVersion,
                        licences,
                        actions.onChangeInstance,
                    )
            }
        }
        TabBar(selected = destination, onSelect = shell::go)
    }
    if (profileOpen) {
        ProfileSheet(profile, shell, actions)
    }
}

@Composable
private fun ShellHeader(
    brandMark: Painter,
    onToggleTheme: () -> Unit,
    onProfile: () -> Unit,
    profileOpen: Boolean,
) {
    Row(
        Modifier.fillMaxWidth()
            .height(MimosTheme.spacing.headerHeight)
            .padding(horizontal = MimosTheme.spacing.page),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s),
    ) {
        Brand(brandMark, Modifier.weight(1f))
        IconToggle("Toggle light and dark mode", onToggleTheme) { ThemeGlyph() }
        IconToggle("Profile", onProfile, active = profileOpen) { ProfileGlyph() }
    }
}

@Composable
private fun ProfileSheet(
    profile: Profile,
    shell: ShellModel,
    actions: ShellActions,
) {
    BottomSheet(onDismiss = shell::closeProfile) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            when (profile) {
                Profile.Loading -> Text("Loading your profile…", color = MimosTheme.colors.muted)
                is Profile.Failed ->
                    Text("Could not load your profile.", color = MimosTheme.colors.danger)
                is Profile.Known -> {
                    Text(profile.displayName, style = MimosTheme.typography.person)
                    Text(
                        memberSinceText(profile.memberSince),
                        style = MimosTheme.typography.small,
                        color = MimosTheme.colors.muted,
                    )
                }
            }
        }
        Rule()
        for (entry in Destination.profileMenu) {
            ListRow(entry.label, onClick = { shell.go(entry) })
        }
        actions.onOpenGallery?.let { open ->
            ListRow(
                "Component gallery",
                onClick = {
                    shell.closeProfile()
                    open()
                },
            )
        }
        ListRow(
            "Sign out",
            onClick = {
                shell.closeProfile()
                actions.onSignOut()
            },
        )
    }
}
