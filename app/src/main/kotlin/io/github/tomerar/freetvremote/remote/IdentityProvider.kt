package io.github.tomerar.freetvremote.remote

import io.github.tomerar.freetvremote.protocol.tls.ClientIdentity
import io.github.tomerar.freetvremote.protocol.tls.IdentityStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Loads (or generates, on first use) the phone's TLS identity once, off the main thread. */
fun interface IdentityProvider {
    suspend fun get(): ClientIdentity
}

class StoredIdentityProvider(private val store: IdentityStore) : IdentityProvider {
    private val mutex = Mutex()
    private var cached: ClientIdentity? = null

    override suspend fun get(): ClientIdentity = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) { store.loadOrCreate() }.also { cached = it }
    }
}
