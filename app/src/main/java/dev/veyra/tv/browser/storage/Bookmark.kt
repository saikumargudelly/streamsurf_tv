package dev.veyra.tv.browser.storage

import org.json.JSONObject

/**
 * Data model representing a saved web page bookmark.
 */
data class Bookmark(
    val url: String,
    val title: String,
    val addedAt: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("url", url)
            put("title", title)
            put("addedAt", addedAt)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): Bookmark {
            return Bookmark(
                url = json.optString("url", ""),
                title = json.optString("title", "Untitled"),
                addedAt = json.optLong("addedAt", System.currentTimeMillis())
            )
        }
    }
}
