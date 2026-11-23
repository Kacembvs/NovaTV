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
