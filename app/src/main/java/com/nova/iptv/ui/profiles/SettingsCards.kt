package com.nova.iptv.ui.profiles

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.iptv.data.ThemeSettings
import com.nova.iptv.data.ParentalSettings
import com.nova.iptv.data.db.GroupCount
import com.nova.iptv.player.TrackLabels
import com.nova.iptv.ui.components.PinDialog
import com.nova.iptv.ui.components.NovaCard
import com.nova.iptv.ui.components.TvSettingToggle
import com.nova.iptv.ui.components.NovaButton as Button
import com.nova.iptv.ui.components.NovaTextButton as TextButton
import com.nova.iptv.ui.components.NovaOutlinedButton as OutlinedButton
import com.nova.iptv.ui.components.NovaFilledTonalButton as FilledTonalButton
import com.nova.iptv.ui.components.NovaFilterChip as FilterChip
import com.nova.iptv.core.toUserMessage
import kotlinx.coroutines.CancellationException
import com.nova.iptv.core.isTvLayout
import androidx.compose.foundation.lazy.LazyColumn
import com.nova.iptv.ui.theme.NovaColors
import com.nova.iptv.ui.theme.NovaPalettes
import com.nova.iptv.ui.theme.NovaThemeId
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val LANGUAGES = listOf("fr", "en", "ar", "es", "de", "it", "pt", "tr")

internal class ParentalActions(
    val create: () -> Unit,
    val manage: () -> Unit,
    val unlock: () -> Unit,
    val change: () -> Unit,
    val remove: () -> Unit,
)

@Composable
internal fun ParentalSettingsContent(
    vm: ProfilesViewModel,
    activeProfileId: Long?,
    content: @Composable (ParentalSettings, Boolean, ParentalActions) -> Unit,
) {
    val tv = isTvLayout()
    val parental by vm.parental.collectAsStateWithLifecycle()
    val unlocked by vm.parentalUnlocked.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<String?>(null) }
    var managing by remember { mutableStateOf(false) }
    content(parental, unlocked, ParentalActions(
        create = { dialog = "create" },
        manage = { if (unlocked) managing = true else dialog = "manage" },
        unlock = { dialog = "unlock" },
        change = { dialog = "change" },
        remove = { dialog = "remove" },
    ))

    when (dialog) {
        "create" -> PinDialog("Nouveau code PIN", "4 à 8 chiffres.", confirm = true, onDismiss = { dialog = null }) { pin ->
            val firstPin = !parental.pinSet
            vm.setPin(pin)
            dialog = null
            if (firstPin && activeProfileId != null) managing = true
            null
        }
        "manage" -> PinDialog("Contrôle parental", "Saisissez le code PIN.", onDismiss = { dialog = null }) { pin ->
            if (vm.verifyPin(pin)) { dialog = null; managing = true; null } else "Code incorrect."
        }
        "unlock" -> PinDialog("Déverrouiller les contenus", "Saisissez le code PIN.", onDismiss = { dialog = null }) { pin ->
            if (vm.verifyPin(pin)) { dialog = null; null } else "Code incorrect."
        }
        "change" -> PinDialog("Code actuel", null, onDismiss = { dialog = null }) { pin ->
            if (vm.verifyPin(pin)) { dialog = "create"; null } else "Code incorrect."
        }
        "remove" -> PinDialog("Supprimer le code", "Toutes les protections seront retirées.", onDismiss = { dialog = null }) { pin ->
            if (vm.verifyPin(pin)) { vm.removePin(); dialog = null; null } else "Code incorrect."
        }
    }

    if (managing && activeProfileId != null) {
        var groupError by remember(activeProfileId) { mutableStateOf<String?>(null) }
        val groups by produceState<List<GroupCount>?>(null, activeProfileId) {
            try {
                value = vm.allGroups(activeProfileId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                groupError = "Échec du chargement des catégories : ${e.toUserMessage()}"
            }
        }
        ParentalGroupDialog(
            groups = groups,
            locked = vm.lockedGroups(parental, activeProfileId),
            tv = tv,
            onSetLocked = { group, value -> vm.setGroupLocked(activeProfileId, group, value) },
            onDismiss = { managing = false },
            error = groupError,
        )
    }
}

