package io.github.noahhhx.mimos.gallery

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import io.github.noahhhx.mimos.instance.Version
import io.github.noahhhx.mimos.shell.Destination
import io.github.noahhhx.mimos.shell.NewerAppNotice
import io.github.noahhhx.mimos.shell.TabBar
import io.github.noahhhx.mimos.ui.BottomSheet
import io.github.noahhhx.mimos.ui.Brand
import io.github.noahhhx.mimos.ui.Button
import io.github.noahhhx.mimos.ui.ButtonKind
import io.github.noahhhx.mimos.ui.Choice
import io.github.noahhhx.mimos.ui.EmptyState
import io.github.noahhhx.mimos.ui.ErrorState
import io.github.noahhhx.mimos.ui.IconToggle
import io.github.noahhhx.mimos.ui.Label
import io.github.noahhhx.mimos.ui.ListRow
import io.github.noahhhx.mimos.ui.MimosDialog
import io.github.noahhhx.mimos.ui.MimosTheme
import io.github.noahhhx.mimos.ui.Notice
import io.github.noahhhx.mimos.ui.NoticeKind
import io.github.noahhhx.mimos.ui.PageHeader
import io.github.noahhhx.mimos.ui.ProfileGlyph
import io.github.noahhhx.mimos.ui.SelectField
import io.github.noahhhx.mimos.ui.Tabs
import io.github.noahhhx.mimos.ui.Tag
import io.github.noahhhx.mimos.ui.Text
import io.github.noahhhx.mimos.ui.TextField
import io.github.noahhhx.mimos.ui.ThemeGlyph
import io.github.noahhhx.mimos.ui.ThemeModel

/**
 * Every component on one scrolling page, with the theme toggle at its top so both themes can be
 * looked at. Debug builds only: [io.github.noahhhx.mimos.AppModel.openGallery] does nothing in a
 * release.
 */
