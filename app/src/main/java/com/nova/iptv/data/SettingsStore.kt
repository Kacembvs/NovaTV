package com.nova.iptv.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nova.iptv.player.PlaybackRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class PlaybackSettings(
    val autoplayNext: Boolean = true,
    val countdownSeconds: Int = PlaybackRules.DEFAULT_COUNTDOWN_SECONDS,
)

class SettingsStore(private val context: Context) {
    private val activeProfileKey = longPreferencesKey("active_profile_id")
    private val autoplayKey = booleanPreferencesKey("autoplay_next")
    private val countdownKey = intPreferencesKey("autoplay_countdown_seconds")

    val activeProfileId: Flow<Long?> = context.dataStore.data.map { it[activeProfileKey] }

    val playback: Flow<PlaybackSettings> = context.dataStore.data.map { prefs ->
        PlaybackSettings(
            autoplayNext = prefs[autoplayKey] ?: true,
            countdownSeconds = prefs[countdownKey] ?: PlaybackRules.DEFAULT_COUNTDOWN_SECONDS,
        )
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
