package io.github.noahhhx.mimos.recipes.pictures

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/**
 * The system photo picker, which needs no storage permission, and the phone's camera app writing to
 * a file only this app can read. More than [MAX_PICTURES] picks are left to the form, which says
 * how many did not fit, as the web does.
 */
@Composable
actual fun rememberPicturePicker(onPicked: (List<PickedPicture>) -> Unit): PicturePicker {
    val context = LocalContext.current
    val picked by rememberUpdatedState(onPicked)
    val gallery =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(MAX_PICTURES)
        ) { uris ->
            if (uris.isNotEmpty()) picked(uris.map { PickedPicture(it.toString()) })
        }
    val photo = remember(context) { CameraFile.uri(context) }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
            if (taken) picked(listOf(PickedPicture(photo.toString())))
        }
    val hasCamera =
        remember(context) {
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(context.packageManager) != null
        }
    return remember(gallery, camera, hasCamera) {
        PicturePicker(
            pick = {
                gallery.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            takePhoto = if (hasCamera) ({ camera.launch(photo) }) else null,
        )
    }
}

/** Where the camera app writes a photo: one file, replaced by each photo, deleted once shrunk. */
internal object CameraFile {
    private fun authority(context: Context) = "${context.packageName}.pictures"

    fun uri(context: Context): Uri {
        val file = File(context.cacheDir, "camera/photo.jpg")
        file.parentFile?.mkdirs()
        return FileProvider.getUriForFile(context, authority(context), file)
    }

    fun isOurs(context: Context, uri: Uri): Boolean = uri.authority == authority(context)
}
