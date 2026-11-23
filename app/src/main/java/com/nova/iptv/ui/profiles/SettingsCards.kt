package com.nova.iptv.ui.profiles

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.iptv.data.ThemeSettings
import com.nova.iptv.player.TrackLabels
import com.nova.iptv.ui.components.PinDialog
import androidx.compose.foundation.lazy.LazyColumn
import com.nova.iptv.ui.theme.NovaColors
import com.nova.iptv.ui.theme.NovaPalettes
import com.nova.iptv.ui.theme.NovaThemeId
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        color = NovaColors.Card,
        border = BorderStroke(1.dp, NovaColors.Border),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
fun AppearanceCard(settings: ThemeSettings, onTheme: (NovaThemeId, Int?) -> Unit) {
    SettingsCard("Apparence") {
        Text("Le thème s'applique immédiatement.", color = NovaColors.TextSecondary, fontSize = 12.sp)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(NovaThemeId.entries) { id ->
                val palette = NovaPalettes.of(id, settings.customAccent)
                val selected = settings.theme == id
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        onClick = { onTheme(id, null) },
                        shape = RoundedCornerShape(14.dp),
                        color = palette.background,
                        border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) NovaColors.Cyan else NovaColors.Border),
                        modifier = Modifier.size(width = 92.dp, height = 64.dp),
                    ) {
                        Box(Modifier.padding(10.dp)) {
                            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(4.dp)).background(palette.card))
                            Box(Modifier.align(Alignment.BottomStart).size(18.dp).clip(CircleShape).background(palette.accent))
                            Box(Modifier.align(Alignment.BottomEnd).size(12.dp).clip(CircleShape).background(palette.accent2))
                            if (selected) Icon(Icons.Rounded.Check, null, tint = palette.textPrimary, modifier = Modifier.align(Alignment.Center))
                        }
                    }
                    Text(id.label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
        if (settings.theme == NovaThemeId.CUSTOM) {
            Text("Couleur d'accent", color = NovaColors.TextSecondary, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val current = settings.customAccent ?: NovaPalettes.CUSTOM_ACCENTS.first()
                NovaPalettes.CUSTOM_ACCENTS.forEach { argb ->
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .border(if (argb == current) 3.dp else 0.dp, NovaColors.TextPrimary, CircleShape)
                            .clickable { onTheme(NovaThemeId.CUSTOM, argb) },
                    )
                }
            }
        }
    }
}

