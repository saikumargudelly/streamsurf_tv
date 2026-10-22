package dev.veyra.tv.browser.engine

import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.veyra.tv.browser.filter.ContentBlocker
import dev.veyra.tv.browser.navigation.UrlHandler
import dev.veyra.tv.browser.security.SecurityPolicy

/**
 * Custom WebViewClient with SSL verification enforcement, renderer crash recovery,
 * mobile-to-desktop URL normalization, dynamic user-agent handling, and advanced
 * ad/popup/redirect filtering.
 */
class VeyraWebViewClient(
    private val onPageStartedCallback: (url: String) -> Unit,
    private val onPageFinishedCallback: (url: String) -> Unit,
    private val onSslErrorCallback: (url: String, error: SslError) -> Unit,
    private val onRendererCrashCallback: (didCrash: Boolean) -> Unit
) : WebViewClient() {

    @Volatile
    private var currentMainUrl: String? = null

    fun setCurrentMainUrl(url: String?) {
        currentMainUrl = url
    }

    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return true
        return handleUrlNavigation(view, url)
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
        if (url == null) return true
        return handleUrlNavigation(view, url)
    }

    private fun handleUrlNavigation(view: WebView?, url: String): Boolean {
        // Enforce desktop YouTube when coming from m.youtube.com
        if (url.startsWith("https://m.youtube.com")) {
            val desktopUrl = url.replace("https://m.youtube.com", "https://www.youtube.com")
            currentMainUrl = desktopUrl
            view?.loadUrl(desktopUrl)
            return true
        }

        // Apply TV Leanback User-Agent when accessing YouTube TV Leanback app
        if (url.contains("youtube.com/tv")) {
            view?.settings?.userAgentString = SecurityPolicy.TV_LEANBACK_USER_AGENT
        } else if (url.contains("youtube.com") || url.contains("netflix.com") || url.contains("amazon.") || url.contains("primevideo.") || url.contains("hotstar.") || url.contains("jiohotstar.") || url.contains("disneyplus.")) {
            view?.settings?.userAgentString = SecurityPolicy.DESKTOP_USER_AGENT
        }

        val targetHost = ContentBlocker.extractHostFromUrl(url)

        // Block navigation if target host or URL is a known ad, popup, tracker, or scam redirect domain
        if (ContentBlocker.isEnabled) {
            if (targetHost != null && ContentBlocker.isBlocked(targetHost)) {
                return true // Discard ad redirect
            }
            if (ContentBlocker.isRedirectOrPopupUrl(url)) {
                return true // Discard popup redirect
            }
        }

        // Allow normal HTTPS/HTTP navigation within WebView
        if (UrlHandler.isAllowedScheme(url)) {
            currentMainUrl = url
            return false
        }

        // Block unsafe schemes or custom app intents
        return true
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        currentMainUrl = url
        if (url?.contains("youtube.com/tv") == true) {
            view?.settings?.userAgentString = SecurityPolicy.TV_LEANBACK_USER_AGENT
        }
        injectDesktopEnvironment(view, url)
        url?.let { onPageStartedCallback(it) }
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?
    ): WebResourceResponse? {
        try {
            if (request != null && !request.isForMainFrame) {
                val url = request.url?.toString() ?: ""
                val requestHost = request.url?.host?.lowercase() ?: ""

                // For legitimate streaming subresources (DRM licenses, media manifests .m3u8/.mpd, video segments .mp4/.m4s/.ts),
                // pass through cleanly
                if (dev.veyra.tv.browser.filter.FilterListAdBlockingEngine.isMediaOrStreamingResource(url, requestHost)) {
                    return super.shouldInterceptRequest(view, request)
                }

                // Intercept network ads, tracking requests, and video ad tags using filter engine
                val blockedResponse = ContentBlocker.shouldIntercept(view, request, currentMainUrl)
                if (blockedResponse != null) {
                    return blockedResponse
                }
            }
        } catch (_: Throwable) {
            return super.shouldInterceptRequest(view, request)
        }
        return super.shouldInterceptRequest(view, request)
    }

    @Deprecated("Deprecated in Java")
    override fun shouldInterceptRequest(view: WebView?, url: String?): WebResourceResponse? {
        try {
            if (url != null) {
                val host = ContentBlocker.extractHostFromUrl(url) ?: ""
                if (dev.veyra.tv.browser.filter.FilterListAdBlockingEngine.isMediaOrStreamingResource(url, host)) {
                    return super.shouldInterceptRequest(view, url)
                }

                val blockedResponse = ContentBlocker.shouldInterceptUrl(url, currentMainUrl)
                if (blockedResponse != null) {
                    return blockedResponse
                }
            }
        } catch (_: Throwable) {
            return super.shouldInterceptRequest(view, url)
        }
        return super.shouldInterceptRequest(view, url)
    }

    companion object {
        fun isOttPlatform(text: String): Boolean {
            val lower = text.lowercase()
            return lower.contains("hotstar") ||
                   lower.contains("jiohotstar") ||
                   lower.contains("starott") ||
                   lower.contains("disney") ||
                   lower.contains("netflix") ||
                   lower.contains("primevideo") ||
                   lower.contains("amazon.") ||
                   lower.contains("akamaized.net") ||
                   lower.contains("boltdns.net") ||
                   lower.contains("conviva") ||
                   lower.contains("widevine")
        }
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
        super.onPageCommitVisible(view, url)
        ContentBlocker.applyCosmeticRules(view, url)
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        injectDesktopEnvironment(view, url)
        ContentBlocker.applyCosmeticRules(view, url)
        url?.let { onPageFinishedCallback(it) }
    }

    /**
     * Injects client hints, true desktop viewport scaling, Widevine EME fallback,
     * and anti-popup shields so OTT websites render smoothly and securely.
     */
    private fun injectDesktopEnvironment(view: WebView?, currentUrl: String?) {
        if (currentUrl?.contains("youtube.com/tv") == true) return
        view?.evaluateJavascript(SecurityPolicy.DESKTOP_INJECTION_SCRIPT.trimIndent(), null)
    }

    /**
     * Strict SSL error handling. Rejects invalid TLS certificates.
     * Cancels the request immediately without ever proceeding.
     * Main-frame TLS failures notify the activity to display a security warning;
     * subresource TLS failures are canceled silently to protect the main browsing session.
     */
    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
        handler?.cancel()
        val failingUrl = error?.url ?: ""
        val mainUrl = currentMainUrl ?: ""

        val isMainFrame = failingUrl.isBlank() ||
                mainUrl.isBlank() ||
                isMatchingMainUrl(failingUrl, mainUrl)

        if (isMainFrame && error != null) {
            onSslErrorCallback(failingUrl.ifBlank { mainUrl }, error)
        }
    }

    private fun isMatchingMainUrl(failingUrl: String, mainUrl: String): Boolean {
        if (failingUrl.equals(mainUrl, ignoreCase = true)) return true
        val failingClean = failingUrl.trimEnd('/')
        val mainClean = mainUrl.trimEnd('/')
        if (failingClean.equals(mainClean, ignoreCase = true)) return true
        return try {
            val failingUri = Uri.parse(failingUrl)
            val mainUri = Uri.parse(mainUrl)
            failingUri.scheme.equals(mainUri.scheme, ignoreCase = true) &&
                    failingUri.host.equals(mainUri.host, ignoreCase = true) &&
                    failingUri.path == mainUri.path
        } catch (_: Exception) {
            false
        }
    }

    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
        val didCrash = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            detail?.didCrash() ?: false
        } else {
            false
        }
        onRendererCrashCallback(didCrash)
        return true
    }
}
