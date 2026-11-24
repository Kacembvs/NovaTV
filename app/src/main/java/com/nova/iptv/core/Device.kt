package com.nova.iptv.core

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

fun Context.isTelevision(): Boolean {
    val uiMode = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    return uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}

/**
 * True when the TV interface is displayed: on a real TV, and on phones/tablets held in landscape.
 * Input-specific behaviour (keyboard opening, remote-only player controls) must keep using
 * [isTelevision], which describes the hardware.
 */
val LocalTvLayout = staticCompositionLocalOf<Boolean?> { null }

@Composable
@ReadOnlyComposable
fun isTvLayout(): Boolean = LocalTvLayout.current ?: resolveTvLayout()

@Composable
@ReadOnlyComposable
fun resolveTvLayout(): Boolean =
    LocalContext.current.isTelevision() ||
        LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

private val clockFormat = object : ThreadLocal<java.text.SimpleDateFormat>() {
    override fun initialValue() = java.text.SimpleDateFormat("HH:mm", java.util.Locale.FRANCE)
}

fun formatClock(ms: Long): String = clockFormat.get()!!.format(java.util.Date(ms))
