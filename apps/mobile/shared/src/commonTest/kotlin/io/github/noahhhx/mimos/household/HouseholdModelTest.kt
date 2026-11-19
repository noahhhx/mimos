package io.github.noahhhx.mimos.household

import io.github.noahhhx.mimos.api.apis.HouseholdApi
import io.github.noahhhx.mimos.client.MimosHttp
import io.github.noahhhx.mimos.client.MimosJson
import io.github.noahhhx.mimos.instance.WebAddress
import io.github.noahhhx.mimos.instance.parseWebAddress
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val API = "https://api.mimos.example.com"
private const val ME = """{"id":"me","displayName":"Sam","you":true}"""
private const val ALEX = """{"id":"alex","displayName":"Alex","you":false}"""
private const val JO = """{"id":"jo","displayName":"Jo","you":false}"""
private const val EXPIRES = "2026-11-26T20:04:00Z"

private val here = parseWebAddress("https://mimos.example.com", false) as WebAddress
private val elsewhere = parseWebAddress("https://other.example", false) as WebAddress

/** A household server: its members, the invites it knows, and every token it was sent. */
private class HouseholdServer {
    var members = listOf(ME)
    /** Token to the invite's answer: the inviting household's members, or an error status. */
    val invites = mutableMapOf<String, Pair<List<String>, HttpStatusCode>>()
    var alreadyMember = false
    var failWrites: HttpStatusCode? = null
    val tokensSent = mutableListOf<String>()

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val problem = headersOf(HttpHeaders.ContentType, "application/problem+json")

    private fun household() = """{"members":[${members.joinToString(",")}]}"""

    val engine = MockEngine { request ->
        val token =
            (request.body as? TextContent)?.text?.let {
                MimosJson.parseToJsonElement(it).jsonObject["token"]?.jsonPrimitive?.content
            }
        token?.let(tokensSent::add)
        val invite = token?.let(invites::get)
        when (request.url.encodedPath) {
            "/api/v1/household" -> respond(household(), headers = json)
            "/api/v1/household/invites" ->
                respond(
                    """{"token":"t-${tokensSent.size}","expiresAt":"$EXPIRES"}""",
                    HttpStatusCode.Created,
                    json,
                )
            "/api/v1/household/invites/preview" ->
                when {
                    invite == null ->
                        respond("""{"title":"Not Found"}""", HttpStatusCode.NotFound, problem)
                    invite.second != HttpStatusCode.OK ->
                        respond(
                            """{"title":"Gone","detail":"This invite has been used."}""",
                            invite.second,
                            problem,
                        )
                    else ->
                        respond(
                            """{"members":[${invite.first.joinToString(",")}],
                                "expiresAt":"$EXPIRES","alreadyMember":$alreadyMember,
                                "currentHouseholdShared":${members.size > 1}}""",
                            headers = json,
                        )
                }
            "/api/v1/household/join" ->
                failWrites?.let {
                    respond(
                        """{"title":"Gone","detail":"This invite has been used."}""",
                        it,
                        problem,
                    )
                }
                    ?: run {
                        members = listOf(ME) + invite!!.first
                        respond(household(), headers = json)
                    }
            "/api/v1/household/leave" ->
                failWrites?.let { respond("""{"title":"Conflict"}""", it, problem) }
                    ?: run {
                        members = listOf(ME)
                        respond(household(), headers = json)
                    }
            else -> respond("", HttpStatusCode.NotFound)
        }
    }
}

private class Fixture(scope: TestScope) {
    val server = HouseholdServer()
    var changes = 0
    val model =
        HouseholdModel(
            scope.backgroundScope,
            api = { MimosHttp(server.engine, API).api(::HouseholdApi) },
            connected = { here },
            allowLocalhostHttp = false,
            onChanged = { changes++ },
        )
}

class HouseholdModelTest {
    @Test
    fun `the household loads with who is in it`() = runTest {
        val fixture = Fixture(this)
        fixture.server.members = listOf(ME, ALEX)

        fixture.model.ensureLoaded()

        val loaded = fixture.model.household.first { it is HouseholdState.Loaded }
        assertIs<HouseholdState.Loaded>(loaded)
        assertEquals(listOf("Sam", "Alex"), loaded.household.members.map { it.displayName })
        assertTrue(loaded.shared)
    }

    @Test
    fun `a new invite is shared as the web's link on the connected instance`() = runTest {
        val fixture = Fixture(this)

        fixture.model.createInvite()

        val created = fixture.model.invite.first { it is InviteState.Created }
        assertIs<InviteState.Created>(created)
        assertEquals("https://mimos.example.com/app/join/t-0", created.link)
    }

    @Test
    fun `a pasted link that is not an invite says so and goes nowhere`() = runTest {
        val fixture = Fixture(this)
        fixture.model.editPaste("https://mimos.example.com/app/recipes/1")

        fixture.model.openPasted()

        assertEquals(NOT_AN_INVITE, fixture.model.paste.value.error)
        assertEquals(HouseholdRoute.Page, fixture.model.route.value)
        fixture.model.editPaste("https://mimos.example.com/app/join/")
        assertEquals(null, fixture.model.paste.value.error)
    }

