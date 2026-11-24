package com.nova.iptv.player

import android.app.Activity
import android.app.Application
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.Button
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.R
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TvPlaybackControlsTest {
    private lateinit var player: ExoPlayer
    private lateinit var view: PlayerView
    private val controls = TvPlaybackControls()

    @Before
    fun setUp() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        player.setMediaItem(MediaItem.fromUri("https://example.com/video.mp4"))
        view = PlayerView(activity).apply {
            setControllerAnimationEnabled(false)
            this.player = this@TvPlaybackControlsTest.player
        }
        activity.setContentView(view)
        view.hideController()
    }

    @After
    fun tearDown() {
        view.player = null
        player.release()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun themeUpdatesRecolourTheNativeProgressWithoutReplacingFocusIndicators() {
        controls.installFocusIndicators(view, cornerRadiusDp = 2)
        val foreground = view.findViewById<android.view.View>(R.id.exo_play_pause).foreground
        val timeBar = view.findViewById<DefaultTimeBar>(R.id.exo_progress)
        timeBar.layout(0, 0, 400, 48)
        timeBar.setDuration(1_000)
        timeBar.setPosition(500)
        timeBar.setBufferedPosition(750)
        for (accent in listOf(Color.RED, Color.WHITE)) {
            TvPlaybackControls.applyTheme(view, accent, Color.DKGRAY)
            val bitmap = Bitmap.createBitmap(400, 48, Bitmap.Config.ARGB_8888)
            timeBar.draw(Canvas(bitmap))
            var themedPixels = 0
            for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
                if (bitmap.getPixel(x, y) == accent) themedPixels++
            }
            assertTrue("The played segment must visibly adopt the theme", themedPixels > 100)
            bitmap.recycle()
            assertSame(foreground, view.findViewById<android.view.View>(R.id.exo_play_pause).foreground)
        }
    }

    @Test
    fun confirmReopensControlsWithoutTogglingPlayback() {
        player.playWhenReady = true
        view.hideController()
        assertTrue(controls.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER), view))
        assertTrue(controls.dispatch(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER), view))
        assertTrue(view.isControllerFullyVisible)
        assertTrue(player.playWhenReady)
        val button = view.findViewById<android.view.View>(R.id.exo_play_pause)
        assertTrue(button.hasFocus())
        assertFalse(controls.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER), view))
    }

    @Test
    fun mediaPauseAndPlayReopenControlsAndChangePlayback() {
        player.playWhenReady = true
        view.hideController()
        assertTrue(controls.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE), view))
        assertTrue(controls.dispatch(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE), view))
        assertFalse(player.playWhenReady)
        assertTrue(view.isControllerFullyVisible)

        assertTrue(controls.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY), view))
        assertTrue(player.playWhenReady)
    }

    @Test
    fun directionReopensControlsWithoutPassingFirstKeyToPlayer() {
        assertTrue(controls.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT), view))
        assertTrue(controls.dispatch(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_RIGHT), view))
        assertTrue(view.isControllerFullyVisible)
        assertFalse(controls.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT), view))
    }

    @Test
    fun visibleControlsRecoverFocusWithoutStealingItFromTopBar() {
        assertFalse(controls.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER), view, topBarFocused = true))
        view.showController()
        val other = Button(view.context)
        (view.parent as ViewGroup).addView(other)
        other.requestFocus()
        assertFalse(view.hasFocus())
        assertFalse(controls.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER), view, topBarFocused = true))
        assertTrue(controls.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER), view))
        assertTrue(view.hasFocus())
    }

    @Test
    fun nativeControlsShowIndicatorOnlyWhenFocused() {
        controls.installFocusIndicators(view)
        view.showController()
        val button = view.findViewById<android.view.View>(R.id.exo_play_pause)
        val other = Button(view.context)
        (view.parent as ViewGroup).addView(other)
        other.requestFocus()
        val foreground = button.foreground
        val indicator = if (foreground is LayerDrawable) {
            foreground.getDrawable(foreground.numberOfLayers - 1) as StateListDrawable
        } else foreground as StateListDrawable
        assertTrue(indicator.current is ColorDrawable)
        assertTrue(button.requestFocus())
        assertTrue(indicator.current is GradientDrawable)
        val bitmap = Bitmap.createBitmap(120, 48, Bitmap.Config.ARGB_8888)
        indicator.setBounds(0, 0, bitmap.width, bitmap.height)
        indicator.draw(Canvas(bitmap))
        assertEquals("Focus must not paint over the playback icon", 0, Color.alpha(bitmap.getPixel(60, 24)))
        bitmap.recycle()
        assertTrue(view.findViewById<android.view.View>(R.id.exo_progress).foreground != null)
        assertTrue(view.findViewById<android.view.View>(R.id.exo_ffwd_with_amount).foreground != null)
    }
}
