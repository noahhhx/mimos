package io.github.noahhhx.mimos.client

/**
 * A failed API call: the server's RFC 9457 problem details when it sent them, and the call's
 * request id, which names its lines in the server's logs.
 */
class ApiProblem(val status: Int, val title: String?, val detail: String?, val requestId: String?) :
    Exception(listOfNotNull("HTTP $status", title, detail).joinToString(": "))
