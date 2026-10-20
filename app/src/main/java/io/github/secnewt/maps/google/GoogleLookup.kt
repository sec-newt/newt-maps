package io.github.secnewt.maps.google

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.secnewt.maps.link.LinkResult
import io.github.secnewt.maps.link.LinkReader

/**
 * "Ask Google": loads the link in an off-screen WebView so Google's own page runs and
 * rewrites its address to the …/@lat,lng form, then reads the coordinates from that address.
 * No cookies are kept. Only used when the user taps Ask Google.
 */
class GoogleLookup(private val context: Context) {

    private val main = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var done: ((LinkResult.Place?) -> Unit)? = null

    @SuppressLint("SetJavaScriptEnabled") // Google's page only gives up the location by running its script.
    fun start(link: String, onDone: (LinkResult.Place?) -> Unit) {
        cancel()
        done = onDone
        CookieManager.getInstance().setAcceptCookie(false)
        val view = WebView(context)
        view.settings.javaScriptEnabled = true
        view.settings.allowFileAccess = false
        view.settings.allowContentAccess = false
        view.webViewClient = object : WebViewClient() {
            override fun doUpdateVisitedHistory(v: WebView, url: String, isReload: Boolean) = check(url)
            override fun onPageFinished(v: WebView, url: String) = check(url)
        }
        webView = view
        main.postDelayed(::timeout, TIMEOUT_MS)
        main.postDelayed(::poll, POLL_MS)
        view.loadUrl(link)
    }

    fun cancel() {
        main.removeCallbacksAndMessages(null)
        webView?.apply { stopLoading(); destroy() }
        webView = null
        done = null
        CookieManager.getInstance().removeAllCookies(null)
    }

    private fun check(url: String) {
        val result = LinkReader.read(url)
        if (result is LinkResult.Place) finish(result)
    }

    // Belt and suspenders: Google's page rewrites its address with the History API
    // (replaceState), and doUpdateVisitedHistory doesn't reliably fire for that on every
    // WebView build. Polling view.url catches the rewrite even when the callback doesn't.
    private fun poll() {
        val view = webView ?: return
        check(view.url ?: return)
        main.postDelayed(::poll, POLL_MS)
    }

    private fun timeout() = finish(null)

    private fun finish(place: LinkResult.Place?) {
        val callback = done ?: return
        cancel()
        callback(place)
    }

    companion object {
        const val TIMEOUT_MS = 20_000L
        const val POLL_MS = 500L
    }
}