    @Test
    fun `a pasted invite shows what joining does, then joins and reloads everything`() = runTest {
        val fixture = Fixture(this)
        fixture.server.invites["good"] = listOf(ALEX, JO) to HttpStatusCode.OK
        fixture.model.editPaste(" https://mimos.example.com/app/join/good ")

        fixture.model.openPasted()

        val ready = fixture.model.join.first { it is JoinState.Ready }
        assertIs<JoinState.Ready>(ready)
        assertEquals(
            "Alex and Jo's household",
            householdName(ready.preview.members.map { it.displayName }),
        )
        assertEquals(false, ready.preview.currentHouseholdShared)
        assertEquals(PasteState(), fixture.model.paste.value)

        fixture.model.confirmJoin()

        fixture.model.route.first { it == HouseholdRoute.Page }
        val joined = fixture.model.household.value
        assertIs<HouseholdState.Loaded>(joined)
        assertEquals(listOf("Sam", "Alex", "Jo"), joined.household.members.map { it.displayName })
        assertEquals(1, fixture.changes)
        assertEquals(listOf("good", "good"), fixture.server.tokensSent)
    }

    @Test
    fun `a join the server refuses keeps the invite on screen with its reason`() = runTest {
        val fixture = Fixture(this)
        fixture.server.invites["good"] = listOf(ALEX) to HttpStatusCode.OK
        fixture.server.failWrites = HttpStatusCode.Gone
        fixture.model.openJoin(InviteLink(here, "good"))
        fixture.model.join.first { it is JoinState.Ready }

        fixture.model.confirmJoin()

        val refused = fixture.model.join.first { (it as? JoinState.Ready)?.error != null }
        assertIs<JoinState.Ready>(refused)
        assertEquals("This invite has been used.", refused.error)
        assertEquals(false, refused.joining)
        assertIs<HouseholdRoute.Join>(fixture.model.route.value)
        assertEquals(0, fixture.changes)
    }

    @Test
    fun `an unknown invite is not valid, and a used one says why`() = runTest {
        val fixture = Fixture(this)
        fixture.server.invites["used"] = emptyList<String>() to HttpStatusCode.Gone

        fixture.model.openJoin(InviteLink(here, "unknown"))
        assertEquals(
            JoinState.Invalid(INVALID_INVITE),
            fixture.model.join.first { it is JoinState.Invalid },
        )

        fixture.model.openJoin(InviteLink(here, "used"))
        assertEquals(
            JoinState.Invalid("This invite has been used."),
            fixture.model.join.first { it is JoinState.Invalid && it.message != INVALID_INVITE },
        )
    }

    @Test
    fun `an invite to the household the person is in says so`() = runTest {
        val fixture = Fixture(this)
        fixture.server.invites["mine"] = listOf(ME) to HttpStatusCode.OK
        fixture.server.alreadyMember = true

        fixture.model.openJoin(InviteLink(here, "mine"))

        assertEquals(
            JoinState.AlreadyMember,
            fixture.model.join.first { it == JoinState.AlreadyMember },
        )
    }

    @Test
    fun `an invite for another instance asks nothing of this one`() = runTest {
        val fixture = Fixture(this)
        val link = InviteLink(elsewhere, "theirs")

        fixture.model.openJoin(link)

        assertEquals(JoinState.OtherInstance(link, here), fixture.model.join.value)
        assertEquals(emptyList(), fixture.server.tokensSent)
        assertTrue(fixture.model.back())
        assertEquals(HouseholdRoute.Page, fixture.model.route.value)
        assertEquals(false, fixture.model.back())
    }

    @Test
    fun `leaving asks first, and staying changes nothing`() = runTest {
        val fixture = Fixture(this)
        fixture.server.members = listOf(ME, ALEX)

        fixture.model.startLeaving()
        assertEquals(LeaveState.Confirming(), fixture.model.leave.value)
        fixture.model.stay()

        assertEquals(LeaveState.Idle, fixture.model.leave.value)
        assertEquals(listOf(ME, ALEX), fixture.server.members)
    }

    @Test
    fun `leaving starts a household of one and reloads everything`() = runTest {
        val fixture = Fixture(this)
        fixture.server.members = listOf(ME, ALEX)
        fixture.model.createInvite()
        fixture.model.invite.first { it is InviteState.Created }

        fixture.model.startLeaving()
        fixture.model.confirmLeave()

        fixture.model.leave.first { it == LeaveState.Idle }
        val left = fixture.model.household.value
        assertIs<HouseholdState.Loaded>(left)
        assertEquals(false, left.shared)
        assertEquals(InviteState.None, fixture.model.invite.value)
        assertEquals(1, fixture.changes)
    }

    @Test
    fun `a refused leave stays on the warnings with its reason`() = runTest {
        val fixture = Fixture(this)
        fixture.server.failWrites = HttpStatusCode.Conflict

        fixture.model.startLeaving()
        fixture.model.confirmLeave()

        assertEquals(
            LeaveState.Confirming(error = "Could not leave the household."),
            fixture.model.leave.first { (it as? LeaveState.Confirming)?.error != null },
        )
        assertEquals(0, fixture.changes)
    }
}
