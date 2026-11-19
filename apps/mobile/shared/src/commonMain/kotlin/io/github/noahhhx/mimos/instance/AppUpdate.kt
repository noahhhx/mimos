package io.github.noahhhx.mimos.instance

import io.github.noahhhx.mimos.api.apis.InstanceApi
import kotlin.coroutines.cancellation.CancellationException

/**
 * Where the app's releases are. The app sends people there and never calls GitHub itself: it learns
 * of a newer version from its own server (ADR-0029).
 */
const val RELEASES_URL = "https://github.com/noahhhx/mimos/releases/latest"

/** How this app stands against the server it is connected to. */
sealed interface AppStanding {
    data object Current : AppStanding

    /** Server and app release together, so a server newer than the app means a newer app is out. */
    data class NewerAvailable(val serverVersion: Version) : AppStanding

    /** The server no longer supports this app. */
    data class Unsupported(val minAppVersion: Version) : AppStanding
}

fun appStanding(
    serverVersion: Version,
    minAppVersion: Version,
    appVersion: Version = APP_VERSION,
): AppStanding =
    when {
        appVersion < minAppVersion -> AppStanding.Unsupported(minAppVersion)
        serverVersion > appVersion -> AppStanding.NewerAvailable(serverVersion)
        else -> AppStanding.Current
    }

/** What the connected server says of itself, and so how this app stands against it. */
data class InstanceStatus(val serverVersion: Version, val standing: AppStanding) {
    companion object {
        /** What the app knows from connecting, before the server has said again. */
        fun remembered(instance: Instance, appVersion: Version = APP_VERSION): InstanceStatus =
            InstanceStatus(
                instance.serverVersion,
                appStanding(instance.serverVersion, Version(0, 0, 0), appVersion),
            )
    }
}

/**
 * Asks the connected server again, since it may have been upgraded since the app connected. Null
 * when it does not answer, or answers with versions the app cannot read: the app then goes on with
 * what it [remembered][InstanceStatus.remembered].
 */
suspend fun checkInstance(api: InstanceApi, appVersion: Version = APP_VERSION): InstanceStatus? {
    val served =
        try {
            api.getInstance().body()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        }
    val serverVersion = Version.parse(served.version) ?: return null
    val minAppVersion = Version.parse(served.minAppVersion) ?: return null
    return InstanceStatus(serverVersion, appStanding(serverVersion, minAppVersion, appVersion))
}
