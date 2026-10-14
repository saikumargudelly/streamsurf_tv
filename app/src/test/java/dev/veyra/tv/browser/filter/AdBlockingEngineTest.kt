package dev.veyra.tv.browser.filter

import dev.veyra.tv.browser.filter.ResourceType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AdBlockingEngineTest {

    private lateinit var engine: AdBlockingEngine

    @Before
    fun setUp() {
        engine = FilterListAdBlockingEngine()
        engine.isEnabled = true
        engine.clearAllowlist()
        ContentBlocker.isEnabled = true
        ContentBlocker.clearAllowlist()
    }

    @After
    fun tearDown() {
        engine.isEnabled = true
        engine.clearAllowlist()
        ContentBlocker.isEnabled = true
        ContentBlocker.clearAllowlist()
    }

    @Test
    fun testEngineInterfaceAndDelegation() {
        assertTrue(engine is AdBlockingEngine)
        assertTrue(ContentBlocker is AdBlockingEngine)
        assertEquals(engine.isEnabled, ContentBlocker.isEnabled)

        ContentBlocker.isEnabled = false
        assertFalse(ContentBlocker.isEnabled)
        ContentBlocker.isEnabled = true
        assertTrue(ContentBlocker.isEnabled)
    }

    @Test
    fun testAllowlistPrecedenceOverRules() {
        val blockedHost = "doubleclick.net"
        assertTrue(engine.isBlocked(blockedHost))

        // Add to allowlist
        engine.allowlistHost(blockedHost)
        assertTrue(engine.isAllowlisted(blockedHost))
        assertFalse(engine.isBlocked(blockedHost))

        // Subresources on allowlisted host should not be intercepted
        val response = engine.shouldInterceptUrl("https://ad.doubleclick.net/ad.js", "https://example.com")
        assertNull(response)

        // Remove from allowlist restores blocking
        engine.removeAllowlistedHost(blockedHost)
        assertFalse(engine.isAllowlisted(blockedHost))
        assertTrue(engine.isBlocked(blockedHost))
    }

    @Test
    fun testSubresourceVersusMainFrameDecisions() {
        // Main-frame requests should never be intercepted by subresource filter
        val mainFrameResponse = ContentBlocker.shouldIntercept(
            view = null,
            request = null, // null request or isForMainFrame handled
            documentUrl = "https://example.com"
        )
        assertNull(mainFrameResponse)

        // Subresource ad request should be intercepted and return non-null WebResourceResponse
        val subresourceResponse = ContentBlocker.shouldInterceptUrl(
            url = "https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js",
            documentUrl = "https://example.com"
        )
        assertNotNull(subresourceResponse)
    }

    @Test
    fun testResourceTypeOptions() {
        assertEquals(0, ResourceType.UNKNOWN.filterOption)
        assertEquals(1, ResourceType.SCRIPT.filterOption)
        assertEquals(2, ResourceType.IMAGE.filterOption)
        assertEquals(4, ResourceType.CSS.filterOption)
        assertEquals(0x10, ResourceType.XMLHTTPREQUEST.filterOption)
        assertEquals(0x40, ResourceType.SUBDOCUMENT.filterOption)
        assertEquals(0x80000, ResourceType.FONT.filterOption)
        assertEquals(0x100000, ResourceType.MEDIA.filterOption)
    }

    @Test
    fun testPopupAndNavigationDecisions() {
        // Known aggressive popunder and scam networks must be blocked from navigation
        assertTrue(engine.shouldBlockNavigation("https://popads.net/serve/popunder.php?id=99"))
        assertTrue(engine.shouldBlockNavigation("https://sub.propellerclick.com/jump/next.php"))
        assertTrue(engine.shouldBlockNavigation("https://landing.bet365.com/affiliate/track"))
        assertTrue(engine.shouldBlockNavigation("https://adcash.com/redirect?zone=123"))
        assertTrue(engine.shouldBlockNavigation("https://example.com/track?onclickalgo=true"))
        assertTrue(engine.shouldBlockNavigation("https://shortzon.com/go/123"))

        // Legitimate top-level web navigation must NEVER be blocked
        assertFalse(engine.shouldBlockNavigation("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertFalse(engine.shouldBlockNavigation("https://www.netflix.com/browse"))
        assertFalse(engine.shouldBlockNavigation("https://www.hotstar.com/in/shows"))
        assertFalse(engine.shouldBlockNavigation("https://www.primevideo.com/detail/0G5M"))
        assertFalse(engine.shouldBlockNavigation("https://en.wikipedia.org/wiki/Main_Page"))
        assertFalse(engine.shouldBlockNavigation("https://github.com/Edsuns/AdblockAndroid"))
    }

    @Test
    fun testOttImmunityAndImaSurrogate() {
        // 1. Google IMA SDK should return compliant surrogate script
        val imaResponse = engine.shouldInterceptUrl("https://imasdk.googleapis.com/js/sdkloader/ima3.js", "https://www.hotstar.com")
        assertNotNull(imaResponse)

        // 2. Video ad servers on OTT platform must be blocked
        val hotstarAdResponse = engine.shouldInterceptUrl("https://videoads.hotstar.com/ads/vast.xml", "https://www.hotstar.com")
        assertNotNull(hotstarAdResponse)

        val akamaiAdResponse = engine.shouldInterceptUrl("https://hesads.akamaized.net/ad/segment.ts", "https://www.hotstar.com")
        assertNotNull(akamaiAdResponse)

        // 3. Essential streaming resources must be immune and NOT intercepted
        assertNull(engine.shouldInterceptUrl("https://api.hotstar.com/v2/stream/manifest.mpd", "https://www.hotstar.com"))
        assertNull(engine.shouldInterceptUrl("https://starott.com/video/chunk_1080p.m4s", "https://www.hotstar.com"))
        assertNull(engine.shouldInterceptUrl("https://vpe.cdn.hotstar.com/manifest.m3u8", "https://www.hotstar.com"))
        assertNull(engine.shouldInterceptUrl("https://nflxvideo.net/range/0-1000", "https://www.netflix.com"))
        assertNull(engine.shouldInterceptUrl("https://googlevideo.com/videoplayback?id=123", "https://www.youtube.com"))
    }

    @Test
    fun testCosmeticHidingCssGeneration() {
        val css = engine.getCosmeticCss("https://example.com")
        assertNotNull(css)
        assertTrue(css.contains("display: none !important"))
        assertTrue(css.contains("visibility: hidden !important"))
        assertTrue(css.contains("adsbygoogle"))
        assertTrue(css.contains("ad-banner"))
    }

    @Test
    fun testMalformedAndEdgeCaseUrls() {
        assertFalse(engine.shouldBlockNavigation(null))
        assertFalse(engine.shouldBlockNavigation(""))
        assertFalse(engine.shouldBlockNavigation("   "))
        assertFalse(engine.isBlocked(null))
        assertFalse(engine.isBlocked(""))
        assertFalse(engine.isBlocked("   "))
        assertNull(engine.shouldInterceptUrl(null, null))
        assertNull(engine.shouldInterceptUrl("", ""))

        // Prefix spoofing resistance
        assertFalse(engine.isBlocked("notdoubleclick.net"))
        assertFalse(engine.isBlocked("doubleclick.net.evil.com"))
    }
}
