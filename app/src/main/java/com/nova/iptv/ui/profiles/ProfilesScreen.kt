package com.nova.iptv.ui.profiles

import android.text.format.DateUtils
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nova.iptv.AppContainer
import com.nova.iptv.data.EpgManager
import com.nova.iptv.data.PlaybackSettings
import com.nova.iptv.data.SyncManager
import com.nova.iptv.data.db.ProfileEntity
import com.nova.iptv.domain.ProfileType
import com.nova.iptv.player.PlaybackRules
import com.nova.iptv.ui.components.EmptyState
import com.nova.iptv.ui.components.ScreenHeader
import com.nova.iptv.ui.novaViewModel
import com.nova.iptv.ui.theme.NovaColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class ProfilesViewModel(private val c: AppContainer) : ViewModel() {
    val profiles: StateFlow<List<ProfileEntity>?> = c.profiles.profiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val activeId: StateFlow<Long?> = c.profiles.activeProfile.map { it?.id }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val syncStates = c.sync.states
    val epgStates = c.epg.states
    val playback: StateFlow<PlaybackSettings> = c.settings.playback
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaybackSettings())

    fun setAutoplay(enabled: Boolean) = viewModelScope.launch { c.settings.setAutoplay(enabled) }
    fun setCountdown(seconds: Int) = viewModelScope.launch { c.settings.setCountdownSeconds(seconds) }
    fun setGestures(enabled: Boolean) = viewModelScope.launch { c.settings.setGesturesEnabled(enabled) }
    fun setFreeRotation(enabled: Boolean) = viewModelScope.launch { c.settings.setFreeRotation(enabled) }
    fun activate(id: Long) = viewModelScope.launch { c.profiles.setActive(id) }
    fun refresh(id: Long) = c.sync.sync(id)
    fun refreshGuide(id: Long) = c.epg.refresh(id)
    fun delete(id: Long) {
        c.sync.cancel(id)
        c.epg.cancel(id)
        viewModelScope.launch { c.profiles.delete(id) }
    }
}

