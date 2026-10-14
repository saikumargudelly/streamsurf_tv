package dev.veyra.tv.browser.filter

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ContentBlockerTest {

    @Before
    fun setUp() {
        ContentBlocker.isEnabled = true
        ContentBlocker.clearAllowlist()
    }

    @After
    fun tearDown() {
        ContentBlocker.isEnabled = true
        ContentBlocker.clearAllowlist()
    }

    @Test
    fun testNormalizeHost() {
        assertEquals("doubleclick.net", ContentBlocker.normalizeHost("doubleclick.net"))
        assertEquals("doubleclick.net", ContentBlocker.normalizeHost("  DOUBLECLICK.NET  "))
        assertEquals("doubleclick.net", ContentBlocker.normalizeHost("doubleclick.net:443"))
        assertEquals("doubleclick.net", ContentBlocker.normalizeHost("doubleclick.net."))
        assertEquals("doubleclick.net", ContentBlocker.normalizeHost("DOUBLECLICK.NET:8080."))

        assertNull(ContentBlocker.normalizeHost(null))
        assertNull(ContentBlocker.normalizeHost(""))
        assertNull(ContentBlocker.normalizeHost("   "))
        assertNull(ContentBlocker.normalizeHost("invalid host with space"))
        assertNull(ContentBlocker.normalizeHost("example.com/path"))
    }

    @Test
    fun testExactDomainBlocked() {
        assertTrue(ContentBlocker.isBlocked("doubleclick.net"))
        assertTrue(ContentBlocker.isBlocked("googleadservices.com"))
        assertTrue(ContentBlocker.isBlocked("criteo.com"))
        assertTrue(ContentBlocker.isBlocked("adnxs.com"))
        assertTrue(ContentBlocker.isBlocked("scorecardresearch.com"))
    }

    @Test
    fun testPopunderAndRedirectNetworksBlocked() {
        // High-frequency popunder & spam ad redirect domains
        assertTrue(ContentBlocker.isBlocked("popads.net"))
        assertTrue(ContentBlocker.isBlocked("popcash.net"))
        assertTrue(ContentBlocker.isBlocked("propellerads.com"))
        assertTrue(ContentBlocker.isBlocked("propellerclick.com"))
        assertTrue(ContentBlocker.isBlocked("adcash.com"))
        assertTrue(ContentBlocker.isBlocked("exoclick.com"))
        assertTrue(ContentBlocker.isBlocked("adsterra.com"))
        assertTrue(ContentBlocker.isBlocked("clickadu.com"))
        assertTrue(ContentBlocker.isBlocked("trafficjunky.com"))
        assertTrue(ContentBlocker.isBlocked("onclickalgo.com"))
        assertTrue(ContentBlocker.isBlocked("onclickperformance.com"))

        // Shady gambling redirect networks
        assertTrue(ContentBlocker.isBlocked("bet365.com"))
        assertTrue(ContentBlocker.isBlocked("1xbet.com"))
        assertTrue(ContentBlocker.isBlocked("parimatch.com"))
        assertTrue(ContentBlocker.isBlocked("stake.com"))

        // Ad redirect shorteners
        assertTrue(ContentBlocker.isBlocked("adfly.com"))
        assertTrue(ContentBlocker.isBlocked("adf.ly"))
        assertTrue(ContentBlocker.isBlocked("ouo.io"))
        assertTrue(ContentBlocker.isBlocked("linkvertise.com"))
    }

    @Test
    fun testIsRedirectOrPopupUrl() {
        assertTrue(ContentBlocker.isRedirectOrPopupUrl("https://popads.net/click?id=123"))
        assertTrue(ContentBlocker.isRedirectOrPopupUrl("https://example.com/pop.php?zone=45"))
        assertTrue(ContentBlocker.isRedirectOrPopupUrl("https://sub.propellerclick.com/jump"))
        assertTrue(ContentBlocker.isRedirectOrPopupUrl("https://landing.bet365.com/affiliate"))
        assertTrue(ContentBlocker.isRedirectOrPopupUrl("https://example.com/page?onclickalgo=true"))
        assertTrue(ContentBlocker.isRedirectOrPopupUrl("https://example.com/jump/next.php"))

        assertFalse(ContentBlocker.isRedirectOrPopupUrl("https://www.youtube.com/watch?v=123"))
        assertFalse(ContentBlocker.isRedirectOrPopupUrl("https://www.hotstar.com/in/movies"))
        assertFalse(ContentBlocker.isRedirectOrPopupUrl("https://www.netflix.com/title/8000"))
        assertFalse(ContentBlocker.isRedirectOrPopupUrl(null))
        assertFalse(ContentBlocker.isRedirectOrPopupUrl(""))
    }

    @Test
    fun testCosmeticAndScriptAssets() {
        assertTrue(ContentBlocker.COSMETIC_BLOCKING_CSS.contains("display: none !important"))
        assertTrue(ContentBlocker.COSMETIC_BLOCKING_CSS.contains("pointer-events: none !important"))
        assertTrue(ContentBlocker.COSMETIC_BLOCKING_CSS.contains("popads.net"))
        assertTrue(ContentBlocker.POPUP_NEUTRALIZATION_SCRIPT.contains("window.open"))
        assertTrue(ContentBlocker.POPUP_NEUTRALIZATION_SCRIPT.contains("_blank"))
    }

    @Test
    fun testVideoAdsAndHotstarAdDomainsBlocked() {
        // Hotstar & OTT video ad delivery domains
        assertTrue(ContentBlocker.isBlocked("videoads.hotstar.com"))
        assertTrue(ContentBlocker.isBlocked("hesads.akamaized.net"))
        assertTrue(ContentBlocker.isBlocked("ads.hotstar.com"))
        assertTrue(ContentBlocker.isBlocked("events-analytics.hotstar.com"))
        assertTrue(ContentBlocker.isBlocked("fp-analytics.hotstar.com"))

        // Google IMA SDK & DoubleClick video ad servers
        assertTrue(ContentBlocker.isBlocked("imasdk.googleapis.com"))
        assertTrue(ContentBlocker.isBlocked("pubads.g.doubleclick.net"))
        assertTrue(ContentBlocker.isBlocked("securepubads.g.doubleclick.net"))
        assertTrue(ContentBlocker.isBlocked("googleads.g.doubleclick.net"))

        // Video ad tags and ad CDN URLs blocked
        assertTrue(ContentBlocker.isRedirectOrPopupUrl("https://videoads.hotstar.com/ads/vast.xml"))
        assertTrue(ContentBlocker.isRedirectOrPopupUrl("https://hesads.akamaized.net/ad/chunk.ts"))
        assertTrue(ContentBlocker.isRedirectOrPopupUrl("https://pubads.g.doubleclick.net/gampad/ads?env=vp"))

        // Legitimate Hotstar streaming domains must NOT be blocked
        assertFalse(ContentBlocker.isBlocked("api.hotstar.com"))
        assertFalse(ContentBlocker.isBlocked("starott.com"))
        assertFalse(ContentBlocker.isBlocked("akamaized.net"))
        assertFalse(ContentBlocker.isBlocked("boltdns.net"))
        assertFalse(ContentBlocker.isBlocked("vpe.cdn.hotstar.com"))
    }

    @Test
    fun testCreateDummyImaResponse() {
        val response = ContentBlocker.createDummyImaResponse()
        assertNotNull(response)
    }

    @Test
    fun testSubdomainsBlocked() {
        assertTrue(ContentBlocker.isBlocked("ad.doubleclick.net"))
        assertTrue(ContentBlocker.isBlocked("secure.ad.doubleclick.net"))
        assertTrue(ContentBlocker.isBlocked("pagead2.googlesyndication.com"))
        assertTrue(ContentBlocker.isBlocked("pixel.facebook.com"))
        assertTrue(ContentBlocker.isBlocked("cdn.criteo.com"))
        assertTrue(ContentBlocker.isBlocked("serve.popads.net"))
        assertTrue(ContentBlocker.isBlocked("traffic.propellerads.com"))
    }

    @Test
    fun testPrefixCollisionNotBlocked() {
        // Strict dot-boundary verification: must not block domains that merely end with the same letters
        assertFalse(ContentBlocker.isBlocked("notdoubleclick.net"))
        assertFalse(ContentBlocker.isBlocked("mygoogleadservices.com"))
        assertFalse(ContentBlocker.isBlocked("fakecriteo.com"))
        assertFalse(ContentBlocker.isBlocked("sub.notdoubleclick.net"))
        assertFalse(ContentBlocker.isBlocked("badadnxs.com"))
    }

    @Test
    fun testStandardLegitimateSitesNotBlocked() {
        assertFalse(ContentBlocker.isBlocked("youtube.com"))
        assertFalse(ContentBlocker.isBlocked("www.youtube.com"))
        assertFalse(ContentBlocker.isBlocked("netflix.com"))
        assertFalse(ContentBlocker.isBlocked("www.netflix.com"))
        assertFalse(ContentBlocker.isBlocked("primevideo.com"))
        assertFalse(ContentBlocker.isBlocked("hotstar.com"))
        assertFalse(ContentBlocker.isBlocked("google.com"))
        assertFalse(ContentBlocker.isBlocked("duckduckgo.com"))
        assertFalse(ContentBlocker.isBlocked("wikipedia.org"))
    }

    @Test
    fun testUserAllowlistPrecedence() {
        assertTrue(ContentBlocker.isBlocked("doubleclick.net"))

        ContentBlocker.allowlistHost("doubleclick.net")
        assertTrue(ContentBlocker.isAllowlisted("doubleclick.net"))
        // Subdomain of allowlisted host is also permitted
        assertTrue(ContentBlocker.isAllowlisted("ad.doubleclick.net"))
        assertFalse(ContentBlocker.isBlocked("doubleclick.net"))
        assertFalse(ContentBlocker.isBlocked("ad.doubleclick.net"))

        // Other blocked domains remain blocked
        assertTrue(ContentBlocker.isBlocked("googleadservices.com"))

        // Remove from allowlist restores blocking
        ContentBlocker.removeAllowlistedHost("doubleclick.net")
        assertFalse(ContentBlocker.isAllowlisted("doubleclick.net"))
        assertTrue(ContentBlocker.isBlocked("doubleclick.net"))
    }

    @Test
    fun testToggleFilterEnabled() {
        assertTrue(ContentBlocker.isBlocked("doubleclick.net"))

        ContentBlocker.isEnabled = false
        assertFalse(ContentBlocker.isBlocked("doubleclick.net"))

        ContentBlocker.isEnabled = true
        assertTrue(ContentBlocker.isBlocked("doubleclick.net"))
    }

    @Test
    fun testCreateBlockedResponseNotNull() {
        val response = ContentBlocker.createBlockedResponse()
        assertNotNull(response)
    }

    @Test
    fun testEdgeCasesAndMalformedDomains() {
        assertFalse(ContentBlocker.isBlocked(null))
        assertFalse(ContentBlocker.isBlocked(""))
        assertFalse(ContentBlocker.isBlocked("   "))
        assertFalse(ContentBlocker.isBlocked("invalid host with space"))
        assertFalse(ContentBlocker.isBlocked("example.com/path"))

        // Malicious suffix where blocked domain is a prefix/subdomain label of an unblocked domain
        assertFalse(ContentBlocker.isBlocked("doubleclick.net.attacker.com"))
        assertFalse(ContentBlocker.isBlocked("googleadservices.com.evil.org"))

        // Normalized hosts with whitespace, port, and trailing dot should match
        assertTrue(ContentBlocker.isBlocked("  DOUBLECLICK.NET:443.  "))
        assertTrue(ContentBlocker.isBlocked("ADS.GOOGLE.COM:8080."))
    }

    @Test
    fun testAdBlockingEngineInstantiation() {
        var initError: Throwable? = null
        try {
            val engineInstance = FilterListAdBlockingEngine()
            println("Initialized FilterListAdBlockingEngine successfully: $engineInstance")
        } catch (t: Throwable) {
            initError = t
        }
        // Engine should initialize without error
        assertNull(initError)
    }
}
