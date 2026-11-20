package io.github.noahhhx.mimos.recipes.pictures

import io.github.noahhhx.mimos.api.apis.RecipePicturesApi
import io.github.noahhhx.mimos.client.MimosHttp
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

private const val PICTURES_API = "https://api.mimos.example.com"

private class ImageServer {
    val asked = mutableListOf<String>()
    val engine = MockEngine { request ->
        val id = request.url.encodedPath.substringAfterLast('/')
        asked += id
        respond(
            ByteArray(4) { id.length.toByte() },
            headers = headersOf(HttpHeaders.ContentType, "image/jpeg"),
        )
    }

    fun pictures(cacheBytes: Int = RecipePictures.CACHE_BYTES) =
        RecipePictures({ MimosHttp(engine, PICTURES_API).api(::RecipePicturesApi) }, cacheBytes)
}

class RecipePicturesTest {
    @Test
    fun `a picture shown again asks nothing, since a picture never changes`() = runTest {
        val server = ImageServer()
        val pictures = server.pictures()

        assertEquals(4, pictures.image("r1", "p1").size)
        pictures.image("r1", "p1")

        assertEquals(listOf("p1"), server.asked)
    }

    @Test
    fun `the least recently shown picture goes first when the cache is full`() = runTest {
        val server = ImageServer()
        val pictures = server.pictures(cacheBytes = 8)

        pictures.image("r1", "p1")
        pictures.image("r1", "p2")
        pictures.image("r1", "p1")
        pictures.image("r1", "p3")
        pictures.image("r1", "p1")
        pictures.image("r1", "p2")

        assertEquals(listOf("p1", "p2", "p3", "p2"), server.asked)
    }

    @Test
    fun `signing out forgets every picture`() = runTest {
        val server = ImageServer()
        val pictures = server.pictures()

        pictures.image("r1", "p1")
        pictures.clear()
        pictures.image("r1", "p1")

        assertEquals(listOf("p1", "p1"), server.asked)
    }
}
