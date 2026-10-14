package dev.veyra.tv.browser.storage

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkTest {

    @Test
    fun testBookmarkJsonSerialization() {
        val original = Bookmark(
            url = "https://www.hotstar.com",
            title = "Disney+ Hotstar",
            addedAt = 1700000000000L
        )

        val json = original.toJson()
        assertEquals("https://www.hotstar.com", json.getString("url"))
        assertEquals("Disney+ Hotstar", json.getString("title"))
        assertEquals(1700000000000L, json.getLong("addedAt"))

        val restored = Bookmark.fromJson(json)
        assertEquals(original.url, restored.url)
        assertEquals(original.title, restored.title)
        assertEquals(original.addedAt, restored.addedAt)
    }

    @Test
    fun testBookmarkDefaultValues() {
        val emptyJson = JSONObject()
        val defaultBm = Bookmark.fromJson(emptyJson)
        assertEquals("", defaultBm.url)
        assertEquals("Untitled", defaultBm.title)
        assertTrue(defaultBm.addedAt > 0)
    }
}
