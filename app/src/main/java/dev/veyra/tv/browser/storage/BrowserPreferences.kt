package dev.veyra.tv.browser.storage

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * Lightweight, zero-dependency persistence layer for StreamSurf TV settings and user bookmarks.
 * Stores user preferences (Desktop mode, ContentBlocker state, allowlisted hostnames, bookmarks)
 * in Android SharedPreferences, ensuring state survives process restarts.
 */
object BrowserPreferences {

    private const val PREFS_NAME = "streamsurf_tv_preferences"
    private const val LEGACY_PREFS_NAME = "veyra_tv_preferences"
    private const val KEY_DESKTOP_MODE = "pref_desktop_mode"
    private const val KEY_CONTENT_BLOCKER_ENABLED = "pref_content_blocker_enabled"
    private const val KEY_ALLOWLISTED_HOSTS = "pref_allowlisted_hosts"
    private const val KEY_ZOOM_LEVEL = "pref_zoom_level"
    private const val KEY_BOOKMARKS = "pref_bookmarks_json"
    private const val KEY_NEXTDNS_ENABLED = "pref_nextdns_enabled"
    private const val KEY_NEXTDNS_INPUT = "pref_nextdns_input"
    const val DEFAULT_ZOOM_LEVEL = 115

    private fun getPrefs(context: Context): SharedPreferences {
        val appCtx = context.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // Migrate legacy preferences seamlessly if needed
        val legacy = appCtx.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
        if (legacy.all.isNotEmpty() && prefs.all.isEmpty()) {
            val editor = prefs.edit()
            legacy.all.forEach { (key, value) ->
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is String -> editor.putString(key, value)
                    is Set<*> -> @Suppress("UNCHECKED_CAST") editor.putStringSet(key, value as Set<String>)
                }
            }
            editor.apply()
        }
        return prefs
    }

    fun getZoomLevel(context: Context): Int {
        return getPrefs(context).getInt(KEY_ZOOM_LEVEL, DEFAULT_ZOOM_LEVEL)
    }

    fun setZoomLevel(context: Context, level: Int) {
        getPrefs(context).edit().putInt(KEY_ZOOM_LEVEL, level).apply()
    }

    fun isDesktopMode(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DESKTOP_MODE, true)
    }

    fun setDesktopMode(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DESKTOP_MODE, enabled).apply()
    }

    fun isContentBlockerEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_CONTENT_BLOCKER_ENABLED, true)
    }

    fun setContentBlockerEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_CONTENT_BLOCKER_ENABLED, enabled).apply()
    }

    fun getAllowlistedHosts(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_ALLOWLISTED_HOSTS, emptySet()) ?: emptySet()
    }

    fun addAllowlistedHost(context: Context, host: String) {
        val current = getAllowlistedHosts(context).toMutableSet()
        current.add(host)
        getPrefs(context).edit().putStringSet(KEY_ALLOWLISTED_HOSTS, current).apply()
    }

    fun removeAllowlistedHost(context: Context, host: String) {
        val current = getAllowlistedHosts(context).toMutableSet()
        current.remove(host)
        getPrefs(context).edit().putStringSet(KEY_ALLOWLISTED_HOSTS, current).apply()
    }

    fun clearAllowlist(context: Context) {
        getPrefs(context).edit().remove(KEY_ALLOWLISTED_HOSTS).apply()
    }

    // --- NextDNS Management ---

    fun isNextDnsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_NEXTDNS_ENABLED, false)
    }

    fun setNextDnsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_NEXTDNS_ENABLED, enabled).apply()
    }

    fun getNextDnsInput(context: Context): String {
        return getPrefs(context).getString(KEY_NEXTDNS_INPUT, "") ?: ""
    }

    fun setNextDnsInput(context: Context, input: String) {
        getPrefs(context).edit().putString(KEY_NEXTDNS_INPUT, input.trim()).apply()
    }

    // --- Bookmarks Management ---

    fun getBookmarks(context: Context): List<Bookmark> {
        val raw = getPrefs(context).getString(KEY_BOOKMARKS, null)
        if (raw.isNullOrBlank()) {
            return emptyList()
        }
        val list = mutableListOf<Bookmark>()
        try {
            val jsonArray = JSONArray(raw)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val bm = Bookmark.fromJson(obj)
                if (bm.url.isNotBlank()) {
                    list.add(bm)
                }
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveBookmarks(context: Context, bookmarks: List<Bookmark>) {
        val array = JSONArray()
        for (bm in bookmarks) {
            array.put(bm.toJson())
        }
        getPrefs(context).edit().putString(KEY_BOOKMARKS, array.toString()).apply()
    }

    fun addBookmark(context: Context, url: String, title: String): Boolean {
        if (url.isBlank() || url == "about:blank") return false
        val current = getBookmarks(context).toMutableList()
        val cleanUrl = url.trim()
        val cleanTitle = if (title.isBlank() || title == url) {
            extractHostOrTitle(cleanUrl)
        } else {
            title.trim()
        }

        val existingIdx = current.indexOfFirst { it.url.equals(cleanUrl, ignoreCase = true) }
        if (existingIdx >= 0) {
            // Update existing bookmark title
            current[existingIdx] = current[existingIdx].copy(title = cleanTitle)
        } else {
            // Add new bookmark to top of list
            current.add(0, Bookmark(url = cleanUrl, title = cleanTitle))
        }
        saveBookmarks(context, current)
        return true
    }

    fun removeBookmark(context: Context, url: String): Boolean {
        val cleanUrl = url.trim()
        val current = getBookmarks(context).toMutableList()
        val removed = current.removeAll { it.url.equals(cleanUrl, ignoreCase = true) }
        if (removed) {
            saveBookmarks(context, current)
        }
        return removed
    }

    fun isBookmarked(context: Context, url: String?): Boolean {
        if (url.isNullOrBlank() || url == "about:blank") return false
        val cleanUrl = url.trim()
        return getBookmarks(context).any { it.url.equals(cleanUrl, ignoreCase = true) }
    }

    private fun extractHostOrTitle(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            uri.host?.removePrefix("www.") ?: url
        } catch (_: Exception) {
            url
        }
    }
}
