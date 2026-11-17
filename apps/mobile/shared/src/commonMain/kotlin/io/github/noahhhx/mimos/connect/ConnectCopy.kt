package io.github.noahhhx.mimos.connect

import io.github.noahhhx.mimos.instance.Discovery
import io.github.noahhhx.mimos.instance.InsecureAddress
import io.github.noahhhx.mimos.instance.InvalidAddress
import io.github.noahhhx.mimos.instance.MIN_SERVER_VERSION

/** The self-hosting guide's "Why HTTPS" (ADR-0030). */
const val WHY_HTTPS_URL = "https://noahhhx.github.io/mimos/self-hosting/#why-https"

/** What the connect screen says when it could not connect. */
fun Discovery.Failure.message(): String =
    when (this) {
        InvalidAddress ->
            "That is not a web address. Type the address you open Mimos at in a browser, such as mimos.example.com."
        InsecureAddress ->
            "Mimos needs an https:// address. Only this device's own localhost may use http://."
        Discovery.Unreachable ->
            "Mimos could not reach that address. Check it and your connection, then try again."
        Discovery.NotMimos ->
            "That address does not answer as Mimos. Use the address you open Mimos at in a browser."
        is Discovery.ServerTooOld ->
            (serverVersion?.let { "This server runs Mimos $it. " }
                ?: "This server runs an older Mimos. ") +
                "The app needs $MIN_SERVER_VERSION or later; ask whoever runs it to update it."
        is Discovery.AppTooOld ->
            "This server needs version $minAppVersion of the app or later. Update the app, then connect again."
    }
