package com.nova.iptv.player

import android.view.KeyEvent
import android.view.View
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.R

@OptIn(UnstableApi::class)
internal class TvPlaybackControls {
    private var revealingKey: Int? = null

    fun dispatch(event: KeyEvent, view: PlayerView, topBarFocused: Boolean = false): Boolean {
        if (event.keyCode == revealingKey) {
            if (event.action == KeyEvent.ACTION_UP) revealingKey = null
            return true
        }
        val confirm = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
            event.keyCode == KeyEvent.KEYCODE_ENTER || event.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER
        val seekDirection = event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT || event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
        val needsFocus = !topBarFocused &&
            (!view.isControllerFullyVisible || !view.hasFocus() || view.findFocus() == view)
        if ((confirm || seekDirection) && event.action == KeyEvent.ACTION_DOWN && needsFocus) {
            revealingKey = event.keyCode
            showAndFocus(view, seekDirection)
            return true
        }
        val mediaKey = event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE ||
            event.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || event.keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE ||
            event.keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD || event.keyCode == KeyEvent.KEYCODE_MEDIA_REWIND ||
            event.keyCode == KeyEvent.KEYCODE_MEDIA_NEXT || event.keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS
        if (mediaKey && view.dispatchMediaKeyEvent(event)) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (view.isControllerFullyVisible) view.showController() else showAndFocus(view)
            }
            return true
        }
        return false
    }

    fun showAndFocus(view: PlayerView, seek: Boolean = false) {
        view.showController()
        val progress = view.findViewById<View>(R.id.exo_progress)
        if (seek && progress != null && progress.isEnabled && progress.requestFocus()) return
        if (view.findViewById<View>(R.id.exo_play_pause)?.requestFocus() != true) view.requestFocus()
    }

    fun installFocusIndicators(view: PlayerView, cornerRadiusDp: Int = 24) {
        val ids = intArrayOf(
            R.id.exo_prev, R.id.exo_rew, R.id.exo_rew_with_amount, R.id.exo_play_pause,
            R.id.exo_ffwd, R.id.exo_ffwd_with_amount, R.id.exo_next, R.id.exo_progress, R.id.exo_settings,
        )
        val density = view.resources.displayMetrics.density
        ids.forEach { id ->
            view.findViewById<View>(id)?.let { control ->
                val focus = GradientDrawable().apply {
                    setColor(Color.TRANSPARENT)
                    setStroke((3 * density).toInt().coerceAtLeast(1), Color.WHITE)
                    cornerRadius = if (id == R.id.exo_progress) 8 * density else cornerRadiusDp * density
                }
                val indicator = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), focus)
                    addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
                }
                val original = control.foreground
                control.foreground = if (original == null) indicator else LayerDrawable(arrayOf(original, indicator))
            }
        }

    }

    companion object {
        fun applyTheme(view: PlayerView, accent: Int, surface: Int) {
            (view.findViewById<View>(R.id.exo_progress) as? DefaultTimeBar)?.apply {
                setPlayedColor(accent)
                setScrubberColor(accent)
                setBufferedColor(0x99FFFFFF.toInt())
                setUnplayedColor(0x44FFFFFF)
            }
            view.findViewById<View>(R.id.exo_bottom_bar)?.setBackgroundColor(surface)
        }
    }
}
