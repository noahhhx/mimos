package io.github.noahhhx.mimos.instance

import io.github.noahhhx.mimos.api.apis.InstanceApi
import io.github.noahhhx.mimos.client.ApiProblem
import io.github.noahhhx.mimos.client.MimosHttp
import io.ktor.client.HttpClient
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.serialization.ContentConvertException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException

sealed interface Discovery {
    data class Connected(val instance: Instance) : Discovery

    sealed interface Failure : Discovery

    /** The server is older than the app supports; its version when it said. */
    data class ServerTooOld(val serverVersion: Version?) : Failure

    data class AppTooOld(val minAppVersion: Version) : Failure

    data object Unreachable : Failure

    data object NotMimos : Failure

    /** The server's own config sends the app to an `http://` address. */
    data object InsecureServer : Failure
}

data object InvalidAddress : AddressResult, Discovery.Failure

/** An `http://` address the app may not use (ADR-0030). */
data object InsecureAddress : AddressResult, Discovery.Failure

/**
 * Connects to the instance whose web address a person typed: the web's `/runtime-config.json` names
 * its API, whose `getInstance` names the server's version and the oldest app it supports.
 */
suspend fun discover(
    input: String,
    engine: HttpClientEngine,
    allowLocalhostHttp: Boolean,
    appVersion: Version = APP_VERSION,
    minServerVersion: Version = MIN_SERVER_VERSION,
): Discovery {
    val webUrl =
        when (val address = parseWebAddress(input, allowLocalhostHttp)) {
            is WebAddress -> address
            InvalidAddress -> return InvalidAddress
            InsecureAddress -> return InsecureAddress
        }
    val (status, body) =
        fetchText(engine, "${webUrl.origin}/runtime-config.json") ?: return Discovery.Unreachable
    if (status.value >= 500) return Discovery.Unreachable
    if (!status.isSuccess()) return Discovery.NotMimos
    val config = parseInstanceConfig(webUrl, body) ?: return Discovery.NotMimos
    if (!isSecureUrl(config.apiUrl, allowLocalhostHttp)) return Discovery.InsecureServer
    val served =
        try {
            MimosHttp(engine, config.apiUrl).use { it.api(::InstanceApi).getInstance().body() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiProblem) {
            return if (e.status == 404) Discovery.ServerTooOld(null) else Discovery.Unreachable
        } catch (e: Exception) {
            return if (e.isUnreadable()) Discovery.NotMimos else Discovery.Unreachable
        }
    val serverVersion = Version.parse(served.version) ?: return Discovery.NotMimos
    val minAppVersion = Version.parse(served.minAppVersion) ?: return Discovery.NotMimos
    return when {
        serverVersion < minServerVersion -> Discovery.ServerTooOld(serverVersion)
        appVersion < minAppVersion -> Discovery.AppTooOld(minAppVersion)
        else -> Discovery.Connected(Instance(config, serverVersion))
    }
}

private suspend fun fetchText(
    engine: HttpClientEngine,
    url: String,
): Pair<HttpStatusCode, String>? =
    try {
        HttpClient(engine).use { client -> client.get(url).let { it.status to it.bodyAsText() } }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

private fun Throwable.isUnreadable(): Boolean =
    this is SerializationException ||
        this is ContentConvertException ||
        this is NoTransformationFoundException
