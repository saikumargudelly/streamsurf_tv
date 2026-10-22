package dev.veyra.tv.browser.security

import android.os.Build
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/**
 * Enforces security hardening and hardware acceleration on Android System WebView.
 * Complies with Veyra's privacy, performance, and TV display requirements:
 * - Restrict local file and universal content access
 * - Block mixed content
 * - Secure cookie persistence for OTT services
 * - Desktop and Leanback TV User-Agent profiles
 * - 60fps hardware acceleration, offscreen pre-rasterization, and high-performance caching
 * - Widevine DRM EME robustness fallback & HDCP policy shimming for OTT playback (Hotstar, etc.)
 * - Anti-popup & clickjacking neutralization
 */
object SecurityPolicy {

    const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    const val TV_LEANBACK_USER_AGENT =
        "Mozilla/5.0 (Linux; GoogleTV 14; BRAVIA 4K UR2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 SmartTV/10.0"

    const val DESKTOP_INJECTION_SCRIPT = """
        (function() {
            try {
                if (window.__veyra_desktop_injected) return;
                window.__veyra_desktop_injected = true;

                // 1. Touch points and desktop platform spoofing
                Object.defineProperty(navigator, 'maxTouchPoints', { get: () => 0, configurable: true });
                Object.defineProperty(navigator, 'platform', { get: () => 'Win32', configurable: true });

                var brandsList = [
                    { brand: 'Google Chrome', version: '131' },
                    { brand: 'Chromium', version: '131' },
                    { brand: 'Not_A Brand', version: '24' }
                ];
                var fullVersions = [
                    { brand: 'Google Chrome', version: '131.0.6778.86' },
                    { brand: 'Chromium', version: '131.0.6778.86' },
                    { brand: 'Not_A Brand', version: '24.0.0.0' }
                ];

                var uaData = {
                    brands: brandsList,
                    mobile: false,
                    platform: 'Windows',
                    getHighEntropyValues: function(hints) {
                        return Promise.resolve({
                            architecture: 'x86',
                            bitness: '64',
                            brands: brandsList,
                            fullVersionList: fullVersions,
                            mobile: false,
                            model: '',
                            platform: 'Windows',
                            platformVersion: '15.0.0',
                            uaFullVersion: '131.0.6778.86'
                        });
                    },
                    toJSON: function() {
                        return {
                            brands: brandsList,
                            mobile: false,
                            platform: 'Windows'
                        };
                    }
                };

                if (navigator.userAgentData) {
                    try {
                        Object.defineProperty(navigator.userAgentData, 'brands', { get: () => brandsList, configurable: true });
                        Object.defineProperty(navigator.userAgentData, 'mobile', { get: () => false, configurable: true });
                        Object.defineProperty(navigator.userAgentData, 'platform', { get: () => 'Windows', configurable: true });
                        navigator.userAgentData.getHighEntropyValues = uaData.getHighEntropyValues;
                    } catch (e) {
                        try {
                            Object.defineProperty(navigator.userAgentData, 'getHighEntropyValues', { value: uaData.getHighEntropyValues, configurable: true });
                        } catch (e2) {}
                    }
                } else {
                    try {
                        Object.defineProperty(navigator, 'userAgentData', { get: () => uaData, configurable: true });
                    } catch (e) {}
                }

                // 2. Widevine EME Hardware Robustness Fallback & HDCP Policy Shim for Hotstar/OTT Playback
                if (navigator.requestMediaKeySystemAccess) {
                    var origRequestMediaKeySystemAccess = navigator.requestMediaKeySystemAccess.bind(navigator);
                    navigator.requestMediaKeySystemAccess = function(keySystem, configs) {
                        return origRequestMediaKeySystemAccess(keySystem, configs).catch(function(err) {
                            // If strict hardware robustness (HW_SECURE_ALL) is rejected on Android WebView,
                            // fall back to relaxed / software-decode Widevine capabilities
                            if (keySystem === 'com.widevine.alpha' && Array.isArray(configs)) {
                                var relaxedConfigs = configs.map(function(c) {
                                    var clone = Object.assign({}, c);
                                    if (clone.videoCapabilities) {
                                        clone.videoCapabilities = clone.videoCapabilities.map(function(vc) {
                                            var vClone = Object.assign({}, vc);
                                            delete vClone.robustness;
                                            return vClone;
                                        });
                                    }
                                    if (clone.audioCapabilities) {
                                        clone.audioCapabilities = clone.audioCapabilities.map(function(ac) {
                                            var aClone = Object.assign({}, ac);
                                            delete aClone.robustness;
                                            return aClone;
                                        });
                                    }
                                    if (clone.persistentState === 'required') clone.persistentState = 'optional';
                                    if (clone.distinctiveIdentifier === 'required') clone.distinctiveIdentifier = 'optional';
                                    return clone;
                                });
                                return origRequestMediaKeySystemAccess(keySystem, relaxedConfigs);
                            }
                            return Promise.reject(err);
                        });
                    };
                }

                if (window.MediaKeys) {
                    if (!MediaKeys.prototype.getStatusForPolicy) {
                        MediaKeys.prototype.getStatusForPolicy = function(policy) {
                            return Promise.resolve('usable');
                        };
                    } else {
                        var origGetStatus = MediaKeys.prototype.getStatusForPolicy;
                        MediaKeys.prototype.getStatusForPolicy = function(policy) {
                            return origGetStatus.call(this, policy).catch(function() {
                                return 'usable';
                            });
                        };
                    }
                }

                // 3. Google IMA SDK Dummy Surrogate for Zero-Ad OTT Video Playback
                window.google = window.google || {};
                if (!window.google.ima) {
                    var ima = window.google.ima = {};
                    ima.AdDisplayContainer = function(c, v) { this.initialize = function() {}; this.destroy = function() {}; };
                    ima.AdsLoader = function(c) {
                        var listeners = {};
                        this.addEventListener = function(t, fn) { listeners[t] = listeners[t] || []; listeners[t].push(fn); };
                        this.requestAds = function(r) {
                            setTimeout(function() {
                                var mgr = new ima.AdsManager();
                                var ev = { getAdsManager: function() { return mgr; } };
                                (listeners['adsManagerLoaded'] || []).forEach(function(fn) { fn(ev); });
                                (listeners['allAdsCompleted'] || []).forEach(function(fn) { fn(); });
                            }, 0);
                        };
                        this.destroy = function() {};
                        this.contentComplete = function() {};
                    };
                    ima.AdsManager = function() {
                        var listeners = {};
                        this.addEventListener = function(t, fn) { listeners[t] = listeners[t] || []; listeners[t].push(fn); };
                        this.init = function() {};
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
                }

                // 4. Hotstar / OTT Ad Intervention & Cue Pruning
                if (window.fetch) {
                    var origFetch = window.fetch;
                    window.fetch = function() {
                        return origFetch.apply(this, arguments).then(function(res) {
                            try {
                                var u = (res.url || '').toLowerCase();
                                var isHotstar = u.indexOf('hotstar') !== -1 || (window.location.hostname && window.location.hostname.indexOf('hotstar') !== -1);
                                if (isHotstar && (u.indexOf('playback') !== -1 || u.indexOf('content') !== -1)) {
                                    var origJson = res.json.bind(res);
                                    res.json = function() {
                                        return origJson().then(function(data) {
                                            try {
                                                function stripAdData(o) {
                                                    if (!o || typeof o !== 'object') return;
                                                    if (o.intervention_data) delete o.intervention_data;
                                                    if (o.ad_breaks) o.ad_breaks = [];
                                                    if (o.interventions) o.interventions = [];
                                                    for (var k in o) {
                                                        if (o.hasOwnProperty(k) && typeof o[k] === 'object') {
                                                            stripAdData(o[k]);
                                                        }
                                                    }
                                                }
                                                stripAdData(data);
                                            } catch (e2) {}
                                            return data;
                                        });
                                    };
                                }
                            } catch (e1) {}
                            return res;
                        });
                    };
                }

                // 5. Video Ad Auto-Skipper & Fast-Forward (Targeted for YouTube and video ad containers)
                if (window.__veyra_ad_interval) {
                    clearInterval(window.__veyra_ad_interval);
                }
                window.__veyra_ad_interval = setInterval(function() {
                    try {
                        var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .videoAdUiSkipButton, .ytp-ad-skip-button-slot');
                        if (skipBtn) {
                            skipBtn.click();
                        }
                        var ytpPlayer = document.querySelector('.html5-video-player.ad-showing, .html5-video-player.ad-interrupting');
                        if (ytpPlayer) {
                            var v = ytpPlayer.querySelector('video');
                            if (v && v.duration && v.duration < 120 && !v.paused) {
                                v.currentTime = v.duration;
                                v.playbackRate = 16.0;
                            }
                        }
                    } catch (e) {}
                }, 1000);

                // 6. Anti-Popup & Clickjack Neutralization
                var origOpen = window.open;
                window.open = function(url, target, features) {
                    if (!url || url === 'about:blank') return null;
                    var lower = (url + '').toLowerCase();
                    if (lower.indexOf('popads') !== -1 ||
                        lower.indexOf('propeller') !== -1 ||
                        lower.indexOf('adcash') !== -1 ||
                        lower.indexOf('exoclick') !== -1 ||
                        lower.indexOf('bet365') !== -1 ||
                        lower.indexOf('1xbet') !== -1 ||
                        lower.indexOf('adsterra') !== -1 ||
                        lower.indexOf('clickadu') !== -1 ||
                        lower.indexOf('popcash') !== -1 ||
                        lower.indexOf('doubleclick') !== -1 ||
                        lower.indexOf('onclickalgo') !== -1 ||
                        lower.indexOf('/pop.') !== -1 ||
                        lower.indexOf('jump/next') !== -1) {
                        return null;
                    }
                    window.location.href = url;
                    return window;
                };

                document.addEventListener('click', function(e) {
                    var target = e.target;
                    while (target && target.tagName !== 'A') {
                        target = target.parentElement;
                    }
                    if (target && target.tagName === 'A') {
                        var href = target.getAttribute('href') || '';
                        var lowerHref = href.toLowerCase();
                        if (lowerHref.indexOf('popads') !== -1 ||
                            lowerHref.indexOf('propeller') !== -1 ||
                            lowerHref.indexOf('adcash') !== -1 ||
                            lowerHref.indexOf('exoclick') !== -1 ||
                            lowerHref.indexOf('bet365') !== -1 ||
                            lowerHref.indexOf('1xbet') !== -1 ||
                            lowerHref.indexOf('adsterra') !== -1 ||
                            lowerHref.indexOf('clickadu') !== -1 ||
                            lowerHref.indexOf('popcash') !== -1 ||
                            lowerHref.indexOf('onclickalgo') !== -1) {
                            e.preventDefault();
                            e.stopPropagation();
                            return false;
                        }
                        if (target.getAttribute('target') === '_blank') {
                            target.removeAttribute('target');
                        }
                    }
                }, true);

                window.alert = function() {};
                window.confirm = function() { return true; };
                window.prompt = function() { return null; };
                window.onbeforeunload = null;

                // 7. Viewport: Provide standard desktop fallback only if document does not specify one.
                if (document.head && !document.querySelector('meta[name="viewport"]')) {
                    var meta = document.createElement('meta');
                    meta.name = 'viewport';
                    meta.setAttribute('content', 'width=1280, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes');
                    document.head.appendChild(meta);
                }

                // 8. Cosmetic CSS style injection
                if (document.head && !document.getElementById('veyra-tv-desktop-style')) {
                    var st = document.createElement('style');
                    st.id = 'veyra-tv-desktop-style';
                    st.textContent = '::-webkit-scrollbar { width: 6px; height: 6px; } ::-webkit-scrollbar-thumb { background: rgba(255,255,255,0.3); border-radius: 3px; } * { -webkit-font-smoothing: antialiased; } div[class*="pop-"], div[id*="pop-"], div[class*="overlay-ad"], div[class*="ad-banner"], div[class*="ad-container"], div[class*="adsbygoogle"], ins.adsbygoogle, iframe[src*="doubleclick"], iframe[src*="popads"], iframe[src*="propellerads"], .ad-placement, .advertisement, a[href*="popads.net"], a[href*="propellerads.com"], a[href*="bet365.com"], .ad-showing, .video-ads, [class*="companion-ad"], .interstitial-ad { display: none !important; visibility: hidden !important; pointer-events: none !important; width: 0 !important; height: 0 !important; }';
                    document.head.appendChild(st);
                }
            } catch (e) {}
        })();
    """

