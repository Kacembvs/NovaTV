package com.nova.iptv.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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

class SettingsStore(private val context: Context) {
    private val activeProfileKey = longPreferencesKey("active_profile_id")
    private val autoplayKey = booleanPreferencesKey("autoplay_next")
    private val countdownKey = intPreferencesKey("autoplay_countdown_seconds")
    private val gesturesKey = booleanPreferencesKey("player_gestures")
    private val freeRotationKey = booleanPreferencesKey("player_free_rotation")
    private val pipKey = booleanPreferencesKey("player_pip")
    private val themeKey = stringPreferencesKey("theme")
    private val customAccentKey = intPreferencesKey("theme_custom_accent")

    val activeProfileId: Flow<Long?> = context.dataStore.data.map { it[activeProfileKey] }

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
