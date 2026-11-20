package io.github.noahhhx.mimos.account

import io.github.noahhhx.mimos.api.models.ImportReport
import io.github.noahhhx.mimos.client.MimosJson
import io.github.noahhhx.mimos.testcases.TestCases
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable private class FileNameCase(val exportedAt: String, val expected: String)

@Serializable
private class ParseCase(
    val text: String,
    val document: JsonObject? = null,
    val error: String? = null,
)

@Serializable private class SummaryCase(val report: ImportReport, val expected: String)

@Serializable
private class AccountDataCases(
    val exportFileNameCases: List<FileNameCase>,
    val parseExportFileCases: List<ParseCase>,
    val importSummaryCases: List<SummaryCase>,
)

private val cases =
    MimosJson.decodeFromString<AccountDataCases>(TestCases.read("account-data.json"))

class AccountDataTest {
    @Test
    fun `an export is saved under its date, as on the web`() {
        for (case in cases.exportFileNameCases) {
            assertEquals(case.expected, exportFileName(case.exportedAt), case.exportedAt)
        }
    }

    @Test
    fun `a JSON object is imported and anything else is refused, saying why`() {
        for (case in cases.parseExportFileCases) {
            val expected =
                case.error?.let { ParsedExport.Invalid(it) } ?: ParsedExport.Ok(case.document!!)
            assertEquals(expected, parseExportFile(case.text), case.text)
        }
    }

    @Test
    fun `an import counts each kind, singular and plural, as on the web`() {
        for (case in cases.importSummaryCases) {
            assertEquals(case.expected, importSummary(case.report))
        }
    }
}
