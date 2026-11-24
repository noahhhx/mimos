package io.github.noahhhx.mimos.account

import io.github.noahhhx.mimos.api.apis.AccountApi
import io.github.noahhhx.mimos.client.MimosHttp
import io.github.noahhhx.mimos.client.MimosJson
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

private const val API = "https://api.mimos.example.com"

/** A field this app's models do not know: an export must keep it all the same. */
private const val EXPORT =
    """{"format":"mimos.export","version":8,"exportedAt":"2026-11-19T22:10:00Z","recipes":[],""" +
        """"fromANewerServer":{"kept":true}}"""

private const val REPORT =
    """{"sourceVersion":8,"recipes":2,"plannedMeals":0,"shoppingLists":1,"mealLogs":0,""" +
        """"fuelProfile":false,"trainingSessions":0,"recipeCollections":0,""" +
        """"warnings":["A picture was left out."]}"""

/** Files the person chose, by URI; a URI in [unreadable] or [unwritable] fails. */
internal class MemoryDocuments : DocumentStore {
    val files = mutableMapOf<String, ByteArray>()
    val unreadable = mutableSetOf<String>()
    val unwritable = mutableSetOf<String>()

    override suspend fun write(document: Document, bytes: ByteArray) {
        if (document.uri in unwritable) error("read-only")
        files[document.uri] = bytes
    }

    override suspend fun read(document: Document): ByteArray {
        if (document.uri in unreadable) error("gone")
        return files[document.uri] ?: error("no such file")
    }
}

private class AccountServer {
    var export: Pair<HttpStatusCode, String> = HttpStatusCode.OK to EXPORT
    var import: Pair<HttpStatusCode, String> = HttpStatusCode.OK to REPORT
    val imported = mutableListOf<String>()

    val engine = MockEngine { request ->
        val (status, body) =
            when (request.url.encodedPath) {
                "/api/v1/account/export" -> export
                "/api/v1/account/import" -> {
                    imported += (request.body as TextContent).text
                    import
                }
                else -> error("unexpected ${request.url}")
            }
        val type = if (status.value < 300) "application/json" else "application/problem+json"
        respond(body, status, headersOf(HttpHeaders.ContentType, type))
    }
}

private class Fixture(scope: TestScope) {
    val server = AccountServer()
    val documents = MemoryDocuments()
    var imports = 0
    private val http = MimosHttp(server.engine, API)
    val model = AccountModel(scope, { http.api(::AccountApi) }, documents, { imports++ })

    suspend fun exported(): ExportState {
        model.startExport()
        return model.export.first { it !is ExportState.Exporting }
    }

    suspend fun imported(name: String, text: String): ImportState {
        documents.files["content://$name"] = text.encodeToByteArray()
        model.pick(Document("content://$name", name))
        model.startImport()
        return model.import.first { it.status !is ImportStatus.Importing }
    }
}

private val chosen = Document("content://downloads/7", "mimos-export-2026-11-19.json")

class AccountModelTest {
    @Test
    fun `an export is saved byte for byte, under its date, where the person chose`() = runTest {
        val fixture = Fixture(this)

        val ready = assertIs<ExportState.Ready>(fixture.exported())
        assertEquals("mimos-export-2026-11-19.json", ready.file.name)
        assertEquals("mimos-export-2026-11-19.json", fixture.model.choose()?.name)
        fixture.model.saveTo(chosen)

        assertEquals(
            ExportState.Saved("mimos-export-2026-11-19.json"),
            fixture.model.export.first { it is ExportState.Saved },
        )
        assertEquals(EXPORT, fixture.documents.files.getValue(chosen.uri).decodeToString())
    }

    @Test
    fun `the file picker opens once for an export, however often the screen asks`() = runTest {
        val fixture = Fixture(this)
        fixture.exported()

        fixture.model.choose()

        assertNull(fixture.model.choose())
        assertIs<ExportState.Choosing>(fixture.model.export.value)
    }

