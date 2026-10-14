package dev.veyra.tv.browser.engine

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import dev.veyra.tv.browser.security.SecurityPolicy

/**
 * Manages the single active WebView instance, enforcing memory optimization,
 * session persistence, and lifecycle transitions.
 */
class VeyraWebViewController(
    private val context: Context,
    private val container: FrameLayout
) {
    var webView: WebView? = null
        private set

    var isDesktopMode: Boolean = dev.veyra.tv.browser.storage.BrowserPreferences.isDesktopMode(context)
        private set

    private var currentUrl: String? = null
    private var cachedClient: VeyraWebViewClient? = null
    private var cachedChromeClient: VeyraWebChromeClient? = null

    fun initializeWebView(
        client: VeyraWebViewClient,
        chromeClient: VeyraWebChromeClient
    ): WebView {
        cachedClient = client
        cachedChromeClient = chromeClient

        // Recycle existing webview if already allocated
        val targetWebView = webView ?: WebView(context).also { wv ->
            wv.id = dev.veyra.tv.browser.R.id.activeWebView
            wv.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            SecurityPolicy.applySecurityDefaults(wv, isDesktopMode)
            wv.isFocusable = true
            wv.isFocusableInTouchMode = true
            container.addView(wv)
            webView = wv
        }

        targetWebView.webViewClient = client
        targetWebView.webChromeClient = chromeClient

        return targetWebView
    }

    fun toggleDesktopMode(): Boolean {
        isDesktopMode = !isDesktopMode
        dev.veyra.tv.browser.storage.BrowserPreferences.setDesktopMode(context, isDesktopMode)
        webView?.let { wv ->
            wv.settings.userAgentString = if (isDesktopMode) {
                SecurityPolicy.DESKTOP_USER_AGENT
            } else {
                null // Revert to system default mobile user agent
            }
            wv.reload()
        }
        return isDesktopMode
    }

    var currentZoomPercent: Int = dev.veyra.tv.browser.storage.BrowserPreferences.getZoomLevel(context)
        private set

    fun zoomIn(): Int {
        if (currentZoomPercent < 200) {
            currentZoomPercent = (currentZoomPercent + 15).coerceAtMost(200)
            applyZoom()
        }
        return currentZoomPercent
    }

    fun zoomOut(): Int {
        if (currentZoomPercent > 70) {
            currentZoomPercent = (currentZoomPercent - 15).coerceAtLeast(70)
            applyZoom()
        }
        return currentZoomPercent
    }

    fun resetZoom(): Int {
        currentZoomPercent = dev.veyra.tv.browser.storage.BrowserPreferences.DEFAULT_ZOOM_LEVEL
        applyZoom()
        return currentZoomPercent
    }

    fun applyZoom() {
        dev.veyra.tv.browser.storage.BrowserPreferences.setZoomLevel(context, currentZoomPercent)
        webView?.let { wv ->
            wv.settings.textZoom = currentZoomPercent
            val zoomRatio = currentZoomPercent / 100.0
            wv.evaluateJavascript(
                "(function() { if (document.body) { document.body.style.zoom = '$zoomRatio'; } })();",
                null
            )
        }
    }


    fun loadUrl(url: String) {
        currentUrl = url
        cachedClient?.setCurrentMainUrl(url)
        webView?.loadUrl(url)
    }

    fun reload() {
        webView?.reload()
    }

    fun goBack(): Boolean {
        return if (webView?.canGoBack() == true) {
            webView?.goBack()
            true
        } else {
            false
        }
    }

    fun goForward(): Boolean {
        return if (webView?.canGoForward() == true) {
            webView?.goForward()
            true
        } else {
            false
        }
    }

    fun canGoBack(): Boolean = webView?.canGoBack() == true

    fun canGoForward(): Boolean = webView?.canGoForward() == true

    var isPaused: Boolean = false
        private set

    fun onPause() {
        if (!isPaused) {
            isPaused = true
            SecurityPolicy.flushCookies()
            webView?.onPause()
            webView?.pauseTimers()
        }
    }

    fun onResume() {
        if (isPaused) {
            isPaused = false
            webView?.onResume()
            webView?.resumeTimers()
        }
    }

    /**
     * Recovers from renderer process termination by tearing down the invalid
     * native WebView and re-instantiating a fresh active WebView.
     */
    fun recoverRenderer(
        client: VeyraWebViewClient? = null,
        chromeClient: VeyraWebChromeClient? = null
    ) {
        val targetClient = client ?: cachedClient ?: return
        val targetChromeClient = chromeClient ?: cachedChromeClient ?: return
        val lastUrl = currentUrl ?: webView?.url
        destroyWebView()
        initializeWebView(targetClient, targetChromeClient)
        if (!lastUrl.isNullOrEmpty()) {
            loadUrl(lastUrl)
        }
    }

    fun saveState(outState: Bundle) {
        webView?.saveState(outState)
    }

    fun restoreState(inState: Bundle) {
        webView?.restoreState(inState)
    }

    fun destroyWebView() {
        webView?.let { wv ->
            container.removeView(wv)
            wv.stopLoading()
            wv.loadUrl("about:blank")
            wv.clearHistory()
            wv.removeAllViews()
            wv.destroy()
        }
        webView = null
    }
}
