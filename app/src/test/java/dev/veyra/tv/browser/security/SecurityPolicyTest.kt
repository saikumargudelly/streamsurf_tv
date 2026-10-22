package dev.veyra.tv.browser.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityPolicyTest {

    @Test
    fun testDesktopUserAgentProfile() {
        val ua = SecurityPolicy.DESKTOP_USER_AGENT
        assertTrue(ua.contains("Windows NT 10.0"))
        assertTrue(ua.contains("Win64; x64"))
        assertTrue(ua.contains("Chrome/"))
        assertTrue(ua.contains("Safari/537.36"))
        assertFalse(ua.contains("Android"))
        assertFalse(ua.contains("Mobile"))
    }

    @Test
    fun testLeanbackUserAgentProfile() {
        val ua = SecurityPolicy.TV_LEANBACK_USER_AGENT
        assertTrue(ua.contains("GoogleTV"))
        assertTrue(ua.contains("SmartTV"))
    }

    @Test
    fun testDesktopInjectionScriptContainsOttRequirements() {
        val script = SecurityPolicy.DESKTOP_INJECTION_SCRIPT
        // Touch point override for TV navigation
        assertTrue(script.contains("maxTouchPoints"))
        // Desktop platform override matching Windows Chrome
        assertTrue(script.contains("Win32"))
        // Client hints spoofing required by Hotstar/Netflix BFF services
        assertTrue(script.contains("userAgentData"))
        assertTrue(script.contains("getHighEntropyValues"))
        assertTrue(script.contains("Google Chrome"))
        assertTrue(script.contains("Chromium"))
        // Standard desktop fallback viewport
        assertTrue(script.contains("1280"))
        assertTrue(script.contains("viewport"))
        // Widevine EME robustness fallback and HDCP policy shim for Hotstar ERR PB_1442 prevention
        assertTrue(script.contains("requestMediaKeySystemAccess"))
        assertTrue(script.contains("com.widevine.alpha"))
        assertTrue(script.contains("getStatusForPolicy"))
        assertTrue(script.contains("usable"))
        // Anti-popup and clickjack shield: confirms safe suppression without navigating active tab
        assertTrue(script.contains("window.open"))
        assertTrue(script.contains("popads"))
        assertFalse(script.contains("window.location.href = url"))
        assertFalse(script.contains("window.location.href ="))
        // Confirmed removal of overbroad cosmetic selectors
        assertFalse(script.contains("div[class*=\"pop-\"]"))
        assertFalse(script.contains("div[id*=\"pop-\"]"))
        // Google IMA dummy surrogate for zero-ad playback
        assertTrue(script.contains("google.ima"))
        assertTrue(script.contains("AdsManager"))
        assertTrue(script.contains("allAdsCompleted"))
        // Hotstar ad cue & intervention pruner
        assertTrue(script.contains("intervention_data"))
        assertTrue(script.contains("ad_breaks"))
        // HTML5 video ad skipper scoped strictly to YouTube top-level window
        assertTrue(script.contains("ytp-ad-skip-button"))
        assertTrue(script.contains("ad-showing"))
        assertTrue(script.contains("isYouTube && window === window.top"))
        // Idempotency guard preventing duplicate timers and event listeners
        assertTrue(script.contains("__veyra_desktop_injected"))
    }
}
