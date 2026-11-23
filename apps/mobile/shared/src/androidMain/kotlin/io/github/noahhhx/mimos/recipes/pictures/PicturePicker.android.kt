package io.github.noahhhx.mimos.recipes.pictures

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * The system photo picker, which needs no storage permission, and the phone's camera app writing to
 * a file only this app can read. More than [MAX_PICTURES] picks are left to the form, which says
 * how many did not fit, as the web does.
 */
@Composable
actual fun rememberPicturePicker(
    onPicked: (List<PickedPicture>) -> Unit,
    single: Boolean,
): PicturePicker {
    val context = LocalContext.current
    val picked by rememberUpdatedState(onPicked)
    val several =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(MAX_PICTURES)
        ) { uris ->
            if (uris.isNotEmpty()) picked(uris.map { PickedPicture(it.toString()) })
        }
    val one =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) picked(listOf(PickedPicture(uri.toString())))
        }
    val gallery = if (single) one else several
    val photo = remember(context) { CameraFile.uri(context) }
    val camera =
        rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
            if (taken) picked(listOf(PickedPicture(photo.toString())))
        }
    // The app declares CAMERA for barcodes, and then the camera app's launch throws without it.
    val ask =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) camera.launch(photo)
        }
    val hasCamera =
        remember(context) {
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).resolveActivity(context.packageManager) != null
        }
    return remember(gallery, camera, ask, hasCamera) {
        PicturePicker(
            pick = {
                gallery.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            takePhoto =
                if (hasCamera) {
                    {
                        val allowed =
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA,
                            ) == PackageManager.PERMISSION_GRANTED
                        if (allowed) camera.launch(photo)
                        else ask.launch(Manifest.permission.CAMERA)
                    }
                } else null,
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
