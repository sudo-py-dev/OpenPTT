package com.openptt.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * DataStore-backed settings for the app.
 * Stores server URL, theme preference, and audio quality settings.
 */
@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val SERVER_URL_KEY = stringPreferencesKey("server_url")
        private val AUDIO_QUALITY_KEY = stringPreferencesKey("audio_quality")
        private val THEME_KEY = stringPreferencesKey("theme")
    }

    val serverUrlFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[SERVER_URL_KEY] ?: ""
    }

    val audioQualityFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[AUDIO_QUALITY_KEY] ?: "normal" // low, normal, high
    }

    val themeFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[THEME_KEY] ?: "system" // system, light, dark
    }

    suspend fun setServerUrl(url: String) {
        context.dataStore.edit { it[SERVER_URL_KEY] = url }
    }

    suspend fun setAudioQuality(quality: String) {
        context.dataStore.edit { it[AUDIO_QUALITY_KEY] = quality }
    }

    suspend fun setTheme(theme: String) {
        context.dataStore.edit { it[THEME_KEY] = theme }
    }

    suspend fun clearAll() {
        context.dataStore.edit { it.clear() }
    }
}
