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
import io.github.tomerar.freetvremote.protocol.remote.ConnectionState
import io.github.tomerar.freetvremote.protocol.remote.TvState
import io.github.tomerar.freetvremote.remote.KeyGestures
import io.github.tomerar.freetvremote.remote.PairingCoordinator
import io.github.tomerar.freetvremote.remote.PairingState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    fun launch(shortcut: AppShortcut) {
        viewModelScope.launch { controller.launchApp(shortcut.link) }
    }

    fun sendText(text: String) {
        viewModelScope.launch { controller.sendText(text) }
    }

    fun switchTv(id: String) {
        viewModelScope.launch { controller.selectTv(id) }
    }

    fun reconnect() = controller.reconnect()

    override fun onCleared() {
        gestures.releaseAll()
    }
}

data class DiscoverUiState(
    val scanning: Boolean = false,
    val devices: List<DiscoveredTv> = emptyList(),
    val failed: Boolean = false,
    val pairedHosts: Set<String> = emptySet(),
)

class DiscoverViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val scan = MutableStateFlow(DiscoverUiState())
    private var job: Job? = null

    val uiState: StateFlow<DiscoverUiState> =
        combine(scan, container.tvRepository.tvs) { s, tvs ->
            s.copy(pairedHosts = tvs.map { it.host }.toSet())
        }.state(viewModelScope, DiscoverUiState())

    val hasSavedTvs: StateFlow<Boolean> =
        container.tvRepository.tvs
            .map { it.isNotEmpty() }
            .state(viewModelScope, false)

    fun startScan() {
        if (job?.isActive == true) return
        scan.value = DiscoverUiState(scanning = true)
        job =
            viewModelScope.launch {
                container.discovery
                    .discover()
                    .catch { scan.update { it.copy(scanning = false, failed = true) } }
                    .collect { found -> scan.update { it.copy(devices = found) } }
            }
    }

    fun stopScan() {
        job?.cancel()
        job = null
        scan.update { it.copy(scanning = false) }
    }

    fun rescan() {
        stopScan()
        startScan()
    }

    override fun onCleared() {
        job?.cancel()
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
