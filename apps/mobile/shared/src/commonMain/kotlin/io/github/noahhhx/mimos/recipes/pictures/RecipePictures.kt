package io.github.noahhhx.mimos.recipes.pictures

import io.github.noahhhx.mimos.api.apis.RecipePicturesApi
import io.ktor.client.request.forms.FormPart
import io.ktor.client.request.forms.InputProvider
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.core.buildPacket
import io.ktor.utils.io.core.writeFully

/** The picture operations over the API, for saving ([PictureApi]) and for showing ([image]). */
class RecipePictures(
    private val api: () -> RecipePicturesApi,
    private val cacheBytes: Int = CACHE_BYTES,
) : PictureApi {
    private val cache = LinkedHashMap<String, ByteArray>()

    override suspend fun remove(recipeId: String, pictureId: String) {
        api().deleteRecipePicture(recipeId, pictureId)
    }

    override suspend fun upload(recipeId: String, jpeg: ByteArray): String {
        // Spring reads a part as a file only when it names one.
        val headers = Headers.build {
            append(HttpHeaders.ContentType, ContentType.Image.JPEG.toString())
            append(HttpHeaders.ContentDisposition, "filename=\"picture.jpg\"")
        }
        val file =
            FormPart(
                "file",
                InputProvider(jpeg.size.toLong()) { buildPacket { writeFully(jpeg) } },
                headers,
            )
        return api().addRecipePicture(recipeId, file).body().id
    }

    /**
     * A saved picture's image bytes. A picture never changes, so the most recently shown are kept
     * in memory, up to [cacheBytes] in all, and a recipe shown again asks nothing. Called from the
     * main thread only.
     */
    suspend fun image(recipeId: String, pictureId: String): ByteArray {
        cache.remove(pictureId)?.let {
            cache[pictureId] = it
            return it
        }
        val bytes = api().getRecipePicture(recipeId, pictureId).response.readRawBytes()
        cache[pictureId] = bytes
        var total = cache.values.sumOf { it.size }
        val oldest = cache.keys.iterator()
        while (total > cacheBytes && cache.size > 1) {
            total -= cache.getValue(oldest.next()).size
            oldest.remove()
        }
        return bytes
    }

    /** Forgets every image, for when the person signs out. */
    fun clear() = cache.clear()

    companion object {
        const val CACHE_BYTES = 24 * 1024 * 1024
    }
}
