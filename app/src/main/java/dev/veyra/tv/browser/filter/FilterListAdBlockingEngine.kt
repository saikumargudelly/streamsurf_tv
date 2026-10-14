package dev.veyra.tv.browser.filter

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import dev.veyra.tv.browser.storage.BrowserPreferences
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-performance, pure Kotlin filter-list ad blocking engine for Android TV.
 *
 * Capabilities:
 * - Parses standard EasyList & EasyPrivacy rules (domain filters, subresource options, exceptions, and cosmetic element hiding)
 * - Zero native dependencies (100% safe across all Android TV architectures: ARMv7 32-bit, ARM64, x86, 16KB page size)
 * - Safe asynchronous background rule loading with instant fallback to built-in domain filter
 * - Streaming OTT playback preservation (Hotstar, Netflix, Prime Video, YouTube) with Google IMA SDK surrogate
 * - Anti-popup, anti-redirect, and video ad auto-skip protections
 * - Cosmetic element-hiding CSS injection
 */
class FilterListAdBlockingEngine : AdBlockingEngine {

    companion object {
        private const val TAG = "FilterListAdBlock"
        private const val ASSET_FILTER_PATH = "filters/easylist_core.txt"

        private fun logInfo(msg: String) {
            try { android.util.Log.i(TAG, msg) } catch (_: Throwable) { println("[$TAG] $msg") }
        }

        private fun logWarn(msg: String) {
            try { android.util.Log.w(TAG, msg) } catch (_: Throwable) { println("[$TAG] $msg") }
        }

        private fun logError(msg: String, tr: Throwable? = null) {
            try { android.util.Log.e(TAG, msg, tr) } catch (_: Throwable) { println("[$TAG] $msg: ${tr?.message}") }
        }
    }

    override var isEnabled: Boolean = true

    private val allowlist = ConcurrentHashMap.newKeySet<String>()
    private val blockedDomains = ConcurrentHashMap.newKeySet<String>()
    private val exceptionDomains = ConcurrentHashMap.newKeySet<String>()
    private val cosmeticSelectors = ConcurrentHashMap.newKeySet<String>()
    private val domainCosmeticSelectors = ConcurrentHashMap<String, MutableSet<String>>()
    private val urlSubstrings = CopyOnWriteArrayList<String>()

    private val isInitialized = AtomicBoolean(false)

    init {
        // Pre-populate with ContentBlocker built-ins so engine is active immediately
        blockedDomains.addAll(ContentBlocker.getBlockedDomainsSnapshot())
    }

    override fun initialize(context: Context) {
        isEnabled = BrowserPreferences.isContentBlockerEnabled(context)
        allowlist.clear()
        allowlist.addAll(BrowserPreferences.getAllowlistedHosts(context))

        if (isInitialized.compareAndSet(false, true)) {
            Thread({
                try {
                    loadRulesFromAssets(context)
                } catch (t: Throwable) {
                    logError("Failed to load filter rules from assets: ${t.message}", t)
                }
            }, "FilterListEngine-Init").start()
        }
    }

