package io.github.noahhhx.mimos.instance

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException

private const val WEB = "https://mimos.example.com"
private const val API = "https://api.mimos.example.com"

private val runtimeConfigJson =
    """
    {"apiUrl":"$API/","oidcAuthority":"https://id.mimos.example.com/realms/mimos",
     "oidcClientId":"mimos-web","appUrl":"$WEB","signup":"closed","addedLater":true}
    """

private fun instanceJson(version: String = "1.4.0", minAppVersion: String = "0.0.0") =
    """{"version":"$version","minAppVersion":"$minAppVersion","addedLater":1}"""

private val goodInstance =
    Instance(
        InstanceConfig(
            webUrl = WebAddress(WEB),
            apiUrl = API,
            oidcAuthority = "https://id.mimos.example.com/realms/mimos",
            oidcClientId = "mimos-web",
            signup = SignupPolicy.CLOSED,
        ),
        serverVersion = Version(1, 4, 0),
    )

private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(
        body,
        status,
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

private fun MockRequestHandleScope.html() =
    respond(
        "<!doctype html><title>Hi</title>",
        HttpStatusCode.OK,
        headersOf(HttpHeaders.ContentType, "text/html"),
    )

private val problem404 = """{"title":"Not Found","status":404}"""

/** A site answering at its web address and at its API, by default as a Mimos instance. */
private fun site(
    runtimeConfig: MockRequestHandleScope.() -> HttpResponseData = { json(runtimeConfigJson) },
    instance: MockRequestHandleScope.() -> HttpResponseData = { json(instanceJson()) },
) = MockEngine { request ->
    when (request.url.toString()) {
        "$WEB/runtime-config.json" -> runtimeConfig()
        "$API/api/v1/public/instance" -> instance()
        else -> json(problem404, HttpStatusCode.NotFound)
    }
}

class DiscoveryTest {
    @Test
    fun `a Mimos instance connects, reading only the fields the app knows`() = runTest {
        assertEquals(Discovery.Connected(goodInstance), discover("mimos.example.com", site()))
    }

    @Test
    fun `the local stack connects over plain http`() = runTest {
        val engine = MockEngine { request ->
            when (request.url.toString()) {
                "http://localhost:3000/runtime-config.json" ->
                    json(
                        """{"apiUrl":"http://localhost:8080","oidcAuthority":"http://localhost:8081/realms/mimos",
                           "oidcClientId":"mimos-web","appUrl":"http://localhost:3000","signup":"open"}"""
                    )
                "http://localhost:8080/api/v1/public/instance" ->
                    json(instanceJson("0.1.0-SNAPSHOT"))
                else -> respondError(HttpStatusCode.NotFound)
            }
        }
        val outcome = discover("http://localhost:3000", engine)
        val instance = (outcome as Discovery.Connected).instance
        assertEquals("http://localhost:3000", instance.config.webUrl.origin)
        assertEquals(SignupPolicy.OPEN, instance.config.signup)
        assertEquals(Version(0, 1, 0), instance.serverVersion)
    }

    @Test
    fun `a site without a runtime config is not Mimos`() = runTest {
        assertEquals(
            Discovery.NotMimos,
            discover(WEB, site(runtimeConfig = { respondError(HttpStatusCode.NotFound) })),
        )
    }

    @Test
    fun `JSON that is not a runtime config is not Mimos`() = runTest {
        val notMimos =
            listOf(
                """{"name":"blog"}""",
                """{"apiUrl":5,"oidcAuthority":"a","oidcClientId":"b","appUrl":"c","signup":"open"}""",
                "[]",
            )
        for (body in notMimos) {
            assertEquals(
                Discovery.NotMimos,
                discover(WEB, site(runtimeConfig = { json(body) })),
                body,
            )
        }
    }

    @Test
    fun `an HTML page is not Mimos`() = runTest {
        assertEquals(Discovery.NotMimos, discover(WEB, site(runtimeConfig = { html() })))
        assertEquals(Discovery.NotMimos, discover(WEB, site(instance = { html() })))
    }

    @Test
    fun `a site that cannot be reached or fails is unreachable`() = runTest {
        val refused = MockEngine { throw IOException("Connection refused") }
        assertEquals(Discovery.Unreachable, discover(WEB, refused))
        assertEquals(
            Discovery.Unreachable,
            discover(WEB, site(runtimeConfig = { respondError(HttpStatusCode.BadGateway) })),
        )
        assertEquals(
            Discovery.Unreachable,
            discover(WEB, site(instance = { respondError(HttpStatusCode.ServiceUnavailable) })),
        )
    }

    @Test
    fun `a server without getInstance is too old`() = runTest {
        assertEquals(
            Discovery.ServerTooOld(null),
            discover(WEB, site(instance = { json(problem404, HttpStatusCode.NotFound) })),
        )
    }

    @Test
    fun `a server older than the app supports is too old`() = runTest {
        assertEquals(
            Discovery.ServerTooOld(Version(0, 9, 2)),
            discover(
                WEB,
                site(instance = { json(instanceJson("0.9.2")) }),
                minServerVersion = Version(1, 0, 0),
            ),
        )
    }

    @Test
    fun `an app older than the server supports is too old`() = runTest {
        assertEquals(
            Discovery.AppTooOld(Version(2, 0, 0)),
            discover(
                WEB,
                site(instance = { json(instanceJson(minAppVersion = "2.0.0")) }),
                appVersion = Version(1, 9, 9),
            ),
        )
    }

    @Test
    fun `an http address is refused before any request is made`() = runTest {
        val requests = mutableListOf<HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            json(runtimeConfigJson)
        }
        assertEquals(InsecureAddress, discover("http://mimos.example.com", engine))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `an instance whose API is plain http is refused`() = runTest {
        val insecureApi = runtimeConfigJson.replace("\"$API/\"", "\"http://api.mimos.example.com\"")
        assertEquals(InsecureAddress, discover(WEB, site(runtimeConfig = { json(insecureApi) })))
    }

    @Test
    fun `something that is not a web address is refused`() = runTest {
        assertEquals(InvalidAddress, discover("not a web address", site()))
    }
}
