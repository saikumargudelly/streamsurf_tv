package dev.veyra.tv.browser.ui

import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.webkit.WebView
import android.widget.ImageView
import dev.veyra.tv.browser.R

/**
 * High-Performance Pixel-Accurate TV Virtual Pointer Controller.
 * Engineered for silky 60fps/120fps fluid cursor motion on television hardware:
 * - Pixel-accurate hotspot targeting locked precisely to the apex tip of the cursor
 * - Progressive acceleration curve: micro-pixel precision (5.5dp) on single taps, ramping to 50dp on hold
 * - Hardware-accelerated continuous interpolation (no coordinate jumps or teleportation)
 * - Dynamic organic tilt animation (-7° to +7°) during horizontal movement
 * - Tactile press spring-animation and anchored hover scaling
 * - Universal hit-testing across Native UI (Home & Toolbar) and WebView HTML elements
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

    private val density: Float
        get() = cursorView.resources.displayMetrics.density

    // Hotspot is locked directly to the apex point of ic_tv_pointer.xml (2.5dp from top-left)
    private val tipOffsetPx: Float
        get() = 2.5f * density

    private val handler = Handler(Looper.getMainLooper())
    private var isHoveringClickable: Boolean = false
    private var isCheckingHover: Boolean = false

    private val decelerateInterpolator = DecelerateInterpolator(1.4f)
    private val resetRotationRunnable = Runnable {
        cursorView.animate()
            .rotation(0f)
            .setDuration(120L)
            .setInterpolator(decelerateInterpolator)
            .start()
    }

    private val hoverDebounceRunnable = Runnable {
        checkHoverTargetInternal()
    }

    fun initialize() {
        cursorView.isClickable = false
        cursorView.isFocusable = false
        cursorView.setImageResource(R.drawable.ic_tv_pointer)

        // Anchor scale and rotation pivot directly at the arrow apex tip
        cursorView.pivotX = tipOffsetPx
        cursorView.pivotY = tipOffsetPx

        // Pointer mode is enabled by default on app launch
        setPointerMode(true)
    }

    fun togglePointerMode(): Boolean {
        setPointerMode(!isPointerModeActive)
        return isPointerModeActive
    }

    fun setPointerMode(active: Boolean) {
        isPointerModeActive = active
        if (active) {
            // Center the cursor initially if not already placed
            if (cursorX <= 0f && cursorY <= 0f) {
                container.post {
                    val w = container.width.toFloat()
                    val h = container.height.toFloat()
                    cursorX = if (w > 0) (w / 2f) - tipOffsetPx else 960f
                    cursorY = if (h > 0) (h / 2f) - tipOffsetPx else 540f
                    cursorView.x = cursorX
                    cursorView.y = cursorY
                    cursorView.alpha = 0f
                    cursorView.visibility = View.VISIBLE
                    cursorView.bringToFront()
                    cursorView.animate().alpha(1f).setDuration(150L).start()
                    scheduleHoverCheck()
                }
            } else {
                cursorView.x = cursorX
                cursorView.y = cursorY
                cursorView.visibility = View.VISIBLE
                cursorView.bringToFront()
                scheduleHoverCheck()
            }
        } else {
            cursorView.visibility = View.GONE
            handler.removeCallbacks(hoverDebounceRunnable)
            handler.removeCallbacks(resetRotationRunnable)
        }
    }

    /**
     * Translates TV remote D-pad presses into smooth, pixel-accurate on-screen cursor motion.
     * Acceleration curve:
     * - Repeat 0 (single tap): 5.5dp (~11px) micro-step for exact single-pixel targeting
     * - Repeat 1-3: 12dp smooth ramp-up
     * - Repeat 4-7: 22dp cruising speed
     * - Repeat 8-14: 36dp fast screen coverage
     * - Repeat >= 15: 50dp high-speed 4K TV traversal
     */
    fun handleKeyEvent(rawEvent: KeyEvent): Boolean {
        if (!isPointerModeActive) return false
        val event = TvKeyTranslator.translate(rawEvent)

        if (event.action == KeyEvent.ACTION_DOWN) {
            val maxX = (container.width - cursorView.width).toFloat().coerceAtLeast(0f)
            val maxY = (container.height - cursorView.height).toFloat().coerceAtLeast(0f)

            val stepDp = when {
                event.repeatCount == 0 -> 5.5f   // Micro-pixel precision on tap
                event.repeatCount in 1..3 -> 12f // Gentle pickup
                event.repeatCount in 4..7 -> 22f // Cruising
                event.repeatCount in 8..14 -> 36f// Fast
                else -> 50f                      // Maximum sprint
            }
            val step = stepDp * density

            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP -> {
                    cursorY = (cursorY - step).coerceIn(0f, maxY)
                    animateCursorMotion(cursorX, cursorY, tiltAngle = 0f)

                    // Scroll assist at page top
                    if (cursorY <= 24f * density) {
                        val targetWebView = webViewProvider()
                        if (targetWebView != null && targetWebView.canScrollVertically(-1)) {
                            val scrollAmount = (step * 3.5f).toInt().coerceAtLeast(35)
                            targetWebView.scrollBy(0, -scrollAmount)
                        }
                    }
                    scheduleHoverCheck()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    cursorY = (cursorY + step).coerceIn(0f, maxY)
                    animateCursorMotion(cursorX, cursorY, tiltAngle = 0f)

                    // Scroll assist at page bottom
                    if (cursorY >= maxY - (24f * density)) {
                        val targetWebView = webViewProvider()
                        if (targetWebView != null && targetWebView.canScrollVertically(1)) {
                            val scrollAmount = (step * 3.5f).toInt().coerceAtLeast(35)
                            targetWebView.scrollBy(0, scrollAmount)
                        }
                    }
                    scheduleHoverCheck()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    cursorX = (cursorX - step).coerceIn(0f, maxX)
                    animateCursorMotion(cursorX, cursorY, tiltAngle = -7f)
                    scheduleHoverCheck()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    cursorX = (cursorX + step).coerceIn(0f, maxX)
                    animateCursorMotion(cursorX, cursorY, tiltAngle = 7f)
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
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    scheduleRotationReset()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Smooth hardware-accelerated position interpolation (50ms duration matching D-pad repeat).
     */
    private fun animateCursorMotion(targetX: Float, targetY: Float, tiltAngle: Float) {
        cursorView.animate()
            .x(targetX)
            .y(targetY)
            .setDuration(50L)
            .setInterpolator(decelerateInterpolator)
            .start()

        if (tiltAngle != 0f) {
            cursorView.animate()
                .rotation(tiltAngle)
                .setDuration(70L)
                .start()
            handler.removeCallbacks(resetRotationRunnable)
            handler.postDelayed(resetRotationRunnable, 140L)
        }
    }

    private fun scheduleRotationReset() {
        handler.removeCallbacks(resetRotationRunnable)
        handler.postDelayed(resetRotationRunnable, 60L)
    }

    /**
     * Executes 100% deterministic pixel-accurate clicks at the exact cursor apex point:
     * 1. Hit-test native top navigation toolbar if open.
     * 2. Hit-test active WebView web content (dispatches native touch events).
     * 3. Hit-test native views on the Home dashboard or modals.
     */
    private fun performClickAtCursor() {
        val downTime = SystemClock.uptimeMillis()
        val targetX = cursorX + tipOffsetPx
        val targetY = cursorY + tipOffsetPx

        // Tactile spring press feedback anchored at tip
        cursorView.animate()
            .scaleX(0.84f)
            .scaleY(0.84f)
            .setDuration(45L)
            .withEndAction {
                val targetScale = if (isHoveringClickable) 1.15f else 1.0f
                cursorView.animate()
                    .scaleX(targetScale)
                    .scaleY(targetScale)
                    .setDuration(85L)
                    .setInterpolator(OvershootInterpolator(1.2f))
                    .start()
            }.start()

        // 1. Check if clicking inside native top toolbar
        val toolbar = toolbarProvider()
        if (toolbar != null && toolbar.visibility == View.VISIBLE) {
            val toolbarLoc = IntArray(2)
            toolbar.getLocationOnScreen(toolbarLoc)
            val toolbarLeft = toolbarLoc[0].toFloat()
            val toolbarTop = toolbarLoc[1].toFloat()
            val toolbarRight = toolbarLeft + toolbar.width
            val toolbarBottom = toolbarTop + toolbar.height

            if (targetY in toolbarTop..toolbarBottom && targetX in toolbarLeft..toolbarRight) {
                for (i in 0 until toolbar.childCount) {
                    val child = toolbar.getChildAt(i)
                    if (child.visibility == View.VISIBLE && (child.isClickable || child.isFocusable)) {
                        val childLoc = IntArray(2)
                        child.getLocationOnScreen(childLoc)
                        val r = RectF(
                            childLoc[0].toFloat(),
                            toolbarTop,
                            (childLoc[0] + child.width).toFloat(),
                            toolbarBottom
                        )
                        if (r.contains(targetX, targetY)) {
                            child.requestFocus()
                            child.performClick()
                            return
                        }
                    }
                }
            }
        }

        // 2. Check if clicking inside active WebView
        val targetWebView = webViewProvider()
        if (targetWebView != null && targetWebView.visibility == View.VISIBLE) {
            val webLoc = IntArray(2)
            targetWebView.getLocationOnScreen(webLoc)
            val localX = targetX - webLoc[0]
            val localY = targetY - webLoc[1]

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
                }, 50L)
                return
            }
        }

        // 3. Fallback: Hit-test native views in container (Home screen dashboard cards, buttons, etc.)
        val clickedNativeView = findClickableViewAt(container, targetX, targetY)
        if (clickedNativeView != null && clickedNativeView != cursorView) {
            clickedNativeView.requestFocus()
            clickedNativeView.performClick()
        }
    }

    private fun findClickableViewAt(view: View, x: Float, y: Float): View? {
        if (view.visibility != View.VISIBLE || view.alpha < 0.05f || view == cursorView) return null
        val loc = IntArray(2)
        view.getLocationOnScreen(loc)
        val vx = loc[0].toFloat()
        val vy = loc[1].toFloat()
        val rect = RectF(vx, vy, vx + view.width, vy + view.height)
        if (!rect.contains(x, y)) return null

        if (view is ViewGroup) {
            for (i in view.childCount - 1 downTo 0) {
                val child = view.getChildAt(i)
                val hit = findClickableViewAt(child, x, y)
                if (hit != null) return hit
            }
        }

        if (view.isClickable || view.isFocusable) {
            return view
        }
        return null
    }

    private fun scheduleHoverCheck() {
        handler.removeCallbacks(hoverDebounceRunnable)
        handler.postDelayed(hoverDebounceRunnable, 75L)
    }

    /**
     * Checks if pointer tip is hovering over an interactive HTML element or native clickable button.
     * Anchored pivot ensures hover scaling never causes cursor tip drift.
     */
    private fun checkHoverTargetInternal() {
        val targetX = cursorX + tipOffsetPx
        val targetY = cursorY + tipOffsetPx

        // 1. Check native toolbar
        val toolbar = toolbarProvider()
        if (toolbar != null && toolbar.visibility == View.VISIBLE) {
            val loc = IntArray(2)
            toolbar.getLocationOnScreen(loc)
            val toolbarTop = loc[1].toFloat()
            val toolbarBottom = toolbarTop + toolbar.height
            if (targetY in toolbarTop..toolbarBottom) {
                for (i in 0 until toolbar.childCount) {
                    val child = toolbar.getChildAt(i)
                    if (child.visibility == View.VISIBLE && (child.isClickable || child.isFocusable)) {
                        val childLoc = IntArray(2)
                        child.getLocationOnScreen(childLoc)
                        if (targetX in childLoc[0].toFloat()..(childLoc[0] + child.width).toFloat()) {
                            setHoverPointer(true)
                            return
                        }
                    }
                }
                setHoverPointer(false)
                return
            }
        }

        // 2. Check active WebView content
        val targetWebView = webViewProvider()
        if (targetWebView != null && targetWebView.visibility == View.VISIBLE) {
            if (isCheckingHover) return
            isCheckingHover = true
            val loc = IntArray(2)
            targetWebView.getLocationOnScreen(loc)
            val localX = (targetX - loc[0]).coerceAtLeast(0f)
            val localY = (targetY - loc[1]).coerceAtLeast(0f)
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
            return
        }

        // 3. Check native Home screen views
        val nativeClickable = findClickableViewAt(container, targetX, targetY)
        setHoverPointer(nativeClickable != null && nativeClickable != container)
    }

    private fun setHoverPointer(hover: Boolean) {
        if (hover != isHoveringClickable) {
            isHoveringClickable = hover
            cursorView.setImageResource(
                if (hover) R.drawable.ic_tv_pointer_hover else R.drawable.ic_tv_pointer
            )
            val targetScale = if (hover) 1.15f else 1.0f
            cursorView.animate()
                .scaleX(targetScale)
                .scaleY(targetScale)
                .setDuration(80L)
                .start()
        }
    }
}
