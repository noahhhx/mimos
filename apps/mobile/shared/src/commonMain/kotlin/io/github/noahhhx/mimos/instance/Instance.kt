package io.github.noahhhx.mimos.instance

import io.github.noahhhx.mimos.client.MimosJson
import kotlinx.serialization.Serializable

/** Who may create an account on an instance (ADR-0020). */
enum class SignupPolicy {
    OPEN,
    CLOSED,
}

/** How an instance's web app is configured: its `/runtime-config.json`. */
data class InstanceConfig(
    val webUrl: WebAddress,
    val apiUrl: String,
    val oidcAuthority: String,
    val oidcClientId: String,
    val signup: SignupPolicy,
)

data class Instance(val config: InstanceConfig, val serverVersion: Version)

/** Keeps the connected instance between launches. The app keeps one at a time. */
interface InstanceStore {
    fun load(): Instance?

    fun save(instance: Instance)

    fun clear()
}

/** The web's `PublicConfig` (`apps/web/src/lib/public-config.ts`). */
@Serializable
private data class RuntimeConfig(
    val apiUrl: String,
    val oidcAuthority: String,
    val oidcClientId: String,
    val signup: String? = null,
)

/**
 * Reads the web's `/runtime-config.json`. Anything the app uses that is missing, blank, or of the
 * wrong type means the site is not Mimos. A sign-up policy other than `open` reads as closed, and
 * an absent or blank one as open, as the web's does.
 */
internal fun parseInstanceConfig(webUrl: WebAddress, body: String): InstanceConfig? {
    val read =
        runCatching { MimosJson.decodeFromString<RuntimeConfig>(body) }.getOrNull() ?: return null
    if (listOf(read.apiUrl, read.oidcAuthority, read.oidcClientId).any { it.isBlank() }) {
        return null
    }
    return InstanceConfig(
        webUrl = webUrl,
        apiUrl = read.apiUrl.trimEnd('/'),
        oidcAuthority = read.oidcAuthority,
        oidcClientId = read.oidcClientId,
        signup = signupPolicy(read.signup),
    )
}

private fun signupPolicy(value: String?): SignupPolicy {
    val trimmed = value?.trim()?.lowercase()
    return if (trimmed.isNullOrEmpty() || trimmed == "open") SignupPolicy.OPEN
    else SignupPolicy.CLOSED
}

@Serializable
private data class StoredInstance(
    val webUrl: String,
    val apiUrl: String,
    val oidcAuthority: String,
    val oidcClientId: String,
    val signup: SignupPolicy,
    val serverVersion: String,
)

fun Instance.encode(): String =
    MimosJson.encodeToString(
        StoredInstance(
            webUrl = config.webUrl.origin,
            apiUrl = config.apiUrl,
            oidcAuthority = config.oidcAuthority,
            oidcClientId = config.oidcClientId,
            signup = config.signup,
            serverVersion = serverVersion.toString(),
        )
    )

/** Reads what [encode] wrote; null for anything else, which a store treats as no instance. */
fun decodeInstance(text: String): Instance? {
    val stored =
        runCatching { MimosJson.decodeFromString<StoredInstance>(text) }.getOrNull() ?: return null
    return Instance(
        config =
            InstanceConfig(
                webUrl =
                    parseWebAddress(stored.webUrl, allowLocalhostHttp = true) as? WebAddress
                        ?: return null,
                apiUrl = stored.apiUrl,
                oidcAuthority = stored.oidcAuthority,
                oidcClientId = stored.oidcClientId,
                signup = stored.signup,
            ),
        serverVersion = Version.parse(stored.serverVersion) ?: return null,
    )
}
