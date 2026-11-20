package io.github.secnewt.maps.google

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import io.github.secnewt.maps.link.LinkResult
import io.github.secnewt.maps.link.LinkReader
import io.github.secnewt.maps.link.ShortLinkResolver
import kotlin.math.abs

/**
 * "Ask Google": loads the link in an off-screen WebView so Google's own page runs and
 * rewrites its address to the …/@lat,lng form, then reads the coordinates from that address.
 * No cookies or site storage are kept. Only used when the user taps Ask Google, and only
 * for Google's own addresses ([isGoogleLink]).
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
        // Google's Maps page is a heavy single-page app that needs localStorage/IndexedDB
        // (DOM storage is off by default on WebView) and may serve a degraded experience
        // when it sees the WebView UA marker ("; wv"), so both are removed for this lookup.
        view.settings.domStorageEnabled = true
        view.settings.databaseEnabled = true
        view.settings.userAgentString = WebSettings.getDefaultUserAgent(context).replace("; wv", "")
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
        // DOM storage is on for the lookup; don't leave Google's localStorage behind either.
        WebStorage.getInstance().deleteAllData()
    }

    private fun check(url: String) {
        val result = LinkReader.read(url)
        // Google's page briefly shows a "@0,0" world-view address before it resolves the
        // search (or as a fallback if it never does) — that's not an answer, so keep
        // waiting rather than finishing on it.
        if (result is LinkResult.Place && !result.isGoogleWorldViewPlaceholder()) finish(result)
    }

    private fun LinkResult.Place.isGoogleWorldViewPlaceholder() = abs(lat) < 0.0001 && abs(lng) < 0.0001

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
        /**
         * A web link on one of Google's map hosts, the same ones the short-link reader trusts
         * ([ShortLinkResolver.GOOGLE]). A look-alike such as "notgoogle.com" or
         * "google.com.example.net" is not, nor a Google host for other people's pages
         * (sites.google.com), so their script never runs in the lookup.
         */
        fun isGoogleLink(link: String): Boolean {
            val uri = try { java.net.URI(link) } catch (_: java.net.URISyntaxException) { return false }
            if (uri.scheme?.lowercase() !in setOf("http", "https")) return false
            val host = uri.host?.lowercase()?.trimEnd('.') ?: return false
            return host in ShortLinkResolver.GOOGLE
        }
        const val POLL_MS = 500L
    }
}
