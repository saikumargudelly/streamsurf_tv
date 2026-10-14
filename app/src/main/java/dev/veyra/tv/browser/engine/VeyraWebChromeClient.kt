package dev.veyra.tv.browser.engine

import android.os.Message
import android.view.View
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout

/**
 * Custom WebChromeClient managing page progress, title updates, HTML5 video fullscreen transitions,
 * protected media DRM permissions for OTT streaming services, and popup suppression.
 */
class VeyraWebChromeClient(
    private val fullscreenContainer: FrameLayout,
    private val mainContainer: View,
    private val onProgressChangedCallback: (progress: Int) -> Unit,
    private val onTitleReceivedCallback: (title: String?) -> Unit,
    private val onFullscreenStateChanged: (isFullscreen: Boolean) -> Unit
) : WebChromeClient() {

    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null

    val isFullscreen: Boolean
        get() = customView != null

    override fun onPermissionRequest(request: PermissionRequest?) {
        if (request == null) return
        // Ensure request.grant() executes safely on the UI thread to satisfy Android WebView threading requirements
        mainContainer.post {
            try {
                val requestedResources = request.resources ?: emptyArray()
                val grantedResources = mutableListOf<String>()

                for (resource in requestedResources) {
                    when (resource) {
                        PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> {
                            // Grant access to DRM / Widevine protected media identifiers for OTT playback (Hotstar, Prime Video, etc.)
                            grantedResources.add(resource)
                        }
                    }
                }

                if (grantedResources.isNotEmpty()) {
                    request.grant(grantedResources.toTypedArray())
                } else {
                    request.deny()
                }
            } catch (_: Exception) {
                try { request.deny() } catch (_: Exception) {}
            }
        }
    }

    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?
    ): Boolean {
        // Unsolicited popups and script-initiated popunders are blocked immediately
        if (!isUserGesture) {
            return false
        }

        // Direct user gestures (e.g. user clicked a link with window.open or target="_blank")
        // Route to the single active WebView without opening rogue new windows
        if (resultMsg != null && view != null) {
            val hrefMsg = view.handler?.obtainMessage()
            view.requestFocusNodeHref(hrefMsg)
            val url = hrefMsg?.data?.getString("url")
            if (!url.isNullOrBlank() && !dev.veyra.tv.browser.filter.ContentBlocker.shouldBlockNavigation(url)) {
                view.loadUrl(url)
                return false
            }
        }
        return false
    }

    override fun onJsAlert(
        view: WebView?,
        url: String?,
        message: String?,
        result: JsResult?
    ): Boolean {
        // Suppress nuisance alert popups from shady ad networks
        result?.confirm()
        return true
    }

    override fun onJsConfirm(
        view: WebView?,
        url: String?,
        message: String?,
        result: JsResult?
    ): Boolean {
        result?.confirm()
        return true
    }

    override fun onJsPrompt(
        view: WebView?,
        url: String?,
        message: String?,
        defaultValue: String?,
        result: JsPromptResult?
    ): Boolean {
        result?.cancel()
        return true
    }

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChangedCallback(newProgress)
    }

    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        onTitleReceivedCallback(title)
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        if (customView != null) {
            callback?.onCustomViewHidden()
            return
        }

        customView = view
        customViewCallback = callback

        // Hide regular browser UI and display fullscreen video view
        mainContainer.visibility = View.GONE
        fullscreenContainer.visibility = View.VISIBLE
        fullscreenContainer.removeAllViews()
        fullscreenContainer.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        // Request focus on fullscreen view for TV remote controls
        view?.isFocusable = true
        view?.isFocusableInTouchMode = true
        view?.requestFocus()

        onFullscreenStateChanged(true)
    }

    override fun onHideCustomView() {
        if (customView == null) return

        // Hide custom view and restore standard UI
        fullscreenContainer.visibility = View.GONE
        fullscreenContainer.removeAllViews()
        customView = null

        customViewCallback?.onCustomViewHidden()
        customViewCallback = null

        mainContainer.visibility = View.VISIBLE
        onFullscreenStateChanged(false)
    }
}
