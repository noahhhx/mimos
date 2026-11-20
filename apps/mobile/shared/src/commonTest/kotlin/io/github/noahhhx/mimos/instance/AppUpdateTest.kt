package io.github.noahhhx.mimos.instance

import io.github.noahhhx.mimos.api.apis.InstanceApi
import io.github.noahhhx.mimos.client.MimosHttp
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

private const val UPDATE_API = "https://api.mimos.example.com"

private val app = Version(1, 4, 0)

private suspend fun checkAgainst(status: HttpStatusCode, body: String): InstanceStatus? {
    val engine = MockEngine {
        respond(
            body,
            status,
            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )
    }
    return MimosHttp(engine, UPDATE_API).use { checkInstance(it.api(::InstanceApi), app) }
}

class AppUpdateTest {
    @Test
    fun `a server on the app's version, or an older one it still supports, is current`() {
        assertEquals(AppStanding.Current, appStanding(Version(1, 4, 0), Version(0, 0, 0), app))
        assertEquals(AppStanding.Current, appStanding(Version(1, 3, 2), Version(1, 4, 0), app))
    }

    @Test
    fun `a newer server means a newer app is out`() {
        for (server in listOf(Version(1, 4, 1), Version(1, 5, 0), Version(2, 0, 0))) {
            assertEquals(
                AppStanding.NewerAvailable(server),
                appStanding(server, Version(0, 0, 0), app),
            )
        }
    }

    @Test
    fun `an app below the server's minimum stops, whatever else is true`() {
        assertEquals(
            AppStanding.Unsupported(Version(1, 4, 1)),
            appStanding(Version(2, 0, 0), Version(1, 4, 1), app),
        )
    }

    @Test
    fun `before the server answers, the version from connecting decides`() {
        val instance = Instance(rememberedConfig, serverVersion = Version(1, 5, 0))
        assertEquals(
            InstanceStatus(Version(1, 5, 0), AppStanding.NewerAvailable(Version(1, 5, 0))),
            InstanceStatus.remembered(instance, app),
        )
    }

    @Test
    fun `the server's answer now replaces what the app remembered`() = runTest {
        assertEquals(
            InstanceStatus(Version(1, 6, 0), AppStanding.Unsupported(Version(1, 5, 0))),
            checkAgainst(HttpStatusCode.OK, """{"version":"1.6.0","minAppVersion":"1.5.0"}"""),
        )
        assertEquals(
            InstanceStatus(Version(1, 4, 0), AppStanding.Current),
            checkAgainst(
                HttpStatusCode.OK,
                """{"version":"1.4.0","minAppVersion":"0.0.0","addedLater":true}""",
            ),
        )
    }

    @Test
    fun `no answer, or one the app cannot read, leaves what it remembered`() = runTest {
        assertNull(checkAgainst(HttpStatusCode.ServiceUnavailable, """{"status":503}"""))
        assertNull(checkAgainst(HttpStatusCode.OK, """{"version":"one","minAppVersion":"0.0.0"}"""))
        assertNull(checkAgainst(HttpStatusCode.OK, "not json"))
        val offline = MockEngine { throw IOException("offline") }
        assertNull(MimosHttp(offline, UPDATE_API).use { checkInstance(it.api(::InstanceApi), app) })
    }
}

private val rememberedConfig =
    InstanceConfig(
        webUrl = WebAddress("https://mimos.example.com"),
        apiUrl = UPDATE_API,
        oidcAuthority = "https://id.mimos.example.com/realms/mimos",
        oidcClientId = "mimos-web",
        signup = SignupPolicy.OPEN,
    )
