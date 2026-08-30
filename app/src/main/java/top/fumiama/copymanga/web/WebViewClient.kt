package top.fumiama.copymanga.web

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import top.fumiama.copymanga.R
import top.fumiama.copymanga.api.CopyMangaApi
import java.io.ByteArrayInputStream

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
            val body = CopyMangaApi.fullChapterJsonForWebRequest(url)
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
}
