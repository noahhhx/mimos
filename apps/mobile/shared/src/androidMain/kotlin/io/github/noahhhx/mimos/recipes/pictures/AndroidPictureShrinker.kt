// Lint flags the platform ExifInterface for parsing bugs in older Android versions. It reads the
// orientation on API 26 and 27 only, below ImageDecoder, and the androidx copy would be a new
// dependency.
@file:SuppressLint("ExifInterface")

package io.github.noahhhx.mimos.recipes.pictures

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.annotation.RequiresApi
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [PictureShrinker] on Android. A picture is decoded at the smallest power-of-two scale that keeps
 * its long edge at [LONG_EDGE] or more, so a 12-megapixel photo never fills memory, then scaled to
 * [fitWithin] exactly. `Bitmap.compress` writes no metadata.
 */
class AndroidPictureShrinker(private val context: Context) : PictureShrinker {
    override suspend fun shrink(picked: PickedPicture): Shrunk =
        withContext(Dispatchers.IO) {
            val uri = picked.uri.toUri()
            val ours = CameraFile.isOurs(context, uri)
            val name = if (ours) "The photo" else displayName(uri) ?: "The picture"
            try {
                val decoded =
                    runCatching { decode(uri) }.getOrNull()
                        ?: return@withContext Shrunk.Failed(
                            "$name can't be read as a picture. Try a JPEG, PNG, or WebP."
                        )
                val jpeg = encodeWithinLimit(flattened(decoded)::jpeg)
                if (jpeg == null)
                    Shrunk.Failed("$name is too detailed to fit in 2 MB, even shrunk.")
                else Shrunk.Ready(PreparedPicture(jpeg))
            } finally {
                if (ours) context.contentResolver.delete(uri, null, null)
            }
        }

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }
    }
        .getOrNull()

    private fun decode(uri: Uri): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) decodeWithImageDecoder(uri)
        else decodeWithBitmapFactory(uri)

    /** ImageDecoder applies the EXIF orientation itself. */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(uri: Uri): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) {
            decoder,
            info,
            _ ->
            // A canvas cannot draw a hardware bitmap.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.setTargetSampleSize(sampleSize(info.size.width, info.size.height))
        }

    private fun decodeWithBitmapFactory(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
            }
        val bitmap =
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return null
        val orientation =
            resolver.openInputStream(uri)?.use {
                ExifInterface(it)
                    .getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL,
                    )
            } ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = orientationMatrix(orientation) ?: return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}

private fun sampleSize(width: Int, height: Int): Int {
    var sample = 1
    while (maxOf(width, height) / (sample * 2) >= LONG_EDGE) sample *= 2
    return sample
}

private fun orientationMatrix(orientation: Int): Matrix? =
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> Matrix().apply { setScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_180 -> Matrix().apply { setRotate(180f) }
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> Matrix().apply { setScale(1f, -1f) }
        ExifInterface.ORIENTATION_TRANSPOSE ->
            Matrix().apply {
                setRotate(90f)
                postScale(-1f, 1f)
            }
        ExifInterface.ORIENTATION_ROTATE_90 -> Matrix().apply { setRotate(90f) }
        ExifInterface.ORIENTATION_TRANSVERSE ->
            Matrix().apply {
                setRotate(-90f)
                postScale(-1f, 1f)
            }
        ExifInterface.ORIENTATION_ROTATE_270 -> Matrix().apply { setRotate(-90f) }
        else -> null
    }

/** [source] at [fitWithin], on white: a transparent PNG has no JPEG equivalent, and turns black. */
private fun flattened(source: Bitmap): Bitmap {
    val size = fitWithin(source.width, source.height)
    val target = createBitmap(size.width, size.height)
    Canvas(target).apply {
        drawColor(Color.WHITE)
        drawBitmap(
            source,
            null,
            Rect(0, 0, size.width, size.height),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
    }
    source.recycle()
    return target
}

private fun Bitmap.jpeg(quality: Int): ByteArray =
    ByteArrayOutputStream().also { compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
