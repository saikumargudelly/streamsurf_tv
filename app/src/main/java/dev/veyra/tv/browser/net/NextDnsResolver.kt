package dev.veyra.tv.browser.net

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

data class NextDnsResolveResult(
    val address: InetAddress?,
    val isBlocked: Boolean,
    val fromCache: Boolean = false
)

data class NextDnsTestResult(
    val success: Boolean,
    val message: String,
    val latencyMs: Long = 0
)

/**
 * High-performance DNS-over-HTTPS (DoH) resolver communicating with the user's personal NextDNS profile.
 * Implements in-memory TTL caching to prevent repetitive network lookups during video streaming.
 */
class NextDnsResolver(val dohEndpointUrl: String) {

    companion object {
        private const val TAG = "NextDnsResolver"
        private const val MIN_CACHE_TTL_MS = 60_000L    // 1 minute
        private const val MAX_CACHE_TTL_MS = 600_000L   // 10 minutes
        private const val BLOCKED_CACHE_TTL_MS = 45_000L // 45 seconds
        private const val CONNECT_TIMEOUT_MS = 3_500
        private const val READ_TIMEOUT_MS = 3_500
    }

    private data class CacheEntry(
        val address: InetAddress?,
        val isBlocked: Boolean,
        val expiresAtMs: Long
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()

    /**
     * Resolves a hostname using the user's custom NextDNS DoH endpoint.
     */
    fun resolve(hostname: String): NextDnsResolveResult {
        val cleanHost = hostname.trim().lowercase().removeSuffix(".")
        if (cleanHost.isEmpty()) {
            return NextDnsResolveResult(address = null, isBlocked = false)
        }

        // Loopback / localhost bypass
        if (cleanHost == "localhost" || cleanHost == "127.0.0.1" || cleanHost == "::1") {
            return NextDnsResolveResult(address = InetAddress.getLoopbackAddress(), isBlocked = false)
        }

        // Direct IP address bypass (no DNS needed)
        if (isDirectIpAddress(cleanHost)) {
            return try {
                NextDnsResolveResult(address = InetAddress.getByName(cleanHost), isBlocked = false)
            } catch (_: Throwable) {
                NextDnsResolveResult(address = null, isBlocked = false)
            }
        }

        // 1. Check in-memory DNS cache
        val now = System.currentTimeMillis()
        val cached = cache[cleanHost]
        if (cached != null && now < cached.expiresAtMs) {
            return NextDnsResolveResult(
                address = cached.address,
                isBlocked = cached.isBlocked,
                fromCache = true
            )
        }

        // 2. Query NextDNS via DoH (JSON format)
        return try {
            val queryUrl = buildQueryUrl(cleanHost)
            val conn = (URL(queryUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/dns-json")
                setRequestProperty("User-Agent", "StreamSurfTV-NextDNS/1.0")
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
            }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val jsonText = reader.use { it.readText() }
                conn.disconnect()

                val json = JSONObject(jsonText)
                val status = json.optInt("Status", -1)
                val answers = json.optJSONArray("Answer")

                // NextDNS ad/tracker blocking detection:
                // Status 3 (NXDOMAIN), or empty answers, or data == 0.0.0.0 / ::
                var resolvedIp: String? = null
                var ttlSeconds = 300L
                var isBlockedByProfile = false

                if (answers != null && answers.length() > 0) {
                    for (i in 0 until answers.length()) {
                        val ans = answers.optJSONObject(i) ?: continue
                        val type = ans.optInt("type", 0)
                        val data = ans.optString("data", "")
                        val ttl = ans.optLong("TTL", 300L)

                        if (data == "0.0.0.0" || data == "::") {
                            isBlockedByProfile = true
                            break
                        }

                        // Type 1 is DNS 'A' record (IPv4)
                        if (type == 1 && data.isNotBlank()) {
                            resolvedIp = data
                            ttlSeconds = ttl
                            break
                        }
                    }
                } else if (status == 3) {
                    isBlockedByProfile = true
                }

                if (isBlockedByProfile || resolvedIp == null) {
                    // Cache the block decision so we don't spam DoH
                    val blockEntry = CacheEntry(
                        address = null,
                        isBlocked = isBlockedByProfile,
                        expiresAtMs = now + BLOCKED_CACHE_TTL_MS
                    )
                    cache[cleanHost] = blockEntry
                    NextDnsResolveResult(address = null, isBlocked = isBlockedByProfile)
                } else {
                    val address = InetAddress.getByName(resolvedIp)
                    val clampedTtlMs = (ttlSeconds * 1000L).coerceIn(MIN_CACHE_TTL_MS, MAX_CACHE_TTL_MS)
                    cache[cleanHost] = CacheEntry(
                        address = address,
                        isBlocked = false,
                        expiresAtMs = now + clampedTtlMs
                    )
                    NextDnsResolveResult(address = address, isBlocked = false)
                }
            } else {
                conn.disconnect()
                fallbackSystemDns(cleanHost, now)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "DoH lookup failed for $cleanHost via NextDNS: ${t.message}. Falling back.")
            fallbackSystemDns(cleanHost, now)
        }
    }

    private fun fallbackSystemDns(cleanHost: String, now: Long): NextDnsResolveResult {
        return try {
            val addr = InetAddress.getByName(cleanHost)
            cache[cleanHost] = CacheEntry(
                address = addr,
                isBlocked = false,
                expiresAtMs = now + MIN_CACHE_TTL_MS
            )
            NextDnsResolveResult(address = addr, isBlocked = false)
        } catch (_: Throwable) {
            NextDnsResolveResult(address = null, isBlocked = false)
        }
    }

    private fun buildQueryUrl(host: String): String {
        val separator = if (dohEndpointUrl.contains("?")) "&" else "?"
        val encodedHost = URLEncoder.encode(host, "UTF-8")
        return "$dohEndpointUrl${separator}name=$encodedHost&type=A"
    }

    private fun isDirectIpAddress(host: String): Boolean {
        // Quick IPv4 check
        val parts = host.split(".")
        if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) {
            return true
        }
        // IPv6 check
        return host.contains(":")
    }

    /**
     * Tests connectivity to the configured NextDNS endpoint.
     */
    fun testConnection(): NextDnsTestResult {
        val start = System.currentTimeMillis()
        return try {
            val queryUrl = buildQueryUrl("test.nextdns.io")
            val conn = (URL(queryUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/dns-json")
                connectTimeout = 4000
                readTimeout = 4000
            }
            val code = conn.responseCode
            val latency = System.currentTimeMillis() - start
            conn.disconnect()

            if (code == 200) {
                NextDnsTestResult(
                    success = true,
                    message = "Connected to NextDNS (${latency}ms)",
                    latencyMs = latency
                )
            } else {
                NextDnsTestResult(
                    success = false,
                    message = "NextDNS returned HTTP $code"
                )
            }
        } catch (t: Throwable) {
            NextDnsTestResult(
                success = false,
                message = "Connection error: ${t.message ?: "Timeout"}"
            )
        }
    }

    fun clearCache() {
        cache.clear()
    }
}