private val LANGUAGES = listOf("fr", "en", "ar", "es", "de", "it", "pt", "tr")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LanguagesCard(vm: ProfilesViewModel) {
    val languages by vm.languages.collectAsStateWithLifecycle()
    val autoRefresh by vm.autoRefresh.collectAsStateWithLifecycle()
    SettingsCard("Langues et actualisation") {
        Text(
            "Appliquées automatiquement quand le flux propose ces pistes. Un choix fait dans le lecteur devient la nouvelle préférence.",
            color = NovaColors.TextSecondary,
            fontSize = 12.sp,
        )
        Text("Audio", fontSize = 15.sp)
        LanguageChips(languages.audio, "Automatique", vm::setAudioLanguage)
        Text("Sous-titres", fontSize = 15.sp)
        LanguageChips(languages.subtitles, "Désactivés", vm::setSubtitleLanguage)
        HorizontalDivider(color = NovaColors.Border)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Actualisation automatique", fontSize = 15.sp)
                Text(
                    "Toutes les 12 h, en arrière-plan : playlists et guide TV, même application fermée (réseau requis, batterie non faible).",
                    color = NovaColors.TextSecondary,
                    fontSize = 12.sp,
                )
            }
            Switch(checked = autoRefresh, onCheckedChange = vm::setAutoRefresh)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageChips(current: String?, noneLabel: String, onSelect: (String?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(selected = current == null, onClick = { onSelect(null) }, label = { Text(noneLabel) })
        val codes = if (current != null && LANGUAGES.none { it == current.take(2) }) LANGUAGES + current else LANGUAGES
        codes.forEach { code ->
            FilterChip(
                selected = current != null && (current == code || current.take(2) == code),
                onClick = { onSelect(code) },
                label = { Text(TrackLabels.language(code) ?: code) },
            )
        }
    }
}

@Composable
fun ParentalCard(vm: ProfilesViewModel, activeProfileId: Long?) {
    val parental by vm.parental.collectAsStateWithLifecycle()
    val unlocked by vm.parentalUnlocked.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<String?>(null) }
    var managing by remember { mutableStateOf(false) }

    SettingsCard("Contrôle parental") {
        if (!parental.pinSet) {
            Text(
                "Définissez un code PIN pour masquer des catégories (chaînes, films, séries) et protéger les réglages.",
                color = NovaColors.TextSecondary,
                fontSize = 12.sp,
            )
            Button(onClick = { dialog = "create" }) { Text("Définir un code PIN") }
        } else {
            val count = activeProfileId?.let { vm.lockedGroups(parental, it).size } ?: 0
            Text(
                "$count catégorie(s) verrouillée(s) pour ce profil. " +
                    if (unlocked) "Contenus protégés visibles jusqu'au reverrouillage." else "Contenus protégés masqués.",
                color = NovaColors.TextSecondary,
                fontSize = 12.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Protéger les réglages", fontSize = 15.sp, modifier = Modifier.weight(1f))
                Switch(checked = parental.lockSettings, onCheckedChange = vm::setLockSettings)
            }
            FlowRowCompat {
                FilledTonalButton(onClick = { if (unlocked) managing = true else dialog = "manage" }, enabled = activeProfileId != null) {
                    Text("Catégories verrouillées")
                }
                if (unlocked) OutlinedButton(onClick = vm::relock) { Text("Reverrouiller") }
                TextButton(onClick = { dialog = "change" }) { Text("Changer le code") }
                TextButton(onClick = { dialog = "remove" }) { Text("Supprimer le code", color = NovaColors.Error) }
            }
        }
    }

    when (dialog) {
        "create" -> PinDialog("Nouveau code PIN", "4 à 8 chiffres.", confirm = true, onDismiss = { dialog = null }) { pin ->
            vm.setPin(pin); dialog = null; null
        }
        "manage" -> PinDialog("Contrôle parental", "Saisissez le code PIN.", onDismiss = { dialog = null }) { pin ->
            if (vm.verifyPin(pin)) { dialog = null; managing = true; null } else "Code incorrect."
        }
        "change" -> PinDialog("Code actuel", null, onDismiss = { dialog = null }) { pin ->
            if (vm.verifyPin(pin)) { dialog = "create"; null } else "Code incorrect."
        }
        "remove" -> PinDialog("Supprimer le code", "Toutes les protections seront retirées.", onDismiss = { dialog = null }) { pin ->
            if (vm.verifyPin(pin)) { vm.removePin(); dialog = null; null } else "Code incorrect."
        }
    }

    if (managing && activeProfileId != null) {
        val groups by produceState<List<com.nova.iptv.data.db.GroupCount>?>(null, activeProfileId) { value = vm.allGroups(activeProfileId) }
        val locked = vm.lockedGroups(parental, activeProfileId)
        AlertDialog(
            onDismissRequest = { managing = false },
            title = { Text("Catégories verrouillées") },
            text = {
                val list = groups
                when {
                    list == null -> CircularProgressIndicator()
                    list.isEmpty() -> Text("Ce profil n'a pas encore de catégories.")
                    else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                        items(list, key = { it.name }) { g ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Checkbox(
                                    checked = g.name in locked,
                                    onCheckedChange = { vm.setGroupLocked(activeProfileId, g.name, it) },
                                )
                                Text(g.name, modifier = Modifier.weight(1f), maxLines = 1)
                                Text("${g.count}", color = NovaColors.TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { managing = false }) { Text("Terminé") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowCompat(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { content() }
}

@Composable
fun BackupCard(vm: ProfilesViewModel) {
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

    SettingsCard("Sauvegarde et restauration") {
        Text(
            "Profils, favoris, j'aime, reprises de lecture et réglages. Les catalogues sont retéléchargés après restauration.",
            color = NovaColors.TextSecondary,
            fontSize = 12.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { exportCode = "" }) { Text("Sauvegarder") }
            OutlinedButton(onClick = {
                try {
                    openFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                } catch (e: ActivityNotFoundException) {
                    vm.backupMessage.value = noPicker
                }
            }) { Text("Restaurer") }
        }
        message?.let {
            Text(it, color = if (it.startsWith("Échec")) NovaColors.Error else NovaColors.Cyan, fontSize = 13.sp)
        }
    }

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
