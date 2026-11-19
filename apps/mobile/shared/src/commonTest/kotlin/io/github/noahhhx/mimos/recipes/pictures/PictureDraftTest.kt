package io.github.noahhhx.mimos.recipes.pictures

import io.github.noahhhx.mimos.client.ApiProblem
import io.github.noahhhx.mimos.testcases.TestCases
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private class Limits(
    val maxPictures: Int,
    val maxPictureBytes: Int,
    val longEdge: Int,
    val qualitySteps: List<Double>,
)

@Serializable private class FitSize(val width: Int, val height: Int)

@Serializable
private class FitCase(val name: String, val width: Int, val height: Int, val expected: FitSize)

@Serializable private class PictureCases(val limits: Limits, val fit: List<FitCase>)

private val cases = Json.decodeFromString<PictureCases>(TestCases.read("recipe-pictures.json"))

private fun saved(vararg ids: String) = PictureDraft.of(ids.toList())

private fun pick(key: String) = Pick(key, PreparedPicture(key.encodeToByteArray()))

private fun ids(draft: PictureDraft) =
    draft.slots.map {
        when (it) {
            is PictureSlot.Saved -> it.id
            is PictureSlot.New -> "new:${it.key}"
        }
    }

/** Records each call and answers from a script; uploads get `$prefix-n` ids in call order. */
private class FakePictures(
    val failRemove: Map<String, Int> = emptyMap(),
    val failUpload: Set<String> = emptySet(),
    val prefix: String = "uploaded",
) : PictureApi {
    val calls = mutableListOf<String>()
    private var next = 0

    override suspend fun remove(recipeId: String, pictureId: String) {
        calls += "remove $recipeId $pictureId"
        failRemove[pictureId]?.let { throw ApiProblem(it, null, "try again", null) }
    }

    override suspend fun upload(recipeId: String, jpeg: ByteArray): String {
        val key = jpeg.decodeToString()
        calls += "upload $recipeId $key"
        if (key in failUpload) throw ApiProblem(400, null, "$key is not a picture", null)
        return "$prefix-${++next}"
    }
}

class PictureDraftTest {
    @Test
    fun `the limits are the web's`() {
        assertEquals(MAX_PICTURES, cases.limits.maxPictures)
        assertEquals(MAX_PICTURE_BYTES, cases.limits.maxPictureBytes)
        assertEquals(LONG_EDGE, cases.limits.longEdge)
        assertEquals(cases.limits.qualitySteps.map { (it * 100).toInt() }, QUALITY_STEPS)
    }

    @Test
    fun `fitWithin shrinks as the web does`() {
        for (case in cases.fit) {
            assertEquals(
                Size(case.expected.width, case.expected.height),
                fitWithin(case.width, case.height),
                case.name,
            )
        }
    }

    @Test
    fun `the first quality that fits is kept, and none fitting is null`() {
        val tried = mutableListOf<Int>()
        val fits = encodeWithinLimit { quality ->
            tried += quality
            ByteArray(if (quality > 60) MAX_PICTURE_BYTES + 1 else MAX_PICTURE_BYTES)
        }
        assertEquals(listOf(85, 75, 60), tried)
        assertEquals(MAX_PICTURE_BYTES, fits?.size)
        assertNull(encodeWithinLimit { ByteArray(MAX_PICTURE_BYTES + 1) })
    }

    @Test
    fun `picks follow the saved pictures and what passes the limit is left out`() {
        val (draft, leftOut) = saved("a", "b").add(listOf(pick("x"), pick("y")))
        assertEquals(listOf("a", "b", "new:x", "new:y"), ids(draft))
        assertEquals(0, leftOut)

        val (full, over) = saved("a").add((0 until MAX_PICTURES).map { pick("p$it") })
        assertEquals(MAX_PICTURES, full.slots.size)
        assertEquals(1, over)
        assertEquals(1, full.add(listOf(pick("late"))).second)
    }

    @Test
    fun `a removed saved picture is remembered and a removed pick is dropped`() {
        val draft = saved("a", "b").add(listOf(pick("x"))).first
        val withoutSaved = draft.remove(0)
        assertEquals(listOf("b", "new:x"), ids(withoutSaved))
        assertEquals(listOf("a"), withoutSaved.removed)

        val withoutPick = draft.remove(2)
        assertEquals(listOf("a", "b"), ids(withoutPick))
        assertEquals(emptyList(), withoutPick.removed)
        assertFalse(withoutPick.hasChanges)
    }

    @Test
    fun `saving removes, then uploads in order, and ends with only saved pictures`() = runTest {
        val draft = saved("a", "b").add(listOf(pick("x"), pick("y"))).first.remove(0)
        val api = FakePictures()

        val result = syncPictures(api, "r1", draft)

        assertNull(result.error)
        assertEquals(listOf("remove r1 a", "upload r1 x", "upload r1 y"), api.calls)
        assertEquals(listOf("b", "uploaded-1", "uploaded-2"), ids(result.draft))
        assertFalse(result.draft.hasChanges)
    }

    @Test
    fun `a failed upload stops the save, and saving again sends only the rest`() = runTest {
        val draft = saved("a").add(listOf(pick("x"), pick("bad"), pick("y"))).first
        val first = FakePictures(failUpload = setOf("bad"))

        val stopped = syncPictures(first, "r1", draft)

        assertEquals("bad is not a picture", stopped.error)
        assertEquals(listOf("upload r1 x", "upload r1 bad"), first.calls)
        assertEquals(listOf("a", "uploaded-1", "new:bad", "new:y"), ids(stopped.draft))

        val retry = FakePictures(prefix = "retried")
        val finished = syncPictures(retry, "r1", stopped.draft.remove(2))
        assertEquals(listOf("upload r1 y"), retry.calls)
        assertEquals(listOf("a", "uploaded-1", "retried-1"), ids(finished.draft))
        assertFalse(finished.draft.hasChanges)
    }

    @Test
    fun `a removal that finds the picture gone counts as done`() = runTest {
        val result =
            syncPictures(
                FakePictures(failRemove = mapOf("gone" to 404)),
                "r1",
                saved("gone", "b").remove(0),
            )

        assertNull(result.error)
        assertEquals(emptyList(), result.draft.removed)
    }

    @Test
    fun `a failed removal stops the save before any upload`() = runTest {
        val draft = saved("a").remove(0).add(listOf(pick("x"))).first
        val api = FakePictures(failRemove = mapOf("a" to 500))

        val result = syncPictures(api, "r1", draft)

        assertEquals("try again", result.error)
        assertEquals(listOf("remove r1 a"), api.calls)
        assertEquals(listOf("a"), result.draft.removed)
        assertEquals(listOf("x"), result.draft.uploads.map { it.key })
    }
}
