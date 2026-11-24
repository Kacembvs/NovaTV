package com.nova.iptv.ui.profiles

import android.text.format.DateUtils
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nova.iptv.AppContainer
import com.nova.iptv.core.isTvLayout
import android.content.Context
import android.net.Uri
import com.nova.iptv.core.toUserMessage
import com.nova.iptv.data.BackupManager
import com.nova.iptv.data.LanguageSettings
import com.nova.iptv.data.ParentalSettings
import com.nova.iptv.ui.components.PinDialog
import com.nova.iptv.data.ThemeSettings
import com.nova.iptv.ui.theme.NovaThemeId
import com.nova.iptv.ui.theme.TvInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import com.nova.iptv.data.EpgManager
import com.nova.iptv.data.PlaybackSettings
import com.nova.iptv.data.SyncManager
import com.nova.iptv.data.db.ProfileEntity
import com.nova.iptv.domain.ProfileType
import com.nova.iptv.player.PlaybackRules
import com.nova.iptv.ui.components.EmptyState
import com.nova.iptv.ui.components.ScreenHeader
import com.nova.iptv.ui.components.NovaButton as Button
import com.nova.iptv.ui.components.NovaTextButton as TextButton
import com.nova.iptv.ui.components.NovaFilledTonalButton as FilledTonalButton
import com.nova.iptv.ui.components.NovaIconButton as IconButton
import com.nova.iptv.ui.components.NovaFilterChip as FilterChip
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
    fun setPictureInPicture(enabled: Boolean) = viewModelScope.launch { c.settings.setPictureInPicture(enabled) }

    val languages: StateFlow<LanguageSettings> = c.settings.languages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LanguageSettings())
    fun setAudioLanguage(code: String?) = viewModelScope.launch { c.settings.setAudioLanguage(code) }
    fun setSubtitleLanguage(code: String?) = viewModelScope.launch { c.settings.setSubtitleLanguage(code) }

    val autoRefresh: StateFlow<Boolean> = c.settings.autoRefresh
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    fun setAutoRefresh(enabled: Boolean) = viewModelScope.launch { c.settings.setAutoRefresh(enabled) }

    val downloadSettings: StateFlow<com.nova.iptv.data.DownloadSettings> = c.settings.downloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.nova.iptv.data.DownloadSettings())
    fun setDownloadWifiOnly(enabled: Boolean) = viewModelScope.launch { c.settings.setDownloadWifiOnly(enabled) }
    fun setDownloadQuality(quality: com.nova.iptv.data.DownloadQuality) = viewModelScope.launch { c.settings.setDownloadQuality(quality) }
    fun setSmartDownloads(enabled: Boolean) = viewModelScope.launch { c.settings.setSmartDownloads(enabled) }
    val offlineItems get() = c.downloads.items
    fun offlineStorage() = c.downloads.storage()
    fun removeAllDownloads() = c.downloads.removeAll()

    val parental: StateFlow<ParentalSettings> = c.parental.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ParentalSettings())
    val parentalUnlocked = c.parental.unlocked
    val settingsBlocked: StateFlow<Boolean> = c.parental.settingsBlocked()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun lockedGroups(p: ParentalSettings, profileId: Long) = c.parental.lockedGroups(p, profileId)
    suspend fun allGroups(profileId: Long) = c.db.contentDao().allGroups(profileId)
    suspend fun verifyPin(pin: String) = c.parental.verify(pin)
    suspend fun setPin(pin: String) = c.parental.setPin(pin)
    fun removePin() = viewModelScope.launch { c.parental.removePin() }
    fun relock() = c.parental.relock()
    fun setLockSettings(enabled: Boolean) = viewModelScope.launch { c.parental.setLockSettings(enabled) }
    fun setGroupLocked(profileId: Long, group: String, locked: Boolean) =
        viewModelScope.launch { c.parental.setGroupLocked(profileId, group, locked) }

    val theme: StateFlow<ThemeSettings> = c.settings.theme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeSettings())
    fun setTheme(id: NovaThemeId, accent: Int? = null) = viewModelScope.launch { c.settings.setTheme(id, accent) }
    fun applyTvTheme(style: TvInterface) = viewModelScope.launch { c.settings.applyTvTheme(style) }

    /** Result / error of the last backup operation, shown in a snackbar-like message. */
    val backupMessage = MutableStateFlow<String?>(null)
    /** Restore waiting for the backup code (file content kept in memory only). */
    val pendingRestore = MutableStateFlow<PendingRestore?>(null)

    data class PendingRestore(val json: String, val error: String? = null)

    fun export(context: Context, uri: Uri, code: String) {
        viewModelScope.launch {
            backupMessage.value = try {
                val json = c.backup.export(code.ifEmpty { null })
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                        ?: throw IOException("Impossible d'écrire le fichier.")
                }
                if (code.isEmpty()) "Sauvegarde créée (sans mots de passe)." else "Sauvegarde créée, mots de passe chiffrés avec votre code."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Échec de la sauvegarde : ${e.toUserMessage()}"
            }
        }
    }

    fun openRestore(context: Context, uri: Uri) {
        viewModelScope.launch {
            val json = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                } ?: throw IOException("Fichier illisible.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                backupMessage.value = "Échec de la lecture : ${e.toUserMessage()}"
                return@launch
            }
            restore(json, null)
        }
    }

    fun restore(json: String, code: String?) {
        viewModelScope.launch {
            try {
                val result = c.backup.restore(json, code)
                pendingRestore.value = null
                result.newProfileIds.forEach { c.sync.sync(it) }
                backupMessage.value = buildString {
                    append("Restauration terminée : ${result.profilesAdded} profil(s) ajouté(s)")
                    if (result.profilesMerged > 0) append(", ${result.profilesMerged} déjà présent(s)")
                    append(", ${result.favorites} favori(s), ${result.history} reprise(s), ${result.ratings} note(s), ${result.customGroups} groupe(s).")
                    if (result.missingPasswords > 0) {
                        append(" ${result.missingPasswords} profil(s) Xtream sans mot de passe : modifiez-les pour le ressaisir.")
                    }
                }
            } catch (e: BackupManager.CodeRequiredException) {
                pendingRestore.value = PendingRestore(json)
            } catch (e: BackupManager.WrongCodeException) {
                pendingRestore.value = PendingRestore(json, "Code incorrect.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                pendingRestore.value = null
                backupMessage.value = "Échec de la restauration : ${e.toUserMessage()}"
            }
        }
    }
    fun activate(id: Long) = viewModelScope.launch { c.profiles.setActive(id) }
    fun refresh(id: Long) = c.sync.sync(id)
    fun refreshGuide(id: Long) = c.epg.refresh(id)
    fun delete(id: Long) {
        c.sync.cancel(id)
        c.epg.cancel(id)
        c.downloads.removeProfile(id)
        viewModelScope.launch { c.profiles.delete(id) }
    }
}

