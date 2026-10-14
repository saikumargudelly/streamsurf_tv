package dev.veyra.tv.browser

import android.app.Application
import android.os.Build
import android.util.Log
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.io.File

/**
 * Application class for Veyra TV.
 * Responsible for process-wide WebView initialization, safe crash logging,
 * and fail-safe ad blocker setup.
 */
class VeyraApplication : Application() {

    companion object {
        private const val TAG = "VeyraApplication"
    }

    override fun onCreate() {
        super.onCreate()

        // 1. Install global uncaught exception handler to prevent silent native/runtime termination
        installCrashHandler()

        // 2. Safe Browsing: guarded defensively against TV ROMs lacking Google Play Services or Chromium provider
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.START_SAFE_BROWSING)) {
                WebViewCompat.startSafeBrowsing(this) { success ->
                    Log.i(TAG, "Safe Browsing initialized (success=$success)")
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Safe Browsing initialization skipped on this device: ${t.message}")
        }

        // 3. Initialize persistent ContentBlocker preferences and allowlist
        try {
            dev.veyra.tv.browser.filter.ContentBlocker.initialize(this)
        } catch (t: Throwable) {
            Log.e(TAG, "Error initializing ContentBlocker: ${t.message}", t)
        }

        // 4. Safe web contents debugging toggle
        try {
            val isDebuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                WebView.setWebContentsDebuggingEnabled(isDebuggable)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Could not set web contents debugging: ${t.message}")
        }
    }

    private fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Log.e("VeyraCrash", "FATAL UNCAUGHT EXCEPTION in thread '${thread.name}': ${throwable.message}", throwable)
                val crashFile = File(filesDir, "last_crash.txt")
                crashFile.writeText(
                    "Timestamp: ${System.currentTimeMillis()}\n" +
                    "Thread: ${thread.name}\n" +
                    "Exception: ${throwable.javaClass.name}: ${throwable.message}\n" +
                    "Stacktrace:\n${throwable.stackTraceToString()}\n"
                )
            } catch (_: Throwable) {
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
