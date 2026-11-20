package io.github.noahhhx.mimos.recipes.pictures

import io.github.noahhhx.mimos.client.ApiProblem
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException

/** Mirrors RecipePictureService.MAX_PICTURES; the API is the authority. */
const val MAX_PICTURES = 8

/** Mirrors RecipePictureService.MAX_PICTURE_BYTES. */
const val MAX_PICTURE_BYTES = 2 * 1024 * 1024

/** The longest side a picture is shrunk to before it is uploaded. */
const val LONG_EDGE = 1600

/** JPEG qualities, in percent, tried in turn until a shrunk picture fits [MAX_PICTURE_BYTES]. */
val QUALITY_STEPS = listOf(85, 75, 60, 45)

data class Size(val width: Int, val height: Int)

/** The size that fits within [longEdge] on both sides, keeping the ratio; never larger. */
fun fitWithin(width: Int, height: Int, longEdge: Int = LONG_EDGE): Size {
    val scale = min(1.0, longEdge.toDouble() / max(width, height))
    return Size(
        max(1, (width * scale).roundToInt()),
        max(1, (height * scale).roundToInt()),
    )
}

/** The first of [QUALITY_STEPS] whose encoding fits [MAX_PICTURE_BYTES], or null when none does. */
fun encodeWithinLimit(encode: (quality: Int) -> ByteArray): ByteArray? =
    QUALITY_STEPS.firstNotNullOfOrNull { quality ->
        encode(quality).takeIf { it.size <= MAX_PICTURE_BYTES }
    }

/** A picture shrunk on the phone and not uploaded yet: JPEG bytes, without metadata. */
class PreparedPicture(val jpeg: ByteArray)

sealed interface PictureSlot {
    data class Saved(val id: String) : PictureSlot

    data class New(val key: String, val prepared: PreparedPicture) : PictureSlot
}

data class Pick(val key: String, val prepared: PreparedPicture)

/**
 * What the recipe form shows ([slots], in order) and the saved pictures the cook took away. Uploads
 * append on the server, so new slots always follow saved ones: the draft never reorders, and slot
 * order is server order.
 */
data class PictureDraft(
    val slots: List<PictureSlot> = emptyList(),
    val removed: List<String> = emptyList(),
) {
    val full: Boolean
        get() = slots.size >= MAX_PICTURES

    /** Removals first, which frees room under the limit, then uploads in slot order. */
    val uploads: List<Pick>
        get() = slots.filterIsInstance<PictureSlot.New>().map { Pick(it.key, it.prepared) }

    val hasChanges: Boolean
        get() = removed.isNotEmpty() || uploads.isNotEmpty()

    /**
     * Appends [picks] after every slot, keeping at most [MAX_PICTURES]; says how many did not fit.
     */
    fun add(picks: List<Pick>): Pair<PictureDraft, Int> {
        val room = max(0, MAX_PICTURES - slots.size)
        val added = picks.take(room).map { PictureSlot.New(it.key, it.prepared) }
        return copy(slots = slots + added) to picks.size - added.size
    }

    /**
     * Takes the slot at [index] out: a saved picture is remembered for removal, a new one dropped.
     */
    fun remove(index: Int): PictureDraft {
        val slot = slots.getOrNull(index) ?: return this
        return PictureDraft(
            slots = slots.filterIndexed { i, _ -> i != index },
            removed = if (slot is PictureSlot.Saved) removed + slot.id else removed,
        )
    }

    /** Once a removal reached the server, or found the picture already gone. */
    fun removedOnServer(pictureId: String): PictureDraft =
        copy(removed = removed.filter { it != pictureId })

    /** Once an upload reached the server: the new slot becomes that saved picture, in place. */
    fun uploaded(key: String, pictureId: String): PictureDraft =
        copy(
            slots =
                slots.map {
                    if (it is PictureSlot.New && it.key == key) PictureSlot.Saved(pictureId) else it
                }
        )

    companion object {
        fun of(pictureIds: List<String>): PictureDraft =
            PictureDraft(slots = pictureIds.map(PictureSlot::Saved))
    }
}

/** The calls Save makes. Each throws [ApiProblem] when the API refuses it. */
interface PictureApi {
    suspend fun remove(recipeId: String, pictureId: String)

    /** Uploads one JPEG and answers the new picture's id. */
    suspend fun upload(recipeId: String, jpeg: ByteArray): String
}

data class Synced(val draft: PictureDraft, val error: String? = null)

/**
 * Sends what [start] still holds to the saved recipe, one call at a time, and returns the draft as
 * far as the server has it. A failure stops there with the API's message, so saving again sends
 * only the rest. A removal that finds the picture gone counts as done.
 */
suspend fun syncPictures(api: PictureApi, recipeId: String, start: PictureDraft): Synced {
    var draft = start
    for (pictureId in draft.removed) {
        try {
            api.remove(recipeId, pictureId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            if ((failure as? ApiProblem)?.status != 404) {
                return Synced(
                    draft,
                    failure.detailOr("A picture could not be removed. Save again to retry."),
                )
            }
        }
        draft = draft.removedOnServer(pictureId)
    }
    for (pick in draft.uploads) {
        val id =
            try {
                api.upload(recipeId, pick.prepared.jpeg)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                return Synced(
                    draft,
                    failure.detailOr("A picture could not be saved. Save again to retry."),
                )
            }
        draft = draft.uploaded(pick.key, id)
    }
    return Synced(draft)
}

private fun Exception.detailOr(fallback: String): String = (this as? ApiProblem)?.detail ?: fallback
