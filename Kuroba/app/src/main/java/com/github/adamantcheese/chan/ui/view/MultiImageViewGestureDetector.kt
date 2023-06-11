package com.github.adamantcheese.chan.ui.view

import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import com.github.adamantcheese.chan.core.settings.ChanSettings
import com.github.adamantcheese.chan.ui.widget.CustomScaleImageView
import com.github.adamantcheese.chan.utils.AndroidUtils.dp
import com.google.android.exoplayer2.ui.StyledPlayerView
import pl.droidsonroids.gif.GifDrawable
import pl.droidsonroids.gif.GifImageView
import kotlin.math.abs

/**
 * A gesture detector that handles swipe-up and swipe-bottom as well as single tap events for
 * ThumbnailView, BigImageView, GifImageView and PlayerView.
 * */
class MultiImageViewGestureDetector(
        private val callback: MultiImageViewGestureDetectorCallback
) : SimpleOnGestureListener() {

    private var startingImageViewportTouchSide = CustomScaleImageView.ImageViewportTouchSide(0)

    override fun onDown(e: MotionEvent?): Boolean {
        val activeView = callback.getActiveView()
        if (activeView is CustomScaleImageView) {
            startingImageViewportTouchSide = activeView.imageViewportTouchSide
        }
        return super.onDown(e)
    }

    override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
        val activeView = callback.getActiveView()
        if (activeView is StyledPlayerView && !ChanSettings.neverShowWebmControls.get()) {
            if (activeView.player != null) {
                if (activeView.isControllerFullyVisible) {
                    activeView.useController = false
                    callback.setClickHandler(true)
                } else {
                    activeView.useController = true
                    activeView.showController()
                    callback.setClickHandler(false)
                    callback.checkImmersive()
                }
                return true
            }
        }

        callback.onTap()
        return true
    }

    override fun onDoubleTap(e: MotionEvent?): Boolean {
        val activeView = callback.getActiveView()
        if (activeView is GifImageView) {
            val gifImageViewDrawable = activeView.drawable as GifDrawable
            if (gifImageViewDrawable.isPlaying) {
                gifImageViewDrawable.pause()
            } else {
                gifImageViewDrawable.start()
            }
            return true
        }

        if (activeView is StyledPlayerView) {
            callback.togglePlayState()
            return true
        }

        return false
    }

    override fun onFling(e1: MotionEvent, e2: MotionEvent, vx: Float, vy: Float): Boolean {
        if (!ChanSettings.imageViewerGestures.get()) {
            return false
        }

        val diffY = e2.y - e1.y
        val diffX = e2.x - e1.x

        val motionEventIsOk = abs(diffY) > FLING_DIFF_Y_THRESHOLD
                && abs(vy) > FLING_VELOCITY_Y_THRESHOLD
                && abs(diffX) < FLING_DIST_X_THRESHOLD

        if (!motionEventIsOk) {
            return false
        }

        if (diffY <= 0) {
            // Swiped up (swipe-to-close)
            if (onSwipedTop()) {
                return true
            }
        } else {
            // Swiped bottom (swipe-to-save file)
            return onSwipedBottom()
        }

        return false
    }

    private fun onSwipedTop(): Boolean {
        // If either any view, other than the big image view, is visible (thumbnail, gif or video) OR
        // big image is visible and the viewport is touching image bottom from start to end then use
        // close-to-swipe gesture
        val activeView = callback.getActiveView()
        if (activeView !is CustomScaleImageView
                || (startingImageViewportTouchSide.isTouchingBottom && activeView.imageViewportTouchSide.isTouchingBottom)) {
            callback.onSwipeToCloseImage()
            return true
        }

        return false
    }

    private fun onSwipedBottom(): Boolean {
        val activeView = callback.getActiveView()
        if (activeView is CustomScaleImageView) {
            val imageViewportTouchSide = activeView.imageViewportTouchSide

            // Current image is big image
            return if (activeView.scale == activeView.minScale) {
                // We are not zoomed in. This is the default state when we open an image.
                // We can use swipe-to-save image gesture.
                swipeToSaveOrClose()
                true
            } else if (activeView.scale > activeView.minScale &&
                    (startingImageViewportTouchSide.isTouchingTop && imageViewportTouchSide.isTouchingTop)) {
                // We are zoomed in and the viewport is touching the top of an image from start to end.
                // We don't want to use swipe-to-save image gesture, we want to use the
                // swipe-to-close gesture instead.
                callback.onSwipeToCloseImage()
                true
            } else {
                false
            }
        } else {
            if (activeView is ImageView && activeView !is GifImageView) {
                // Current image is thumbnail, we can't use swipe-to-save gesture
                callback.onSwipeToCloseImage()
            } else {
                // Current image is either a video or a gif, it's safe to use swipe-to-save gesture
                swipeToSaveOrClose()
            }

            return true
        }
    }

    /**
     * If already saved, then swipe-to-close gesture will be used instead of swipe-to-save
     * */
    private fun swipeToSaveOrClose() {
        if (callback.isImageAlreadySaved()) {
            // Image already saved, we can't use swipe-to-save image gesture but we
            // can use swipe-to-close image instead
            callback.onSwipeToCloseImage()
        } else {
            callback.onSwipeToSaveImage()
            callback.setImageAlreadySaved()
        }
    }

    interface MultiImageViewGestureDetectorCallback {
        fun getActiveView(): View?
        fun isImageAlreadySaved(): Boolean
        fun setImageAlreadySaved()
        fun onTap()
        fun checkImmersive()
        fun setClickHandler(set: Boolean)
        fun togglePlayState()
        fun onSwipeToCloseImage()
        fun onSwipeToSaveImage()
    }

    companion object {
        private val FLING_DIFF_Y_THRESHOLD = dp(100f)
        private val FLING_VELOCITY_Y_THRESHOLD = dp(300f)
        private val FLING_DIST_X_THRESHOLD = dp(75f)
    }
}