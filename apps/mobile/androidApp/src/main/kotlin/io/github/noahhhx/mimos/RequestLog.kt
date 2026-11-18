package io.github.noahhhx.mimos

import android.util.Log
import io.github.noahhhx.mimos.client.REQUEST_ID_HEADER
import okhttp3.Interceptor
import okhttp3.Response

const val REQUEST_LOG_TAG = "MimosRequest"

class RequestLog : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        request.header(REQUEST_ID_HEADER)?.let {
            Log.i(
                REQUEST_LOG_TAG,
                "requestId=$it method=${request.method} path=${request.url.encodedPath}",
            )
        }
        return chain.proceed(request)
    }
}
