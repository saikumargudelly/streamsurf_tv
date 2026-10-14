package dev.veyra.tv.browser.filter

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView

/**
 * Common abstraction boundary for ad blocking, tracking prevention,
 * cosmetic element hiding, and popup/redirect suppression.
 */
interface AdBlockingEngine {

    /**
     * Whether ad blocking and shield protections are globally active.
     */
    var isEnabled: Boolean

    /**
     * Initializes the engine with application context and loads persisted rules.
     */
    fun initialize(context: Context)

    /**
     * Intercepts subresource requests from [WebViewClient.shouldInterceptRequest].
     * Returns a [WebResourceResponse] (e.g. 204 No Content or surrogate JS) if blocked,
     * or null if the request is permitted to proceed.
     */
    fun shouldIntercept(
        view: WebView?,
        request: WebResourceRequest?,
        documentUrl: String?
    ): WebResourceResponse?

    /**
     * Evaluates a URL string for subresource blocking.
     */
    fun shouldInterceptUrl(
        url: String?,
        documentUrl: String?
    ): WebResourceResponse?

    /**
     * Checks if a destination URL is a known ad network redirect, popunder launch script,
     * or malicious tracker that should be prevented from navigating in the WebView.
     */
    fun shouldBlockNavigation(url: String?): Boolean

    /**
     * Evaluates whether a given host should be filtered.
     */
    fun isBlocked(host: String?): Boolean

    /**
     * Allowlist management. Allowlisted hosts bypass all blocking rules.
     */
    fun isAllowlisted(host: String): Boolean
    fun allowlistHost(host: String, context: Context? = null)
    fun removeAllowlistedHost(host: String, context: Context? = null)
    fun clearAllowlist(context: Context? = null)

    /**
     * Computes cosmetic element-hiding CSS for the specified page URL.
     */
    fun getCosmeticCss(documentUrl: String): String

    /**
     * Injects cosmetic element hiding and anti-popup shields into the active WebView.
     */
    fun applyCosmeticRules(view: WebView?, url: String?)
}
