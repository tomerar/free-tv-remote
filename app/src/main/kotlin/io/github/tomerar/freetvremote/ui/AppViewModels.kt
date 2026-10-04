package io.github.tomerar.freetvremote.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.tomerar.freetvremote.AppContainer
import io.github.tomerar.freetvremote.R
import io.github.tomerar.freetvremote.data.AppSettings
import io.github.tomerar.freetvremote.data.AppShortcut
import io.github.tomerar.freetvremote.data.SavedTv
import io.github.tomerar.freetvremote.data.ThemeMode
import io.github.tomerar.freetvremote.diagnostics.maskHost
import io.github.tomerar.freetvremote.discovery.DiscoveredTv
import io.github.tomerar.freetvremote.discovery.KnownTvAction
import io.github.tomerar.freetvremote.discovery.TvDiscovery
import io.github.tomerar.freetvremote.discovery.matchKnownTvs
import io.github.tomerar.freetvremote.protocol.remote.ConnectionState
import io.github.tomerar.freetvremote.protocol.remote.TvState
import io.github.tomerar.freetvremote.protocol.remote.confirmPinnedTv
import io.github.tomerar.freetvremote.remote.KeyGestures
import io.github.tomerar.freetvremote.remote.KeyboardDraftController
import io.github.tomerar.freetvremote.remote.PairingCoordinator
import io.github.tomerar.freetvremote.remote.PairingState
import io.github.tomerar.freetvremote.remote.ShortcutLauncher
import io.github.tomerar.freetvremote.timer.SleepTimer
import io.github.tomerar.freetvremote.timer.SleepTimerLimits
import io.github.tomerar.freetvremote.timer.SleepTimerResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

private const val STOP_TIMEOUT_MS = 5_000L

private fun <T> kotlinx.coroutines.flow.Flow<T>.state(scope: kotlinx.coroutines.CoroutineScope, initial: T): StateFlow<T> =
    stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initial)

/** The sleep timer as the screens need it: the timer and last result, plus what the user still has to allow. */
data class SleepTimerUiState(
    val active: SleepTimer? = null,
    val last: SleepTimerResult? = null,
    val exactAllowed: Boolean = true,
    val notificationsAllowed: Boolean = true,
    /** What the "new timer" form starts with: the minutes of the previous timer, or the default. */
    val initialMinutes: Int = SleepTimerLimits.DEFAULT_MINUTES,
)

data class RemoteUiState(
    val activeTv: SavedTv? = null,
    val tvs: List<SavedTv> = emptyList(),
    val connection: ConnectionState = ConnectionState.Idle,
    val tvState: TvState = TvState(),
    val connectedSince: Long? = null,
    val shortcuts: List<AppShortcut> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val sleepTimer: SleepTimerUiState = SleepTimerUiState(),
)

class RemoteViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val controller = container.remoteController

    val gestures = KeyGestures(viewModelScope, controller)

    private val timer = container.sleepTimer
    private val permissionsChanged = MutableStateFlow(0)
    private val _timerMessages = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    /** Short messages about the sleep timer (a string resource), shown once. */
    val timerMessages: SharedFlow<Int> = _timerMessages

    private val sleepTimerState: Flow<SleepTimerUiState> =
        combine(timer.state, permissionsChanged) { state, _ ->
            SleepTimerUiState(
                active = state.active,
                last = state.last,
                initialMinutes = state.lastMinutes ?: SleepTimerLimits.DEFAULT_MINUTES,
                exactAllowed = container.sleepTimerScheduler.canScheduleExact,
                notificationsAllowed = container.sleepTimerNotifier.enabled(),
            )
        }

    fun startSleepTimer(minutes: Int) {
        val tv = controller.activeTv.value ?: return
        viewModelScope.launch { timer.start(tv, minutes) }
    }

    fun extendSleepTimer(minutes: Int) {
        viewModelScope.launch { timer.extend(minutes) }
    }

    fun cancelSleepTimer() {
        viewModelScope.launch { if (!timer.cancel()) _timerMessages.emit(R.string.timer_too_late) }
    }

    fun dismissSleepResult() {
        viewModelScope.launch { timer.dismissResult() }
    }

    /** Call when the app returns from the system settings: the user may have changed a permission there. */
    fun refreshSleepPermissions() {
        permissionsChanged.value++
    }

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
            combine(controller.tvState, controller.connectedSince) { tvState, since -> tvState to since },
            combine(
                container.shortcutsRepository.enabled,
                container.settingsRepository.settings,
                sleepTimerState,
            ) { shortcuts, settings, timer -> Triple(shortcuts, settings, timer) },
        ) { active, tvs, connection, tv, rest ->
            RemoteUiState(active, tvs, connection, tv.first, tv.second, rest.first, rest.second, rest.third)
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
    /** Everything the search found so far, including TVs that are already saved. */
    val devices: List<DiscoveredTv> = emptyList(),
    /** The saved (paired) TVs, always shown, searching or not. */
    val saved: List<SavedTv> = emptyList(),
) {
    /** Found TVs that are not saved yet: the ones that can be paired. */
    val available: List<DiscoveredTv> get() = devices.filter { found -> saved.none { it.host == found.host } }

    /** A saved TV that answered the search is shown as found on the network. */
    fun isNearby(tv: SavedTv): Boolean = devices.any { it.host == tv.host }
}

