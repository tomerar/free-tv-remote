package io.github.tomerar.freetvremote.timer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.tomerar.freetvremote.data.AppJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Keeps the running timer and the last result in the app's DataStore, so both survive the process being killed. */
class SleepTimerRepository(
    private val store: DataStore<Preferences>,
) {
    val state: Flow<SleepTimerState> =
        store.data.map { prefs -> SleepTimerState(decode(prefs[ACTIVE]), decode(prefs[LAST])) }

    suspend fun current(): SleepTimerState = state.first()

    suspend fun save(timer: SleepTimer) {
        store.edit { it[ACTIVE] = AppJson.encodeToString(SleepTimer.serializer(), timer) }
    }

    /** Ends the running timer and remembers how it ended. */
    suspend fun finish(result: SleepTimerResult) {
        store.edit {
            it.remove(ACTIVE)
            it[LAST] = AppJson.encodeToString(SleepTimerResult.serializer(), result)
        }
    }

    /** Removes the running timer without recording a result (cancelled by the user). */
    suspend fun clearActive() {
        store.edit { it.remove(ACTIVE) }
    }

    suspend fun clearLast() {
        store.edit { it.remove(LAST) }
    }

    private inline fun <reified T> decode(raw: String?): T? =
        raw?.let { runCatching { AppJson.decodeFromString<T>(it) }.getOrNull() }

    private companion object {
        val ACTIVE = stringPreferencesKey("sleep_timer_active")
        val LAST = stringPreferencesKey("sleep_timer_last")
    }
}
