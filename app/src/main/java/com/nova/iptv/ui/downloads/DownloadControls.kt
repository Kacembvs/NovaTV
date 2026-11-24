package com.nova.iptv.ui.downloads

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.iptv.core.toUserMessage
import com.nova.iptv.data.download.DownloadMeta
import com.nova.iptv.data.download.OfflineItem
import com.nova.iptv.data.download.OfflineStatus
import com.nova.iptv.ui.LocalAppContainer
import com.nova.iptv.ui.components.NovaButton
import com.nova.iptv.ui.components.NovaIconButton
import com.nova.iptv.ui.components.NovaOutlinedButton
import com.nova.iptv.ui.components.NovaTextButton
import com.nova.iptv.ui.theme.NovaColors
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** "1,4 Go", "350 Mo". */
fun formatBytes(bytes: Long): String {
    val units = listOf("o", "Ko", "Mo", "Go", "To")
    var value = bytes.coerceAtLeast(0).toDouble()
    var unit = 0
    while (value >= 1000 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    val pattern = if (unit >= 3 && value < 10) "%.1f %s" else "%.0f %s"
    return String.format(Locale.FRANCE, pattern, value, units[unit])
}

fun OfflineItem?.statusLabel(): String = when (this?.status) {
    null -> "Télécharger"
    OfflineStatus.QUEUED -> "En file d'attente"
    OfflineStatus.WAITING_FOR_WIFI -> "En attente du Wi-Fi"
    OfflineStatus.WAITING_FOR_NETWORK -> "En attente de connexion"
    OfflineStatus.DOWNLOADING -> percent?.let { "Téléchargement · ${it.toInt()} %" } ?: "Téléchargement…"
    OfflineStatus.PAUSED -> percent?.let { "En pause · ${it.toInt()} %" } ?: "En pause"
    OfflineStatus.COMPLETED -> "Téléchargé"
    OfflineStatus.FAILED -> "Échec · Réessayer"
}

private fun OfflineItem?.icon(): ImageVector = when (this?.status) {
    null -> Icons.Rounded.Download
    OfflineStatus.COMPLETED -> Icons.Rounded.DownloadDone
    OfflineStatus.FAILED -> Icons.Rounded.ErrorOutline
    OfflineStatus.PAUSED -> Icons.Rounded.Pause
    OfflineStatus.WAITING_FOR_WIFI -> Icons.Rounded.Wifi
    OfflineStatus.WAITING_FOR_NETWORK -> Icons.Rounded.CloudOff
    else -> Icons.Rounded.Download
}

/** Starts downloads, asking (once, without blocking) for permission to show progress notifications. */
@Composable
fun rememberDownloadStarter(): (Long, List<DownloadMeta>) -> Unit {
    val context = LocalContext.current
    val downloads = LocalAppContainer.current.downloads
    val scope = rememberCoroutineScope()
    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return remember(downloads) {
        { profileId, items ->
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                runCatching { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }
            scope.launch {
                try {
                    downloads.download(profileId, items)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Toast.makeText(context, "Téléchargement impossible : ${e.toUserMessage()}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

/**
 * Download control for a movie (labelled button) or an episode ([compact] icon with a progress ring).
 * A tap starts the download; once started, it opens the pause / resume / delete choices.
 */
@Composable
fun DownloadButton(meta: DownloadMeta, modifier: Modifier = Modifier, compact: Boolean = false) {
    val downloads = LocalAppContainer.current.downloads
    val all by downloads.items.collectAsStateWithLifecycle()
    val item = all.firstOrNull { it.id == meta.id }
    val start = rememberDownloadStarter()
    var choices by remember { mutableStateOf(false) }
    val onClick = {
        if (item == null || item.status == OfflineStatus.FAILED) start(meta.profileId, listOf(meta)) else choices = true
    }
    val label = item.statusLabel()
    if (compact) {
        NovaIconButton(onClick = onClick, modifier = modifier.semantics { contentDescription = "$label · ${meta.subtitle ?: meta.title}" }) {
            Box(contentAlignment = Alignment.Center) {
                if (item != null && item.active) {
                    val percent = item.percent
                    if (percent != null) CircularProgressIndicator(progress = { percent / 100f }, modifier = Modifier.size(30.dp), strokeWidth = 2.5.dp)
                    else CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 2.5.dp)
                }
                Icon(
                    item.icon(), null, Modifier.size(if (item?.active == true) 16.dp else 22.dp),
                    tint = when (item?.status) {
                        OfflineStatus.COMPLETED -> NovaColors.Cyan
                        OfflineStatus.FAILED -> NovaColors.Error
                        else -> NovaColors.TextPrimary
                    },
                )
            }
        }
    } else {
        NovaOutlinedButton(onClick = onClick, modifier = modifier) {
            if (item != null && item.active) {
                val percent = item.percent
                if (percent != null) CircularProgressIndicator(progress = { percent / 100f }, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(item.icon(), null, tint = if (item?.status == OfflineStatus.COMPLETED) NovaColors.Cyan else androidx.compose.material3.LocalContentColor.current)
            }
            Spacer(Modifier.width(6.dp))
            Text(label)
        }
    }
    if (choices && item != null) {
        DownloadChoices(item, onDismiss = { choices = false })
    }
}

@Composable
fun DownloadChoices(item: OfflineItem, onDismiss: () -> Unit, onPlay: (() -> Unit)? = null) {
    val downloads = LocalAppContainer.current.downloads
    val name = listOfNotNull(item.meta.title, item.meta.subtitle).joinToString(" · ")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.statusLabel(), color = NovaColors.TextSecondary)
                if (item.bytes > 0) Text(formatBytes(item.bytes), color = NovaColors.TextSecondary)
                if (item.status == OfflineStatus.COMPLETED && onPlay != null) {
                    NovaButton(onClick = { onDismiss(); onPlay() }, modifier = Modifier.fillMaxWidth()) { Text("Lire") }
                }
                when (item.status) {
                    OfflineStatus.PAUSED -> NovaButton(onClick = { downloads.resume(item.id); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Reprendre") }
                    OfflineStatus.COMPLETED, OfflineStatus.FAILED -> Unit
                    else -> NovaButton(onClick = { downloads.pause(item.id); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Mettre en pause") }
                }
                NovaOutlinedButton(onClick = { downloads.remove(item.id); onDismiss() }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (item.status == OfflineStatus.COMPLETED) "Supprimer le téléchargement" else "Annuler le téléchargement",
                        color = NovaColors.Error,
                    )
                }
            }
        },
        confirmButton = { NovaTextButton(onClick = onDismiss) { Text("Fermer") } },
    )
}