@Composable
fun Gallery(theme: ThemeModel, brandMark: Painter, onClose: () -> Unit) {
    val colors = MimosTheme.colors
    val type = MimosTheme.typography
    val choice by theme.state.collectAsState()
    var dialog by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(Destination.Plan) }
    var pageTab by remember { mutableStateOf(0) }
    var field by rememberSaveable { mutableStateOf("mimos.example.com") }
    var broken by rememberSaveable { mutableStateOf("mimos") }
    var note by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf("g") }
    var calculated by rememberSaveable { mutableStateOf(true) }
    Column(
        Modifier.fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(MimosTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.l),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s),
        ) {
            Brand(brandMark, Modifier.weight(1f))
            IconToggle("Toggle light and dark mode", theme::toggle) { ThemeGlyph() }
            Button("Close", onClose, kind = ButtonKind.Secondary, small = true)
        }
        Text("Theme: ${choice.key}", style = type.small, color = colors.muted)

        PageHeader(
            title = "Component gallery",
            eyebrow = "Debug build",
            actions = {
                Button("Add", {}, small = true)
                Button("Edit", {}, kind = ButtonKind.Secondary, small = true)
            },
        )

        Section("Type")
        Text("Page title", style = type.title)
        Text("Screen title, Newsreader 28", style = type.screenTitle)
        Text("Heading, Newsreader 22 medium", style = type.heading)
        Text(
            "A lede in the serif, muted, for the line under a page title.",
            style = type.lede,
            color = colors.muted,
        )
        Text(
            "Body text in Public Sans at 17 with a line height of 1.6, so a paragraph of it reads easily on a phone held in one hand."
        )
        Text("Small text for notes and rows.", style = type.small, color = colors.muted)
        Text("Regular 400", style = type.body)
        Text(
            "Semibold 600",
            style = type.body.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        )
        Label("Label, eyebrow and section heading")
        Text("Subheading, Newsreader 19 medium", style = type.subheading)
        Text("A ticked row: a shopping item or an ingredient, 16", style = type.tickRow)

        Section("Buttons")
        Row(horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s)) {
            Button("Primary", {})
            Button("Secondary", {}, kind = ButtonKind.Secondary)
            Button("Danger", {}, kind = ButtonKind.Danger)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s)) {
            Button("Small", {}, small = true)
            Button("Small", {}, kind = ButtonKind.Secondary, small = true)
            Button("Small", {}, kind = ButtonKind.Danger, small = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s)) {
            Button("Disabled", {}, enabled = false)
            Button("Disabled", {}, kind = ButtonKind.Secondary, enabled = false)
            Button("Disabled", {}, kind = ButtonKind.Danger, enabled = false)
        }

        Section("Text fields")
        TextField(field, { field = it }, label = "Web address")
        TextField(
            broken,
            { broken = it },
            label = "Web address",
            error = "That is not a web address.",
        )
        TextField("Not editable", {}, label = "Disabled", enabled = false)
        TextField(
            note,
            { note = it },
            label = "Step 1",
            placeholder = "Several lines, for a step or a description",
            minLines = 2,
        )

        Section("Select and choices")
        SelectField(
            "Unit",
            selected = unit,
            options = listOf("", "g", "kg", "ml"),
            optionLabel = { if (it == "") "pieces" else it },
            onSelect = { unit = it },
        )
        Column {
            Choice("Calculate from ingredients", calculated, { calculated = true })
            Choice("Enter it myself", !calculated, { calculated = false })
        }

        Section("List rows")
        Column(Modifier.fillMaxWidth().border(MimosTheme.spacing.hairline, colors.line)) {
            ListRow("Tomato and basil soup", detail = "Soup, vegetarian", trailing = "35 min")
            ListRow(
                "Pressed rows fill with hover",
                detail = "Tap and hold me",
                trailing = "520 kcal",
                onClick = {},
            )
            ListRow("A row with only a title", onClick = {})
        }

        Section("Notices")
        Notice("That did not work. Check the address and try again.")
        Notice("Saved.", kind = NoticeKind.Ok)
        NewerAppNotice(Version(9, 9, 9), onDismiss = {})

        Section("Empty and error states")
        EmptyState(
            title = "No recipes yet",
            message = "Add your first recipe, or browse the library.",
            action = { Button("Browse the library", {}, kind = ButtonKind.Secondary) },
        )
        ErrorState("Mimos could not load your recipes.", onRetry = {})

        Section("Icon toggles")
        Row(horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s)) {
            IconToggle("Theme", {}) { ThemeGlyph() }
            IconToggle("Profile", {}) { ProfileGlyph() }
            IconToggle("Profile, open", {}, active = true) { ProfileGlyph() }
        }

        Section("Tab bar")
        TabBar(selected = tab, onSelect = { tab = it })

        Section("Tabs and tags")
        Tabs(
            listOf("My recipes (3)", "Library (13)"),
            selected = pageTab,
            onSelect = { pageTab = it },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.xs)) {
            Tag("breakfast")
            Tag("vegetarian")
            Tag("quick")
        }

        Section("Dialog and bottom sheet")
        Row(horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s)) {
            Button("Open dialog", { dialog = true }, kind = ButtonKind.Secondary)
            Button("Open sheet", { sheet = true }, kind = ButtonKind.Secondary)
        }
        Spacer(Modifier.height(MimosTheme.spacing.xxl))
        Box(Modifier.fillMaxWidth())
    }
    if (dialog) {
        MimosDialog(
            title = "Delete this recipe?",
            onDismiss = { dialog = false },
            actions = {
                Button("Cancel", { dialog = false }, kind = ButtonKind.Secondary)
                Button("Delete", { dialog = false }, kind = ButtonKind.Danger)
            },
        ) {
            Text(
                "It is removed from every plan it is in. This cannot be undone.",
                color = colors.muted,
            )
        }
    }
    if (sheet) {
        BottomSheet(onDismiss = { sheet = false }) {
            ListRow("A sheet holds rows", detail = "Edge to edge, with a 2dp amber top edge")
            ListRow("Close", onClick = { sheet = false })
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(MimosTheme.spacing.s))
    Label(title)
}
