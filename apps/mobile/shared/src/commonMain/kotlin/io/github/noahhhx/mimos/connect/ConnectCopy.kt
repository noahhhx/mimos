package io.github.noahhhx.mimos.connect

import io.github.noahhhx.mimos.instance.Discovery
import io.github.noahhhx.mimos.instance.InsecureAddress
import io.github.noahhhx.mimos.instance.InvalidAddress
import io.github.noahhhx.mimos.instance.MIN_SERVER_VERSION
import io.github.noahhhx.mimos.instance.RELEASES_URL
import io.github.noahhhx.mimos.instance.Version

/** The self-hosting guide's "Why HTTPS" (ADR-0030). */
const val WHY_HTTPS_URL = "https://noahhhx.github.io/mimos/self-hosting/#why-https"

fun Discovery.Failure.message(): String =
    when (this) {
        InvalidAddress ->
            "That is not a web address. Type the address you open Mimos at in a browser, such as mimos.example.com."
        InsecureAddress -> "Mimos needs an https:// address."
        Discovery.InsecureServer ->
            "This server is set up to send the app to an http:// address. Whoever runs it must serve Mimos over https://."
        Discovery.Unreachable ->
            "Mimos could not reach that address. Check it and your connection, then try again."
        Discovery.NotMimos ->
            "That address does not answer as Mimos. Use the address you open Mimos at in a browser."
        is Discovery.ServerTooOld ->
            if (serverVersion != null && MIN_SERVER_VERSION > Version(0, 0, 0)) {
                "This server runs Mimos $serverVersion. The app needs $MIN_SERVER_VERSION or later. Ask whoever runs it to update it."
            } else {
                "This server runs an older Mimos than the app supports. Ask whoever runs it to update it."
            }
        is Discovery.AppTooOld ->
            "This server needs version $minAppVersion of the app or later. Update the app, then connect again."
    }

/** A page that explains a failure or the way past it. */
data class HelpLink(val label: String, val url: String)

fun Discovery.Failure.helpLink(): HelpLink? =
    when (this) {
        InsecureAddress,
        Discovery.InsecureServer -> HelpLink("Why Mimos needs HTTPS", WHY_HTTPS_URL)
        is Discovery.AppTooOld -> HelpLink("Get the new app", RELEASES_URL)
        else -> null
    }
