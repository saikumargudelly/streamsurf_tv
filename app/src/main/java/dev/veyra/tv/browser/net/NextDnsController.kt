package dev.veyra.tv.browser.net

import android.content.Context
import android.util.Log
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import dev.veyra.tv.browser.storage.BrowserPreferences
import java.util.concurrent.Executors

/**
 * Central controller for managing user's NextDNS configuration, loopback proxy lifecycle,
 * and WebView proxy routing.
 */
object NextDnsController {

    private const val TAG = "NextDnsController"

    private var proxyServer: NextDnsProxyServer? = null
    private var activeResolver: NextDnsResolver? = null
    private val testExecutor = Executors.newSingleThreadExecutor()

    @Volatile
    var isActive: Boolean = false
        private set

    /**
     * Initializes NextDNS proxy on application start if enabled by user.
     */
    @Synchronized
    fun initialize(context: Context) {
        val enabled = BrowserPreferences.isNextDnsEnabled(context)
        val profileInput = BrowserPreferences.getNextDnsInput(context)
        if (enabled && profileInput.isNotBlank()) {
            start(context)
        }
    }

    /**
     * Starts the local DoH proxy with the user's manual NextDNS profile and overrides WebView routing.
     */
    @Synchronized
    fun start(context: Context): Boolean {
        try {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                Log.w(TAG, "WebView PROXY_OVERRIDE is not supported on this device.")
                return false
            }

            val input = BrowserPreferences.getNextDnsInput(context)
            val dohUrl = NextDnsConfig.buildDoHUrl(input)
            Log.i(TAG, "Starting NextDNS with DoH URL: $dohUrl")

            val resolver = NextDnsResolver(dohUrl)
            activeResolver = resolver

            // Stop existing proxy if running
            proxyServer?.stop()

            val server = NextDnsProxyServer(resolver)
            val port = server.start()
            proxyServer = server

            val proxyConfig = ProxyConfig.Builder()
                .addProxyRule("127.0.0.1:$port")
                .addBypassRule("localhost")
                .addBypassRule("127.0.0.1")
                .addDirect() // fallback to direct if proxy fails
                .build()

            ProxyController.getInstance().setProxyOverride(
                proxyConfig,
                { r -> r.run() },
                {
                    Log.i(TAG, "WebView proxy override successfully pointed to NextDNS on port $port")
                    isActive = true
                }
            )
            return true
        } catch (t: Throwable) {
            Log.e(TAG, "Error starting NextDNS proxy: ${t.message}", t)
            isActive = false
            return false
        }
    }

    /**
     * Disables the NextDNS proxy and restores standard direct routing in WebView.
     */
    @Synchronized
    fun stop(context: Context) {
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                ProxyController.getInstance().clearProxyOverride({ r -> r.run() }) {
                    Log.i(TAG, "WebView proxy override cleared. Restored direct routing.")
                }
            }
            proxyServer?.stop()
            proxyServer = null
            activeResolver?.clearCache()
            activeResolver = null
            isActive = false
        } catch (t: Throwable) {
            Log.e(TAG, "Error stopping NextDNS proxy: ${t.message}", t)
        }
    }

    /**
     * Tests connectivity to the user's configured NextDNS endpoint asynchronously.
     */
    fun testConnection(context: Context, onResult: (NextDnsTestResult) -> Unit) {
        testExecutor.submit {
            val input = BrowserPreferences.getNextDnsInput(context)
            val dohUrl = NextDnsConfig.buildDoHUrl(input)
            val testResolver = NextDnsResolver(dohUrl)
            val result = testResolver.testConnection()
            onResult(result)
        }
    }

    fun getResolver(): NextDnsResolver? = activeResolver
}
