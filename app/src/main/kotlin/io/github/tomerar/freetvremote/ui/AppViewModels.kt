package io.github.tomerar.freetvremote.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tomerar.freetvremote.AppContainer
import io.github.tomerar.freetvremote.data.AppSettings
import io.github.tomerar.freetvremote.data.AppShortcut
import io.github.tomerar.freetvremote.data.SavedTv
import io.github.tomerar.freetvremote.data.ThemeMode
import io.github.tomerar.freetvremote.discovery.DiscoveredTv
import io.github.tomerar.freetvremote.discovery.TvDiscovery
import io.github.tomerar.freetvremote.protocol.remote.ConnectionState
import io.github.tomerar.freetvremote.protocol.remote.TvState
import io.github.tomerar.freetvremote.remote.KeyGestures
import io.github.tomerar.freetvremote.remote.KeyboardDraftController
import io.github.tomerar.freetvremote.remote.PairingCoordinator
import io.github.tomerar.freetvremote.remote.PairingState
import io.github.tomerar.freetvremote.remote.ShortcutLauncher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

private const val STOP_TIMEOUT_MS = 5_000L

private fun <T> kotlinx.coroutines.flow.Flow<T>.state(scope: kotlinx.coroutines.CoroutineScope, initial: T): StateFlow<T> =
    stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial)

data class RemoteUiState(
    val activeTv: SavedTv? = null,
    val tvs: List<SavedTv> = emptyList(),
    val connection: ConnectionState = ConnectionState.Idle,
    val tvState: TvState = TvState(),
    val shortcuts: List<AppShortcut> = emptyList(),
    val settings: AppSettings = AppSettings(),
)

class RemoteViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val controller = container.remoteController

    val gestures = KeyGestures(viewModelScope, controller)

    val keyboard =
        KeyboardDraftController(
            scope = viewModelScope,
            isConnected = { controller.connection.value == ConnectionState.Connected },
            sendText = controller::sendText,
        )

    val uiState: StateFlow<RemoteUiState> =
        combine(
            controller.activeTv,
            container.tvRepository.tvs,
            controller.connection,
            controller.tvState,
            combine(container.shortcutsRepository.enabled, container.settingsRepository.settings) { a, b -> a to b },
        ) { active, tvs, connection, tvState, rest ->
            RemoteUiState(active, tvs, connection, tvState, rest.first, rest.second)
        }.state(viewModelScope, RemoteUiState())

    val volumeKeys get() = container.volumeKeys

    fun tap(code: Int) {
        viewModelScope.launch { controller.pressKey(code) }
    }

    private val shortcuts = ShortcutLauncher(viewModelScope, controller::launchApp)

    /** Names of shortcuts that could not be sent, for a short message. */
    val shortcutNotSent = shortcuts.notSent

    fun launch(shortcut: AppShortcut) = shortcuts.launch(shortcut)

    fun switchTv(id: String) {
        viewModelScope.launch { controller.selectTv(id) }
    }

    fun reconnect() = controller.reconnect()

    override fun onCleared() {
        gestures.releaseAll()
    }
}

/** Where a network search is: not started, running, or finished (with or without an error). */
enum class ScanPhase { IDLE, SCANNING, DONE, FAILED }

data class DiscoverUiState(
    val phase: ScanPhase = ScanPhase.IDLE,
    /** 0..1 while [ScanPhase.SCANNING]; the search ends by itself when it reaches 1. */
    val progress: Float = 0f,
    val devices: List<DiscoveredTv> = emptyList(),
    val pairedHosts: Set<String> = emptySet(),
)

/**
 * Searches only when asked and stops by itself after [scanDurationMs], so nothing keeps listening on the
 * network (and draining the battery) while the user looks at the screen.
 */
