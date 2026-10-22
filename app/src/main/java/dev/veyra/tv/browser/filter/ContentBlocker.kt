package dev.veyra.tv.browser.filter

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import dev.veyra.tv.browser.storage.BrowserPreferences
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentHashMap

import android.webkit.WebResourceRequest
import android.webkit.WebView

/**
 * Advanced in-app network request filter, cosmetic ad blocker, and popup/redirect shield
 * for Android System WebView.
 *
 * Implements [AdBlockingEngine] and integrates the Edsuns/AdblockAndroid native C++
 * rule matching core (Brave ad-block) with offline EasyList filter rules, deterministic
 * domain matching, and streaming platform protection.
 */
object ContentBlocker : AdBlockingEngine {

    val engine: AdBlockingEngine by lazy { FilterListAdBlockingEngine() }

    fun getBlockedDomainsSnapshot(): Set<String> = blockedDomains

    override var isEnabled: Boolean
        get() = engine.isEnabled
        set(value) {
            engine.isEnabled = value
        }

    // Thread-safe allowlist of hostnames exempted from filtering
    private val allowlist = ConcurrentHashMap.newKeySet<String>()

    // Set of normalized blocked domain suffixes (ad networks, analytics, trackers, popunder engines)
    // Curated from Peter Lowe's Blocklist, EasyList Core, and top popunder/redirect network databases
    private val blockedDomains: Set<String> = HashSet<String>(600).apply {
        // Google AdServices, DoubleClick, IMA SDK & Video Ad Delivery
        add("doubleclick.net")
        add("googleadservices.com")
        add("googlesyndication.com")
        add("google-analytics.com")
        add("adservice.google.com")
        add("pagead2.googlesyndication.com")
        add("admob.com")
        add("ads.google.com")
        add("analytics.google.com")
        add("imasdk.googleapis.com")
        add("pubads.g.doubleclick.net")
        add("securepubads.g.doubleclick.net")
        add("googleads.g.doubleclick.net")
        add("static.doubleclick.net")
        add("g.doubleclick.net")

        // Hotstar & OTT Video Ad Delivery, Ad CDNs & Telemetry
        add("videoads.hotstar.com")
        add("hesads.akamaized.net")
        add("ads.hotstar.com")
        add("campaigns.hotstar.com")
        add("events-analytics.hotstar.com")
        add("fp-analytics.hotstar.com")
        add("hb-analytics.hotstar.com")
        add("settings-analytics.hotstar.com")
        add("secure-dcr.hotstar.com")
        add("cws-hotstar.conviva.com")

        // Major Ad Exchanges & Networks
        add("criteo.com")
        add("criteo.net")
        add("taboola.com")
        add("outbrain.com")
        add("scorecardresearch.com")
        add("quantserve.com")
        add("quantcount.com")
        add("openx.net")
        add("rubiconproject.com")
        add("pubmatic.com")
        add("casalemedia.com")
        add("indexexchange.com")
        add("adnxs.com")
        add("applovin.com")
        add("applvn.com")
        add("unityads.unity3d.com")
        add("vungle.com")
        add("chartboost.com")
        add("inmobi.com")
        add("mopub.com")
        add("ironsrc.com")
        add("supersonicads.com")
        add("adcolony.com")
        add("fyber.com")
        add("bidswitch.net")
        add("smartadserver.com")
        add("adsystem.com")
        add("amazon-adsystem.com")
        add("advertising.com")
        add("adtechus.com")
        add("adtech.de")
        add("advertising.amazon.com")
        add("a-msedge.net")
        add("adnxs.net")
        add("media.net")
        add("sovrn.com")
        add("lijit.com")
        add("yieldmo.com")
        add("triplelift.com")
        add("teads.tv")
        add("gumgum.com")
        add("sharethrough.com")
        add("undertone.com")
        add("exponential.com")
        add("conversantmedia.com")
        add("contextweb.com")
        add("smartclip.net")
        add("spotxchange.com")
        add("spotx.tv")
        add("tremorhub.com")
        add("unruly.co")
        add("distroscale.com")
        add("sonobi.com")
        add("yieldlab.net")
        add("liveintent.com")
        add("nativo.com")
        add("smaato.net")
        add("infolinks.com")
        add("chitika.net")
        add("adverline.com")
        add("yieldlove.com")

        // Popunder, Popup & Aggressive Redirect Networks
        add("popads.net")
        add("popcash.net")
        add("propellerads.com")
        add("propellerclick.com")
        add("adcash.com")
        add("exoclick.com")
        add("trafficjunky.com")
        add("adsterra.com")
        add("clickadu.com")
        add("hilltopads.com")
        add("monetag.com")
        add("evadav.com")
        add("bidvertiser.com")
        add("trafficstars.com")
        add("juicyads.com")
        add("ero-advertising.com")
        add("kadam.net")
        add("kadam.ru")
        add("richads.com")
        add("pushub.com")
        add("pushground.com")
        add("yllix.com")
        add("a-ads.com")
        add("admaven.com")
        add("ad-maven.com")
        add("popmyads.com")
        add("onclickalgo.com")
        add("onclickperformance.com")
        add("adtrue.com")
        add("adxprts.com")
        add("runative-syndicate.com")
        add("ad-delivery.net")
        add("adkernel.com")
        add("adnuntius.com")
        add("adtarget.io")
        add("advangelists.com")
        add("bidtheatre.com")
        add("adsupply.com")
        add("plugrush.com")
        add("trafficfactory.biz")
        add("zergnet.com")
        add("revcontent.com")
        add("mgid.com")
        add("adblade.com")
        add("adroll.com")
        add("zedo.com")
        add("revenuehits.com")
        add("adclickmedia.com")
        add("clicksfly.com")
        add("clksite.com")
        add("adversal.com")
        add("clicksor.com")
        add("bidvertiser.com")
        add("galaksion.com")

        // Gambling, Scam & Spam Redirect Domains
        add("bet365.com")
        add("1xbet.com")
        add("1x-bet.com")
        add("parimatch.com")
        add("stake.com")
        add("betfair.com")
        add("22bet.com")
        add("melbet.com")
        add("mostbet.com")
        add("linebet.com")
        add("megapari.com")
        add("betway.com")
        add("dafabet.com")
        add("betwinner.com")
        add("bwin.com")
        add("10cric.com")
        add("betano.com")

        // Interstitial Ad Link Redirectors & Shorteners
        add("adfly.com")
        add("adf.ly")
        add("ouo.io")
        add("ouo.press")
        add("sh.st")
        add("bc.vc")
        add("linkvertise.com")
        add("linkvertise.net")
        add("shortzon.com")
        add("shrinkearn.com")
        add("shrinkme.io")
        add("clk.sh")
        add("droplink.co")
        add("adshrink.it")

        // Mobile Telemetry & Trackers
        add("appsflyer.com")
        add("adjust.com")
        add("branch.io")
        add("kochava.com")
        add("flurry.com")
        add("singular.net")
        add("localytics.com")
        add("mixpanel.com")
        add("segment.io")
        add("segment.com")
        add("amplitude.com")
        add("hotjar.com")
        add("hotjar.io")
        add("crazyegg.com")
        add("clarity.ms")
        add("optimizely.com")
        add("newrelic.com")
        add("nr-data.net")
        add("bugsnag.com")
        add("sentry.io")
        add("crashlytics.com")
        add("statcounter.com")
        add("histats.com")
        add("gemius.pl")
        add("scorecard.com")
        add("liveinternet.ru")

        // Social Trackers & Pixels
        add("pixel.facebook.com")
        add("an.facebook.com")
        add("analytics.twitter.com")
        add("ads-twitter.com")
        add("t.co")
        add("ads.linkedin.com")
        add("analytics.tiktok.com")
        add("ads.tiktok.com")
        add("tr.snapchat.com")
        add("ads.pinterest.com")

        // Regional & Global Trackers
        add("metrika.yandex.ru")
        add("mc.yandex.ru")
        add("an.yandex.ru")
        add("metrika.yandex.net")
    }

