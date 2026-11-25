package com.nova.iptv.ui.profiles

import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nova.iptv.data.EpgManager
import com.nova.iptv.data.SyncManager
import com.nova.iptv.data.db.ProfileEntity
import com.nova.iptv.domain.ProfileType
import com.nova.iptv.player.PlaybackRules
import com.nova.iptv.player.TrackLabels
import com.nova.iptv.ui.components.PinDialog
import com.nova.iptv.ui.components.TvActionButton
import com.nova.iptv.ui.components.TvSettingToggle
import com.nova.iptv.ui.theme.NovaColors
import com.nova.iptv.ui.theme.NovaPalettes
import com.nova.iptv.ui.theme.NovaThemeId
import com.nova.iptv.ui.theme.TvInterface
import com.nova.iptv.ui.theme.LocalTvInterface
import com.nova.iptv.ui.theme.TvNavigation

private enum class TvSettingsSection(val title: String, val subtitle: String, val icon: ImageVector) {
    PROFILES("Profils", "Vos sources IPTV · M3U / M3U8 et Xtream Codes", Icons.Rounded.People),
    PLAYBACK("Lecture", "Une expérience de lecture à votre rythme", Icons.Rounded.PlayArrow),
    DOWNLOADS("Téléchargements", "Lecture hors ligne · qualité, Wi-Fi et stockage", Icons.Rounded.DownloadForOffline),
    LANGUAGES("Langues", "Vos préférences audio et sous-titres", Icons.Rounded.Language),
    APPEARANCE("Apparence", "Personnalisez votre espace de divertissement", Icons.Rounded.ColorLens),
    PARENTAL("Contrôle parental", "Code PIN et catégories protégées", Icons.Rounded.Lock),
    BACKUP("Sauvegarde", "Conservez vos profils, favoris et réglages", Icons.Rounded.Backup),
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun TvSettingsScreen(
    vm: ProfilesViewModel,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    compact: Boolean = false,
    onOpenDownloads: () -> Unit = {},
) {
    val blocked by vm.settingsBlocked.collectAsStateWithLifecycle()
    var sectionName by rememberSaveable { mutableStateOf(TvSettingsSection.PROFILES.name) }
    val section = TvSettingsSection.valueOf(sectionName)
    val sectionFocus = remember { TvSettingsSection.entries.map { FocusRequester() } }
    val firstAction = remember { FocusRequester() }
    val contentState = rememberLazyListState()
    var enterRequest by remember { mutableIntStateOf(0) }
    var asking by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<ProfileEntity?>(null) }

