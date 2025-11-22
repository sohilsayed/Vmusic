// Location: com.example.holodex.extractor/DownloaderImpl.kt
package com.example.holodex.extractor

import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response // Ensure this is org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException

class DownloaderImpl(private val okHttpClient: OkHttpClient) : Downloader() {

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val okHttpRequestBuilder = okhttp3.Request.Builder()
            .url(request.url())
            .method(request.httpMethod(), request.dataToSend()?.toRequestBody(null))

        for ((headerName, headerValueList) in request.headers()) {
            if (headerValueList.size > 1) {
                headerValueList.forEach { headerValue ->
                    okHttpRequestBuilder.addHeader(headerName, headerValue)
                }
            } else if (headerValueList.size == 1) {
                okHttpRequestBuilder.header(headerName, headerValueList[0])
            }
        }

        val call = okHttpClient.newCall(okHttpRequestBuilder.build())
        val okHttpResponse = call.execute() // This is a synchronous call

        // It's crucial to read the body string only once if you need to inspect it AND pass it on.
        // If the body is very large, this could be memory inefficient.
        // For ReCaptcha detection, we often only need to check a small part or rely on status codes/headers.
        // However, many ReCaptcha pages are full HTML, so checking content is common.
        val responseBodyString = okHttpResponse.body?.string() // Read body once

        // Simplified ReCaptcha check
        if (okHttpResponse.code == 429 || (responseBodyString != null &&
                    (responseBodyString.contains("consent.youtube.com", ignoreCase = true) ||
                            responseBodyString.contains("道のページへようこそ", ignoreCase = true) ||
                            responseBodyString.contains("before you continue to youtube", ignoreCase = true) ||
                            responseBodyString.contains("/sorry/index?continue=", ignoreCase = true) ||
                            responseBodyString.contains("www.google.com/recaptcha", ignoreCase = true) ||
                            responseBodyString.contains("Щоб продовжити, підтвердьте, що ви не робот", ignoreCase = true)
                            ))) {
            okHttpResponse.close() // Ensure response is closed if not using its body stream
            throw ReCaptchaException("ReCaptcha Challenge Found", okHttpResponse.request.url.toString())
        }

        // Pass the already read responseBodyString to the Response constructor
        return Response(
            okHttpResponse.code,
            okHttpResponse.message,
            okHttpResponse.headers.toMultimap(), // Convert OkHttp Headers to Map<String, List<String>>
            responseBodyString,                  // Pass the String body directly
            okHttpResponse.request.url.toString()
        )
    }

    // As per your diff, the getCookies/setCookies overrides are removed
    // as they are not part of the current Downloader base class.
    // Cookie management, if needed, should be handled via OkHttp's CookieJar
    // configured on the OkHttpClient instance passed to this DownloaderImpl.
}