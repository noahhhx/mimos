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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import io.github.noahhhx.mimos.recipes.RecipesModel
import io.github.noahhhx.mimos.recipes.RecipesScreen
import io.github.noahhhx.mimos.ui.BottomSheet
import io.github.noahhhx.mimos.ui.Brand
import io.github.noahhhx.mimos.ui.Button
import io.github.noahhhx.mimos.ui.ButtonKind
import io.github.noahhhx.mimos.ui.EmptyState
import io.github.noahhhx.mimos.ui.IconToggle
import io.github.noahhhx.mimos.ui.ListRow
import io.github.noahhhx.mimos.ui.MimosTheme
import io.github.noahhhx.mimos.ui.PageHeader
import io.github.noahhhx.mimos.ui.ProfileGlyph
import io.github.noahhhx.mimos.ui.Rule
import io.github.noahhhx.mimos.ui.Text
import io.github.noahhhx.mimos.ui.ThemeGlyph
import io.github.noahhhx.mimos.ui.ThemeModel

/** What the profile menu does besides going to a destination. */
class ShellActions(
    val onSignOut: () -> Unit,
    val onChangeInstance: () -> Unit,
    /** Debug builds only: the component gallery. Null hides its entry. */
    val onOpenGallery: (() -> Unit)? = null,
)

/**
 * The signed-in app: a header with the brand and the two tools, the destination on screen, and the
 * tab bar along the bottom (a phone's thumb reaches it; the web's tabs sit under its header).
 * [webOrigin] is the instance's web address, where screens not yet in the app send people.
 */
@Composable
fun AppShell(
    shell: ShellModel,
    recipes: RecipesModel,
    theme: ThemeModel,
    profile: Profile,
    webOrigin: String,
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
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (destination) {
                Destination.Recipes -> RecipesScreen(recipes, webOrigin)
                else ->
                    Column(
                        Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(
                                horizontal = MimosTheme.spacing.page,
                                vertical = MimosTheme.spacing.xxl,
                            )
                    ) {
                        ComingToTheApp(destination, webOrigin)
                    }
            }
        }
        TabBar(selected = destination, onSelect = shell::go)
    }
    if (profileOpen) {
        ProfileSheet(profile, shell, actions, webOrigin)
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
    webOrigin: String,
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
        ListRow(
            "Change instance",
            detail = webOrigin,
            onClick = {
                shell.closeProfile()
                actions.onChangeInstance()
            },
        )
    }
}

/** The stand-in for a screen the app has not built yet: its header, and the way to the web's. */
@Composable
private fun ComingToTheApp(destination: Destination, webOrigin: String) {
    val uriHandler = LocalUriHandler.current
    PageHeader(title = destination.label)
    EmptyState(
        title = "Coming to the app",
        message =
            "${destination.label} is not in the app yet. You can use it on the web meanwhile.",
        action = {
            Button(
                "Open on the web",
                onClick = { uriHandler.openUri(destination.webUrl(webOrigin)) },
                kind = ButtonKind.Secondary,
            )
        },
    )
}
