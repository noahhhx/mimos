package io.github.noahhhx.mimos.account

import io.github.noahhhx.mimos.api.apis.AccountApi
import io.github.noahhhx.mimos.api.models.ImportReport
import io.github.noahhhx.mimos.client.ApiProblem
import io.ktor.client.statement.readRawBytes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** An export as the server wrote it, byte for byte, and the name it is saved under. */
class ExportFile(val name: String, val bytes: ByteArray)

sealed interface ExportState {
    data object Idle : ExportState

    data object Exporting : ExportState

    /** The export is in hand, and the file picker should ask where it goes. */
    class Ready(val file: ExportFile) : ExportState

    /** The file picker is on screen. */
    class Choosing(val file: ExportFile) : ExportState

    data object Writing : ExportState

    data class Saved(val name: String) : ExportState

    data class Failed(val message: String) : ExportState
}

/** Whether an export is under way, from asking the server to the file being written. */
val ExportState.inProgress: Boolean
    get() =
        this is ExportState.Exporting ||
            this is ExportState.Ready ||
            this is ExportState.Choosing ||
            this is ExportState.Writing

sealed interface ImportStatus {
    data object Idle : ImportStatus

    data object Importing : ImportStatus

    data class Failed(val message: String) : ImportStatus

    data class Done(val report: ImportReport) : ImportStatus
}

/** The file chosen to import, if any, and how its import went. */
data class ImportState(val file: Document? = null, val status: ImportStatus = ImportStatus.Idle)

const val EXPORT_FAILED = "Could not export your data."
const val SAVE_FAILED = "Could not save the file. Try again, or choose another place."
const val READ_FAILED = "Could not read that file."
const val IMPORT_FAILED = "Could not import the file."

/**
 * The Your data screen, as the web's Account page (ADR-0011): export everything as one file saved
 * where the person picks, and restore such a file into an empty account. The export is kept as the
 * server sent it, never read into the app's models and written back, which would drop whatever a
 * newer server put in it. [onImported] runs after an import, since the recipes and plans the app
 * holds are then out of date.
 */
class AccountModel(
    private val scope: CoroutineScope,
    private val api: () -> AccountApi,
    private val documents: DocumentStore,
    private val onImported: () -> Unit,
) {
    private val mutableExport = MutableStateFlow<ExportState>(ExportState.Idle)
    val export: StateFlow<ExportState> = mutableExport.asStateFlow()

    private val mutableImport = MutableStateFlow(ImportState())
    val import: StateFlow<ImportState> = mutableImport.asStateFlow()

    private var job: Job? = null

    fun startExport() {
        if (busy()) return
        mutableExport.value = ExportState.Exporting
        job = scope.launch {
            mutableExport.value =
                try {
                    val bytes = api().exportAccount().response.readRawBytes()
                    ExportState.Ready(ExportFile(exportFileName(exportedAt(bytes)), bytes))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    ExportState.Failed(EXPORT_FAILED)
                }
        }
    }

    /** The file the picker should be opened for, once: a rotation must not open a second one. */
    fun choose(): ExportFile? {
        val ready = export.value as? ExportState.Ready ?: return null
        mutableExport.value = ExportState.Choosing(ready.file)
        return ready.file
    }

    /**
     * Writes the export to [document], the file the person chose, or drops it if they cancelled.
     */
    fun saveTo(document: Document?) {
        val choosing = export.value as? ExportState.Choosing ?: return
        if (document == null) {
            mutableExport.value = ExportState.Idle
            return
        }
        mutableExport.value = ExportState.Writing
        job = scope.launch {
            mutableExport.value =
                try {
                    documents.write(document, choosing.file.bytes)
                    ExportState.Saved(document.name)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    ExportState.Failed(SAVE_FAILED)
                }
        }
    }

    /** The file the person chose to import; a cancelled picker keeps the one chosen before. */
    fun pick(document: Document?) {
        if (document == null || busy()) return
        mutableImport.value = ImportState(document)
    }

    fun startImport() {
        val file = import.value.file ?: return
        if (busy()) return
        mutableImport.value = ImportState(file, ImportStatus.Importing)
        job = scope.launch {
            val status = importFrom(file)
            mutableImport.value = ImportState(file, status)
            if (status is ImportStatus.Done) onImported()
        }
    }

    private suspend fun importFrom(file: Document): ImportStatus {
        val text =
            try {
                documents.read(file).decodeToString()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return ImportStatus.Failed(READ_FAILED)
            }
        val document =
            when (val parsed = parseExportFile(text)) {
                is ParsedExport.Invalid -> return ImportStatus.Failed(parsed.message)
                is ParsedExport.Ok -> parsed.document
            }
        return try {
            ImportStatus.Done(api().importAccount(document).body())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (problem: ApiProblem) {
            ImportStatus.Failed(problem.detail ?: IMPORT_FAILED)
        } catch (_: Exception) {
            ImportStatus.Failed(IMPORT_FAILED)
        }
    }

    private fun busy(): Boolean =
        export.value.inProgress || import.value.status == ImportStatus.Importing

    /** Forgets everything, for when the person signs out and someone else may sign in. */
    fun reset() {
        job?.cancel()
        mutableExport.value = ExportState.Idle
        mutableImport.value = ImportState()
    }
}

private fun exportedAt(bytes: ByteArray): String =
    Json.parseToJsonElement(bytes.decodeToString())
        .jsonObject
        .getValue("exportedAt")
        .jsonPrimitive
        .content
