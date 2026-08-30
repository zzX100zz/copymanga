package top.fumiama.copymanga.web

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import top.fumiama.copymanga.R
import top.fumiama.copymanga.api.CopyMangaApi
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

class WebViewClient(private val context: Context, jsFileName: String):WebViewClient() {
    private val js = context.assets.open(jsFileName).readBytes().decodeToString()
    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        Log.d("MyWC", "Load URL: $url")
        url?.let {
            if(!it.startsWith(context.getString(R.string.web_home)) && !it.startsWith(context.getString(R.string.web_home_www))){
                view?.goBack()
                Toast.makeText(context, R.string.blocked_ad, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        url?.let {
            view?.loadUrl(js)
        }
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
        if (request.method != "GET" ||
            !url.contains("/api/v3/comic/") ||
            !url.contains("/chapter2/")) {
            return super.shouldInterceptRequest(view, request)
        }
        return try {
            // Let the official H5 endpoint see the authenticated request so its
            // server-side reading record remains connected. Its response also
            // supplies the exact metadata expected by this old Vue reader.
            val officialBody = runCatching { fetchOfficialChapter(request) }
                .onFailure { Log.w("MyWC", "Unable to forward official chapter request: $url", it) }
                .getOrNull()
            val fullBody = CopyMangaApi.fullChapterJsonForWebRequest(url)
            val body = CopyMangaApi.mergeFullChapterForWeb(officialBody, fullBody)
            val origin = request.requestHeaders.entries
                .firstOrNull { it.key.equals("Origin", ignoreCase = true) }
                ?.value ?: "*"
            WebResourceResponse(
                "application/json",
                "UTF-8",
                ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
            ).apply {
                setStatusCodeAndReasonPhrase(200, "OK")
                responseHeaders = mapOf(
                    "Access-Control-Allow-Origin" to origin,
                    "Access-Control-Allow-Credentials" to "true",
                    "Cache-Control" to "no-store"
                )
            }
        } catch (e: Exception) {
            Log.w("MyWC", "Unable to replace preview chapter response: $url", e)
            super.shouldInterceptRequest(view, request)
        }
    }

    private fun fetchOfficialChapter(request: WebResourceRequest): String {
        val url = request.url.toString()
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 20_000
        connection.readTimeout = 30_000
        connection.instanceFollowRedirects = true

        var hasCookie = false
        request.requestHeaders.forEach { (name, value) ->
            if (name.equals("Cookie", ignoreCase = true)) hasCookie = true
            if (!name.equals("Accept-Encoding", ignoreCase = true) &&
                !name.equals("Connection", ignoreCase = true) &&
                !name.equals("Host", ignoreCase = true)) {
                connection.setRequestProperty(name, value)
            }
        }
        if (!hasCookie) {
            CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let {
                connection.setRequestProperty("Cookie", it)
            }
        }
        connection.setRequestProperty("Accept-Encoding", "gzip")

        return try {
            val code = connection.responseCode
            val source = if (code >= 400) connection.errorStream else connection.inputStream
            val body = decodeStream(source, connection.getHeaderField("Content-Encoding"))
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            if (code >= 400) throw IllegalStateException("HTTP $code: $body")
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun decodeStream(stream: InputStream, encoding: String?): InputStream =
        if (encoding.equals("gzip", ignoreCase = true)) GZIPInputStream(stream) else stream
}
