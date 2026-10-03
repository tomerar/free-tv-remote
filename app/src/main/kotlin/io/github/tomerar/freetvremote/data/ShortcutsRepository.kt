package io.github.tomerar.freetvremote.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID

/** The configurable app shortcut buttons: built-ins can be toggled, custom ones added and removed. */
class ShortcutsRepository(
    private val store: DataStore<Preferences>,
) {
    private val serializer = ListSerializer(AppShortcut.serializer())

    /** All shortcuts, enabled or not, in display order. */
    val all: Flow<List<AppShortcut>> = store.data.map { decode(it[KEY]) }

    val enabled: Flow<List<AppShortcut>> = all.map { list -> list.filter { it.enabled } }

    /** Adds a custom shortcut. Returns `null` if [name] is blank or [link] is not a valid deep link. */
    suspend fun addCustom(name: String, link: String): AppShortcut? {
        val cleanName = name.trim()
        val cleanLink = link.trim()
        if (cleanName.isEmpty() || !AppShortcut.isValidLink(cleanLink)) return null
        val shortcut = AppShortcut(id = "custom-" + UUID.randomUUID(), name = cleanName, link = cleanLink)
        write { it + shortcut }
        return shortcut
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        write { list -> list.map { if (it.id == id) it.copy(enabled = enabled) else it } }
    }

    /** Only custom shortcuts can be deleted; built-ins can be disabled instead. */
    suspend fun remove(id: String) {
        write { list -> list.filterNot { it.id == id && !it.builtIn } }
    }

    suspend fun move(id: String, offset: Int) {
        write { list ->
            val from = list.indexOfFirst { it.id == id }
            val to = (from + offset).coerceIn(0, list.lastIndex)
            if (from < 0 || from == to) {
                list
            } else {
                list.toMutableList().apply { add(to, removeAt(from)) }
            }
        }
    }

    private suspend fun write(change: (List<AppShortcut>) -> List<AppShortcut>) {
        store.edit { prefs -> prefs[KEY] = AppJson.encodeToString(serializer, change(decode(prefs[KEY]))) }
    }

    private fun decode(raw: String?): List<AppShortcut> {
        val stored = if (raw.isNullOrEmpty()) null else runCatching { AppJson.decodeFromString(serializer, raw) }.getOrNull()
        return stored ?: AppShortcut.defaults
    }

    private companion object {
        val KEY = stringPreferencesKey("shortcuts")
    }
}