    /**
     * Comprehensive CSS element-hiding rules for ads, floating overlay banners,
     * video overlay click traps, and popunder containers.
     */
    const val COSMETIC_BLOCKING_CSS = """
        div[class*="popup-ad"], div[id*="popup-ad"],
        div[class*="overlay-ad"], div[id*="overlay-ad"],
        div[class*="ad-banner"], div[id*="ad-banner"],
        div[class*="ad-container"], div[id*="ad-container"],
        div[class*="ad-wrapper"], div[id*="ad-wrapper"],
        div[class*="adsbygoogle"], ins.adsbygoogle,
        iframe[src*="doubleclick"], iframe[src*="adservice"],
        iframe[src*="googlesyndication"], iframe[src*="popads"],
        iframe[src*="propellerads"], iframe[src*="exoclick"],
        div[id*="google_ads"], div[class*="google_ads"],
        .ad-placement, .advertisement, .ad-unit,
        a[href*="popads.net"], a[href*="propellerads.com"],
        a[href*="bet365.com"], a[href*="1xbet.com"], a[href*="adcash.com"],
        [class*="sponsored-post"], [class*="native-ad-"] {
            display: none !important;
            visibility: hidden !important;
            pointer-events: none !important;
            width: 0 !important;
            height: 0 !important;
            max-height: 0 !important;
            max-width: 0 !important;
            opacity: 0 !important;
        }
    """