    @Test
    fun `cancelling the file picker writes nothing`() = runTest {
        val fixture = Fixture(this)
        fixture.exported()
        fixture.model.choose()

        fixture.model.saveTo(null)

        assertEquals(ExportState.Idle, fixture.model.export.value)
        assertTrue(fixture.documents.files.isEmpty())
    }

    @Test
    fun `a failed export or save says which`() = runTest {
        val fixture = Fixture(this)
        fixture.server.export = HttpStatusCode.InternalServerError to """{"title":"Oops"}"""
        assertEquals(ExportState.Failed(EXPORT_FAILED), fixture.exported())

        fixture.server.export = HttpStatusCode.OK to EXPORT
        fixture.exported()
        fixture.model.choose()
        fixture.documents.unwritable += chosen.uri
        fixture.model.saveTo(chosen)

        assertEquals(
            ExportState.Failed(SAVE_FAILED),
            fixture.model.export.first { it is ExportState.Failed },
        )
    }

    @Test
    fun `an import sends the file as it is and reports what came over`() = runTest {
        val fixture = Fixture(this)

        val state = fixture.imported("backup.json", EXPORT)

        assertEquals("backup.json", state.file?.name)
        val done = assertIs<ImportStatus.Done>(state.status)
        assertEquals(listOf("A picture was left out."), done.report.warnings)
        assertEquals(
            MimosJson.parseToJsonElement(EXPORT),
            MimosJson.parseToJsonElement(fixture.server.imported.single()),
        )
        assertEquals(1, fixture.imports)
    }

    @Test
    fun `a server from before recipe tabs reports an import without counting them`() = runTest {
        val fixture = Fixture(this)
        fixture.server.import =
            HttpStatusCode.OK to REPORT.replace(""""recipeCollections":0,""", "")

        val state = fixture.imported("backup.json", EXPORT)

        val done = assertIs<ImportStatus.Done>(state.status)
        assertEquals(2, done.report.recipes)
    }

    @Test
    fun `an account that is not empty is told so in the API's words`() = runTest {
        val fixture = Fixture(this)
        val detail =
            "Import only works into an empty account: this one already has recipes, plans, " +
                "shopping lists, or logged meals."
        fixture.server.import =
            HttpStatusCode.Conflict to """{"title":"Conflict","detail":"$detail"}"""

        assertEquals(ImportStatus.Failed(detail), fixture.imported("backup.json", EXPORT).status)
        assertEquals(0, fixture.imports)
    }

    @Test
    fun `a failure without a problem's detail falls back to the web's words`() = runTest {
        val fixture = Fixture(this)
        fixture.server.import = HttpStatusCode.BadGateway to "upstream"

        assertEquals(ImportStatus.Failed(IMPORT_FAILED), fixture.imported("b.json", EXPORT).status)
    }

    @Test
    fun `a file that is not JSON is refused before anything is sent`() = runTest {
        val fixture = Fixture(this)

        val state = fixture.imported("notes.txt", "recipes, plans")

        assertEquals(
            ImportStatus.Failed("That file is not a Mimos export: it is not valid JSON."),
            state.status,
        )
        assertTrue(fixture.server.imported.isEmpty())
    }

    @Test
    fun `a file that cannot be read says so`() = runTest {
        val fixture = Fixture(this)
        fixture.documents.unreadable += "content://gone.json"

        assertEquals(ImportStatus.Failed(READ_FAILED), fixture.imported("gone.json", EXPORT).status)
    }

    @Test
    fun `a cancelled picker keeps the file chosen before`() = runTest {
        val fixture = Fixture(this)
        fixture.model.pick(Document("content://a", "a.json"))

        fixture.model.pick(null)

        assertEquals("a.json", fixture.model.import.value.file?.name)
    }

    @Test
    fun `signing out forgets the export and the chosen file`() = runTest {
        val fixture = Fixture(this)
        fixture.exported()
        fixture.model.pick(Document("content://a", "a.json"))

        fixture.model.reset()

        assertEquals(ExportState.Idle, fixture.model.export.value)
        assertEquals(ImportState(), fixture.model.import.value)
    }
}