class DiscoverViewModel(
    private val discovery: TvDiscovery,
    private val savedTvs: Flow<List<SavedTv>>,
    private val scanDurationMs: Long = SCAN_DURATION_MS,
) : ViewModel() {
    constructor(container: AppContainer) : this(container.discovery, container.tvRepository.tvs)

    private val scan = MutableStateFlow(DiscoverUiState())
    private var job: Job? = null

    val uiState: StateFlow<DiscoverUiState> =
        combine(scan, savedTvs) { s, tvs ->
            s.copy(pairedHosts = tvs.map { it.host }.toSet())
        }.state(viewModelScope, DiscoverUiState())

    val hasSavedTvs: StateFlow<Boolean> =
        savedTvs
            .map { it.isNotEmpty() }
            .state(viewModelScope, false)

    /** Starts a bounded search. Ignored while one is already running. */
    fun startScan() {
        if (job?.isActive == true) return
        scan.value = DiscoverUiState(phase = ScanPhase.SCANNING)
        job =
            viewModelScope.launch {
                var failed = false
                val ticker =
                    launch {
                        var elapsed = 0L
                        while (elapsed < scanDurationMs) {
                            delay(TICK_MS)
                            elapsed += TICK_MS
                            scan.update { it.copy(progress = (elapsed.toFloat() / scanDurationMs).coerceAtMost(1f)) }
                        }
                    }
                withTimeoutOrNull(scanDurationMs) {
                    discovery
                        .discover()
                        .catch { failed = true }
                        .collect { found -> scan.update { it.copy(devices = found) } }
                }
                ticker.cancel()
                scan.update { it.copy(phase = if (failed) ScanPhase.FAILED else ScanPhase.DONE, progress = 1f) }
            }
    }

    /** Cancels a running search (leaving the screen, choosing a TV). Finished results are kept. */
    fun stopScan() {
        if (job?.isActive == true) {
            job?.cancel()
            scan.value = DiscoverUiState()
        }
        job = null
    }

    override fun onCleared() {
        job?.cancel()
    }

    companion object {
        const val SCAN_DURATION_MS = 15_000L
        private const val TICK_MS = 100L
    }
}

class PairViewModel(
    container: AppContainer,
    val host: String,
    val name: String,
) : ViewModel() {
    private val coordinator = PairingCoordinator(viewModelScope, container.identityProvider, container.tvRepository)

    val state: StateFlow<PairingState> = coordinator.state

    init {
        coordinator.start(name, host)
    }

    fun submit(code: String) = coordinator.submit(code)

    fun retry() = coordinator.start(name, host)

    override fun onCleared() {
        coordinator.cancel()
    }
}

class TvsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    val tvs: StateFlow<List<SavedTv>> = container.tvRepository.tvs.state(viewModelScope, emptyList())
    val activeId: StateFlow<String?> =
        container.remoteController.activeTv
            .map { it?.id }
            .state(viewModelScope, null)

    fun select(id: String) {
        viewModelScope.launch { container.remoteController.selectTv(id) }
    }

    fun rename(id: String, name: String) {
        viewModelScope.launch { container.tvRepository.rename(id, name) }
    }

    fun remove(id: String) {
        viewModelScope.launch { container.tvRepository.remove(id) }
    }
}

class ShortcutsViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val repo = container.shortcutsRepository
    val shortcuts: StateFlow<List<AppShortcut>> = repo.all.state(viewModelScope, emptyList())

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { repo.setEnabled(id, enabled) }
    }

    fun move(id: String, offset: Int) {
        viewModelScope.launch { repo.move(id, offset) }
    }

    fun remove(id: String) {
        viewModelScope.launch { repo.remove(id) }
    }

    /** Returns `false` when the input is invalid. */
    suspend fun add(name: String, link: String): Boolean = repo.addCustom(name, link) != null
}

class SettingsViewModel(
    container: AppContainer,
) : ViewModel() {
    private val repo = container.settingsRepository
    val settings: StateFlow<AppSettings> = repo.settings.state(viewModelScope, AppSettings())

    fun setHaptics(value: Boolean) {
        viewModelScope.launch { repo.setHaptics(value) }
    }

    fun setKeepScreenOn(value: Boolean) {
        viewModelScope.launch { repo.setKeepScreenOn(value) }
    }

    fun setUseVolumeKeys(value: Boolean) {
        viewModelScope.launch { repo.setUseVolumeKeys(value) }
    }

    fun setDynamicColor(value: Boolean) {
        viewModelScope.launch { repo.setDynamicColor(value) }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { repo.setTheme(mode) }
    }
}

/** A [androidx.lifecycle.ViewModelProvider.Factory] for view models that need only a constructor call. */
inline fun <reified VM : ViewModel> simpleFactory(crossinline create: () -> VM): androidx.lifecycle.ViewModelProvider.Factory =
    object : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