/**
 * Searches only when asked and stops by itself after [scanDurationMs], so nothing keeps listening on the
 * network (and draining the battery) while the user looks at the screen.
 */
class DiscoverViewModel(
    private val discovery: TvDiscovery,
    private val savedTvs: Flow<List<SavedTv>>,
    private val scanDurationMs: Long = SCAN_DURATION_MS,
    private val log: (String) -> Unit = {},
    private val selectTv: suspend (String) -> Unit = {},
    /** Is the device at this address the saved TV (does it present the pinned key)? */
    private val confirmSameTv: suspend (tv: SavedTv, host: String) -> Boolean = { _, _ -> false },
    private val recordAddress: suspend (id: String, host: String, serviceName: String?) -> Unit = { _, _, _ -> },
) : ViewModel() {
    constructor(container: AppContainer) : this(
        container.discovery,
        container.tvRepository.tvs,
        log = { container.eventLog.log("Discovery", it) },
        selectTv = container.remoteController::selectTv,
        confirmSameTv = { tv, host -> confirmPinnedTv(container.identityProvider.get(), host, tv.remotePort, tv.pinBytes) },
        recordAddress = container.tvRepository::updateAddress,
    )

    private val scan = MutableStateFlow(DiscoverUiState())
    private var job: Job? = null
    private val handled =
        java.util.concurrent.ConcurrentHashMap
            .newKeySet<String>()

    val uiState: StateFlow<DiscoverUiState> =
        combine(scan, savedTvs) { s, tvs -> s.copy(saved = tvs) }.state(viewModelScope, DiscoverUiState())

    val hasSavedTvs: StateFlow<Boolean> =
        savedTvs
            .map { it.isNotEmpty() }
            .state(viewModelScope, false)

    /** Starts a bounded search. Ignored while one is already running. */
    fun startScan() {
        if (job?.isActive == true) return
        log("search started")
        handled.clear()
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
                        .collect { found ->
                            scan.update { it.copy(devices = found) }
                            launch { recognizeSavedTvs(found) }
                        }
                }
                ticker.cancel()
                log(if (failed) "search failed" else "search finished: ${scan.value.devices.size} TV(s) found")
                scan.update { it.copy(phase = if (failed) ScanPhase.FAILED else ScanPhase.DONE, progress = 1f) }
            }
    }

    /**
     * A saved TV that answers the search at a new address (the router gave it another one) is pointed at that
     * address, so it does not have to be paired again. The device is first checked against the pinned key: only the
     * paired TV itself can pass, so a different device with the same name (say, at someone else's home) never
     * hijacks a saved entry.
     */
    private suspend fun recognizeSavedTvs(found: List<DiscoveredTv>) {
        for (action in matchKnownTvs(savedTvs.first(), found)) {
            if (!handled.add("${action.saved.id}@${action.found.host}")) continue
            val where = maskHost(action.found.host)
            when (action) {
                is KnownTvAction.RememberName -> {
                    recordAddress(action.saved.id, action.found.host, action.found.name)
                }

                is KnownTvAction.MoveAddress -> {
                    if (confirmSameTv(action.saved, action.found.host)) {
                        recordAddress(action.saved.id, action.found.host, action.found.name)
                        log("a saved TV moved: now at $where (was ${maskHost(action.saved.host)})")
                    } else {
                        log("a TV with the name of a saved TV answered at $where, but it is not the same device")
                    }
                }
            }
        }
    }

    /**
     * The user chose the TV at [host]. A TV that is already paired is simply selected and the remote opens
     * ([onOpenRemote]); pairing it a second time would only ask for a code the app does not need. Anything else
     * goes on to pairing ([onPair]).
     */
    fun choose(
        host: String,
        name: String,
        serviceName: String?,
        onPair: (host: String, name: String, serviceName: String?) -> Unit,
        onOpenRemote: () -> Unit,
    ) {
        viewModelScope.launch {
            val saved = savedTvs.first().firstOrNull { it.host == host }
            if (saved == null) {
                onPair(host, name, serviceName)
            } else {
                stopScan()
                selectTv(saved.id)
                log("selected the already paired TV at ${maskHost(host)}")
                onOpenRemote()
            }
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
    private val serviceName: String? = null,
) : ViewModel() {
    private val coordinator =
        PairingCoordinator(
            viewModelScope,
            container.identityProvider,
            container.tvRepository,
            log = { container.eventLog.log("Pairing", it) },
        )

    val state: StateFlow<PairingState> = coordinator.state

    init {
        coordinator.start(name, host, serviceName = serviceName)
    }

    fun submit(code: String) = coordinator.submit(code)

    fun retry() = coordinator.start(name, host, serviceName = serviceName)

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
