package com.nova.iptv

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.graphics.Color
import android.graphics.BitmapFactory
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34], application = Application::class)
class LauncherResourcesTest {
    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television-mdpi")
    fun tvLauncherLoadsOpaqueBitmapBrandingRatherThanAdaptiveOrVectorDrawables() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        val icon = info.loadIcon(context.packageManager)
        assertTrue("The TV launcher needs a directly readable bitmap icon", icon is BitmapDrawable)
        val bitmap = (icon as BitmapDrawable).bitmap
        assertEquals(512, bitmap.width)
        assertEquals(512, bitmap.height)
        assertEquals(255, Color.alpha(bitmap.getPixel(0, 0)))
        var brandedPixels = 0
        for (y in 0 until bitmap.height) for (x in 0 until bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            if (Color.blue(pixel) > 200 && Color.red(pixel) < 100) brandedPixels++
        }
        assertTrue("The bitmap must contain the NOVA mark, not a blank placeholder", brandedPixels > 1_000)
        val banner = info.loadBanner(context.packageManager)
        assertTrue("The launcher banner must also be a bitmap", banner is BitmapDrawable)
        val displayed = (banner as BitmapDrawable).bitmap
        assertEquals(16f / 9f, displayed.width.toFloat() / displayed.height, 0.01f)
        val original = BitmapFactory.decodeResource(context.resources, info.banner, BitmapFactory.Options().apply { inScaled = false })
        assertEquals(320, original.width)
        assertEquals(180, original.height)
        original.recycle()
        val roundIcon = context.getDrawable(R.mipmap.ic_launcher_round)
        assertTrue(roundIcon is BitmapDrawable)
    }

    @Test
    @Config(qualifiers = "xhdpi-notouch")
    fun nonTouchDevicesReadingTheDefaultConfigurationGetAPngIcon() {
        // Fire TV's launcher does not rely on the television qualifier: the default icon must be raster.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
        val icon = info.loadIcon(context.packageManager)
        assertTrue(icon is BitmapDrawable)
        assertEquals(96, (icon as BitmapDrawable).bitmap.width)
        assertTrue(context.getDrawable(R.mipmap.ic_launcher_round) is BitmapDrawable)
    }

    @Test
    @Config(qualifiers = "finger")
    fun mobileKeepsItsAdaptiveLauncherIcon() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(context.getDrawable(R.mipmap.ic_launcher) is AdaptiveIconDrawable)
        assertTrue(context.getDrawable(R.mipmap.ic_launcher_round) is AdaptiveIconDrawable)
    }
}