    private fun loadRulesFromAssets(context: Context) {
        val assetManager = context.assets
        try {
            assetManager.open(ASSET_FILTER_PATH).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    var line: String?
                    var ruleCount = 0
                    while (reader.readLine().also { line = it } != null) {
                        val trimmed = line?.trim() ?: continue
                        if (trimmed.isEmpty() || trimmed.startsWith("!") || trimmed.startsWith("[")) {
                            continue
                        }
                        parseRule(trimmed)
                        ruleCount++
                    }
                    logInfo("Loaded and parsed $ruleCount filter rules from $ASSET_FILTER_PATH")
                }
            }
        } catch (t: Throwable) {
            logWarn("Could not open $ASSET_FILTER_PATH, keeping default rule set: ${t.message}")
        }
    }

    /**
     * Parses an ABP / EasyList standard rule string.
     */
    fun parseRule(rawRule: String) {
        val rule = rawRule.trim()
        if (rule.isEmpty() || rule.startsWith("!") || rule.startsWith("[")) return

        // 1. Cosmetic Element Hiding Rule (##selector or domain##selector)
        val cosmeticIndex = rule.indexOf("##")
        if (cosmeticIndex != -1) {
            val domainPart = rule.substring(0, cosmeticIndex).trim()
            val selector = rule.substring(cosmeticIndex + 2).trim()
            if (selector.isNotEmpty()) {
                if (domainPart.isEmpty()) {
                    cosmeticSelectors.add(selector)
                } else {
                    for (domain in domainPart.split(",")) {
                        val d = domain.trim().lowercase()
                        if (d.isNotEmpty()) {
                            domainCosmeticSelectors.computeIfAbsent(d) { ConcurrentHashMap.newKeySet() }.add(selector)
                        }
                    }
                }
            }
            return
        }

        // 2. Exception Rule (@@||domain^ or @@rule)
        if (rule.startsWith("@@")) {
            val unblockRule = rule.substring(2)
            if (unblockRule.startsWith("||")) {
                val endIdx = unblockRule.indexOf('^').let { if (it == -1) unblockRule.indexOf('$') else it }
                val domain = (if (endIdx != -1) unblockRule.substring(2, endIdx) else unblockRule.substring(2)).trim().lowercase()
                if (domain.isNotEmpty()) {
                    exceptionDomains.add(domain)
                }
            }
            return
        }

        // 3. Domain blocking rule (||domain.com^ options)
        if (rule.startsWith("||")) {
            val endIdx = rule.indexOf('^').let { if (it == -1) rule.indexOf('$') else it }
            val domain = (if (endIdx != -1) rule.substring(2, endIdx) else rule.substring(2)).trim().lowercase()
            if (domain.isNotEmpty()) {
                blockedDomains.add(domain)
            }
            return
        }

        // 4. Substring rule (e.g. /ads/vast)
        if (rule.length > 3 && !rule.contains(" ")) {
            urlSubstrings.add(rule)
        }
    }

    override fun shouldIntercept(
        view: WebView?,
        request: WebResourceRequest?,
        documentUrl: String?
    ): WebResourceResponse? {
        if (!isEnabled || request == null || request.isForMainFrame) {
            return null
        }

        val requestUrl = request.url?.toString() ?: return null
        val requestHost = request.url?.host?.lowercase() ?: ""
        val mainUrl = documentUrl ?: ""

        // 1. Google IMA SDK (ima3.js): return compliant dummy SDK surrogate for uninterrupted OTT playback
        if (requestUrl.contains("imasdk.googleapis.com") || requestUrl.contains("/ima3.js")) {
            return ContentBlocker.createDummyImaResponse()
        }

        // 2. Allowlisted hosts bypass all filtering
        if (requestHost.isNotBlank() && isAllowlisted(requestHost)) {
            return null
        }

        // 3. Exception rules bypass filtering
        if (isExceptionDomain(requestHost)) {
            return null
        }

        // 4. Protect essential OTT streaming resources
        if (isOttStreamingResource(requestUrl, requestHost, mainUrl)) {
            return null
        }

        // 5. Match against blocked domains
        if (isDomainBlocked(requestHost)) {
            return ContentBlocker.createBlockedResponse()
        }

        // 6. Match against substring rules or popups/redirects
        if (matchesUrlRules(requestUrl) || ContentBlocker.isRedirectOrPopupUrl(requestUrl)) {
            return ContentBlocker.createBlockedResponse()
        }

        return null
    }

    override fun shouldInterceptUrl(
        url: String?,
        documentUrl: String?
    ): WebResourceResponse? {
        if (!isEnabled || url.isNullOrBlank()) return null

        if (url.contains("imasdk.googleapis.com") || url.contains("/ima3.js")) {
            return ContentBlocker.createDummyImaResponse()
        }

        val host = ContentBlocker.extractHostFromUrl(url) ?: ""
        if (host.isNotBlank() && isAllowlisted(host)) {
            return null
        }

        if (isExceptionDomain(host)) {
            return null
        }

        val mainUrl = documentUrl ?: url
        if (isOttStreamingResource(url, host, mainUrl)) {
            return null
        }

        if (isDomainBlocked(host) || matchesUrlRules(url) || ContentBlocker.isRedirectOrPopupUrl(url)) {
            return ContentBlocker.createBlockedResponse()
        }

        return null
    }

    private fun isExceptionDomain(host: String): Boolean {
        if (host.isBlank() || exceptionDomains.isEmpty()) return false
        val normalized = ContentBlocker.normalizeHost(host) ?: return false
        return ContentBlocker.isHostInSet(normalized, exceptionDomains)
    }

    private fun isDomainBlocked(host: String): Boolean {
        if (host.isBlank()) return false
        val normalized = ContentBlocker.normalizeHost(host) ?: return false
        return ContentBlocker.isHostInSet(normalized, blockedDomains)
    }

    private fun matchesUrlRules(url: String): Boolean {
        if (urlSubstrings.isEmpty()) return false
        for (pattern in urlSubstrings) {
            if (url.contains(pattern, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    private fun isOttStreamingResource(url: String, host: String, documentUrl: String): Boolean {
        // Protect DRM licenses and media segments even on streaming platforms
        val lowerUrl = url.lowercase()
        val lowerHost = host.lowercase()
        val isOtt = ContentBlocker.isOttPlatform(documentUrl) || ContentBlocker.isOttPlatform(lowerHost)
        if (!isOtt) return false

        // Ad delivery subdomains on OTT are blocked
        if (lowerHost.contains("videoads") || lowerHost.contains("hesads") || lowerUrl.contains("/ads/vast")) {
            return false
        }

        return lowerUrl.contains("widevine") ||
                lowerUrl.contains(".mpd") ||
                lowerUrl.contains(".m3u8") ||
                lowerUrl.contains(".mp4") ||
                lowerUrl.contains(".m4s") ||
                lowerUrl.contains(".ts") ||
                lowerUrl.contains("license") ||
                lowerHost.contains("akamaized.net") ||
                lowerHost.contains("boltdns.net")
    }

    override fun shouldBlockNavigation(url: String?): Boolean {
        if (!isEnabled || url.isNullOrBlank()) return false
        return ContentBlocker.isRedirectOrPopupUrl(url)
    }

    override fun isBlocked(host: String?): Boolean {
        if (!isEnabled || host.isNullOrBlank()) return false
        val normalized = ContentBlocker.normalizeHost(host) ?: return false

        if (isAllowlisted(normalized)) {
            return false
        }

        return isDomainBlocked(normalized)
    }

    override fun isAllowlisted(host: String): Boolean {
        val normalized = ContentBlocker.normalizeHost(host) ?: return false
        return ContentBlocker.isHostInSet(normalized, allowlist)
    }

    override fun allowlistHost(host: String, context: Context?) {
        ContentBlocker.normalizeHost(host)?.let {
            allowlist.add(it)
            context?.let { ctx -> BrowserPreferences.addAllowlistedHost(ctx, it) }
        }
    }

    override fun removeAllowlistedHost(host: String, context: Context?) {
        ContentBlocker.normalizeHost(host)?.let {
            allowlist.remove(it)
            context?.let { ctx -> BrowserPreferences.removeAllowlistedHost(ctx, it) }
        }
    }

    override fun clearAllowlist(context: Context?) {
        allowlist.clear()
        context?.let { ctx -> BrowserPreferences.clearAllowlist(ctx) }
    }

    override fun getCosmeticCss(documentUrl: String): String {
        val host = ContentBlocker.extractHostFromUrl(documentUrl)?.lowercase() ?: ""
        val additionalSelectors = mutableListOf<String>()

        if (cosmeticSelectors.isNotEmpty()) {
            additionalSelectors.addAll(cosmeticSelectors)
        }

        if (host.isNotEmpty()) {
            domainCosmeticSelectors[host]?.let { additionalSelectors.addAll(it) }
        }

        val extraCss = if (additionalSelectors.isNotEmpty()) {
            additionalSelectors.joinToString(", ") + " { display: none !important; visibility: hidden !important; }"
        } else ""

        return "${ContentBlocker.COSMETIC_BLOCKING_CSS}\n$extraCss"
    }

    override fun applyCosmeticRules(view: WebView?, url: String?) {
        if (!isEnabled || view == null || url.isNullOrBlank()) return
        val css = getCosmeticCss(url)
        val escapedCss = css.replace("`", "\\`").replace("\\", "\\\\")
        val js = """
            (function() {
                try {
                    var styleId = 'veyra-adblock-cosmetic';
                    var existing = document.getElementById(styleId);
                    if (!existing) {
                        var style = document.createElement('style');
                        style.id = styleId;
                        style.type = 'text/css';
                        style.appendChild(document.createTextNode(`$escapedCss`));
                        (document.head || document.documentElement).appendChild(style);
                    }
                } catch(e) {}
            })();
        """.trimIndent()
        view.evaluateJavascript(js, null)
    }
}
