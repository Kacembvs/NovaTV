package com.nova.iptv.ui.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nova.iptv.ui.theme.NovaColors

/** Live connectivity state, so the app can point to downloads as soon as the connection drops. */
@Composable
fun rememberIsOnline(): Boolean {
    val context = LocalContext.current
    val manager = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager }
    fun current() = manager?.activeNetwork?.let(manager::getNetworkCapabilities)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: (manager == null)
    var online by remember { mutableStateOf(current()) }
    DisposableEffect(manager) {
        if (manager == null) return@DisposableEffect onDispose { }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online = true }
            override fun onLost(network: Network) { online = current() }
        }
        runCatching {
            manager.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), callback)
        }
        onDispose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }
    return online
}

@Composable
fun OfflineBanner(onOpenDownloads: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(12.dp))
            .background(NovaColors.Card).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Rounded.CloudOff, null, tint = NovaColors.TextSecondary)
        Column(Modifier.weight(1f)) {
            Text("Vous êtes hors ligne", fontWeight = FontWeight.SemiBold)
            Text("Vos films et épisodes téléchargés restent disponibles.", color = NovaColors.TextSecondary, fontSize = 13.sp)
        }
        NovaButton(onClick = onOpenDownloads) { Text("Mes téléchargements") }
    }
}
