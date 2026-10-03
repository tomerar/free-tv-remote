package io.github.tomerar.freetvremote.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class AppSettings(
    val hapticsEnabled: Boolean = true,
    val keepScreenOn: Boolean = false,
    val theme: ThemeMode = ThemeMode.DARK,
    val useVolumeKeys: Boolean = true,
)

class SettingsRepository(
    private val store: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> =
        store.data.map { prefs ->
            val defaults = AppSettings()
            AppSettings(
                hapticsEnabled = prefs[HAPTICS] ?: defaults.hapticsEnabled,
                keepScreenOn = prefs[KEEP_SCREEN_ON] ?: defaults.keepScreenOn,
                theme = prefs[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: defaults.theme,
                useVolumeKeys = prefs[VOLUME_KEYS] ?: defaults.useVolumeKeys,
            )
        }

    suspend fun setHaptics(enabled: Boolean) {
        store.edit { it[HAPTICS] = enabled }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        store.edit { it[KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setTheme(mode: ThemeMode) {
        store.edit { it[THEME] = mode.name }
    }

    suspend fun setUseVolumeKeys(enabled: Boolean) {
        store.edit { it[VOLUME_KEYS] = enabled }
    }

    private companion object {
        val HAPTICS = booleanPreferencesKey("haptics")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val THEME = stringPreferencesKey("theme")
        val VOLUME_KEYS = booleanPreferencesKey("volume_keys")
    }
}
