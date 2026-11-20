package io.github.noahhhx.mimos.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import io.github.noahhhx.mimos.ui.Button
import io.github.noahhhx.mimos.ui.ButtonKind
import io.github.noahhhx.mimos.ui.MimosTheme
import io.github.noahhhx.mimos.ui.Notice
import io.github.noahhhx.mimos.ui.NoticeKind
import io.github.noahhhx.mimos.ui.PageHeader
import io.github.noahhhx.mimos.ui.Text

/** Your data: export to a file the person places, and import one into an empty account. */
@Composable
fun AccountScreen(model: AccountModel) {
    val export by model.export.collectAsState()
    val import by model.import.collectAsState()
    val picker = rememberDocumentPicker(onCreated = model::saveTo, onOpened = model::pick)
    LaunchedEffect(export) {
        if (export is ExportState.Ready) model.choose()?.let { picker.create(it.name) }
    }
    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MimosTheme.spacing.page, vertical = MimosTheme.spacing.xxl),
        verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.xl),
    ) {
        PageHeader(title = "Your data", eyebrow = "Account")
        Section("Export") {
            Text(
                "Download your recipes, meal plans, shopping lists, food log, and fuel settings " +
                    "with the training you added by hand as one file. In a shared household, it " +
                    "holds the household's recipes and lists but only the meals you eat and your " +
                    "own food log. Keep it somewhere safe. You can import it into a fresh " +
                    "account, here or on another Mimos."
            )
            when (val state = export) {
                is ExportState.Failed -> Notice(state.message)
                is ExportState.Saved -> Notice("Saved ${state.name}.", kind = NoticeKind.Ok)
                else -> {}
            }
            Button(
                when (export) {
                    ExportState.Exporting -> "Exporting…"
                    is ExportState.Ready,
                    is ExportState.Choosing,
                    ExportState.Writing -> "Saving…"
                    else -> "Download export"
                },
                onClick = model::startExport,
                enabled = !export.inProgress,
            )
        }
        Section("Import") {
            Text(
                "Restore an export into this account. Import only works into an empty account, " +
                    "one with no recipes, ingredients of your own, plans, shopping lists, logged " +
                    "meals, fuel settings or training yet, so nothing here is ever overwritten or " +
                    "doubled up."
            )
            val importing = import.status == ImportStatus.Importing
            Text("Export file", style = MimosTheme.typography.label)
            Button(
                if (import.file == null) "Choose file" else "Choose another file",
                onClick = picker.open,
                kind = ButtonKind.Secondary,
                enabled = !importing,
            )
            Text(
                import.file?.name ?: "No file chosen",
                color = if (import.file == null) MimosTheme.colors.muted else MimosTheme.colors.fg,
            )
            when (val status = import.status) {
                is ImportStatus.Failed -> Notice(status.message)
                is ImportStatus.Done -> ImportDone(status)
                else -> {}
            }
            Button(
                if (importing) "Importing…" else "Import",
                onClick = model::startImport,
                enabled = import.file != null && !importing,
            )
        }
    }
}

@Composable
private fun ImportDone(done: ImportStatus.Done) {
    Column(verticalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s)) {
        Notice(importSummary(done.report), kind = NoticeKind.Ok)
        for (warning in done.report.warnings) {
            Row(horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.s)) {
                Text("•")
                Text(warning)
            }
        }
    }
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
