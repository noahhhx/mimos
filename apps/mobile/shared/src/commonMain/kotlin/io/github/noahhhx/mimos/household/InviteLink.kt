package io.github.noahhhx.mimos.household

import io.github.noahhhx.mimos.instance.WebAddress
import io.github.noahhhx.mimos.instance.parseWebAddress
import io.ktor.http.decodeURLPart
import io.ktor.http.decodeURLQueryComponent
import io.ktor.http.encodeURLPathPart

/** The app's own scheme for links it opens, apart from its sign-in redirect (ADR-0029). */
const val APP_LINK_SCHEME = "io.github.noahhhx.mimos"

/** An invite to a household on the instance at [instance], as a link names it. */
data class InviteLink(val instance: WebAddress, val token: String)

private const val MAX_TOKEN_LENGTH = 200
private val webLink = Regex("""(\S+?)/app/join/([^/?#\s]+)/?(?:[?#]\S*)?""")
private val appLink = Regex("""${Regex.escape(APP_LINK_SCHEME)}://join\?(\S*)""")

/**
 * Reads an invite link: the web's `<web address>/app/join/<token>`, as someone pastes it, or the
 * web join page's `io.github.noahhhx.mimos://join?instance=<web address>&token=<token>`. Null for
 * anything else, or a web address the app may not connect to.
 */
fun parseInviteLink(text: String, allowLocalhostHttp: Boolean): InviteLink? {
    val trimmed = text.trim()
    val (address, token) =
        webLink.matchEntire(trimmed)?.let {
            it.groupValues[1] to runCatching { it.groupValues[2].decodeURLPart() }.getOrNull()
        } ?: appLink.matchEntire(trimmed)?.let { query(it.groupValues[1]) } ?: return null
    if (address == null || token == null) return null
    if (token.isEmpty() || token.length > MAX_TOKEN_LENGTH || token.any { it.isWhitespace() }) {
        return null
    }
    val instance = parseWebAddress(address, allowLocalhostHttp) as? WebAddress ?: return null
    return InviteLink(instance, token)
}

/** The link's `instance` and `token`; a malformed escape reads as absent rather than a crash. */
private fun query(text: String): Pair<String?, String?> {
    val values =
        text.split('&').associate { pair ->
            val key = pair.substringBefore('=')
            key to
                runCatching { pair.substringAfter('=', "").decodeURLQueryComponent() }.getOrNull()
        }
    return values["instance"] to values["token"]
}

/** The link that opens an invite on the web, as the web's `inviteLink` makes it. */
fun webInviteLink(instance: WebAddress, token: String): String =
    "${instance.origin}/app/join/${token.encodeURLPathPart()}"