@Composable
fun ProfilesScreen(onAdd: () -> Unit, onEdit: (Long) -> Unit) {
    val vm = novaViewModel { ProfilesViewModel(it) }
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val activeId by vm.activeId.collectAsStateWithLifecycle()
    val syncStates by vm.syncStates.collectAsStateWithLifecycle()
    val epgStates by vm.epgStates.collectAsStateWithLifecycle()
    val playback by vm.playback.collectAsStateWithLifecycle()
    var toDelete by remember { mutableStateOf<ProfileEntity?>(null) }

    val list = profiles ?: return
    LazyColumn(
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { ScreenHeader("Réglages") }
        item { PlaybackSettingsCard(playback, vm::setAutoplay, vm::setCountdown, vm::setGestures, vm::setFreeRotation) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(end = 20.dp)) {
                ScreenHeader("Profils IPTV", subtitle = "M3U / M3U8 et Xtream Codes", modifier = Modifier.weight(1f))
                Button(onClick = onAdd) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text("Ajouter")
                }
            }
        }
        if (list.isEmpty()) {
            item {
                EmptyState(
                    "Aucun profil",
                    "Ajoutez votre première source IPTV pour commencer.",
                    actionLabel = "Ajouter un profil",
                    onAction = onAdd,
                )
            }
        }
        items(list, key = { it.id }) { profile ->
            ProfileCard(
                profile = profile,
                active = profile.id == activeId,
                sync = syncStates[profile.id],
                epg = epgStates[profile.id],
                onActivate = { vm.activate(profile.id) },
                onRefresh = { vm.refresh(profile.id) },
                onRefreshGuide = { vm.refreshGuide(profile.id) },
                onEdit = { onEdit(profile.id) },
                onDelete = { toDelete = profile },
            )
        }
    }

    toDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Supprimer « ${profile.name} » ?") },
            text = { Text("Les chaînes, favoris et l'historique de ce profil seront supprimés de l'appareil.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(profile.id); toDelete = null }) { Text("Supprimer", color = NovaColors.Error) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annuler") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackSettingsCard(
    settings: PlaybackSettings,
    onAutoplay: (Boolean) -> Unit,
    onCountdown: (Int) -> Unit,
    onGestures: (Boolean) -> Unit,
    onFreeRotation: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        color = NovaColors.Card,
        border = androidx.compose.foundation.BorderStroke(1.dp, NovaColors.Border),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Lecture", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Épisode suivant automatique", fontSize = 15.sp)
                    Text(
                        "Propose l'épisode suivant avant la fin avec un compte à rebours.",
                        color = NovaColors.TextSecondary,
                        fontSize = 12.sp,
                    )
                }
                Switch(checked = settings.autoplayNext, onCheckedChange = onAutoplay)
            }
            if (settings.autoplayNext) {
                Text("Compte à rebours", color = NovaColors.TextSecondary, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlaybackRules.COUNTDOWN_CHOICES.forEach { seconds ->
                        FilterChip(
                            selected = settings.countdownSeconds == seconds,
                            onClick = { onCountdown(seconds) },
                            label = { Text("$seconds s") },
                        )
                    }
                }
            }
            SettingSwitch(
                "Gestes luminosité / volume",
                "Glisser à gauche : luminosité, à droite : volume. Double appui : ±10 s.",
                settings.gesturesEnabled,
                onGestures,
            )
            SettingSwitch(
                "Rotation libre du lecteur",
                "Désactivé : lecture en paysage uniquement.",
                settings.freeRotation,
                onFreeRotation,
            )
            Text(
                "Minuteur de sommeil : icône lune dans le lecteur (touche Menu sur la télécommande). " +
                    "Il est prioritaire sur la lecture automatique.",
                color = NovaColors.TextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun SettingSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp)
            Text(description, color = NovaColors.TextSecondary, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ProfileCard(
    profile: ProfileEntity,
    active: Boolean,
    sync: SyncManager.State?,
    epg: EpgManager.State?,
    onActivate: () -> Unit,
    onRefresh: () -> Unit,
    onRefreshGuide: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        color = NovaColors.Card,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) NovaColors.Cyan else NovaColors.Border),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(profile.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f, fill = false))
                Text(
                    if (profile.type == ProfileType.M3U) "M3U" else "Xtream",
                    fontSize = 11.sp,
                    color = NovaColors.Violet,
                    fontWeight = FontWeight.Bold,
                )
                if (active) Icon(Icons.Rounded.CheckCircle, contentDescription = "Profil actif", tint = NovaColors.Cyan, modifier = Modifier.size(18.dp))
            }
            val status = when {
                sync is SyncManager.State.Running -> "Synchronisation… ${sync.loaded} contenus"
                sync is SyncManager.State.Failed -> "Erreur : ${sync.message}"
                profile.lastSyncError != null -> "Erreur : ${profile.lastSyncError}"
                profile.lastSyncAt != null -> "${profile.contentCount} contenus · actualisé " +
                    DateUtils.getRelativeTimeSpanString(profile.lastSyncAt).toString().lowercase()
                else -> "Jamais synchronisé"
            }
            val isError = sync is SyncManager.State.Failed || (sync == null && profile.lastSyncError != null)
            Text(status, fontSize = 13.sp, color = if (isError) NovaColors.Error else NovaColors.TextSecondary)
            val guide = when {
                epg is EpgManager.State.Running -> "Guide TV : chargement… ${epg.programmes} programmes"
                epg is EpgManager.State.Failed -> "Guide TV : ${epg.message}"
                profile.epgError != null -> "Guide TV : ${profile.epgError}"
                profile.epgSyncedAt != null -> "Guide TV actualisé " +
                    DateUtils.getRelativeTimeSpanString(profile.epgSyncedAt).toString().lowercase()
                else -> "Guide TV : pas encore chargé"
            }
            val guideError = epg is EpgManager.State.Failed || (epg == null && profile.epgError != null)
            Text(guide, fontSize = 13.sp, color = if (guideError) NovaColors.Error else NovaColors.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (!active) FilledTonalButton(onClick = onActivate) { Text("Utiliser") }
                TextButton(onClick = onRefresh, enabled = sync !is SyncManager.State.Running) { Text("Actualiser") }
                TextButton(onClick = onRefreshGuide, enabled = epg !is EpgManager.State.Running) { Text("Guide TV") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit) { Text("Modifier") }
                TextButton(onClick = onDelete) { Text("Supprimer", color = NovaColors.Error) }
            }
        }
    }
}

data class ProfileForm(
    val type: ProfileType = ProfileType.M3U,
    val name: String = "",
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val epgUrl: String = "",
    val hasStoredPassword: Boolean = false,
    val error: String? = null,
    val saving: Boolean = false,
)

class ProfileEditViewModel(private val c: AppContainer, private val profileId: Long?) : ViewModel() {
    var form by mutableStateOf(ProfileForm())
        private set
    var loaded by mutableStateOf(profileId == null)
        private set

