package io.github.noahhhx.mimos.recipes.pictures

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import io.github.noahhhx.mimos.ui.MimosTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap

/** What a picture shows as: "Soup, picture 2 of 3". */
fun pictureDescription(title: String, index: Int, count: Int): String =
    "${title.ifBlank { "Recipe" }}, picture ${index + 1} of $count"

/**
 * A recipe's pictures, one row at [height], scrolling sideways on its own and snapping to each
 * picture (ADR-0023). Each picture's frame is at most 85% of the row's width, so the next one
 * always shows. A recipe without pictures renders nothing and fetches nothing.
 */
@Composable
fun PictureRow(
    pictures: RecipePictures,
    recipeId: String,
    title: String,
    pictureIds: List<String>,
    modifier: Modifier = Modifier,
) {
    if (pictureIds.isEmpty()) return
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gutter = MimosTheme.spacing.page
        val widest = (maxWidth - gutter * 2) * 0.85f
        val state = rememberLazyListState()
        LazyRow(
            Modifier.semantics { contentDescription = "Pictures" },
            state = state,
            contentPadding = PaddingValues(horizontal = gutter),
            horizontalArrangement = Arrangement.spacedBy(MimosTheme.spacing.m),
            flingBehavior = rememberSnapFlingBehavior(state),
        ) {
            itemsIndexed(pictureIds, key = { _, id -> id }) { index, id ->
                SavedPicture(
                    pictures,
                    recipeId,
                    id,
                    pictureDescription(title, index, pictureIds.size),
                    PICTURE_HEIGHT,
                    widest,
                )
            }
        }
    }
}

/** A saved picture's image, fetched through the API because it needs the bearer token. */
@Composable
fun SavedPicture(
    pictures: RecipePictures,
    recipeId: String,
    pictureId: String,
    description: String,
    height: Dp,
    widest: Dp,
) {
    val image by
        produceState<ImageBitmap?>(null, recipeId, pictureId) {
            value =
                try {
                    decode(pictures.image(recipeId, pictureId))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // The network failed: the frame stays empty, as on the web.
                    null
                }
        }
    PictureFrame(image, description, height, widest)
}

/** A picture not uploaded yet, decoded from its bytes. */
@Composable
fun NewPicture(prepared: PreparedPicture, description: String, height: Dp, widest: Dp) {
    val image by
        produceState<ImageBitmap?>(null, prepared) {
            value = runCatching { decode(prepared.jpeg) }.getOrNull()
        }
    PictureFrame(image, description, height, widest)
}

/**
 * The picture at [height], cropped at [widest]. A picture whose image has not arrived holds a 4:3
 * box in `hover`.
 */
@Composable
private fun PictureFrame(image: ImageBitmap?, description: String, height: Dp, widest: Dp) {
    val ratio = image?.let { it.width.toFloat() / it.height } ?: (4f / 3f)
    val frame =
        Modifier.height(height)
            .width(min(height * ratio, widest))
            .clipToBounds()
            .background(MimosTheme.colors.hover)
            .semantics { contentDescription = description }
    if (image == null) {
        Box(frame)
    } else {
        Image(image, contentDescription = null, modifier = frame, contentScale = ContentScale.Crop)
    }
}

/** Decoding a 1600 px JPEG takes a moment, so it stays off the main thread. */
private suspend fun decode(bytes: ByteArray): ImageBitmap =
    withContext(Dispatchers.Default) { bytes.decodeToImageBitmap() }

val PICTURE_HEIGHT = 240.dp
val PICTURE_HEIGHT_COMPACT = 120.dp
