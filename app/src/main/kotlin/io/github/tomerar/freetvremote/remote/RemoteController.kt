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
import kotlinx.coroutines.withTimeoutOrNull

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
) : KeySender {
    private val session = MutableStateFlow<RemoteSession?>(null)
    private val _activeTv = MutableStateFlow<SavedTv?>(null)
    private var foreground = false
    private var disconnectJob: Job? = null
    private var started = false

    /** The TV currently selected (not necessarily connected). */
    val activeTv: StateFlow<SavedTv?> = _activeTv

    val connection: StateFlow<ConnectionState> = session
        .flatMapLatest { it?.connectionState ?: flowOf(ConnectionState.Idle) }
        .stateIn(scope, SharingStarted.Eagerly, ConnectionState.Idle)

    val tvState: StateFlow<TvState> = session
        .flatMapLatest { it?.tvState ?: flowOf(TvState()) }
        .stateIn(scope, SharingStarted.Eagerly, TvState())

    /** Call once at startup: selects the last used TV so the app reconnects automatically. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            // Keep the active TV in sync with storage (renames, removals, re-pairing, address changes).
            combine(tvs.tvs, tvs.lastUsedId) { all, lastId -> all.firstOrNull { it.id == lastId } ?: all.firstOrNull() }
                .collect { target -> apply(target) }
        }
    }

    private suspend fun apply(target: SavedTv?) {
        val current = _activeTv.value
        if (target == current) return
        val sameConnectionTarget = current != null && target != null &&
            current.id == target.id && current.host == target.host && current.pin == target.pin &&
            current.remotePort == target.remotePort
        _activeTv.value = target
        if (sameConnectionTarget) return // only the display name changed
        session.value?.stop()
        session.value = null
        if (target != null) {
            session.value = factory.create(scope, target)
            if (foreground) session.value?.start()
        }
    }

    /** Switches to another saved TV and remembers it as the last used one. */
    suspend fun selectTv(id: String) {
        tvs.setLastUsed(id)
    }

    fun onAppForeground() {
        foreground = true
        disconnectJob?.cancel()
        session.value?.start()
    }

    /** Keeps the link for a short grace period (app switching), then disconnects to save battery. */
    fun onAppBackground() {
        foreground = false
        disconnectJob?.cancel()
        disconnectJob = scope.launch {
            delay(backgroundGraceMs)
            if (!foreground) session.value?.stop()
        }
    }

    /** Manual retry, e.g. after the connection ended in a failure state. */
    fun reconnect() {
        session.value?.stop()
        session.value?.start()
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
    suspend fun sendQuickKey(code: Int): Boolean {
        if (connection.value == ConnectionState.Connected) return pressKey(code)
        val tv = _activeTv.value ?: tvs.lastUsed.first() ?: return false
        val temporary = factory.create(scope, tv)
        return try {
            temporary.start()
            val state = withTimeoutOrNull(quickConnectTimeoutMs) {
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