    init {
        if (profileId != null) {
            viewModelScope.launch {
                c.profiles.get(profileId)?.let { p ->
                    form = ProfileForm(
                        type = p.type,
                        name = p.name,
                        url = p.url,
                        username = p.username.orEmpty(),
                        epgUrl = p.customEpgUrl.orEmpty(),
                        hasStoredPassword = p.encryptedPassword != null,
                    )
                }
                loaded = true
            }
        }
    }

    fun update(transform: ProfileForm.() -> ProfileForm) {
        form = form.transform().copy(error = null)
    }

    fun save(onSaved: () -> Unit) {
        val f = form
        val url = f.url.trim()
        val error = when {
            url.isEmpty() -> if (f.type == ProfileType.M3U) "Indiquez l'adresse de la playlist." else "Indiquez l'adresse du serveur."
            f.type == ProfileType.M3U && url.toHttpUrlOrNull() == null -> "Adresse invalide : elle doit commencer par http:// ou https://"
            f.type == ProfileType.XTREAM && f.username.isBlank() -> "Indiquez le nom d'utilisateur."
            f.type == ProfileType.XTREAM && f.password.isEmpty() && !f.hasStoredPassword -> "Indiquez le mot de passe."
            f.epgUrl.isNotBlank() && f.epgUrl.trim().toHttpUrlOrNull() == null -> "Adresse du guide TV invalide."
            else -> null
        }
        if (error != null) {
            form = f.copy(error = error)
            return
        }
        form = f.copy(saving = true)
        viewModelScope.launch {
            val name = f.name.ifBlank {
                ("http://" + url.removePrefix("http://").removePrefix("https://")).toHttpUrlOrNull()?.host ?: "Mon profil"
            }
            val id = c.profiles.save(profileId, f.type, name, url, f.username, f.password, f.epgUrl)
            c.sync.sync(id)
            onSaved()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(profileId: Long?, onDone: () -> Unit) {
    val vm = novaViewModel(key = "edit-$profileId") { ProfileEditViewModel(it, profileId) }
    if (!vm.loaded) return
    val f = vm.form

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour") }
                Text(if (profileId == null) "Nouveau profil" else "Modifier le profil", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = f.type == ProfileType.M3U,
                    onClick = { vm.update { copy(type = ProfileType.M3U) } },
                    label = { Text("Playlist M3U / M3U8") },
                )
                FilterChip(
                    selected = f.type == ProfileType.XTREAM,
                    onClick = { vm.update { copy(type = ProfileType.XTREAM) } },
                    label = { Text("Xtream Codes") },
                )
            }
        }
        item {
            OutlinedTextField(
                value = f.name,
                onValueChange = { v -> vm.update { copy(name = v) } },
                label = { Text("Nom du profil (facultatif)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = f.url,
                onValueChange = { v -> vm.update { copy(url = v) } },
                label = { Text(if (f.type == ProfileType.M3U) "Adresse de la playlist (URL)" else "Adresse du serveur") },
                placeholder = { Text(if (f.type == ProfileType.M3U) "https://exemple.com/playlist.m3u" else "http://serveur.com:8080") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (f.type == ProfileType.XTREAM) {
            item {
                OutlinedTextField(
                    value = f.username,
                    onValueChange = { v -> vm.update { copy(username = v) } },
                    label = { Text("Nom d'utilisateur") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                OutlinedTextField(
                    value = f.password,
                    onValueChange = { v -> vm.update { copy(password = v) } },
                    label = { Text(if (f.hasStoredPassword) "Mot de passe (laisser vide pour conserver)" else "Mot de passe") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        f.error?.let { item { Text(it, color = NovaColors.Error, fontSize = 14.sp) } }
        item {
            OutlinedTextField(
                value = f.epgUrl,
                onValueChange = { v -> vm.update { copy(epgUrl = v) } },
                label = { Text("Guide TV XMLTV (facultatif)") },
                placeholder = { Text("https://exemple.com/guide.xml.gz") },
                supportingText = {
                    Text(
                        if (f.type == ProfileType.M3U) "Vide : guide indiqué dans la playlist (url-tvg)."
                        else "Vide : guide fourni par le serveur Xtream."
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(
                "Le mot de passe est chiffré sur l'appareil (Android Keystore). Le catalogue est chargé progressivement : " +
                    "vous pouvez naviguer pendant la synchronisation.",
                color = NovaColors.TextSecondary,
                fontSize = 12.sp,
            )
        }
        item {
            Button(onClick = { vm.save(onDone) }, enabled = !f.saving, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                Text(if (profileId == null) "Enregistrer et charger" else "Enregistrer et actualiser")
            }
        }
    }
}
