package dev.veyra.tv.browser.ui

import android.animation.TimeInterpolator
import android.graphics.Rect
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import androidx.core.view.ViewCompat

/**
 * Production-grade Android TV Motion & Focus Animation Engine.
 * Implements SmartTube / Leanback-style GPU-accelerated motion:
 * - 60fps/120fps hardware-accelerated card scaling (1.0x -> 1.08x) with elevation
 * - Zero-relayout GPU transforms via RenderNode (scaleX, scaleY, translationZ)
 * - Intelligent viewport centering on focus (auto-scrolls active rows/cards into TV sweet spot)
 * - Instant animation cancellation on rapid D-pad remote scrolling to eliminate input lag
 */
object TvMotionHelper {

    private val TV_INTERPOLATOR: TimeInterpolator = DecelerateInterpolator(1.6f)
    private const val ANIM_DURATION_MS: Long = 140L

    /**
     * Attaches production TV focus motion to a card or actionable UI element.
     * When focused, smoothly expands the view, elevates above neighbors,
     * and glides its parent scroll container into optimal TV viewing alignment.
     */
    fun attachTvFocusAnimation(
        view: View,
        scaleFactor: Float = 1.05f,
        elevationDp: Float = 6f,
        parentHorizontalScroll: HorizontalScrollView? = null,
        parentVerticalScroll: ScrollView? = null
    ) {
        val density = view.resources.displayMetrics.density
        val elevationPx = elevationDp * density

        view.isFocusable = true
        view.isFocusableInTouchMode = true

        view.setOnFocusChangeListener { v, hasFocus ->
            // Cancel any ongoing animation immediately for zero-latency D-pad response
            v.animate().cancel()

            if (hasFocus) {
                // Elevate Z-index so scaled card draws on top of adjacent neighbors
                v.z = 20f
                v.animate()
                    .scaleX(scaleFactor)
                    .scaleY(scaleFactor)
                    .translationZ(elevationPx)
                    .setDuration(ANIM_DURATION_MS)
                    .setInterpolator(TV_INTERPOLATOR)
                    .withLayer() // Promotes to hardware layer during transformation
                    .start()

                // Auto-center in horizontal shelf
                parentHorizontalScroll?.let { hScroll ->
                    smoothScrollToHorizontalCenter(hScroll, v)
                }

                // Auto-center in vertical page container
                parentVerticalScroll?.let { vScroll ->
                    smoothScrollToVerticalCenter(vScroll, v)
                }
            } else {
                v.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .translationZ(0f)
                    .setDuration(ANIM_DURATION_MS)
                    .setInterpolator(TV_INTERPOLATOR)
                    .withEndAction {
                        v.z = 0f
                    }
                    .start()
            }
        }
    }

    /**
     * Smoothly centers the focused card within its horizontal row container.
     */
    fun smoothScrollToHorizontalCenter(scroll: HorizontalScrollView, target: View) {
        val rect = Rect()
        target.getDrawingRect(rect)
        scroll.offsetDescendantRectToMyCoords(target, rect)

        val targetLeft = rect.left
        val targetWidth = target.width
        val containerWidth = scroll.width

        if (containerWidth > 0 && targetWidth > 0) {
            val scrollTo = scroll.scrollX + targetLeft - (containerWidth / 2) + (targetWidth / 2)
            scroll.smoothScrollTo(scrollTo.coerceAtLeast(0), 0)
        }
    }

    /**
     * Smoothly brings the focused shelf/card into comfortable vertical viewing range.
     */
    fun smoothScrollToVerticalCenter(scroll: ScrollView, target: View) {
        val rect = Rect()
        target.getDrawingRect(rect)
        scroll.offsetDescendantRectToMyCoords(target, rect)

        val targetTop = rect.top
        val targetHeight = target.height
        val containerHeight = scroll.height

        if (containerHeight > 0 && targetHeight > 0) {
            val scrollTo = scroll.scrollY + targetTop - (containerHeight / 2) + (targetHeight / 2)
            scroll.smoothScrollTo(0, scrollTo.coerceAtLeast(0))
        }
    }

    /**
     * Subtle focus pulse for toolbar navigation buttons.
     */
    fun attachToolbarFocusAnimation(view: View) {
        view.setOnFocusChangeListener { v, hasFocus ->
            v.animate().cancel()
            if (hasFocus) {
                v.z = 10f
                v.animate()
                    .scaleX(1.06f)
                    .scaleY(1.06f)
                    .setDuration(110L)
                    .setInterpolator(TV_INTERPOLATOR)
                    .start()
            } else {
                v.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(110L)
                    .setInterpolator(TV_INTERPOLATOR)
                    .withEndAction { v.z = 0f }
                    .start()
            }
        }
    }
}
