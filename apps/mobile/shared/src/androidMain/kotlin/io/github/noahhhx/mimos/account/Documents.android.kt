package io.github.noahhhx.mimos.account

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** [DocumentStore] through the content resolver, which reaches only the files the person chose. */
class AndroidDocumentStore(private val context: Context) : DocumentStore {
    override suspend fun write(document: Document, bytes: ByteArray) =
        withContext(Dispatchers.IO) {
            // "wt" truncates: a file the person chose to replace must not keep the old one's tail.
            val stream =
                context.contentResolver.openOutputStream(document.uri.toUri(), "wt")
                    ?: throw FileNotFoundException(document.name)
            stream.use { it.write(bytes) }
        }

    override suspend fun read(document: Document): ByteArray =
        withContext(Dispatchers.IO) {
            val stream =
                context.contentResolver.openInputStream(document.uri.toUri())
                    ?: throw FileNotFoundException(document.name)
            stream.use { it.readBytes() }
        }
}

/**
 * The Storage Access Framework's pickers: `ACTION_CREATE_DOCUMENT` for a new JSON file and
 * `ACTION_OPEN_DOCUMENT` for one to read. Neither needs a storage permission. Open offers any file,
 * since many file managers do not call a `.json` file JSON; the import says if it is not one.
 */
@Composable
actual fun rememberDocumentPicker(
    onCreated: (Document?) -> Unit,
    onOpened: (Document?) -> Unit,
): DocumentPicker {
    val context = LocalContext.current
    val created by rememberUpdatedState(onCreated)
    val opened by rememberUpdatedState(onOpened)
    val create =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            created(uri?.let { context.document(it) })
        }
    val open =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            opened(uri?.let { context.document(it) })
        }
    return remember(create, open) {
        DocumentPicker(
            create = { name ->
                try {
                    create.launch(name)
                } catch (_: ActivityNotFoundException) {
                    created(null)
                }
            },
            open = {
                try {
                    open.launch(arrayOf("*/*"))
                } catch (_: ActivityNotFoundException) {
                    opened(null)
                }
            },
        )
    }
}

private fun Context.document(uri: Uri): Document {
    val name = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }
    return Document(uri.toString(), name.getOrNull() ?: uri.lastPathSegment ?: "the file")
}
