package io.github.noahhhx.mimos.plugins

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.noahhhx.mimos.api.models.UserPlugin
import io.github.noahhhx.mimos.ui.MimosTheme
import io.github.noahhhx.mimos.ui.Notice
import io.github.noahhhx.mimos.ui.PageHeader
import io.github.noahhhx.mimos.ui.Rule
import io.github.noahhhx.mimos.ui.Text
import io.github.noahhhx.mimos.ui.TickBox

/**
 * The Plugins screen, from the profile menu: what plugins are, then the instance's plugins, each
 * with the household's switch and a link to its homepage.
 */
@Composable
fun PluginsScreen(model: PluginsModel) {
    LaunchedEffect(Unit) { model.load() }
    val state by model.state.collectAsState()
    val saving by model.saving.collectAsState()
    val error by model.error.collectAsState()
    val gutter = Modifier.padding(horizontal = MimosTheme.spacing.page)
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = MimosTheme.spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.xl),
    ) {
        PageHeader(title = "Plugins", eyebrow = "Account", modifier = gutter)
        Section("What plugins are", gutter) {
            Text(
                "Plugins are add-ons that whoever runs this Mimos chose to install, bringing " +
                    "features beyond what Mimos does on its own. Country of the Week, for " +
                    "example, spins a wheel to pick a country for your week and suggests dishes " +
                    "from it."
            )
            Text(
                "Every plugin starts off. Turning one on lets it remember things about you, under " +
                    "a pseudonym rather than your name or account. Turn on the ones you want " +
                    "below, and turn them off again at any time."
            )
            Text(
                "If you share a household, a plugin is on or off for everyone in it, whoever " +
                    "turned it on, and it knows the household by one pseudonym."
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.m)) {
            Text("On this Mimos", gutter, style = MimosTheme.typography.heading)
            when (val current = state) {
                PluginsState.Loading ->
                    Text("Loading plugins…", gutter, color = MimosTheme.colors.muted)
                PluginsState.Failed -> Notice("Could not load plugins.", gutter)
                is PluginsState.Loaded ->
                    if (current.plugins.isEmpty()) {
                        Text(
                            "No plugins are installed on this Mimos right now.",
                            gutter,
                            color = MimosTheme.colors.muted,
                        )
                    } else {
                        Column {
                            Rule()
                            for (plugin in current.plugins) {
                                PluginRow(plugin, saving == plugin.id) {
                                    model.setEnabled(plugin, it)
                                }
                            }
                        }
                    }
            }
            error?.let { Notice(it, gutter) }
        }
    }
}

@Composable
private fun Section(
    title: String,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.m)) {
        Text(title, style = MimosTheme.typography.heading)
        content()
    }
}

/** A plugin's switch, the whole row its target as the web's label is, then its homepage link. */
@Composable
private fun PluginRow(plugin: UserPlugin, saving: Boolean, onChange: (Boolean) -> Unit) {
    val colors = MimosTheme.colors
    val uriHandler = LocalUriHandler.current
    val homepage = pluginHomepage(plugin.homepageUrl)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth()
                .toggleable(
                    value = plugin.enabled,
                    enabled = !saving,
                    role = Role.Checkbox,
                    onValueChange = onChange,
                )
                .alpha(if (saving) 0.6f else 1f)
                .padding(horizontal = MimosTheme.spacing.page, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TickBox(plugin.enabled)
            Text(plugin.name, Modifier.weight(1f))
            Text(
                if (plugin.enabled) "On" else "Off",
                style = MimosTheme.typography.small,
                color = colors.muted,
            )
        }
        if (homepage != null) {
            Text(
                "About ${plugin.name}",
                Modifier.padding(horizontal = MimosTheme.spacing.page)
                    .clickable(role = Role.Button) { uriHandler.openUri(homepage) }
                    .padding(bottom = 14.dp),
                style = MimosTheme.typography.small.copy(textDecoration = TextDecoration.Underline),
                color = colors.accent,
            )
        }
        Rule()
    }
}
