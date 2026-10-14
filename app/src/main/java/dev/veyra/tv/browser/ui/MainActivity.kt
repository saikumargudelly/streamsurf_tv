package dev.veyra.tv.browser.ui

import android.content.ComponentCallbacks2
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.PixelFormat
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.provider.Settings
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import dev.veyra.tv.browser.R
import dev.veyra.tv.browser.net.NextDnsConfig
import dev.veyra.tv.browser.net.NextDnsController
import dev.veyra.tv.browser.databinding.ActivityMainBinding
import dev.veyra.tv.browser.engine.VeyraWebChromeClient
import dev.veyra.tv.browser.engine.VeyraWebViewController
import dev.veyra.tv.browser.engine.VeyraWebViewClient
import dev.veyra.tv.browser.filter.ContentBlocker
import dev.veyra.tv.browser.navigation.UrlHandler
import dev.veyra.tv.browser.security.SecurityPolicy
import dev.veyra.tv.browser.storage.Bookmark
import dev.veyra.tv.browser.storage.BrowserPreferences

/**
 * Main Android TV Browser Activity.
 * Provides remote-friendly D-pad navigation, single active WebView management,
 * OTT shortcuts (Desktop + TV QR Activate), and HTML5 video fullscreen support.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var controller: VeyraWebViewController
    private lateinit var webChromeClient: VeyraWebChromeClient
    private lateinit var webViewClient: VeyraWebViewClient
    private lateinit var pointerController: VirtualPointerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.setFormat(PixelFormat.RGBA_8888)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setupController()
        setupPointerController()
        setupHomeActions()
        setupToolbarActions()

        savedInstanceState?.let {
            controller.restoreState(it)
        }

        // Browser starts on Home screen, keep WebView paused initially unless intent immediately loads URL
        if (intent?.action != Intent.ACTION_VIEW) {
            controller.onPause()
        }
        handleIncomingIntent(intent)
    }

    private fun setupPointerController() {
        pointerController = VirtualPointerController(
            cursorView = binding.tvCursorView,
            webViewProvider = { controller.webView },
            container = binding.rootContainer,
            toolbarProvider = { binding.navToolbar }
        )
        pointerController.initialize()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val data: Uri? = intent.data
            data?.toString()?.let { url ->
                loadWebPage(url)
            }
        }
    }

    private fun setupController() {
        controller = VeyraWebViewController(this, binding.webViewContainer)

        webChromeClient = VeyraWebChromeClient(
            fullscreenContainer = binding.fullscreenContainer,
            mainContainer = binding.browserContainer,
            onProgressChangedCallback = { progress ->
                if (progress in 1..99) {
                    binding.pageProgressBar.visibility = View.VISIBLE
                    binding.pageProgressBar.alpha = 1f
                    binding.pageProgressBar.setProgress(progress, true)
                } else {
                    binding.pageProgressBar.animate()
                        .alpha(0f)
                        .setDuration(250)
                        .withEndAction { binding.pageProgressBar.visibility = View.GONE }
                        .start()
                }
            },
            onTitleReceivedCallback = { title ->
                if (!title.isNullOrEmpty() && binding.browserContainer.visibility == View.VISIBLE) {
                    binding.tvCurrentUrl.text = title
                }
            },
            onFullscreenStateChanged = { isFullscreen ->
                if (!isFullscreen) {
                    controller.webView?.requestFocus()
                }
            }
        )

        webViewClient = VeyraWebViewClient(
            onPageStartedCallback = { url ->
                binding.tvCurrentUrl.text = url
                updateBookmarkButtonState(url)
            },
            onPageFinishedCallback = { url ->
                binding.tvCurrentUrl.text = url
                updateToolbarNavigationState()
                controller.applyZoom()
                updateBookmarkButtonState(url)
            },

            onSslErrorCallback = { url, error ->
                showSslWarningDialog(url, error)
            },
            onRendererCrashCallback = { didCrash ->
                Toast.makeText(this, getString(R.string.renderer_crashed_message), Toast.LENGTH_LONG).show()
                controller.recoverRenderer(webViewClient, webChromeClient)
            }
        )

        controller.initializeWebView(webViewClient, webChromeClient)
    }

    private fun setupHomeActions() {
        // Go / Surf button
        binding.btnGo.setOnClickListener {
            val query = binding.inputUrlOrSearch.text.toString()
            if (query.isNotBlank()) {
                val targetUrl = UrlHandler.resolveInput(query)
                loadWebPage(targetUrl)
            }
        }

        // Search Input IME Action
        binding.inputUrlOrSearch.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                val query = binding.inputUrlOrSearch.text.toString()
                if (query.isNotBlank()) {
                    val targetUrl = UrlHandler.resolveInput(query)
                    loadWebPage(targetUrl)
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }

        // Search text watcher to toggle Clear button visibility & D-pad navigation
        binding.inputUrlOrSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val hasText = !s.isNullOrEmpty()
                binding.btnClearSearch.visibility = if (hasText) View.VISIBLE else View.GONE
                binding.inputUrlOrSearch.nextFocusRightId = if (hasText) R.id.btnClearSearch else R.id.btnGo
                binding.btnGo.nextFocusLeftId = if (hasText) R.id.btnClearSearch else R.id.inputUrlOrSearch
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Search capsule focus glow
        binding.inputUrlOrSearch.setOnFocusChangeListener { _, hasFocus ->
            binding.searchBarLayout.isActivated = hasFocus
        }

        // Clear Search text action
        binding.btnClearSearch.setOnClickListener {
            binding.inputUrlOrSearch.text?.clear()
            binding.inputUrlOrSearch.requestFocus()
        }

        // Home Shield Pill Action: Quick toggle ad & tracker blocker
        binding.btnHomeShield.setOnClickListener {
            val enabled = !ContentBlocker.isEnabled
            ContentBlocker.isEnabled = enabled
            BrowserPreferences.setContentBlockerEnabled(this, enabled)
            updateShieldStatusUI(enabled)
            val msg = if (enabled) "Ad & Tracker Shield: ACTIVE (Blocking ads, popups & trackers)" else "Ad & Tracker Shield: PAUSED"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // Home NextDNS Pill Action: Open manual NextDNS configuration dialog
        binding.btnHomeNextDns.setOnClickListener {
            showNextDnsDialog()
        }

        // Home Bookmarks Pill Action: Jump to saved bookmarks row or open bookmarks dialog
        binding.btnHomeBookmarks.setOnClickListener {
            val bookmarks = BrowserPreferences.getBookmarks(this)
            if (bookmarks.isNotEmpty() && binding.containerBookmarks.childCount > 0) {
                binding.homeContainer.smoothScrollTo(0, binding.sectionBookmarksHeader.top - 20)
                binding.containerBookmarks.getChildAt(0)?.requestFocus()
            } else {
                showBookmarksDialog()
            }
        }

        // Home Phone Sign-In Pill Action: TV QR & Activation Codes
        binding.btnHomeQrLogin.setOnClickListener {
            handleQrLoginAction()
        }

        // Section 1: Easy TV Sign-In (Scan QR / Phone Activation Code)
        binding.tileTvYoutube.setOnClickListener { loadWebPage("https://www.youtube.com/tv") }
        binding.tileTvPrime.setOnClickListener { loadWebPage("https://www.primevideo.com/mytv") }
        binding.tileTvHotstar.setOnClickListener { loadWebPage("https://www.hotstar.com/in/subscribe/sign-in") }
        binding.tileTvDisney.setOnClickListener { loadWebPage("https://www.disneyplus.com/begin") }
        binding.tileTvNetflix.setOnClickListener { loadWebPage("https://www.netflix.com/tv8") }

        // Section 2: Full Desktop Web Streaming Apps
        binding.tileYoutube.setOnClickListener { loadWebPage("https://www.youtube.com") }
        binding.tileNetflix.setOnClickListener { loadWebPage("https://www.netflix.com") }
        binding.tilePrime.setOnClickListener { loadWebPage("https://www.primevideo.com") }
        binding.tileDisney.setOnClickListener { loadWebPage("https://www.disneyplus.com") }
        binding.tileHotstar.setOnClickListener { loadWebPage("https://www.hotstar.com") }
        binding.tileApple.setOnClickListener { loadWebPage("https://tv.apple.com") }
        binding.tileMax.setOnClickListener { loadWebPage("https://www.max.com") }

        // Section 3: Media, Anime & Search
        binding.tileHulu.setOnClickListener { loadWebPage("https://www.hulu.com") }
        binding.tileSpotify.setOnClickListener { loadWebPage("https://open.spotify.com") }
        binding.tileTwitch.setOnClickListener { loadWebPage("https://www.twitch.tv") }
        binding.tileCrunchyroll.setOnClickListener { loadWebPage("https://www.crunchyroll.com") }
        binding.tileJioCinema.setOnClickListener { loadWebPage("https://www.jiocinema.com") }
        binding.tilePeacock.setOnClickListener { loadWebPage("https://www.peacocktv.com") }
        binding.tileDuckDuckGo.setOnClickListener { loadWebPage("https://duckduckgo.com") }

        // Attach SmartTube Leanback GPU-Accelerated Focus Motion to All Shelves & Elements
        val qrTiles = listOf(
            binding.tileTvYoutube,
            binding.tileTvPrime,
            binding.tileTvHotstar,
            binding.tileTvDisney,
            binding.tileTvNetflix
        )
        qrTiles.forEach { tile ->
            TvMotionHelper.attachTvFocusAnimation(
                tile,
                parentHorizontalScroll = binding.scrollShelfQr,
                parentVerticalScroll = binding.homeContainer
            )
        }

        val desktopTiles = listOf(
            binding.tileYoutube,
            binding.tileNetflix,
            binding.tilePrime,
            binding.tileDisney,
            binding.tileHotstar,
            binding.tileApple,
            binding.tileMax
        )
        desktopTiles.forEach { tile ->
            TvMotionHelper.attachTvFocusAnimation(
                tile,
                parentHorizontalScroll = binding.scrollShelf1,
                parentVerticalScroll = binding.homeContainer
            )
        }

        val mediaTiles = listOf(
            binding.tileHulu,
            binding.tileSpotify,
            binding.tileTwitch,
            binding.tileCrunchyroll,
            binding.tileJioCinema,
            binding.tilePeacock,
            binding.tileDuckDuckGo
        )
        mediaTiles.forEach { tile ->
            TvMotionHelper.attachTvFocusAnimation(
                tile,
                parentHorizontalScroll = binding.scrollShelf2,
                parentVerticalScroll = binding.homeContainer
            )
        }

        // Attach focus motion to Header Action Pills & Omnibar buttons
        TvMotionHelper.attachTvFocusAnimation(binding.btnHomeShield, scaleFactor = 1.05f)
        TvMotionHelper.attachTvFocusAnimation(binding.btnHomeNextDns, scaleFactor = 1.05f)
        TvMotionHelper.attachTvFocusAnimation(binding.btnHomeBookmarks, scaleFactor = 1.05f)
        TvMotionHelper.attachTvFocusAnimation(binding.btnHomeQrLogin, scaleFactor = 1.05f)
        TvMotionHelper.attachTvFocusAnimation(binding.btnGo, scaleFactor = 1.05f)
        TvMotionHelper.attachTvFocusAnimation(binding.btnClearSearch, scaleFactor = 1.08f)
        TvMotionHelper.attachTvFocusAnimation(binding.searchBarLayout, scaleFactor = 1.02f)
        updateShieldStatusUI(ContentBlocker.isEnabled)
        updateNextDnsStatusUI()
        refreshBookmarksShelf()
        binding.inputUrlOrSearch.post {
            binding.inputUrlOrSearch.requestFocus()
        }
    }

    private fun setupToolbarActions() {
        binding.btnNavBack.setOnClickListener {
            if (controller.canGoBack()) {
                controller.goBack()
            } else {
                showHomeScreen()
            }
        }

        binding.btnNavForward.setOnClickListener {
            if (controller.canGoForward()) {
                controller.goForward()
            }
        }

        binding.btnNavReload.setOnClickListener {
            controller.reload()
            Toast.makeText(this, "Page reloaded", Toast.LENGTH_SHORT).show()
        }

        binding.btnNavHome.setOnClickListener {
            showHomeScreen()
        }

        binding.btnNavPointer.setOnClickListener {
            val active = pointerController.togglePointerMode()
            binding.btnNavPointer.isSelected = active
            val message = if (active) {
                "Virtual Pointer: ON (Move with D-pad, OK to click anywhere)"
            } else {
                "Virtual Pointer: OFF (Remote Focus Mode)"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            if (!active) {
                controller.webView?.requestFocus()
            }
        }

        binding.btnNavDesktop.isSelected = controller.isDesktopMode
        binding.btnNavDesktop.setOnClickListener {
            val isDesktop = controller.toggleDesktopMode()
            binding.btnNavDesktop.isSelected = isDesktop
            val message = if (isDesktop) {
                "Desktop Web Mode: ON (1440px High-Definition View)"
            } else {
                "Standard Web Mode: ON"
            }
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }

        // Magnifier & Page Zoom Controls
        updateZoomLevelDisplay(controller.currentZoomPercent)
        binding.btnNavZoomIn.setOnClickListener {
            performZoomIn()
        }
        binding.btnNavZoomOut.setOnClickListener {
            performZoomOut()
        }
        binding.tvNavZoomLevel.setOnClickListener {
            val resetZoom = controller.resetZoom()
            updateZoomLevelDisplay(resetZoom)
            showZoomOverlay(resetZoom)
        }

        // In-App Ad & Tracker Blocker Shield Toggle
        binding.btnNavShield.setOnClickListener {
            val enabled = !ContentBlocker.isEnabled
            ContentBlocker.isEnabled = enabled
            BrowserPreferences.setContentBlockerEnabled(this, enabled)
            updateShieldStatusUI(enabled)
            val msg = if (enabled) "Ad & Tracker Shield: ACTIVE" else "Ad & Tracker Shield: PAUSED"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        // NextDNS Configuration Action
        binding.btnNavNextDns.setOnClickListener {
            showNextDnsDialog()
        }

        // Easy Phone QR & TV Code Sign-in Action
        binding.btnNavQrLogin.setOnClickListener {
            handleQrLoginAction()
        }

        // Bookmark Button Action
        binding.btnNavBookmark.setOnClickListener {
            toggleBookmarkCurrentPage()
        }
        binding.btnNavBookmark.setOnLongClickListener {
            showBookmarksDialog()
            true
        }

        binding.tvCurrentUrl.setOnClickListener {
            showHomeScreen()
            binding.inputUrlOrSearch.requestFocus()
        }

        // Attach smooth focus pulse animations to toolbar buttons
        val toolbarButtons = listOf(
            binding.btnNavBack,
            binding.btnNavForward,
            binding.btnNavReload,
            binding.btnNavHome,
            binding.btnNavPointer,
            binding.btnNavDesktop,
            binding.btnNavZoomOut,
            binding.tvNavZoomLevel,
            binding.btnNavZoomIn,
            binding.btnNavShield,
            binding.btnNavNextDns,
            binding.btnNavQrLogin,
            binding.btnNavBookmark,
            binding.tvCurrentUrl
        )
        toolbarButtons.forEach { btn ->
            TvMotionHelper.attachToolbarFocusAnimation(btn)
        }
    }


    private fun handleQrLoginAction() {
        val currentUrl = controller.webView?.url ?: ""
        when {
            currentUrl.contains("youtube.com") -> {
                Toast.makeText(this, "Loading YouTube TV (Scan QR Code on screen)...", Toast.LENGTH_LONG).show()
                loadWebPage("https://www.youtube.com/tv")
            }
            currentUrl.contains("amazon.") || currentUrl.contains("primevideo.") -> {
                Toast.makeText(this, "Loading Prime Video TV Code Sign-In...", Toast.LENGTH_LONG).show()
                loadWebPage("https://www.primevideo.com/mytv")
            }
            currentUrl.contains("hotstar.") -> {
                Toast.makeText(this, "Loading Hotstar QR Sign-In...", Toast.LENGTH_LONG).show()
                loadWebPage("https://www.hotstar.com/in/subscribe/sign-in")
            }
            currentUrl.contains("disney") -> {
                Toast.makeText(this, "Loading Disney+ TV Code Sign-In...", Toast.LENGTH_LONG).show()
                loadWebPage("https://www.disneyplus.com/begin")
            }
            currentUrl.contains("netflix") -> {
                Toast.makeText(this, "Loading Netflix TV Code Sign-In...", Toast.LENGTH_LONG).show()
                loadWebPage("https://www.netflix.com/tv8")
            }
            else -> {
                // Show Quick Activation Chooser Dialog
                val options = arrayOf(
                    "YouTube TV (Scan QR Code)",
                    "Amazon Prime Video (mytv Code)",
                    "Hotstar (Scan QR with Mobile App)",
                    "Disney+ (8-Digit Code)",
                    "Netflix TV (TV Code)"
                )
                AlertDialog.Builder(this)
                    .setTitle("Easy TV Sign-In (No Typing Required)")
                    .setItems(options) { _, which ->
                        when (which) {
                            0 -> loadWebPage("https://www.youtube.com/tv")
                            1 -> loadWebPage("https://www.primevideo.com/mytv")
                            2 -> loadWebPage("https://www.hotstar.com/in/subscribe/sign-in")
                            3 -> loadWebPage("https://www.disneyplus.com/begin")
                            4 -> loadWebPage("https://www.netflix.com/tv8")
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    fun loadWebPage(url: String) {
        val resolved = UrlHandler.resolveInput(url)
        binding.homeContainer.visibility = View.GONE
        binding.browserContainer.visibility = View.VISIBLE
        binding.tvCurrentUrl.text = resolved
        updateBookmarkButtonState(resolved)

        controller.onResume()
        controller.loadUrl(resolved)
        controller.webView?.requestFocus()
    }

    fun showHomeScreen() {
        pointerController.setPointerMode(false)
        binding.btnNavPointer.isSelected = false
        binding.browserContainer.visibility = View.GONE
        binding.fullscreenContainer.visibility = View.GONE
        binding.homeContainer.visibility = View.VISIBLE
        controller.onPause()
        updateShieldStatusUI(ContentBlocker.isEnabled)
        refreshBookmarksShelf()
        binding.inputUrlOrSearch.requestFocus()
    }

    private var zoomOverlayRunnable: Runnable? = null

    private fun updateZoomLevelDisplay(percent: Int) {
        binding.tvNavZoomLevel.text = "$percent%"
    }

    private fun showZoomOverlay(percent: Int) {
        binding.tvZoomToastText.text = "🔍 Zoom: $percent%"
        binding.zoomToastOverlay.visibility = View.VISIBLE
        binding.zoomToastOverlay.alpha = 1f

        zoomOverlayRunnable?.let { binding.zoomToastOverlay.removeCallbacks(it) }
        val runnable = Runnable {
            binding.zoomToastOverlay.animate()
                .alpha(0f)
                .setDuration(350)
                .withEndAction { binding.zoomToastOverlay.visibility = View.GONE }
                .start()
        }
        zoomOverlayRunnable = runnable
        binding.zoomToastOverlay.postDelayed(runnable, 1500)
    }

    private fun performZoomIn(): Boolean {
        if (binding.browserContainer.visibility == View.VISIBLE) {
            val newZoom = controller.zoomIn()
            updateZoomLevelDisplay(newZoom)
            showZoomOverlay(newZoom)
            return true
        }
        return false
    }

    private fun performZoomOut(): Boolean {
        if (binding.browserContainer.visibility == View.VISIBLE) {
            val newZoom = controller.zoomOut()
            updateZoomLevelDisplay(newZoom)
            showZoomOverlay(newZoom)
            return true
        }
        return false
    }

    private fun updateToolbarNavigationState() {
        binding.btnNavBack.alpha = if (controller.canGoBack()) 1.0f else 0.5f
        binding.btnNavForward.alpha = if (controller.canGoForward()) 1.0f else 0.5f
    }


    private fun showSslWarningDialog(url: String, error: SslError) {
        AlertDialog.Builder(this)
            .setTitle("SSL Security Warning")
            .setMessage("The security certificate for $url is not trusted (Error: ${error.primaryError}). Connection blocked for privacy and security.")
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                showHomeScreen()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Remote control D-pad and Back button handling.
     * Follows TV navigation hierarchy:
     * 1. Virtual Pointer Mode -> intercepts D-pad movements and clicks (anywhere on screen)
     * 2. Fullscreen video -> exits fullscreen
     * 3. Toolbar navigation -> UP at top of page jumps to toolbar; DOWN on toolbar jumps to page
     * 4. WebView back stack -> navigates back in page history
     * 5. Home view -> standard Android exit
     */
    override fun dispatchKeyEvent(rawEvent: KeyEvent): Boolean {
        val event = TvKeyTranslator.translate(rawEvent)
        if (binding.browserContainer.visibility == View.VISIBLE && pointerController.isPointerModeActive) {
            if (pointerController.handleKeyEvent(event)) {
                return true
            }
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (binding.browserContainer.visibility == View.VISIBLE && binding.navToolbar.hasFocus()) {
                        controller.webView?.requestFocus()
                        return true
                    }
                }
                KeyEvent.KEYCODE_BACK -> {
                    if (webChromeClient.isFullscreen) {
                        webChromeClient.onHideCustomView()
                        return true
                    }
                    if (binding.browserContainer.visibility == View.VISIBLE) {
                        if (binding.navToolbar.hasFocus()) {
                            controller.webView?.requestFocus()
                            return true
                        }
                        if (controller.canGoBack()) {
                            controller.goBack()
                            return true
                        } else {
                            showHomeScreen()
                            return true
                        }
                    }
                }
                KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    if (binding.browserContainer.visibility == View.VISIBLE) {
                        controller.webView?.pageDown(false)
                        return true
                    }
                }
                KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                    if (binding.browserContainer.visibility == View.VISIBLE) {
                        controller.webView?.pageUp(false)
                        return true
                    }
                }
                KeyEvent.KEYCODE_MENU -> {
                    if (binding.browserContainer.visibility == View.VISIBLE) {
                        binding.btnNavHome.requestFocus()
                        return true
                    }
                }
                KeyEvent.KEYCODE_ZOOM_IN,
                KeyEvent.KEYCODE_EQUALS,
                KeyEvent.KEYCODE_PLUS,
                KeyEvent.KEYCODE_NUMPAD_ADD -> {
                    if (performZoomIn()) return true
                }
                KeyEvent.KEYCODE_ZOOM_OUT,
                KeyEvent.KEYCODE_MINUS,
                KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> {
                    if (performZoomOut()) return true
                }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    if (!webChromeClient.isFullscreen && performZoomIn()) return true
                }
                KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    if (!webChromeClient.isFullscreen && performZoomOut()) return true
                }
            }
        }

        if (event.action == KeyEvent.ACTION_UP && event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (binding.browserContainer.visibility == View.VISIBLE || webChromeClient.isFullscreen) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        if (binding.browserContainer.visibility == View.VISIBLE) {
            controller.onResume()
        }
    }

    override fun onPause() {
        super.onPause()
        controller.onPause()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                // UI is hidden or app is in background LRU: onPause() has already flushed cookies.
                // Ensure controller timers/state are paused if browserContainer was hidden.
                if (binding.browserContainer.visibility != View.VISIBLE) {
                    controller.onPause()
                }
            }
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> {
                // Foreground device memory is critically constrained: commit cookies in case of imminent LMK termination
                SecurityPolicy.flushCookies()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        controller.saveState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        controller.destroyWebView()
    }

    // --- Bookmarks Feature Implementation ---

    private fun updateBookmarkButtonState(url: String?) {
        val current = url ?: controller.webView?.url ?: ""
        val isBookmarked = BrowserPreferences.isBookmarked(this, current)
        if (isBookmarked) {
            binding.btnNavBookmark.setImageResource(R.drawable.ic_nav_bookmark_filled)
            binding.btnNavBookmark.setColorFilter(getColor(R.color.star_gold))
        } else {
            binding.btnNavBookmark.setImageResource(R.drawable.ic_nav_bookmark)
            binding.btnNavBookmark.setColorFilter(getColor(R.color.text_secondary))
        }
    }

    private fun toggleBookmarkCurrentPage() {
        val url = controller.webView?.url ?: ""
        if (url.isBlank() || url == "about:blank") {
            Toast.makeText(this, "No active web page to bookmark", Toast.LENGTH_SHORT).show()
            return
        }
        val title = controller.webView?.title?.takeIf { it.isNotBlank() } ?: url
        if (BrowserPreferences.isBookmarked(this, url)) {
            BrowserPreferences.removeBookmark(this, url)
            updateBookmarkButtonState(url)
            Toast.makeText(this, getString(R.string.bookmark_removed), Toast.LENGTH_SHORT).show()
        } else {
            BrowserPreferences.addBookmark(this, url, title)
            updateBookmarkButtonState(url)
            Toast.makeText(this, "${getString(R.string.bookmark_added)}: $title", Toast.LENGTH_SHORT).show()
        }
        refreshBookmarksShelf()
    }

    private fun refreshBookmarksShelf() {
        val bookmarks = BrowserPreferences.getBookmarks(this)
        if (bookmarks.isEmpty()) {
            binding.tvHomeBookmarksCount.text = getString(R.string.home_bookmarks)
            binding.sectionBookmarksHeader.visibility = View.GONE
            binding.scrollShelfBookmarks.visibility = View.GONE
            binding.containerBookmarks.removeAllViews()
            binding.inputUrlOrSearch.nextFocusDownId = R.id.tileTvYoutube
            binding.btnGo.nextFocusDownId = R.id.tileTvYoutube
            return
        }

        binding.tvHomeBookmarksCount.text = "${getString(R.string.home_bookmarks)} (${bookmarks.size})"
        binding.sectionBookmarksHeader.visibility = View.VISIBLE
        binding.scrollShelfBookmarks.visibility = View.VISIBLE
        binding.containerBookmarks.removeAllViews()

        for ((index, bm) in bookmarks.withIndex()) {
            val itemView = layoutInflater.inflate(R.layout.item_bookmark_tile, binding.containerBookmarks, false)
            val tvHost = itemView.findViewById<TextView>(R.id.tvBookmarkHost)
            val tvTitle = itemView.findViewById<TextView>(R.id.tvBookmarkTitle)

            val uri = try { Uri.parse(bm.url) } catch (_: Exception) { null }
            tvHost.text = uri?.host?.removePrefix("www.") ?: bm.url
            tvTitle.text = bm.title

            itemView.setOnClickListener {
                loadWebPage(bm.url)
            }

            itemView.setOnLongClickListener {
                showDeleteBookmarkDialog(bm)
                true
            }

            itemView.id = View.generateViewId()
            if (index == 0) {
                binding.inputUrlOrSearch.nextFocusDownId = itemView.id
                binding.btnGo.nextFocusDownId = itemView.id
            }

            TvMotionHelper.attachTvFocusAnimation(itemView, scaleFactor = 1.05f)
            binding.containerBookmarks.addView(itemView)
        }
    }

    private fun updateShieldStatusUI(enabled: Boolean) {
        binding.btnNavShield.isSelected = enabled
        binding.btnHomeShield.isSelected = enabled
        if (enabled) {
            binding.tvHomeShieldStatus.text = getString(R.string.home_shield_active)
            binding.imgHomeShieldDot.setImageResource(R.drawable.ic_status_dot_green)
            binding.imgHomeShieldIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.accent_primary)
        } else {
            binding.tvHomeShieldStatus.text = getString(R.string.home_shield_off)
            binding.imgHomeShieldDot.setImageResource(R.drawable.ic_status_dot_red)
            binding.imgHomeShieldIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.text_muted)
        }
    }

    private fun updateNextDnsStatusUI() {
        val enabled = BrowserPreferences.isNextDnsEnabled(this)
        val input = BrowserPreferences.getNextDnsInput(this)
        val profileId = NextDnsConfig.extractProfileId(input)

        binding.btnNavNextDns.isSelected = enabled
        binding.btnHomeNextDns.isSelected = enabled

        if (enabled && profileId.isNotEmpty()) {
            binding.tvHomeNextDnsStatus.text = "NextDNS ($profileId)"
            binding.imgHomeNextDnsDot.setImageResource(R.drawable.ic_status_dot_green)
            binding.imgHomeNextDnsIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.accent_primary)
            binding.btnNavNextDns.imageTintList = ContextCompat.getColorStateList(this, R.color.accent_primary)
        } else if (enabled) {
            binding.tvHomeNextDnsStatus.text = "NextDNS (On)"
            binding.imgHomeNextDnsDot.setImageResource(R.drawable.ic_status_dot_green)
            binding.imgHomeNextDnsIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.accent_primary)
            binding.btnNavNextDns.imageTintList = ContextCompat.getColorStateList(this, R.color.accent_primary)
        } else {
            binding.tvHomeNextDnsStatus.text = "NextDNS (Off)"
            binding.imgHomeNextDnsDot.setImageResource(R.drawable.ic_status_dot_red)
            binding.imgHomeNextDnsIcon.imageTintList = ContextCompat.getColorStateList(this, R.color.text_muted)
            binding.btnNavNextDns.imageTintList = ContextCompat.getColorStateList(this, R.color.text_primary)
        }
    }

    private fun showNextDnsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_nextdns, null)
        val chkEnable = dialogView.findViewById<CheckBox>(R.id.chkEnableNextDns)
        val editInput = dialogView.findViewById<EditText>(R.id.editNextDnsInput)
        val tvDoHPreview = dialogView.findViewById<TextView>(R.id.tvDoHPreview)
        val tvDotPreview = dialogView.findViewById<TextView>(R.id.tvDotPreview)
        val btnOpenSettings = dialogView.findViewById<Button>(R.id.btnOpenSystemSettings)
        val tvTestStatus = dialogView.findViewById<TextView>(R.id.tvTestStatus)
        val btnTest = dialogView.findViewById<Button>(R.id.btnTestNextDns)
        val btnDisable = dialogView.findViewById<Button>(R.id.btnDisableNextDns)
        val btnSave = dialogView.findViewById<Button>(R.id.btnSaveNextDns)
        val btnClose = dialogView.findViewById<Button>(R.id.btnCloseNextDns)

        val currentEnabled = BrowserPreferences.isNextDnsEnabled(this)
        val currentInput = BrowserPreferences.getNextDnsInput(this)

        chkEnable.isChecked = currentEnabled
        editInput.setText(currentInput)

        fun updatePreviews(text: String) {
            tvDoHPreview.text = "DoH Endpoint: ${NextDnsConfig.buildDoHUrl(text)}"
            tvDotPreview.text = "Android TV Hostname: ${NextDnsConfig.buildDotHostname(text)}"
        }
        updatePreviews(currentInput)

        editInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePreviews(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnOpenSettings.setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS))
            } catch (_: Throwable) {
                try {
                    startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                } catch (_: Throwable) {
                    try {
                        startActivity(Intent(Settings.ACTION_SETTINGS))
                    } catch (_: Throwable) {
                        Toast.makeText(this, "Could not open TV Network Settings directly", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        btnTest.setOnClickListener {
            val input = editInput.text.toString().trim()
            tvTestStatus.visibility = View.VISIBLE
            tvTestStatus.text = "Testing NextDNS resolution..."
            tvTestStatus.setTextColor(ContextCompat.getColor(this, R.color.text_muted))

            val dohUrl = NextDnsConfig.buildDoHUrl(input)
            Thread {
                val resolver = dev.veyra.tv.browser.net.NextDnsResolver(dohUrl)
                val result = resolver.testConnection()
                runOnUiThread {
                    if (result.success) {
                        tvTestStatus.text = "✓ ${result.message}"
                        tvTestStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green))
                    } else {
                        tvTestStatus.text = "✗ ${result.message}"
                        tvTestStatus.setTextColor(ContextCompat.getColor(this, R.color.danger))
                    }
                }
            }.start()
        }

        btnDisable.setOnClickListener {
            BrowserPreferences.setNextDnsEnabled(this, false)
            NextDnsController.stop(this)
            updateNextDnsStatusUI()
            Toast.makeText(this, "NextDNS Disabled", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        btnSave.setOnClickListener {
            val input = editInput.text.toString().trim()
            val enable = chkEnable.isChecked || input.isNotEmpty()

            BrowserPreferences.setNextDnsInput(this, input)
            BrowserPreferences.setNextDnsEnabled(this, enable)

            if (enable) {
                val started = NextDnsController.start(this)
                val profileId = NextDnsConfig.extractProfileId(input)
                if (started) {
                    Toast.makeText(this, "NextDNS Activated (Profile: $profileId)", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Saved, but proxy override failed on this device", Toast.LENGTH_SHORT).show()
                }
            } else {
                NextDnsController.stop(this)
                Toast.makeText(this, "NextDNS Deactivated", Toast.LENGTH_SHORT).show()
            }
            updateNextDnsStatusUI()
            dialog.dismiss()
        }

        btnClose.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
        editInput.post {
            editInput.requestFocus()
        }
    }

    private fun showDeleteBookmarkDialog(bm: Bookmark) {
        AlertDialog.Builder(this)
            .setTitle("Remove Bookmark?")
            .setMessage("Do you want to delete bookmark for \"${bm.title}\"?")
            .setPositiveButton("Remove") { _, _ ->
                BrowserPreferences.removeBookmark(this, bm.url)
                updateBookmarkButtonState(controller.webView?.url)
                refreshBookmarksShelf()
                Toast.makeText(this, getString(R.string.bookmark_removed), Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBookmarksDialog() {
        val bookmarks = BrowserPreferences.getBookmarks(this)
        if (bookmarks.isEmpty()) {
            Toast.makeText(this, "No saved bookmarks yet • Click star on any page", Toast.LENGTH_SHORT).show()
            return
        }
        val titles = bookmarks.map { "${it.title}\n${it.url}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Saved Bookmarks (${bookmarks.size})")
            .setItems(titles) { _, which ->
                loadWebPage(bookmarks[which].url)
            }
            .setNegativeButton("Close", null)
            .show()
    }
}
