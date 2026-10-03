package io.github.tomerar.freetvremote.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import java.util.UUID

/** Persists the paired TVs and which one was used last. */
class TvRepository(
    private val store: DataStore<Preferences>,
) {
    private val listSerializer = ListSerializer(SavedTv.serializer())

    val tvs: Flow<List<SavedTv>> = store.data.map { decode(it[TVS_KEY]) }

    val lastUsedId: Flow<String?> = store.data.map { it[LAST_USED_KEY] }

    /** The TV to reconnect to: the last used one if it still exists, otherwise the first saved one. */
    val lastUsed: Flow<SavedTv?> =
        store.data.map { prefs ->
            val all = decode(prefs[TVS_KEY])
            all.firstOrNull { it.id == prefs[LAST_USED_KEY] } ?: all.firstOrNull()
        }

    /**
     * Adds a freshly paired TV, or replaces the entry with the same host (re-pairing).
     * The TV becomes the last used one. Returns the stored entry.
     */
    suspend fun savePaired(
        name: String,
        host: String,
        pin: ByteArray,
        remotePort: Int,
        pairingPort: Int,
        serviceName: String? = null,
    ): SavedTv {
        var saved: SavedTv? = null
        store.edit { prefs ->
            val all = decode(prefs[TVS_KEY])
            // Same address, or the same network name (the TV moved to a new address): this is a re-pairing.
            val existing =
                all.firstOrNull { it.host == host && it.remotePort == remotePort }
                    ?: serviceName?.let { svc -> all.firstOrNull { it.serviceName == svc } }
            val entry =
                SavedTv(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    name = existing?.name ?: name,
                    host = host,
                    pin = SavedTv.encodePin(pin),
                    remotePort = remotePort,
                    pairingPort = pairingPort,
                    serviceName = serviceName ?: existing?.serviceName,
                )
            prefs[TVS_KEY] = AppJson.encodeToString(listSerializer, all.filterNot { it.id == entry.id } + entry)
            prefs[LAST_USED_KEY] = entry.id
            saved = entry
        }
        return checkNotNull(saved)
    }

    suspend fun setLastUsed(id: String) {
        store.edit { prefs ->
            if (decode(prefs[TVS_KEY]).any { it.id == id }) prefs[LAST_USED_KEY] = id
        }
    }

    suspend fun rename(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        update(id) { it.copy(name = trimmed) }
    }

    /** The TV changed its address (DHCP): keep the pairing, update where to find it. */
    suspend fun updateHost(id: String, host: String) {
        update(id) { it.copy(host = host) }
    }

    /**
     * Records where the TV was found (and the name it announces). Only the address and the network name change;
     * the pairing (pin) and the user's own name stay as they are.
     */
    suspend fun updateAddress(id: String, host: String, serviceName: String?) {
        update(id) { it.copy(host = host, serviceName = serviceName ?: it.serviceName) }
    }

    suspend fun remove(id: String) {
        store.edit { prefs ->
            prefs[TVS_KEY] = AppJson.encodeToString(listSerializer, decode(prefs[TVS_KEY]).filterNot { it.id == id })
            if (prefs[LAST_USED_KEY] == id) prefs.remove(LAST_USED_KEY)
        }
    }

    suspend fun get(id: String): SavedTv? = tvs.first().firstOrNull { it.id == id }

    private suspend fun update(id: String, change: (SavedTv) -> SavedTv) {
        store.edit { prefs ->
            val updated = decode(prefs[TVS_KEY]).map { if (it.id == id) change(it) else it }
            prefs[TVS_KEY] = AppJson.encodeToString(listSerializer, updated)
        }
    }

    private fun decode(raw: String?): List<SavedTv> =
        if (raw.isNullOrEmpty()) emptyList() else runCatching { AppJson.decodeFromString(listSerializer, raw) }.getOrDefault(emptyList())

    private companion object {
        val TVS_KEY = stringPreferencesKey("tvs")
        val LAST_USED_KEY = stringPreferencesKey("last_used_tv")
    }
}
