package io.github.noahhhx.mimos.account

import io.github.noahhhx.mimos.api.models.ImportReport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** The saved file's name: the export's date, so backups sort by when they were taken. */
fun exportFileName(exportedAt: String): String = "mimos-export-${exportedAt.take(10)}.json"

sealed interface ParsedExport {
    data class Ok(val document: JsonObject) : ParsedExport

    data class Invalid(val message: String) : ParsedExport
}

/**
 * An export file's contents, parsed for the import call, as the web's `parseExportFile`. The API
 * judges whether it is a Mimos export (and which version); this only rules out files that are not a
 * JSON object at all.
 */
fun parseExportFile(text: String): ParsedExport {
    val parsed =
        runCatching { Json.parseToJsonElement(text) }.getOrNull()
            ?: return ParsedExport.Invalid("That file is not a Mimos export: it is not valid JSON.")
    return if (parsed is JsonObject) ParsedExport.Ok(parsed)
    else ParsedExport.Invalid("That file is not a Mimos export.")
}

fun importSummary(report: ImportReport): String {
    val parts =
        listOf(
            count(report.recipes, "recipe"),
            count(report.plannedMeals, "planned meal"),
            count(report.shoppingLists, "shopping list"),
            count(report.mealLogs, "logged meal"),
            if (report.fuelProfile) "your fuel settings" else "no fuel settings",
            count(report.trainingSessions, "training session"),
        )
    return "Imported ${parts.dropLast(1).joinToString(", ")} and ${parts.last()}."
}

private fun count(n: Int, noun: String): String =
    when (n) {
        0 -> "no ${noun}s"
        1 -> "1 $noun"
        else -> "$n ${noun}s"
    }
