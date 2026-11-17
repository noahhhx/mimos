package io.github.noahhhx.mimos.instance

sealed interface AddressResult

/** A web address the app may connect to: an origin such as `https://mimos.example.com`. */
data class WebAddress internal constructor(val origin: String) : AddressResult

private val urlPattern =
    Regex("""(?:([A-Za-z][A-Za-z0-9+.-]*)://)?([^/?#:@\s]+)(?::(\d{1,5}))?(?:[/?#]\S*)?""")
private val hostLabel = Regex("""[a-z0-9]([a-z0-9-]*[a-z0-9])?""")
private const val LOCALHOST = "localhost"
private val defaultPorts = mapOf("https" to 443, "http" to 80)

/**
 * Reads what a person typed as their instance's web address: `https://` when no scheme is given,
 * the path dropped, and `http://` only for `localhost` when [allowLocalhostHttp], which the
 * platform sets when its network policy permits that cleartext traffic (ADR-0030).
 */
fun parseWebAddress(input: String, allowLocalhostHttp: Boolean): AddressResult {
    val url = parseUrl(input.trim()) ?: return InvalidAddress
    if (!url.isAllowed(allowLocalhostHttp)) return InsecureAddress
    return WebAddress(url.origin)
}

/** Whether the app may call [url], an absolute URL from an instance's runtime config. */
internal fun isSecureUrl(url: String, allowLocalhostHttp: Boolean): Boolean =
    parseUrl(url)?.isAllowed(allowLocalhostHttp) ?: false

private class ParsedUrl(val scheme: String, val host: String, val port: Int?) {
    fun isAllowed(allowLocalhostHttp: Boolean): Boolean =
        scheme == "https" || (allowLocalhostHttp && host == LOCALHOST)

    val origin: String
        get() =
            "$scheme://$host" + (port?.takeIf { it != defaultPorts[scheme] }?.let { ":$it" } ?: "")
}

private fun parseUrl(text: String): ParsedUrl? {
    val match = urlPattern.matchEntire(text) ?: return null
    val (rawScheme, rawHost, rawPort) = match.destructured
    val scheme = rawScheme.lowercase().ifEmpty { "https" }
    if (scheme !in defaultPorts) return null
    val host = rawHost.lowercase()
    if (!host.split('.').all { hostLabel.matches(it) }) return null
    val port = rawPort.takeIf { it.isNotEmpty() }?.let { it.toIntOrNull() ?: return null }
    if (port != null && port !in 1..65535) return null
    return ParsedUrl(scheme, host, port)
}
