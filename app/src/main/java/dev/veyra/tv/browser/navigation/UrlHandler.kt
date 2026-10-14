package dev.veyra.tv.browser.navigation

import android.net.Uri
import java.net.URLEncoder
import java.util.regex.Pattern

/**
 * Handles URL validation, scheme normalization, and fallback to privacy-friendly search engine.
 */
object UrlHandler {

    private const val DEFAULT_SEARCH_URL = "https://duckduckgo.com/?q="
    private val DOMAIN_PATTERN = Pattern.compile("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(:\\d+)?(/.*)?$")

    /**
     * Resolves user input from the address bar into a valid HTTPS URL or search query.
     */
    fun resolveInput(rawInput: String): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return "https://duckduckgo.com"
        }

        // Check if already has a scheme
        val lower = trimmed.lowercase()
        if (lower.startsWith("https://") || lower.startsWith("http://")) {
            return if (isAllowedScheme(trimmed)) trimmed else fallbackSearch(trimmed)
        }

        // Check if it's a domain name (no whitespace, contains valid domain dot structure)
        if (!trimmed.contains(" ") && DOMAIN_PATTERN.matcher(trimmed).matches()) {
            return "https://$trimmed"
        }

        // Fallback to search query
        return fallbackSearch(trimmed)
    }

    /**
     * Verifies if a given URI uses a safe web scheme (HTTP or HTTPS).
     * Rejects file://, javascript:, data:, content:, etc.
     */
    fun isAllowedScheme(url: String): Boolean {
        return try {
            val uri = java.net.URI(url)
            val scheme = uri.scheme?.lowercase()
            scheme == "https" || scheme == "http"
        } catch (_: Exception) {
            val lower = url.trim().lowercase()
            lower.startsWith("https://") || lower.startsWith("http://")
        }
    }

    /**
     * Constructs a privacy-friendly search URL for user keywords.
     */
    fun fallbackSearch(query: String, searchBase: String = DEFAULT_SEARCH_URL): String {
        val encoded = try {
            URLEncoder.encode(query.trim(), "UTF-8")
        } catch (e: Exception) {
            query.trim()
        }
        return "$searchBase$encoded"
    }

    /**
     * Extracts a clean display host/title from a URL.
     */
    fun extractHost(url: String): String {
        return try {
            val uri = java.net.URI(url)
            uri.host ?: url
        } catch (e: Exception) {
            url
        }
    }
}
