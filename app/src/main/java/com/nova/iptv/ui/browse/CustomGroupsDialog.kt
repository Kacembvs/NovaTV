package com.nova.iptv.ui.browse

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.*
import com.nova.iptv.ui.components.NovaTextButton as TextButton
import com.nova.iptv.ui.components.NovaIconButton as IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.iptv.data.db.ContentEntity
import com.nova.iptv.data.db.CustomGroupCount
import com.nova.iptv.core.isTvLayout
import com.nova.iptv.ui.components.TvActionButton
import com.nova.iptv.ui.components.TvSettingToggle
import com.nova.iptv.ui.theme.NovaColors

/**
 * "Mes groupes": with a [channel], ticks add/remove it; without, the dialog only manages the groups
 * (create, rename, delete).
 */
@Composable
fun CustomGroupsDialog(vm: BrowseViewModel, channel: ContentEntity?, onDismiss: () -> Unit) {
    val tv = isTvLayout()
    val groups by vm.customGroups.collectAsStateWithLifecycle()
    val memberOf by remember(channel) { channel?.let { vm.groupIdsFor(it) } ?: kotlinx.coroutines.flow.flowOf(emptyList()) }
        .collectAsStateWithLifecycle(emptyList())
    var newName by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<CustomGroupCount?>(null) }
    var deleting by remember { mutableStateOf<CustomGroupCount?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (channel != null) "Ajouter « ${channel.name} » à…" else "Mes groupes de chaînes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (groups.isEmpty()) {
                    Text("Aucun groupe pour l'instant. Créez-en un ci-dessous.", color = NovaColors.TextSecondary, fontSize = 13.sp)
                }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(groups, key = { it.id }) { group ->
                        if (tv) {
                            Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (channel != null) {
                                    TvSettingToggle(group.name, "${group.count} chaînes", group.id in memberOf, { vm.setInGroup(group.id, channel, it) })
                                } else {
                                    Text("${group.name} · ${group.count} chaînes")
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TvActionButton("Renommer", { renaming = group }, Modifier.weight(1f), icon = Icons.Rounded.Edit)
                                    TvActionButton("Supprimer", { deleting = group }, Modifier.weight(1f), destructive = true, icon = Icons.Rounded.Delete)
                                }
                            }
                            return@items
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            if (channel != null) {
                                Checkbox(checked = group.id in memberOf, onCheckedChange = { vm.setInGroup(group.id, channel, it) })
                            }
                            Text(group.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${group.count}", color = NovaColors.TextSecondary, fontSize = 12.sp)
                            IconButton(onClick = { renaming = group }) { Icon(Icons.Rounded.Edit, contentDescription = "Renommer") }
                            IconButton(onClick = { deleting = group }) { Icon(Icons.Rounded.Delete, contentDescription = "Supprimer", tint = NovaColors.Error) }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { if (it.length <= 40) newName = it },
                        label = { Text("Nouveau groupe") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        enabled = newName.isNotBlank(),
                        onClick = {
                            vm.createGroup(newName, channel)
                            newName = ""
                        },
                    ) { Text("Créer") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Terminé") } },
    )

    renaming?.let { group ->
        var name by remember(group) { mutableStateOf(group.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Renommer le groupe") },
            text = { OutlinedTextField(value = name, onValueChange = { if (it.length <= 40) name = it }, singleLine = true) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { vm.renameGroup(group.id, name); renaming = null }) { Text("Renommer") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Annuler") } },
        )
    }
    deleting?.let { group ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Supprimer « ${group.name} » ?") },
            text = { Text("Les chaînes ne sont pas supprimées, seulement le groupe.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteGroup(group.id); deleting = null }) { Text("Supprimer", color = NovaColors.Error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Annuler") } },
        )
    }
}
