package io.github.noahhhx.mimos.recipes.pictures

import androidx.compose.runtime.Composable

/** A picture the person picked or took, as the platform names it (a content URI on Android). */
data class PickedPicture(val uri: String)

sealed interface Shrunk {
    data class Ready(val prepared: PreparedPicture) : Shrunk

    /** [message] is for the cook, naming the picture. */
    data class Failed(val message: String) : Shrunk
}

/**
 * Turns a picked picture into an upload, as the web's `prepare-picture.ts`: decoded with its EXIF
 * orientation applied, shrunk with [fitWithin], flattened onto white, and re-encoded as JPEG with
 * [encodeWithinLimit]. Re-encoding drops the file's metadata, a phone photo's location included
 * (ADR-0023).
 */
interface PictureShrinker {
    suspend fun shrink(picked: PickedPicture): Shrunk
}

/** The platform's ways to get pictures. [takePhoto] is null when the phone has no camera app. */
class PicturePicker(val pick: () -> Unit, val takePhoto: (() -> Unit)?)

/** The photo picker and the camera, which hand what they got to [onPicked]. */
@Composable expect fun rememberPicturePicker(onPicked: (List<PickedPicture>) -> Unit): PicturePicker
