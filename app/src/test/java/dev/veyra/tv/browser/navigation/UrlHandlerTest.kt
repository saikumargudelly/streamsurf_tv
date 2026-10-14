package dev.veyra.tv.browser.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlHandlerTest {

    @Test
    fun testDirectHttpsUrlPreserved() {
        val input = "https://www.netflix.com/browse"
        val result = UrlHandler.resolveInput(input)
        assertEquals("https://www.netflix.com/browse", result)
    }

    @Test
    fun testDirectHttpUrlPreserved() {
        val input = "http://example.com"
        val result = UrlHandler.resolveInput(input)
        assertEquals("http://example.com", result)
    }

    @Test
    fun testDomainWithoutSchemeAddsHttps() {
        val input = "youtube.com"
        val result = UrlHandler.resolveInput(input)
        assertEquals("https://youtube.com", result)
    }

    @Test
    fun testSubdomainWithoutSchemeAddsHttps() {
        val input = "m.hotstar.com/in"
        val result = UrlHandler.resolveInput(input)
        assertEquals("https://m.hotstar.com/in", result)
    }

    @Test
    fun testSearchQueryFallback() {
        val input = "android tv privacy browser"
        val result = UrlHandler.resolveInput(input)
        assertTrue(result.startsWith("https://duckduckgo.com/?q="))
        assertTrue(result.contains("android+tv+privacy+browser") || result.contains("android%20tv%20privacy%20browser"))
    }

    @Test
    fun testEmptyInputFallsBackToDefaultSearch() {
        val input = "   "
        val result = UrlHandler.resolveInput(input)
        assertEquals("https://duckduckgo.com", result)
    }

    @Test
    fun testAllowedSchemes() {
        assertTrue(UrlHandler.isAllowedScheme("https://example.com"))
        assertTrue(UrlHandler.isAllowedScheme("http://example.com"))
        assertFalse(UrlHandler.isAllowedScheme("javascript:alert(1)"))
        assertFalse(UrlHandler.isAllowedScheme("file:///etc/hosts"))
        assertFalse(UrlHandler.isAllowedScheme("content://media/external/images"))
        assertFalse(UrlHandler.isAllowedScheme("data:text/html,<h1>Hello</h1>"))
    }

    @Test
    fun testOttPlatformDetection() {
        assertTrue(dev.veyra.tv.browser.engine.VeyraWebViewClient.isOttPlatform("www.hotstar.com"))
        assertTrue(dev.veyra.tv.browser.engine.VeyraWebViewClient.isOttPlatform("hotstar.com"))
        assertTrue(dev.veyra.tv.browser.engine.VeyraWebViewClient.isOttPlatform("api.hotstar.com"))
        assertTrue(dev.veyra.tv.browser.engine.VeyraWebViewClient.isOttPlatform("jiohotstar.com"))
        assertTrue(dev.veyra.tv.browser.engine.VeyraWebViewClient.isOttPlatform("www.disneyplus.com"))
        assertTrue(dev.veyra.tv.browser.engine.VeyraWebViewClient.isOttPlatform("www.netflix.com"))
        assertTrue(dev.veyra.tv.browser.engine.VeyraWebViewClient.isOttPlatform("www.primevideo.com"))

        assertFalse(dev.veyra.tv.browser.engine.VeyraWebViewClient.isOttPlatform("duckduckgo.com"))
        assertFalse(dev.veyra.tv.browser.engine.VeyraWebViewClient.isOttPlatform("google.com"))
        assertFalse(dev.veyra.tv.browser.engine.VeyraWebViewClient.isOttPlatform("wikipedia.org"))
    }
}

