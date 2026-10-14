package dev.veyra.tv.browser.net

import org.junit.Assert.assertEquals
import org.junit.Test

class NextDnsConfigTest {

    @Test
    fun testExtractProfileId_fromPlainId() {
        assertEquals("28a9b1", NextDnsConfig.extractProfileId("28a9b1"))
        assertEquals("abcdef", NextDnsConfig.extractProfileId("  abcdef  "))
    }

    @Test
    fun testExtractProfileId_fromFullUrl() {
        assertEquals("28a9b1", NextDnsConfig.extractProfileId("https://dns.nextdns.io/28a9b1"))
        assertEquals("28a9b1", NextDnsConfig.extractProfileId("http://dns.nextdns.io/28a9b1"))
        assertEquals("28a9b1", NextDnsConfig.extractProfileId("https://dns.nextdns.io/28a9b1/LivingRoomTV"))
    }

    @Test
    fun testExtractProfileId_fromDotHostname() {
        assertEquals("28a9b1", NextDnsConfig.extractProfileId("28a9b1.dns.nextdns.io"))
    }

    @Test
    fun testBuildDoHUrl_fromPlainId() {
        val expected = "https://dns.nextdns.io/28a9b1/Veyra_TV/dns-query"
        assertEquals(expected, NextDnsConfig.buildDoHUrl("28a9b1"))
    }

    @Test
    fun testBuildDoHUrl_fromFullUrl() {
        val expected = "https://dns.nextdns.io/28a9b1/Veyra_TV/dns-query"
        assertEquals(expected, NextDnsConfig.buildDoHUrl("https://dns.nextdns.io/28a9b1"))
    }

    @Test
    fun testBuildDoHUrl_fromEmpty() {
        assertEquals("https://dns.nextdns.io/dns-query", NextDnsConfig.buildDoHUrl(""))
        assertEquals("https://dns.nextdns.io/dns-query", NextDnsConfig.buildDoHUrl("   "))
    }

    @Test
    fun testBuildDotHostname() {
        assertEquals("28a9b1.dns.nextdns.io", NextDnsConfig.buildDotHostname("28a9b1"))
        assertEquals("28a9b1.dns.nextdns.io", NextDnsConfig.buildDotHostname("https://dns.nextdns.io/28a9b1"))
        assertEquals("dns.nextdns.io", NextDnsConfig.buildDotHostname(""))
    }
}