@Composable
fun ProfilesScreen(onAdd: () -> Unit, onEdit: (Long) -> Unit, onOpenDownloads: () -> Unit = {}) {
    val vm = novaViewModel { ProfilesViewModel(it) }
    // Mobile and TV share the same sectioned settings; phones in portrait use compact tabs.
    TvSettingsScreen(vm, onAdd, onEdit, compact = !isTvLayout(), onOpenDownloads = onOpenDownloads)
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
    var passwordVisible by remember(profileId) { mutableStateOf(false) }
    val saveFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

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
            ProfileTextField(
                value = f.name,
                onValueChange = { v -> vm.update { copy(name = v) } },
                label = "Nom du profil (facultatif)",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            ProfileTextField(
                value = f.url,
                onValueChange = { v -> vm.update { copy(url = v) } },
                label = if (f.type == ProfileType.M3U) "Adresse de la playlist (URL)" else "Adresse du serveur",
                placeholder = if (f.type == ProfileType.M3U) "https://exemple.com/playlist.m3u" else "http://serveur.com:8080",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (f.type == ProfileType.XTREAM) {
            item {
                ProfileTextField(
                    value = f.username,
                    onValueChange = { v -> vm.update { copy(username = v) } },
                    label = "Nom d'utilisateur",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                ProfileTextField(
                    value = f.password,
                    onValueChange = { v -> vm.update { copy(password = v) } },
                    label = if (f.hasStoredPassword) "Mot de passe (laisser vide pour conserver)" else "Mot de passe",
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (passwordVisible) "Masquer le mot de passe" else "Afficher le mot de passe",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        f.error?.let { item { Text(it, color = NovaColors.Error, fontSize = 14.sp) } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ProfileTextField(
                    value = f.epgUrl,
                    onValueChange = { v -> vm.update { copy(epgUrl = v) } },
                    label = "Guide TV XMLTV (facultatif)",
                    placeholder = "https://exemple.com/guide.xml.gz",
                    supportingText = if (f.type == ProfileType.M3U) "Vide : guide indiqué dans la playlist (url-tvg)."
                        else "Vide : guide fourni par le serveur Xtream.",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboard?.hide()
                        saveFocus.requestFocus()
                    }),
                    modifier = Modifier.fillMaxWidth().focusProperties { down = saveFocus },
                    nextFocus = saveFocus,
                )
                Text(
                    "Le mot de passe est chiffré sur l'appareil (Android Keystore). Le catalogue est chargé progressivement : " +
                        "vous pouvez naviguer pendant la synchronisation.",
                    color = NovaColors.TextSecondary,
                    fontSize = 12.sp,
                )
                Button(onClick = { vm.save(onDone) }, enabled = !f.saving, modifier = Modifier.fillMaxWidth().height(50.dp).focusRequester(saveFocus)) {
                    Text(if (profileId == null) "Enregistrer et charger" else "Enregistrer et actualiser")
                }
            }
        }
    }
}
