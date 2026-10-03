package io.github.tomerar.freetvremote.remote

import io.github.tomerar.freetvremote.data.SavedTv
import io.github.tomerar.freetvremote.data.TvRepository
import io.github.tomerar.freetvremote.protocol.proto.RemoteDirection
import io.github.tomerar.freetvremote.protocol.remote.ConnectionState
import io.github.tomerar.freetvremote.protocol.remote.RemoteSession
import io.github.tomerar.freetvremote.protocol.remote.RemoteSessionConfig
import io.github.tomerar.freetvremote.protocol.remote.TvState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.security.GeneralSecurityException

fun interface SessionFactory {
    suspend fun create(scope: CoroutineScope, tv: SavedTv): RemoteSession
}

class DefaultSessionFactory(
    private val identity: IdentityProvider,
    private val config: (SavedTv) -> RemoteSessionConfig = { RemoteSessionConfig(port = it.remotePort) },
) : SessionFactory {
    override suspend fun create(scope: CoroutineScope, tv: SavedTv): RemoteSession =
        RemoteSession(scope, tv.host, identity.get(), tv.pinBytes, config(tv))
}

/**
 * Owns the one active [RemoteSession]. It lives in the application scope (never in a
 * Composable), reconnects to the last used TV, follows the app's foreground state
 * and exposes everything the UI needs as flows.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemoteController(
    private val scope: CoroutineScope,
    private val tvs: TvRepository,
    private val factory: SessionFactory,
    private val backgroundGraceMs: Long = BACKGROUND_GRACE_MS,
    private val quickConnectTimeoutMs: Long = QUICK_CONNECT_TIMEOUT_MS,
    /** Receives short, non-sensitive notes about the connection (state changes, why a session could not be built). */
    private val log: (String) -> Unit = {},
) : KeySender {
    private val session = MutableStateFlow<RemoteSession?>(null)
    private val _activeTv = MutableStateFlow<SavedTv?>(null)

    private val sessionLock = Mutex()
    private val quickLock = Mutex()

    @Volatile
    private var foreground = false
    private var disconnectJob: Job? = null
    private var started = false

    /** The TV currently selected (not necessarily connected). */
    val activeTv: StateFlow<SavedTv?> = _activeTv

    val connection: StateFlow<ConnectionState> =
        session
            .flatMapLatest { it?.connectionState ?: flowOf(ConnectionState.Idle) }
            .stateIn(scope, SharingStarted.Eagerly, ConnectionState.Idle)

    val tvState: StateFlow<TvState> =
        session
            .flatMapLatest { it?.tvState ?: flowOf(TvState()) }
            .stateIn(scope, SharingStarted.Eagerly, TvState())

    /** Call once at startup: selects the last used TV so the app reconnects automatically. */
    fun start() {
        if (started) return
        started = true
        scope.launch { connection.collect { log("state: $it") } }
        scope.launch {
            // Keep the active TV in sync with storage (renames, removals, re-pairing, address changes).
            combine(tvs.tvs, tvs.lastUsedId) { all, lastId -> all.firstOrNull { it.id == lastId } ?: all.firstOrNull() }
                .collect { target -> apply(target) }
        }
    }

    private suspend fun apply(target: SavedTv?) =
        sessionLock.withLock {
            val current = _activeTv.value
            if (target == current) return@withLock
            val sameConnectionTarget =
                current != null && target != null &&
                    current.id == target.id && current.host == target.host && current.pin == target.pin &&
                    current.remotePort == target.remotePort
            if (sameConnectionTarget) {
                _activeTv.value = target // only the display name changed
                return@withLock
            }
            // Swap sessions first so that observers never see the new TV paired with the old connection state.
            // If the phone's identity cannot be loaded the TV is still selected, just not connected: the
            // user can retry with "Reconnect" (see retryCreate) instead of the app crashing.
            val replacement = target?.let { createSessionOrNull(it) }
            val old = session.value
            session.value = replacement
            _activeTv.value = target
            old?.stop()
            if (foreground) replacement?.start()
        }

    private suspend fun createSessionOrNull(tv: SavedTv): RemoteSession? =
        try {
            factory.create(scope, tv)
        } catch (e: IOException) {
            log("could not build the session: ${e::class.simpleName}")
            null // identity unreadable / unwritable (storage full, keystore problem)
        } catch (e: GeneralSecurityException) {
            log("could not build the session: ${e::class.simpleName}")
            null
        }

    /** Tries again to build the session of the selected TV after an earlier identity failure. */
    private fun retryCreate() {
        scope.launch {
            sessionLock.withLock {
                val tv = _activeTv.value ?: return@withLock
                if (session.value != null) return@withLock
                val created = createSessionOrNull(tv) ?: return@withLock
                session.value = created
                if (foreground) created.start()
            }
        }
    }

    /** Switches to another saved TV and remembers it as the last used one. */
    suspend fun selectTv(id: String) {
        tvs.setLastUsed(id)
    }

    fun onAppForeground() {
        foreground = true
        disconnectJob?.cancel()
        session.value?.start() ?: retryCreate()
    }

    /** Keeps the link for a short grace period (app switching), then disconnects to save battery. */
    fun onAppBackground() {
        foreground = false
        disconnectJob?.cancel()
        disconnectJob =
            scope.launch {
                delay(backgroundGraceMs)
                if (!foreground) session.value?.stop()
            }
    }

    /** Manual retry, e.g. after the connection ended in a failure state. */
    fun reconnect() {
        val current = session.value
        if (current == null) {
            retryCreate()
        } else {
            current.stop()
            current.start()
        }
    }

    // --- Commands --------------------------------------------------------------------------------

    suspend fun pressKey(code: Int): Boolean = session.value?.pressKey(code) ?: false

    override suspend fun tap(code: Int) {
        pressKey(code)
    }

    override suspend fun holdStart(code: Int) {
        session.value?.keyDown(code)
    }

    override suspend fun holdEnd(code: Int) {
        session.value?.keyUp(code)
    }

    suspend fun launchApp(link: String): Boolean = session.value?.launchApp(link) ?: false

    suspend fun sendText(text: String): Boolean = session.value?.sendText(text) ?: false

    /**
     * For the Quick Settings tile and the widget, which can run while the app UI is gone: reuses the live
     * session if there is one, otherwise connects briefly, sends the key and disconnects again.
     */
    suspend fun sendQuickKey(code: Int): Boolean = quickLock.withLock { sendQuickKeyLocked(code) }

    // Serialised: simultaneous widget/tile taps must not open several temporary connections to the TV at once
    // (many TVs only keep one remote connection and may drop the others).
    private suspend fun sendQuickKeyLocked(code: Int): Boolean {
        if (connection.value == ConnectionState.Connected) return pressKey(code)
        val tv = _activeTv.value ?: tvs.lastUsed.first() ?: return false
        val temporary = createSessionOrNull(tv) ?: return false
        return try {
            temporary.start()
            val state =
                withTimeoutOrNull(quickConnectTimeoutMs) {
                    temporary.connectionState.first { it == ConnectionState.Connected || it is ConnectionState.Failed }
                }
            state == ConnectionState.Connected && temporary.sendKey(code, RemoteDirection.SHORT).also { delay(QUICK_FLUSH_MS) }
        } finally {
            temporary.stop()
        }
    }

    companion object {
        const val BACKGROUND_GRACE_MS = 30_000L
        const val QUICK_CONNECT_TIMEOUT_MS = 6_000L
        private const val QUICK_FLUSH_MS = 150L
    }
}
