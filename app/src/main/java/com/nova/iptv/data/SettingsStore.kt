package com.nova.iptv.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nova.iptv.player.PlaybackRules
import com.nova.iptv.ui.theme.NovaThemeId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class PlaybackSettings(
    val autoplayNext: Boolean = true,
    val countdownSeconds: Int = PlaybackRules.DEFAULT_COUNTDOWN_SECONDS,
    /** Mobile: vertical swipes for brightness (left) / volume (right), double tap to seek. */
    val gesturesEnabled: Boolean = true,
    /** false = landscape only, true = follows the device (portrait allowed). */
    val freeRotation: Boolean = false,
    /** Mobile: keep watching in a small window when leaving the app. */
    val pictureInPicture: Boolean = true,
)

data class ThemeSettings(val theme: NovaThemeId = NovaThemeId.NOVA_BLUE, val customAccent: Int? = null)

/** Preferred track languages (ISO codes). audio = null: automatic; subtitles = null: off. */
data class LanguageSettings(val audio: String? = null, val subtitles: String? = null)

data class ParentalSettings(
    val pinHash: String? = null,
    val pinSalt: String? = null,
    /** "profileId<US>groupTitle" entries. */
    val lockedGroups: Set<String> = emptySet(),
    val lockSettings: Boolean = false,
) {
    val pinSet get() = pinHash != null && pinSalt != null
}

class SettingsStore(private val context: Context) {
    private val activeProfileKey = longPreferencesKey("active_profile_id")
    private val autoplayKey = booleanPreferencesKey("autoplay_next")
    private val countdownKey = intPreferencesKey("autoplay_countdown_seconds")
    private val gesturesKey = booleanPreferencesKey("player_gestures")
    private val freeRotationKey = booleanPreferencesKey("player_free_rotation")
    private val pipKey = booleanPreferencesKey("player_pip")
    private val themeKey = stringPreferencesKey("theme")
    private val customAccentKey = intPreferencesKey("theme_custom_accent")

    private val audioLangKey = stringPreferencesKey("lang_audio")
    private val subtitleLangKey = stringPreferencesKey("lang_subtitles")
    private val pinHashKey = stringPreferencesKey("parental_pin_hash")
    private val pinSaltKey = stringPreferencesKey("parental_pin_salt")
    private val lockedGroupsKey = stringSetPreferencesKey("parental_locked_groups")
    private val lockSettingsKey = booleanPreferencesKey("parental_lock_settings")
    private val autoRefreshKey = booleanPreferencesKey("auto_refresh")

    val activeProfileId: Flow<Long?> = context.dataStore.data.map { it[activeProfileKey] }

    val languages: Flow<LanguageSettings> = context.dataStore.data.map {
        LanguageSettings(it[audioLangKey]?.ifEmpty { null }, it[subtitleLangKey]?.ifEmpty { null })
    }

    suspend fun setAudioLanguage(code: String?) {
        context.dataStore.edit { if (code == null) it.remove(audioLangKey) else it[audioLangKey] = code }
    }

    suspend fun setSubtitleLanguage(code: String?) {
        context.dataStore.edit { if (code == null) it.remove(subtitleLangKey) else it[subtitleLangKey] = code }
    }

    val parental: Flow<ParentalSettings> = context.dataStore.data.map {
        ParentalSettings(it[pinHashKey], it[pinSaltKey], it[lockedGroupsKey].orEmpty(), it[lockSettingsKey] ?: false)
    }

    suspend fun setPin(hash: String?, salt: String?) {
        context.dataStore.edit {
            if (hash == null || salt == null) {
                it.remove(pinHashKey); it.remove(pinSaltKey); it.remove(lockedGroupsKey); it.remove(lockSettingsKey)
            } else {
                it[pinHashKey] = hash; it[pinSaltKey] = salt
            }
        }
    }

    suspend fun setLockedGroups(groups: Set<String>) {
        context.dataStore.edit { it[lockedGroupsKey] = groups }
    }

    suspend fun setLockSettings(enabled: Boolean) {
        context.dataStore.edit { it[lockSettingsKey] = enabled }
    }

    /** Background refresh of catalogues and guides (WorkManager), on by default. */
    val autoRefresh: Flow<Boolean> = context.dataStore.data.map { it[autoRefreshKey] ?: true }

    suspend fun setAutoRefresh(enabled: Boolean) {
        context.dataStore.edit { it[autoRefreshKey] = enabled }
    }

    val playback: Flow<PlaybackSettings> = context.dataStore.data.map { prefs ->
        PlaybackSettings(
            autoplayNext = prefs[autoplayKey] ?: true,
            countdownSeconds = prefs[countdownKey] ?: PlaybackRules.DEFAULT_COUNTDOWN_SECONDS,
            gesturesEnabled = prefs[gesturesKey] ?: true,
            freeRotation = prefs[freeRotationKey] ?: false,
            pictureInPicture = prefs[pipKey] ?: true,
        )
    }

    val theme: Flow<ThemeSettings> = context.dataStore.data.map { prefs ->
        ThemeSettings(
            theme = prefs[themeKey]?.let { name -> NovaThemeId.entries.firstOrNull { it.name == name } } ?: NovaThemeId.NOVA_BLUE,
            customAccent = prefs[customAccentKey],
        )
    }

    suspend fun setTheme(theme: NovaThemeId, customAccent: Int? = null) {
        context.dataStore.edit { prefs ->
            prefs[themeKey] = theme.name
            if (customAccent != null) prefs[customAccentKey] = customAccent
        }
    }

    suspend fun setPlayback(settings: PlaybackSettings) {
        context.dataStore.edit {
            it[autoplayKey] = settings.autoplayNext
            it[countdownKey] = settings.countdownSeconds
            it[gesturesKey] = settings.gesturesEnabled
            it[freeRotationKey] = settings.freeRotation
            it[pipKey] = settings.pictureInPicture
        }
    }

    suspend fun setPictureInPicture(enabled: Boolean) {
        context.dataStore.edit { it[pipKey] = enabled }
    }

    suspend fun setGesturesEnabled(enabled: Boolean) {
        context.dataStore.edit { it[gesturesKey] = enabled }
    }

    suspend fun setFreeRotation(enabled: Boolean) {
        context.dataStore.edit { it[freeRotationKey] = enabled }
    }

    suspend fun setActiveProfile(id: Long?) {
        context.dataStore.edit { prefs ->
            if (id == null) prefs.remove(activeProfileKey) else prefs[activeProfileKey] = id
        }
    }

    suspend fun setAutoplay(enabled: Boolean) {
        context.dataStore.edit { it[autoplayKey] = enabled }
    }

    suspend fun setCountdownSeconds(seconds: Int) {
        context.dataStore.edit { it[countdownKey] = seconds }
    }
}
