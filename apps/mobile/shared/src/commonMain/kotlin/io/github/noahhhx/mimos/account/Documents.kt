package io.github.noahhhx.mimos.account

import androidx.compose.runtime.Composable

/** A file the person chose with the system's file picker (a content URI on Android). */
data class Document(val uri: String, val name: String)

/**
 * Reads and writes the files the person chose. Only those: the app asks for no storage permission,
 * and Android's Storage Access Framework grants each file on its own.
 */
interface DocumentStore {
    suspend fun write(document: Document, bytes: ByteArray)

    suspend fun read(document: Document): ByteArray
}

/**
 * The system's file pickers: [create] asks where a new file named as suggested goes, and [open]
 * asks for a JSON file to read.
 */
class DocumentPicker(val create: (suggestedName: String) -> Unit, val open: () -> Unit)

/** The pickers, which hand the chosen file to [onCreated] or [onOpened], or null when cancelled. */
@Composable
expect fun rememberDocumentPicker(
    onCreated: (Document?) -> Unit,
    onOpened: (Document?) -> Unit,
): DocumentPicker
