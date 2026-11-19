package io.github.noahhhx.mimos.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.noahhhx.mimos.instance.APP_VERSION
import io.github.noahhhx.mimos.instance.Version
import io.github.noahhhx.mimos.ui.Button
import io.github.noahhhx.mimos.ui.ButtonKind
import io.github.noahhhx.mimos.ui.Choice
import io.github.noahhhx.mimos.ui.ListRow
import io.github.noahhhx.mimos.ui.MimosTheme
import io.github.noahhhx.mimos.ui.PageHeader
import io.github.noahhhx.mimos.ui.Text
import io.github.noahhhx.mimos.ui.ThemeChoice
import io.github.noahhhx.mimos.ui.ThemeModel

/** A piece of software the app is built from, with the licences it is shared under. */
data class OpenSource(val name: String, val version: String, val licences: List<String>)

/**
 * What the web keeps in its header and profile menu and an app has to say for itself: the theme,
 * the connected instance and the version it runs, the way to another instance, and the app's own
 * version and licences. [serverVersion] is what the instance said last.
 */
@Composable
fun SettingsScreen(
    theme: ThemeModel,
    webOrigin: String,
    serverVersion: Version,
    licences: List<OpenSource>,
    onChangeInstance: () -> Unit,
) {
    val chosen by theme.state.collectAsState()
    val gutter = Modifier.padding(horizontal = MimosTheme.spacing.page)
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            Column(
                gutter.padding(top = MimosTheme.spacing.xxl),
                verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.xl),
            ) {
                PageHeader(title = "Settings", eyebrow = "Account")
                Section("Theme") {
                    Column {
                        for (choice in ThemeChoice.entries) {
                            Choice(choice.label, choice == chosen, { theme.choose(choice) })
                        }
                    }
                    Muted("Mimos uses light until you choose dark, whatever the phone is set to.")
                }
                Section("Instance") {
                    Text(webOrigin)
                    Muted("Runs Mimos $serverVersion.")
                    Button(
                        "Change instance",
                        onClick = onChangeInstance,
                        kind = ButtonKind.Secondary,
                    )
                    Muted("Signs you out, so you can connect to another Mimos.")
                }
                Section("This app") {
                    Text("Mimos for Android $APP_VERSION")
                    Muted(
                        "Mimos is free software under the GNU General Public License, version 3. " +
                            "It is built on the open source software below."
                    )
                }
                Text("Licences", style = MimosTheme.typography.heading)
            }
        }
        items(licences, key = { it.name }) { each ->
            ListRow(
                each.name,
                detail = each.licences.joinToString(", "),
                trailing = each.version.ifEmpty { null },
            )
        }
        item { Spacer(Modifier.height(MimosTheme.spacing.xxl)) }
    }
}

private val ThemeChoice.label: String
    get() =
        when (this) {
            ThemeChoice.Light -> "Light"
            ThemeChoice.Dark -> "Dark"
        }

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.m),
    ) {
        Text(title, style = MimosTheme.typography.heading)
        content()
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MimosTheme.typography.small, color = MimosTheme.colors.muted)
}
