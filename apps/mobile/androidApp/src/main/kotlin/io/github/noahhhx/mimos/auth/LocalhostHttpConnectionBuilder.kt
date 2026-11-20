package io.github.noahhhx.mimos.auth

import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import net.openid.appauth.connectivity.ConnectionBuilder
import net.openid.appauth.connectivity.DefaultConnectionBuilder

/**
 * AppAuth's default builder refuses `http://`. A debug or minified build also reaches the compose
 * stack's Keycloak through `adb reverse`, where the device's `localhost` is the host's, and nothing
 * else.
 */
internal object LocalhostHttpConnectionBuilder : ConnectionBuilder {
    private val timeoutMillis = TimeUnit.SECONDS.toMillis(15).toInt()

    override fun openConnection(uri: Uri): HttpURLConnection {
        if (uri.scheme != "http" || uri.host != "localhost") {
            return DefaultConnectionBuilder.INSTANCE.openConnection(uri)
        }
        return (URL(uri.toString()).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMillis
            readTimeout = timeoutMillis
            instanceFollowRedirects = false
        }
    }
}