@Composable
internal fun ParentalGroupDialog(
    groups: List<GroupCount>?,
    locked: Set<String>,
    tv: Boolean,
    onSetLocked: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
    error: String? = null,
) {
    val firstGroupFocus = remember { FocusRequester() }
    val groupPaneFocus = remember { FocusRequester() }
    val doneFocus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Catégories verrouillées") },
        text = {
            when {
                error != null -> Text(error, color = NovaColors.Error)
                groups == null -> CircularProgressIndicator()
                groups.isEmpty() -> Text("Ce profil n'a pas encore de catégories.")
                else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (tv) "OK : sélectionner. Droite : terminer. Retour : fermer."
                        else "Sélectionnez les catégories à masquer.",
                        color = NovaColors.TextSecondary,
                        fontSize = 12.sp,
                    )
                    LazyColumn(
                        Modifier.heightIn(max = 300.dp)
                            .then(if (tv) Modifier.focusRequester(groupPaneFocus).focusGroup() else Modifier),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        itemsIndexed(groups, key = { _, g -> g.name }) { index, g ->
                            val checked = g.name in locked
                            if (tv) {
                                TvSettingToggle(
                                    title = g.name,
                                    description = "${g.count} contenus",
                                    checked = checked,
                                    onChange = { onSetLocked(g.name, it) },
                                    modifier = Modifier.fillMaxWidth()
                                        .then(if (index == 0) Modifier.focusRequester(firstGroupFocus) else Modifier)
                                        .focusProperties { right = doneFocus },
                                )
                            } else {
                                NovaCard(
                                    onClick = { onSetLocked(g.name, !checked) },
                                    modifier = Modifier.fillMaxWidth(),
                                    focusScale = 1f,
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                        Checkbox(checked = checked, onCheckedChange = null)
                                        Text(g.name, modifier = Modifier.weight(1f), maxLines = 1)
                                        Text("${g.count}", color = NovaColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(end = 12.dp))
                                    }
                                }
                            }
                        }
                    }
                    LaunchedEffect(groups) {
                        if (tv) {
                            withFrameNanos { }
                            firstGroupFocus.requestFocus()
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.focusRequester(doneFocus)
                    .then(if (tv && !groups.isNullOrEmpty()) Modifier.focusProperties { left = groupPaneFocus } else Modifier),
            ) { Text("Terminé") }
        },
    )
    LaunchedEffect(groups, error) {
        if (tv && (groups?.isEmpty() == true || error != null)) doneFocus.requestFocus()
    }
}

@Composable
internal fun BackupSettingsContent(
    vm: ProfilesViewModel,
    content: @Composable (String?, () -> Unit, () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val message by vm.backupMessage.collectAsStateWithLifecycle()
    val pending by vm.pendingRestore.collectAsStateWithLifecycle()
    var exportCode by remember { mutableStateOf<String?>(null) }
    var codeToUse by remember { mutableStateOf("") }

    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.export(context, uri, codeToUse)
        codeToUse = ""
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.openRestore(context, uri)
    }
    val noPicker = "Aucun sélecteur de fichiers disponible sur cet appareil."

    content(message, { exportCode = "" }, {
        try {
            openFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
        } catch (e: ActivityNotFoundException) {
            vm.backupMessage.value = noPicker
        }
    })

    exportCode?.let { code ->
        AlertDialog(
            onDismissRequest = { exportCode = null },
            title = { Text("Code de sauvegarde") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Facultatif. Avec un code, les mots de passe Xtream sont inclus, chiffrés ; il faudra ce code pour restaurer. " +
                            "Sans code, ils ne sont pas sauvegardés.",
                        fontSize = 13.sp,
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = { exportCode = it },
                        label = { Text("Code (4 caractères minimum)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = code.isEmpty() || code.length >= 4,
                    onClick = {
                        codeToUse = code
                        exportCode = null
                        val date = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())
                        try {
                            createFile.launch("nova-iptv-sauvegarde-$date.json")
                        } catch (e: ActivityNotFoundException) {
                            vm.backupMessage.value = noPicker
                        }
                    },
                ) { Text(if (code.isEmpty()) "Continuer sans code" else "Continuer") }
            },
            dismissButton = { TextButton(onClick = { exportCode = null }) { Text("Annuler") } },
        )
    }

    pending?.let { restore ->
        var code by remember(restore) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { vm.pendingRestore.value = null },
            title = { Text("Code de la sauvegarde") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cette sauvegarde contient des mots de passe chiffrés. Saisissez le code choisi lors de la sauvegarde.", fontSize = 13.sp)
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Code") },
                        singleLine = true,
                        isError = restore.error != null,
                        supportingText = restore.error?.let { { Text(it) } },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                }
            },
            confirmButton = { TextButton(enabled = code.isNotEmpty(), onClick = { vm.restore(restore.json, code) }) { Text("Restaurer") } },
            dismissButton = { TextButton(onClick = { vm.pendingRestore.value = null }) { Text("Annuler") } },
        )
    }
}
