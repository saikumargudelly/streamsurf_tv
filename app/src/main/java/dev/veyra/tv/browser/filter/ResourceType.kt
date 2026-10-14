package dev.veyra.tv.browser.filter

import android.net.Uri
import android.webkit.WebResourceRequest

/**
 * Categorization of web subresource types for filter rule matching and ad evaluation.
 */
enum class ResourceType(val filterOption: Int) {
    UNKNOWN(0),
    SCRIPT(1),
    IMAGE(2),
    CSS(4),
    XMLHTTPREQUEST(0x10),
    SUBDOCUMENT(0x40),
    FONT(0x80000),
    MEDIA(0x100000);

    companion object {
        fun from(request: WebResourceRequest?): ResourceType {
            if (request == null) return UNKNOWN
            return from(request.url) ?: UNKNOWN
        }

        fun from(uri: Uri?): ResourceType? {
            if (uri == null) return null
            val path = uri.path?.lowercase() ?: return UNKNOWN
            return when {
                path.endsWith(".js") || path.contains("/js/") -> SCRIPT
                path.endsWith(".css") -> CSS
                path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                    path.endsWith(".gif") || path.endsWith(".webp") || path.endsWith(".svg") || path.endsWith(".ico") -> IMAGE
                path.endsWith(".woff") || path.endsWith(".woff2") || path.endsWith(".ttf") || path.endsWith(".otf") -> FONT
                path.endsWith(".mp4") || path.endsWith(".mpd") || path.endsWith(".m3u8") ||
                    path.endsWith(".m4s") || path.endsWith(".ts") || path.endsWith(".webm") || path.endsWith(".ogg") -> MEDIA
                else -> UNKNOWN
            }
        }
    }
}
