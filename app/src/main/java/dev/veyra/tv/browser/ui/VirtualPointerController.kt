package dev.veyra.tv.browser.ui

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.ImageView
import dev.veyra.tv.browser.R

/**
 * High-Performance TV Virtual Pointer Controller.
 * Built for 60fps/120fps fluid motion on low-spec Android TV hardware:
 * - Direct GPU coordinate updates (zero IPC or JS evaluation during remote motion)
 * - 120ms debounced hover detection to eliminate thread contention and Chromium bridge lag
 * - Deterministic native toolbar hit testing (100% reliable button clicks with zero dead zones)
 * - Direct WebView coordinate dispatch when clicking inside web content
 */
class VirtualPointerController(
    private val cursorView: ImageView,
    private val webViewProvider: () -> WebView?,
    private val container: ViewGroup,
    private val toolbarProvider: () -> ViewGroup?
) {
    var isPointerModeActive: Boolean = false
        private set

    private var cursorX: Float = 0f
    private var cursorY: Float = 0f
    private val baseStepSize: Float = 28f // Tuned for rapid TV screen coverage
    private val handler = Handler(Looper.getMainLooper())
    private var isHoveringClickable: Boolean = false
    private var isCheckingHover: Boolean = false

    private val hoverDebounceRunnable = Runnable {
        checkHoverTargetInternal()
    }

    fun initialize() {
        cursorView.visibility = View.GONE
        cursorView.isClickable = false
        cursorView.isFocusable = false
        cursorView.setImageResource(R.drawable.ic_tv_pointer)
    }

    fun togglePointerMode(): Boolean {
        setPointerMode(!isPointerModeActive)
        return isPointerModeActive
    }

    fun setPointerMode(active: Boolean) {
        isPointerModeActive = active
        if (active) {
            // Center the cursor initially if not set
            if (cursorX == 0f && cursorY == 0f) {
                val w = container.width.toFloat()
                val h = container.height.toFloat()
                cursorX = if (w > 0) w / 2f else 960f
                cursorY = if (h > 0) h / 2f else 540f
            }
            updateCursorPosition(cursorX, cursorY)
            cursorView.visibility = View.VISIBLE
            cursorView.bringToFront()
            scheduleHoverCheck()
        } else {
            cursorView.visibility = View.GONE
            handler.removeCallbacks(hoverDebounceRunnable)
        }
    }

    /**
     * Translates TV remote D-pad presses into smooth on-screen cursor motion.
     * Acceleration curve ensures fast traversal across wide 1080p/4K TV screens.
     */
    fun handleKeyEvent(rawEvent: KeyEvent): Boolean {
        if (!isPointerModeActive) return false
        val event = TvKeyTranslator.translate(rawEvent)

        if (event.action == KeyEvent.ACTION_DOWN) {
            val maxX = (container.width - cursorView.width).toFloat().coerceAtLeast(0f)
            val maxY = (container.height - cursorView.height).toFloat().coerceAtLeast(0f)

            // Dynamic acceleration when remote arrow is held down
            val speedMultiplier = when {
                event.repeatCount > 10 -> 2.8f
                event.repeatCount > 4 -> 1.8f
                event.repeatCount > 0 -> 1.3f
                else -> 1.0f
            }
            val step = baseStepSize * speedMultiplier

            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    cursorY = (cursorY - step).coerceIn(0f, maxY)
                    updateCursorPosition(cursorX, cursorY)
                    if (cursorY <= 24f) {
                        val targetWebView = webViewProvider()
                        if (targetWebView != null && targetWebView.canScrollVertically(-1)) {
                            val scrollAmount = (step * 3.5f).toInt().coerceAtLeast(40)
                            targetWebView.scrollBy(0, -scrollAmount)
                        }
                    }
                    scheduleHoverCheck()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    cursorY = (cursorY + step).coerceIn(0f, maxY)
                    updateCursorPosition(cursorX, cursorY)
                    if (cursorY >= maxY - 24f) {
                        val targetWebView = webViewProvider()
                        if (targetWebView != null && targetWebView.canScrollVertically(1)) {
                            val scrollAmount = (step * 3.5f).toInt().coerceAtLeast(40)
                            targetWebView.scrollBy(0, scrollAmount)
                        }
                    }
                    scheduleHoverCheck()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    cursorX = (cursorX - step).coerceIn(0f, maxX)
                    updateCursorPosition(cursorX, cursorY)
                    scheduleHoverCheck()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    cursorX = (cursorX + step).coerceIn(0f, maxX)
                    updateCursorPosition(cursorX, cursorY)
                    scheduleHoverCheck()
                    return true
                }
                KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    val targetWebView = webViewProvider()
                    if (targetWebView != null && targetWebView.canScrollVertically(1)) {
                        targetWebView.pageDown(false)
                        scheduleHoverCheck()
                        return true
                    }
                }
                KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                    val targetWebView = webViewProvider()
                    if (targetWebView != null && targetWebView.canScrollVertically(-1)) {
                        targetWebView.pageUp(false)
                        scheduleHoverCheck()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (event.repeatCount == 0) {
                        performClickAtCursor()
                    }
                    return true
                }
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    return true
                }
            }
        }
        return false
    }

    private fun updateCursorPosition(x: Float, y: Float) {
        cursorView.x = x
        cursorView.y = y
    }

    /**
     * Executes 100% deterministic clicks:
     * 1. If inside native toolbar: direct native view hit-test & performClick() (zero dead zones).
     * 2. If inside WebView: dispatches touch events directly to target WebView.
     */
    private fun performClickAtCursor() {
        val downTime = SystemClock.uptimeMillis()

        // Tactile press visual feedback
        cursorView.animate()
            .scaleX(0.8f)
            .scaleY(0.8f)
            .setDuration(70)
            .withEndAction {
                cursorView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(70).start()
            }.start()

        // 1. Check if clicking native toolbar controls
        val toolbar = toolbarProvider()
        if (toolbar != null && toolbar.visibility == View.VISIBLE) {
            val toolbarLoc = IntArray(2)
            toolbar.getLocationOnScreen(toolbarLoc)
            val toolbarLeft = toolbarLoc[0]
            val toolbarTop = toolbarLoc[1]
            val toolbarBottom = toolbarTop + toolbar.height
            val toolbarRight = toolbarLeft + toolbar.width

            if (cursorY in toolbarTop.toFloat()..toolbarBottom.toFloat() &&
                cursorX in toolbarLeft.toFloat()..toolbarRight.toFloat()
            ) {
                // Find clicked toolbar child button
                for (i in 0 until toolbar.childCount) {
                    val child = toolbar.getChildAt(i)
                    if (child.visibility == View.VISIBLE && (child.isClickable || child.isFocusable)) {
                        val childLoc = IntArray(2)
                        child.getLocationOnScreen(childLoc)
                        val r = Rect(
                            childLoc[0],
                            toolbarTop, // Span entire toolbar height for easy targeting
                            childLoc[0] + child.width,
                            toolbarBottom
                        )
                        if (r.contains(cursorX.toInt(), cursorY.toInt())) {
                            child.performClick()
                            return
                        }
                    }
                }
            }
        }

        // 2. Dispatch to WebView
        val targetWebView = webViewProvider() ?: return
        val webLoc = IntArray(2)
        targetWebView.getLocationOnScreen(webLoc)
        val localX = cursorX - webLoc[0]
        val localY = cursorY - webLoc[1]

        if (localX >= 0 && localY >= 0 && localX <= targetWebView.width && localY <= targetWebView.height) {
            val downEvent = MotionEvent.obtain(
                downTime, downTime,
                MotionEvent.ACTION_DOWN,
                localX, localY, 0
            )
            targetWebView.dispatchTouchEvent(downEvent)
            downEvent.recycle()

            handler.postDelayed({
                val upEvent = MotionEvent.obtain(
                    downTime, SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_UP,
                    localX, localY, 0
                )
                targetWebView.dispatchTouchEvent(upEvent)
                upEvent.recycle()
            }, 60)
        }
    }

    private fun scheduleHoverCheck() {
        handler.removeCallbacks(hoverDebounceRunnable)
        handler.postDelayed(hoverDebounceRunnable, 110L)
    }

    /**
     * Checks if pointer is hovering over an interactive HTML element or toolbar button.
     * Evaluated with debouncing to keep the UI thread completely unblocked during motion.
     */
    private fun checkHoverTargetInternal() {
        val toolbar = toolbarProvider()
        if (toolbar != null && toolbar.visibility == View.VISIBLE) {
            val loc = IntArray(2)
            toolbar.getLocationOnScreen(loc)
            val toolbarTop = loc[1]
            val toolbarBottom = toolbarTop + toolbar.height
            if (cursorY in toolbarTop.toFloat()..toolbarBottom.toFloat()) {
                // Check if hovering over any clickable button in toolbar
                for (i in 0 until toolbar.childCount) {
                    val child = toolbar.getChildAt(i)
                    if (child.visibility == View.VISIBLE && (child.isClickable || child.isFocusable)) {
                        val childLoc = IntArray(2)
                        child.getLocationOnScreen(childLoc)
                        if (cursorX in childLoc[0].toFloat()..(childLoc[0] + child.width).toFloat()) {
                            setHoverPointer(true)
                            return
                        }
                    }
                }
                setHoverPointer(false)
                return
            }
        }

        if (isCheckingHover) return
        val targetWebView = webViewProvider() ?: return

        isCheckingHover = true
        val loc = IntArray(2)
        targetWebView.getLocationOnScreen(loc)
        val localX = (cursorX - loc[0]).coerceAtLeast(0f)
        val localY = (cursorY - loc[1]).coerceAtLeast(0f)
        val density = targetWebView.resources.displayMetrics.density
        val cssX = localX / density
        val cssY = localY / density

        val js = """
            (function() {
                try {
                    var el = document.elementFromPoint($cssX, $cssY);
                    if (!el) return false;
                    var style = window.getComputedStyle(el);
                    if (style && style.cursor === 'pointer') return true;
                    var clickable = el.closest('a, button, input, select, textarea, [role="button"], [onclick], [tabindex], ytd-thumbnail, ytd-rich-item-renderer, .yt-simple-endpoint');
                    return clickable !== null;
                } catch(e) {
                    return false;
                }
            })();
        """.trimIndent()

        targetWebView.evaluateJavascript(js) { result ->
            isCheckingHover = false
            val isHover = result?.trim()?.equals("true", ignoreCase = true) == true
            setHoverPointer(isHover)
        }
    }

    private fun setHoverPointer(hover: Boolean) {
        if (hover != isHoveringClickable) {
            isHoveringClickable = hover
            cursorView.setImageResource(
                if (hover) R.drawable.ic_tv_pointer_hover else R.drawable.ic_tv_pointer
            )
        }
    }
}