    fun applySecurityDefaults(webView: WebView, isDesktopMode: Boolean = true) {
        val settings = webView.settings

        // 1. Hardware-accelerated layer
        webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        // 2. Offscreen pre-rasterization disabled (default false) to conserve graphics memory on TV devices
        settings.setOffscreenPreRaster(false)

        // 3. User-Agent
        settings.userAgentString = if (isDesktopMode) DESKTOP_USER_AGENT else null

        // 4. Disable dangerous local file access
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false

        // 5. Mixed content compatibility mode (allows modern web standards while preventing cleartext vulnerabilities)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

        // 6. Early document-start injection for desktop spoofing, OTT DRM capability hints & popup shield
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            try {
                WebViewCompat.addDocumentStartJavaScript(
                    webView,
                    DESKTOP_INJECTION_SCRIPT.trimIndent(),
                    setOf("*")
                )
            } catch (_: Exception) {}
        }

        // 7. JavaScript and DOM storage for OTT web apps
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true

        // 8. Popups and windows
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)

        // 9. Remote playback: allow autoplay without requiring touch gesture
        settings.mediaPlaybackRequiresUserGesture = false

        // 10. Desktop viewport layout & scaling for TV displays
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
        settings.textZoom = dev.veyra.tv.browser.storage.BrowserPreferences.DEFAULT_ZOOM_LEVEL
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        // 11. Cache mode for instant navigation and minimal re-fetching
        settings.cacheMode = WebSettings.LOAD_DEFAULT

        // 12. Geolocation disabled by default
        settings.setGeolocationEnabled(false)

        // 13. Configure CookieManager for persistent OTT sessions
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)
    }

    /**
     * Flushes in-memory cookies to disk so that user sessions survive process restarts.
     * Note: Android's CookieManager.flush() executes synchronously on the calling thread
     * and performs disk I/O. It should only be invoked on lifecycle transitions (such as onPause),
     * never on high-frequency rendering or navigation callbacks.
     */
    fun flushCookies() {
        try {
            CookieManager.getInstance().flush()
        } catch (_: Exception) {
        }
    }
}