    /**
     * Evaluates whether a given host should be filtered.
     * Normalized hostname matching with strict subdomain boundary enforcement:
     * - "ad.doubleclick.net" matches "doubleclick.net" -> BLOCKED
     * - "doubleclick.net" matches "doubleclick.net" -> BLOCKED
     * - "notdoubleclick.net" does NOT match "doubleclick.net" -> NOT BLOCKED
     * - "example.com" does NOT match -> NOT BLOCKED
     */
    override fun isBlocked(host: String?): Boolean {
        if (!isEnabled || host.isNullOrBlank()) return false
        val normalized = normalizeHost(host) ?: return false

        // User allowlist takes precedence
        if (isHostInSet(normalized, allowlist)) {
            return false
        }

        return isHostInSet(normalized, blockedDomains)
    }

    /**
     * Extracts and normalizes the hostname directly from a URL string without relying on android.net.Uri.
     */
    fun extractHostFromUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val cleanUrl = url.trim()
        val schemeIdx = cleanUrl.indexOf("://")
        val startIdx = if (schemeIdx != -1) schemeIdx + 3 else 0
        var endIdx = cleanUrl.indexOf('/', startIdx)
        if (endIdx == -1) endIdx = cleanUrl.indexOf('?', startIdx)
        if (endIdx == -1) endIdx = cleanUrl.indexOf('#', startIdx)
        val rawHost = if (endIdx != -1) cleanUrl.substring(startIdx, endIdx) else cleanUrl.substring(startIdx)
        return normalizeHost(rawHost)
    }

    /**
     * Identifies whether a given URL is an ad redirect, popunder launch script,
     * or known tracking link that should be blocked from top-level navigation.
     */
    fun isRedirectOrPopupUrl(url: String?): Boolean {
        if (!isEnabled || url.isNullOrBlank()) return false
        val host = extractHostFromUrl(url) ?: ""
        if (FilterListAdBlockingEngine.isMediaOrStreamingResource(url, host)) {
            return false
        }
        val lower = url.lowercase()

        // 1. Check known ad redirect / popunder paths & query params
        if (lower.contains("popunder") ||
            lower.contains("/pop.php") ||
            lower.contains("onclickperformance") ||
            lower.contains("onclickalgo") ||
            lower.contains("ad_type=pop") ||
            lower.contains("target=_blank&ad") ||
            lower.contains("propellerclick") ||
            lower.contains("/jump/next.php") ||
            lower.contains("adsterra") ||
            lower.contains("clickadu") ||
            lower.contains("exoclick") ||
            lower.contains("adcash") ||
            lower.contains("/pagead/") ||
            lower.contains("/ads/vmap") ||
            lower.contains("/ads/vast") ||
            lower.contains("videoads.hotstar.com") ||
            lower.contains("hesads.akamaized.net")
        ) {
            return true
        }

        // 2. Check destination hostname against blocked ad/tracker domains
        if (host.isNotEmpty() && isBlocked(host)) {
            return true
        }

        return false
    }

    /**
     * Initializes filter settings and loads persisted allowlist from SharedPreferences.
     */
    override fun initialize(context: Context) {
        isEnabled = BrowserPreferences.isContentBlockerEnabled(context)
        allowlist.clear()
        allowlist.addAll(BrowserPreferences.getAllowlistedHosts(context))
        engine.initialize(context)
    }

    override fun shouldIntercept(
        view: WebView?,
        request: WebResourceRequest?,
        documentUrl: String?
    ): WebResourceResponse? {
        return engine.shouldIntercept(view, request, documentUrl)
    }

    override fun shouldInterceptUrl(
        url: String?,
        documentUrl: String?
    ): WebResourceResponse? {
        return engine.shouldInterceptUrl(url, documentUrl)
    }

    override fun shouldBlockNavigation(url: String?): Boolean {
        return isRedirectOrPopupUrl(url) || engine.shouldBlockNavigation(url)
    }

    override fun getCosmeticCss(documentUrl: String): String {
        return engine.getCosmeticCss(documentUrl)
    }

    override fun applyCosmeticRules(view: WebView?, url: String?) {
        engine.applyCosmeticRules(view, url)
    }

    fun isOttPlatform(text: String): Boolean {
        return dev.veyra.tv.browser.engine.VeyraWebViewClient.isOttPlatform(text)
    }

    override fun allowlistHost(host: String, context: Context?) {
        normalizeHost(host)?.let {
            allowlist.add(it)
            engine.allowlistHost(it, context)
            context?.let { ctx -> BrowserPreferences.addAllowlistedHost(ctx, it) }
        }
    }

    override fun removeAllowlistedHost(host: String, context: Context?) {
        normalizeHost(host)?.let {
            allowlist.remove(it)
            engine.removeAllowlistedHost(it, context)
            context?.let { ctx -> BrowserPreferences.removeAllowlistedHost(ctx, it) }
        }
    }

    override fun clearAllowlist(context: Context?) {
        allowlist.clear()
        engine.clearAllowlist(context)
        context?.let { ctx -> BrowserPreferences.clearAllowlist(ctx) }
    }

    override fun isAllowlisted(host: String): Boolean {
        val normalized = normalizeHost(host) ?: return false
        return isHostInSet(normalized, allowlist) || engine.isAllowlisted(normalized)
    }

    fun normalizeHost(rawHost: String?): String? {
        if (rawHost.isNullOrBlank()) return null
        var h = rawHost.trim().lowercase()

        // Strip port if present
        val colonIdx = h.indexOf(':')
        if (colonIdx != -1) {
            h = h.substring(0, colonIdx)
        }

        // Strip trailing dots
        h = h.trimEnd('.')

        // Reject invalid hosts with spaces, slashes, or empty results
        return if (h.isEmpty() || h.contains(' ') || h.contains('/')) null else h
    }

    /**
     * Checks if a host or any of its parent domain suffixes matches an entry in the given set.
     * Uses dot boundary parsing to guarantee no substring false positives.
     */
    fun isHostInSet(host: String, set: Set<String>): Boolean {
        if (set.contains(host)) return true

        var dotIndex = host.indexOf('.')
        while (dotIndex != -1 && dotIndex < host.length - 1) {
            val parentDomain = host.substring(dotIndex + 1)
            if (parentDomain.contains('.') && set.contains(parentDomain)) {
                return true
            }
            dotIndex = host.indexOf('.', dotIndex + 1)
        }
        return false
    }

    /**
     * Produces a clean HTTP 204 No Content response for blocked subresources.
     * Cancels network transfer cleanly without client-side network error popups.
     */
    fun createBlockedResponse(): WebResourceResponse {
        val headers = mapOf(
            "Access-Control-Allow-Origin" to "*",
            "X-Content-Filtered-By" to "Veyra"
        )
        return WebResourceResponse(
            "text/plain",
            "UTF-8",
            204,
            "No Content",
            headers,
            ByteArrayInputStream(ByteArray(0))
        )
    }

    /**
     * Produces a lightweight, compliant dummy Google IMA (Interactive Media Ads) SDK surrogate.
     * Replaces imasdk.googleapis.com (ima3.js) so streaming video players (Hotstar, etc.)
     * receive an immediate "all ads completed" notification with 0 mid-roll cue points.
     * Prevents pre-roll and mid-roll commercial interruptions without stalling playback.
     */
    fun createDummyImaResponse(): WebResourceResponse {
        val dummyJs = """
            (function() {
                window.google = window.google || {};
                var ima = window.google.ima = window.google.ima || {};
                ima.AdDisplayContainer = function(container, video) {
                    this.initialize = function() {};
                    this.destroy = function() {};
                };
                ima.AdsLoader = function(container) {
                    var listeners = {};
                    this.addEventListener = function(type, fn) {
                        listeners[type] = listeners[type] || [];
                        listeners[type].push(fn);
                    };
                    this.requestAds = function(req) {
                        setTimeout(function() {
                            var manager = new ima.AdsManager();
                            var ev = { getAdsManager: function() { return manager; } };
                            (listeners['adsManagerLoaded'] || []).forEach(function(fn) { fn(ev); });
                            (listeners['allAdsCompleted'] || []).forEach(function(fn) { fn(); });
                        }, 0);
                    };
                    this.destroy = function() {};
                    this.contentComplete = function() {};
                };
                ima.AdsManager = function() {
                    var listeners = {};
                    this.addEventListener = function(type, fn) {
                        listeners[type] = listeners[type] || [];
                        listeners[type].push(fn);
                    };
                    this.init = function(w, h, mode) {};
                    this.start = function() {
                        setTimeout(function() {
                            (listeners['allAdsCompleted'] || []).forEach(function(fn) { fn(); });
                            (listeners['complete'] || []).forEach(function(fn) { fn(); });
                        }, 0);
                    };
                    this.stop = function() {};
                    this.destroy = function() {};
                    this.resize = function() {};
                    this.pause = function() {};
                    this.resume = function() {};
                    this.getCuePoints = function() { return []; };
                    this.getAdSkippableState = function() { return true; };
                    this.skip = function() {};
                };
                ima.AdsRequest = function() {};
                ima.ViewMode = { NORMAL: 'normal', FULLSCREEN: 'fullscreen' };
                ima.AdErrorEvent = { Type: { AD_ERROR: 'adError' } };
                ima.AdEvent = { Type: {
                    ALL_ADS_COMPLETED: 'allAdsCompleted',
                    CLICK: 'click',
                    COMPLETE: 'complete',
                    CONTENT_PAUSE_REQUESTED: 'contentPauseRequested',
                    CONTENT_RESUME_REQUESTED: 'contentResumeRequested',
                    FIRST_QUARTILE: 'firstQuartile',
                    LOADED: 'loaded',
                    MIDPOINT: 'midpoint',
                    PAUSED: 'paused',
                    STARTED: 'started',
                    THIRD_QUARTILE: 'thirdQuartile'
                }};
            })();
        """.trimIndent()
        val headers = mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Content-Type" to "application/javascript; charset=UTF-8",
            "Cache-Control" to "max-age=86400"
        )
        return WebResourceResponse(
            "application/javascript",
            "UTF-8",
            200,
            "OK",
            headers,
            ByteArrayInputStream(dummyJs.toByteArray(Charsets.UTF_8))
        )
    }
}