    if (blocked) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("Réglages protégés", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            TvSettingsDescription("Le contrôle parental protège les réglages. Saisissez le code PIN pour y accéder.")
            TvActionButton("Saisir le code PIN", { asking = true }, Modifier.focusRequester(firstAction), icon = Icons.Rounded.Lock)
        }
        LaunchedEffect(Unit) { if (!compact) firstAction.requestFocus() }
        if (asking) {
            PinDialog("Réglages protégés", null, onDismiss = { asking = false }) { pin ->
                if (vm.verifyPin(pin)) { asking = false; null } else "Code incorrect."
            }
        }
        return
    }

    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val activeId by vm.activeId.collectAsStateWithLifecycle()
    val sync by vm.syncStates.collectAsStateWithLifecycle()
    val epg by vm.epgStates.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    val languages by vm.languages.collectAsStateWithLifecycle()
    val autoRefresh by vm.autoRefresh.collectAsStateWithLifecycle()
    val theme by vm.theme.collectAsStateWithLifecycle()
    val downloadSettings by vm.downloadSettings.collectAsStateWithLifecycle()
    val offline by vm.offlineItems.collectAsStateWithLifecycle()
    var confirmClearDownloads by remember { mutableStateOf(false) }
    val sideFocus = sectionFocus[section.ordinal]
    val style = LocalTvInterface.current
    val horizontalSections = compact || style.navigation == TvNavigation.TOP
    fun firstModifier(requester: FocusRequester) = Modifier.fillMaxWidth().focusRequester(requester)
        .focusProperties { if (horizontalSections) up = sideFocus }
    val sectionButtons: @Composable () -> Unit = {
        TvSettingsSection.entries.forEachIndexed { index, entry ->
            TvActionButton(
                entry.title,
                onClick = { sectionName = entry.name; enterRequest++ },
                selected = section == entry,
                icon = entry.icon,
                modifier = (if (compact) Modifier else if (horizontalSections) Modifier.width(190.dp) else Modifier.fillMaxWidth())
                    .focusRequester(sectionFocus[index])
                    .focusProperties {
                        if (horizontalSections) {
                            left = if (index == 0) FocusRequester.Default else sectionFocus[index - 1]
                            right = if (index == sectionFocus.lastIndex) FocusRequester.Cancel else sectionFocus[index + 1]
                            down = firstAction
                        } else {
                            up = if (index == 0) FocusRequester.Default else sectionFocus[index - 1]
                            down = if (index == sectionFocus.lastIndex) FocusRequester.Cancel else sectionFocus[index + 1]
                            right = firstAction
                        }
                    }
                    .onFocusChanged { if (it.isFocused) sectionName = entry.name },
            )
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = if (compact) 16.dp else 28.dp, vertical = if (compact) 12.dp else 18.dp)) {
        Text("Réglages", fontSize = if (compact) 26.sp else 30.sp, fontWeight = FontWeight.Bold)
        Text(
            "VOTRE ESPACE, VOS PRÉFÉRENCES",
            fontSize = 11.sp,
            letterSpacing = 2.sp,
            color = NovaColors.TextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = if (compact) 12.dp else 20.dp),
        )
        Box(Modifier.weight(1f)) {
            val sectionsModifier = Modifier
                    .onPreviewKeyEvent { event ->
                        val enterKey = if (horizontalSections) Key.DirectionDown else Key.DirectionRight
                        if (event.key == enterKey || event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter) {
                            if (event.type == KeyEventType.KeyDown) enterRequest++
                            true
                        } else false
                    }
                    .focusProperties { enter = { sideFocus } }
                    .focusGroup()
            if (horizontalSections) {
                Row(
                    sectionsModifier.fillMaxWidth().height(68.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) { sectionButtons() }
            } else Column(
                sectionsModifier.width(230.dp).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sectionButtons()
                Spacer(Modifier.weight(1f))
                Text("↑ ↓ Choisir\n→ Ouvrir · ← Sections", color = NovaColors.TextSecondary, fontSize = 12.sp)
            }
            Surface(
                shape = RoundedCornerShape(style.cardRadius.dp),
                color = NovaColors.Card,
                border = BorderStroke(1.dp, NovaColors.Border),
                modifier = Modifier.fillMaxSize()
                    .padding(start = if (horizontalSections) 0.dp else 254.dp, top = if (horizontalSections) 80.dp else 0.dp)
                    .focusProperties {
                        enter = { firstAction }
                        if (!horizontalSections) left = sideFocus
                        right = FocusRequester.Cancel
                    }
                    .focusGroup(),
            ) {
                Column(Modifier.padding(if (compact) 14.dp else 22.dp)) {
                    Text(section.title, fontSize = if (compact) 21.sp else 25.sp, fontWeight = FontWeight.Bold)
                    TvSettingsDescription(section.subtitle, Modifier.padding(top = 6.dp, bottom = 16.dp))
                    if (section == TvSettingsSection.PARENTAL) {
                        Text(
                            "Protection incomplète : le masquage ne couvre pas encore l'accueil, le guide TV et certaines reprises de lecture.",
                            color = NovaColors.Error,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 14.dp),
                        )
                    }
                    key(section) {
                        when (section) {
                            TvSettingsSection.PROFILES -> TvSettingsList(contentState) {
                                item {
                                    TvActionButton("Ajouter un profil", onAdd, firstModifier(firstAction), icon = Icons.Rounded.Add)
                                }
                                if (profiles == null) item { CircularProgressIndicator() }
                                else if (profiles!!.isEmpty()) item {
                                    TvSettingsDescription("Aucun profil. Ajoutez votre première source IPTV pour commencer.")
                                }
                                profiles.orEmpty().forEach { profile ->
                                    val syncState = sync[profile.id]
                                    val epgState = epg[profile.id]
                                    val active = profile.id == activeId
                                    item("profile-${profile.id}") { TvProfileSummary(profile, active, syncState, epgState) }
                                    item("activate-${profile.id}") {
                                        TvActionButton(
                                            if (active) "Profil actif · ${profile.name}" else "Utiliser · ${profile.name}",
                                            { vm.activate(profile.id) }, Modifier.fillMaxWidth(), selected = active,
                                        )
                                    }
                                    item("edit-${profile.id}") { TvActionButton("Modifier · ${profile.name}", { onEdit(profile.id) }, Modifier.fillMaxWidth()) }
                                    item("refresh-${profile.id}") {
                                        TvActionButton("Actualiser le catalogue", { vm.refresh(profile.id) }, Modifier.fillMaxWidth(), enabled = syncState !is SyncManager.State.Running)
                                    }
                                    item("epg-${profile.id}") {
                                        TvActionButton("Actualiser le guide TV", { vm.refreshGuide(profile.id) }, Modifier.fillMaxWidth(), enabled = epgState !is EpgManager.State.Running)
                                    }
                                    item("delete-${profile.id}") {
                                        TvActionButton("Supprimer · ${profile.name}", { toDelete = profile }, Modifier.fillMaxWidth(), destructive = true)
                                    }
                                }
                            }
                            TvSettingsSection.PLAYBACK -> TvSettingsList(contentState) {
                                item {
                                    TvSettingToggle("Épisode suivant automatique", "Propose l'épisode suivant avant la fin.", playback.autoplayNext, vm::setAutoplay, firstModifier(firstAction))
                                }
                                if (playback.autoplayNext) {
                                    item { TvSettingsDescription("Compte à rebours") }
                                    PlaybackRules.COUNTDOWN_CHOICES.forEach { seconds ->
                                        item { TvActionButton("$seconds secondes", { vm.setCountdown(seconds) }, Modifier.fillMaxWidth(), selected = playback.countdownSeconds == seconds) }
                                    }
                                }
                                item { TvSettingToggle("Gestes luminosité / volume", "Sur écran tactile : glisser et double appui ±10 s.", playback.gesturesEnabled, vm::setGestures, Modifier.fillMaxWidth()) }
                                item { TvSettingToggle("Lecture en portrait avec navigation", "Sur mobile tenu à la verticale : vidéo en haut, groupes, chaînes, films ou épisodes en dessous. Tourner le téléphone pour le plein écran.", playback.portraitBrowse, vm::setPortraitBrowse, Modifier.fillMaxWidth()) }
                                item { TvSettingToggle("Rotation libre du lecteur", "Sur mobile : désactivé pour rester en paysage.", playback.freeRotation, vm::setFreeRotation, Modifier.fillMaxWidth()) }
                                item { TvSettingToggle("Picture-in-Picture", "Sur mobile : continuer dans une petite fenêtre.", playback.pictureInPicture, vm::setPictureInPicture, Modifier.fillMaxWidth()) }
                                item { TvSettingsDescription("Minuteur de sommeil : touche Menu dans le lecteur. Il est prioritaire sur la lecture automatique.") }
                            }
                            TvSettingsSection.DOWNLOADS -> TvSettingsList(contentState) {
                                item { TvActionButton("Ouvrir mes téléchargements", onOpenDownloads, firstModifier(firstAction), icon = Icons.Rounded.DownloadForOffline) }
                                item {
                                    val storage = remember(offline) { vm.offlineStorage() }
                                    TvSettingsDescription(
                                        "${offline.size} téléchargement(s) · NOVA utilise ${com.nova.iptv.ui.downloads.formatBytes(storage.usedBytes)} · " +
                                            "${com.nova.iptv.ui.downloads.formatBytes(storage.freeBytes)} libres sur l'appareil",
                                    )
                                }
                                item { TvSettingToggle("Wi-Fi uniquement", "Les téléchargements attendent une connexion Wi-Fi pour préserver vos données mobiles.", downloadSettings.wifiOnly, vm::setDownloadWifiOnly, Modifier.fillMaxWidth()) }
                                item { TvSettingToggle("Téléchargements intelligents", "Un épisode téléchargé regardé jusqu'au bout est supprimé et l'épisode suivant est téléchargé.", downloadSettings.smartDownloads, vm::setSmartDownloads, Modifier.fillMaxWidth()) }
                                item { TvSettingsDescription("Qualité de téléchargement") }
                                com.nova.iptv.data.DownloadQuality.entries.forEach { quality ->
                                    item {
                                        TvActionButton("${quality.label}\n${quality.description}", { vm.setDownloadQuality(quality) }, Modifier.fillMaxWidth(), selected = downloadSettings.quality == quality)
                                    }
                                }
                                item { TvSettingsDescription("La qualité s'applique aux flux adaptatifs (HLS). Les films fournis en un seul fichier sont enregistrés tels quels. Jusqu'à 2 téléchargements simultanés, repris automatiquement après une coupure.") }
                                if (offline.isNotEmpty()) item {
                                    TvActionButton("Supprimer tous les téléchargements", { confirmClearDownloads = true }, Modifier.fillMaxWidth(), destructive = true)
                                }
                            }
                            TvSettingsSection.LANGUAGES -> TvSettingsList(contentState) {
                                item { TvSettingToggle("Actualisation automatique", "Playlists et guide TV toutes les 12 h en arrière-plan, réseau requis et batterie non faible.", autoRefresh, vm::setAutoRefresh, firstModifier(firstAction)) }
                                item { TvSettingsDescription("Les pistes disponibles sont choisies automatiquement. Un choix dans le lecteur devient la nouvelle préférence.") }
                                tvLanguageItems("Audio", languages.audio, "Automatique", vm::setAudioLanguage)
                                tvLanguageItems("Sous-titres", languages.subtitles, "Désactivés", vm::setSubtitleLanguage)
                            }
                            TvSettingsSection.APPEARANCE -> TvSettingsList(contentState) {
                                item { TvSettingsDescription(if (compact) "Interface TV et paysage · disposition, navigation et couleurs" else "Interface TV · disposition, navigation et couleurs") }
                                TvInterface.entries.forEachIndexed { index, style ->
                                    item {
                                        TvActionButton(
                                            "${style.label}\n${style.description}",
                                            { vm.applyTvTheme(style) },
                                            if (index == 0) firstModifier(firstAction) else Modifier.fillMaxWidth(),
                                            selected = theme.tvInterface == style,
                                        )
                                    }
                                }
                                item { TvSettingsDescription("Personnaliser les couleurs · changer de thème TV réapplique ses couleurs") }
                                NovaThemeId.entries.forEach { id ->
                                    item {
                                        TvActionButton(id.label, { vm.setTheme(id, null) }, Modifier.fillMaxWidth(), selected = theme.theme == id)
                                    }
                                }
                                if (theme.theme == NovaThemeId.CUSTOM) {
                                    item { TvSettingsDescription("Couleur d'accent") }
                                    NovaPalettes.CUSTOM_ACCENTS.forEachIndexed { index, argb ->
                                        item {
                                            TvActionButton("Accent ${index + 1} · #${String.format("%06X", argb and 0xffffff)}", { vm.setTheme(NovaThemeId.CUSTOM, argb) }, Modifier.fillMaxWidth(), selected = (theme.customAccent ?: NovaPalettes.CUSTOM_ACCENTS.first()) == argb)
                                        }
                                    }
                                }
                            }
                            TvSettingsSection.PARENTAL -> ParentalSettingsContent(vm, activeId) { parental, unlocked, actions ->
                                TvSettingsList(contentState) {
                                    if (!parental.pinSet) {
                                        item { TvActionButton("Définir un code PIN", actions.create, firstModifier(firstAction), icon = Icons.Rounded.Lock) }
                                        item { TvSettingsDescription("Un code PIN permet de masquer des catégories et de protéger les réglages.") }
                                    } else {
                                        item {
                                            TvSettingToggle("Protéger les réglages", "Un code PIN sera demandé pour accéder à cet écran.", parental.lockSettings, vm::setLockSettings, firstModifier(firstAction))
                                        }
                                        item {
                                            val count = activeId?.let { vm.lockedGroups(parental, it).size } ?: 0
                                            TvSettingsDescription("$count catégorie(s) verrouillée(s) pour le profil actif. " + if (unlocked) "Contenus protégés visibles jusqu'au reverrouillage." else "Contenus protégés masqués.")
                                        }
                                        item { TvActionButton("Catégories verrouillées", actions.manage, Modifier.fillMaxWidth(), enabled = activeId != null) }
                                        if (activeId == null) item { TvSettingsDescription("Activez un profil pour gérer ses catégories.") }
                                        item {
                                            TvActionButton(if (unlocked) "Reverrouiller les contenus" else "Déverrouiller les contenus", if (unlocked) vm::relock else actions.unlock, Modifier.fillMaxWidth())
                                        }
                                        item { TvActionButton("Changer le code PIN", actions.change, Modifier.fillMaxWidth()) }
                                        item { TvActionButton("Supprimer le code PIN", actions.remove, Modifier.fillMaxWidth(), destructive = true) }
                                    }
                                }
                            }
                            TvSettingsSection.BACKUP -> BackupSettingsContent(vm) { message, onExport, onRestore ->
                                TvSettingsList(contentState) {
                                    item { TvActionButton("Sauvegarder", onExport, firstModifier(firstAction), icon = Icons.Rounded.Backup) }
                                    item { TvActionButton("Restaurer une sauvegarde", onRestore, Modifier.fillMaxWidth()) }
                                    item { TvSettingsDescription("Profils, favoris, j'aime, reprises et réglages. Les catalogues sont retéléchargés après restauration. Un code facultatif chiffre les mots de passe Xtream.") }
                                    message?.let { result ->
                                        item { Text(result, color = if (result.startsWith("Échec") || result.startsWith("Aucun")) NovaColors.Error else NovaColors.Cyan, fontSize = 15.sp) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(blocked) {
        contentState.scrollToItem(0)
        if (compact) return@LaunchedEffect
        withFrameNanos { }
        firstAction.requestFocus()
    }
    LaunchedEffect(section) { contentState.scrollToItem(0) }
    LaunchedEffect(enterRequest) {
        if (enterRequest > 0 && !compact) {
            contentState.scrollToItem(0)
            withFrameNanos { }
            firstAction.requestFocus()
        }
    }
    if (confirmClearDownloads) {
        AlertDialog(
            onDismissRequest = { confirmClearDownloads = false },
            title = { Text("Supprimer tous les téléchargements ?") },
            text = { Text("Les vidéos enregistrées sur cet appareil seront effacées. Vous pourrez les télécharger à nouveau.") },
            confirmButton = { TvActionButton("Supprimer", { vm.removeAllDownloads(); confirmClearDownloads = false }, destructive = true) },
            dismissButton = { TvActionButton("Annuler", { confirmClearDownloads = false }) },
        )
    }
    toDelete?.let { profile ->
        val cancelFocus = remember { FocusRequester() }
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Supprimer « ${profile.name} » ?") },
            text = { Text("Les chaînes, favoris et l'historique de ce profil seront supprimés de l'appareil.") },
            confirmButton = { TvActionButton("Supprimer", { vm.delete(profile.id); toDelete = null }, destructive = true) },
            dismissButton = { TvActionButton("Annuler", { toDelete = null }, Modifier.focusRequester(cancelFocus)) },
        )
        LaunchedEffect(profile.id) { if (!compact) cancelFocus.requestFocus() }
    }
}

@Composable
private fun TvSettingsList(state: LazyListState, content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = state,
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun TvSettingsDescription(text: String, modifier: Modifier = Modifier) {
    Text(text, color = NovaColors.TextSecondary, fontSize = 14.sp, lineHeight = 21.sp, modifier = modifier)
}

private fun LazyListScope.tvLanguageItems(title: String, current: String?, noneLabel: String, onSelect: (String?) -> Unit) {
    item { Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp)) }
    item { TvActionButton(noneLabel, { onSelect(null) }, Modifier.fillMaxWidth(), selected = current == null) }
    val codes = if (current != null && LANGUAGES.none { it == current.take(2) }) LANGUAGES + current else LANGUAGES
    codes.forEach { code ->
        item {
            TvActionButton(TrackLabels.language(code) ?: code, { onSelect(code) }, Modifier.fillMaxWidth(), selected = current != null && (current == code || current.take(2) == code))
        }
    }
}

@Composable
private fun TvProfileSummary(profile: ProfileEntity, active: Boolean, sync: SyncManager.State?, epg: EpgManager.State?) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(profile.name, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        TvSettingsDescription("${if (profile.type == ProfileType.M3U) "M3U" else "Xtream"} · ${if (active) "Profil actif" else "Profil disponible"}")
        val status = when {
            sync is SyncManager.State.Running -> "Synchronisation… ${sync.loaded} contenus"
            sync is SyncManager.State.Failed -> "Erreur : ${sync.message}"
            profile.lastSyncError != null -> "Erreur : ${profile.lastSyncError}"
            profile.lastSyncAt != null -> "${profile.contentCount} contenus · actualisé ${DateUtils.getRelativeTimeSpanString(profile.lastSyncAt)}"
            else -> "Jamais synchronisé"
        }
        Text(status, fontSize = 14.sp, color = if (sync is SyncManager.State.Failed || profile.lastSyncError != null) NovaColors.Error else NovaColors.TextSecondary)
        val guide = when {
            epg is EpgManager.State.Running -> "Guide TV : chargement… ${epg.programmes} programmes"
            epg is EpgManager.State.Failed -> "Guide TV : ${epg.message}"
            profile.epgError != null -> "Guide TV : ${profile.epgError}"
            profile.epgSyncedAt != null -> "Guide TV actualisé ${DateUtils.getRelativeTimeSpanString(profile.epgSyncedAt)}"
            else -> "Guide TV : pas encore chargé"
        }
        Text(guide, fontSize = 14.sp, color = if (epg is EpgManager.State.Failed || profile.epgError != null) NovaColors.Error else NovaColors.TextSecondary)
    }
}
