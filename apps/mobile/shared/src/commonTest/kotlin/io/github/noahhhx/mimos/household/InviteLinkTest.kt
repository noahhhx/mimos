package io.github.noahhhx.mimos.household

import io.github.noahhhx.mimos.client.MimosJson
import io.github.noahhhx.mimos.instance.WebAddress
import io.github.noahhhx.mimos.instance.parseWebAddress
import io.github.noahhhx.mimos.testcases.TestCases
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.serialization.Serializable

@Serializable private class NamedCase(val names: List<String>, val expected: String)

@Serializable
private class LinkCase(
    val webAddress: String,
    val token: String,
    val link: String,
    val appLink: String,
)

@Serializable
private class HouseholdCases(
    val householdNameCases: List<NamedCase>,
    val listNamesCases: List<NamedCase>,
    val inviteLinkCases: List<LinkCase>,
)

private val cases = MimosJson.decodeFromString<HouseholdCases>(TestCases.read("household.json"))

private fun address(text: String) = parseWebAddress(text, allowLocalhostHttp = true) as WebAddress

class InviteLinkTest {
    @Test
    fun `households and their members are named as on the web`() {
        for (case in cases.householdNameCases) {
            assertEquals(case.expected, householdName(case.names), case.names.toString())
        }
        for (case in cases.listNamesCases) {
            assertEquals(case.expected, listNames(case.names), case.names.toString())
        }
    }

    @Test
    fun `the web's invite link and its app link both read as the invite`() {
        for (case in cases.inviteLinkCases) {
            val expected = InviteLink(address(case.webAddress), case.token)
            assertEquals(expected, parseInviteLink(case.link, allowLocalhostHttp = true), case.link)
            assertEquals(
                expected,
                parseInviteLink(case.appLink, allowLocalhostHttp = true),
                case.appLink,
            )
            assertEquals(case.link, webInviteLink(expected.instance, case.token))
        }
    }

    @Test
    fun `a pasted link may carry spaces, a trailing slash, a query, or a path before the app`() {
        val expected = InviteLink(address("https://mimos.example"), "abc")
        for (text in
            listOf(
                "  https://mimos.example/app/join/abc\n",
                "https://mimos.example/app/join/abc/",
                "https://mimos.example/app/join/abc?utm=x#top",
                "mimos.example/app/join/abc",
                "https://MIMOS.example:443/app/join/abc",
            )) {
            assertEquals(expected, parseInviteLink(text, allowLocalhostHttp = false), text)
        }
    }

    @Test
    fun `the app link's parameters may come in any order and are decoded`() {
        assertEquals(
            InviteLink(address("https://mimos.example"), "a+b"),
            parseInviteLink(
                "io.github.noahhhx.mimos://join?token=a%2Bb&instance=https%3A%2F%2Fmimos.example",
                allowLocalhostHttp = false,
            ),
        )
    }

    @Test
    fun `anything else is not an invite`() {
        for (text in
            listOf(
                "",
                "abc",
                "https://mimos.example/app/join/",
                "https://mimos.example/app/recipes/abc",
                "https://mimos.example/app/join/a%20b",
                "https://mimos.example/app/join/%zz",
                "https://mimos.example/app/join/" + "x".repeat(201),
                "ftp://mimos.example/app/join/abc",
                "io.github.noahhhx.mimos://join?instance=https%3A%2F%2Fmimos.example",
                "io.github.noahhhx.mimos://join?token=abc",
                "io.github.noahhhx.mimos://join?instance=not%20a%20site&token=abc",
                "io.github.noahhhx.mimos.oauth://join?instance=https%3A%2F%2Fmimos.example&token=a",
                "other://join?instance=https%3A%2F%2Fmimos.example&token=abc",
            )) {
            assertNull(parseInviteLink(text, allowLocalhostHttp = true), text)
        }
    }

    @Test
    fun `plain http is an invite only to localhost, and only where the app may connect to it`() {
        val local = "http://localhost:3000/app/join/abc"
        assertEquals(
            InviteLink(address("http://localhost:3000"), "abc"),
            parseInviteLink(local, allowLocalhostHttp = true),
        )
        assertNull(parseInviteLink(local, allowLocalhostHttp = false))
        assertNull(parseInviteLink("http://mimos.example/app/join/abc", allowLocalhostHttp = true))
    }

    @Test
    fun `an invite's expiry reads as a date and a time in the phone's zone`() {
        val at = Instant.parse("2026-11-26T20:04:00Z")
        assertEquals("26 November 2026, 20:04", dateTimeText(at, TimeZone.UTC))
        assertEquals("26 November 2026, 21:04", dateTimeText(at, TimeZone.of("Europe/Berlin")))
        assertEquals(
            "1 January 2027, 00:05",
            dateTimeText(Instant.parse("2027-01-01T00:05:00Z"), TimeZone.UTC),
        )
    }
}
