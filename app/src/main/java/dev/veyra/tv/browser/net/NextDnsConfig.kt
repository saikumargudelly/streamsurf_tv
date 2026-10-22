package dev.veyra.tv.browser.net

/**
 * Helper object for parsing, validating, and formatting custom NextDNS configuration.
 * Accepts user-entered NextDNS ID (e.g. "28a9b1") or full URL (e.g. "https://dns.nextdns.io/28a9b1").
 */
object NextDnsConfig {

    private const val DEFAULT_ENDPOINT = "https://dns.nextdns.io"
    private const val DEVICE_TAG = "StreamSurf_TV"

    /**
     * Extracts the raw NextDNS profile ID from user input.
     * E.g. "28a9b1" -> "28a9b1"
     * "https://dns.nextdns.io/28a9b1" -> "28a9b1"
     * "28a9b1.dns.nextdns.io" -> "28a9b1"
     */
    fun extractProfileId(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""

        return when {
            trimmed.contains("dns.nextdns.io") -> {
                val withoutProtocol = trimmed
                    .removePrefix("https://")
                    .removePrefix("http://")
                val parts = withoutProtocol.split("/")
                if (parts[0] == "dns.nextdns.io" && parts.size > 1) {
                    parts[1]
                } else if (parts[0].endsWith(".dns.nextdns.io")) {
                    parts[0].removeSuffix(".dns.nextdns.io")
                } else {
                    trimmed
                }
            }
            else -> trimmed
        }
    }

    /**
     * Constructs the full DNS-over-HTTPS (DoH) endpoint URL for queries.
     * Appends device tag so the user can easily identify StreamSurf TV queries in their NextDNS dashboard.
     */
    fun buildDoHUrl(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return "$DEFAULT_ENDPOINT/dns-query"
        }

        // If the user pasted an explicit custom DoH query URL
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return if (trimmed.contains("dns-query")) {
                trimmed
            } else {
                "${trimmed.removeSuffix("/")}/$DEVICE_TAG/dns-query"
            }
        }

        val profileId = extractProfileId(trimmed)
        return "$DEFAULT_ENDPOINT/$profileId/$DEVICE_TAG/dns-query"
    }

    /**
     * Returns the DNS-over-TLS (DoT) hostname used for Android TV system-wide Private DNS settings.
     * E.g. "28a9b1.dns.nextdns.io"
     */
    fun buildDotHostname(input: String): String {
        val profileId = extractProfileId(input)
        return if (profileId.isNotEmpty()) {
            "$profileId.dns.nextdns.io"
        } else {
            "dns.nextdns.io"
        }
    }
}
