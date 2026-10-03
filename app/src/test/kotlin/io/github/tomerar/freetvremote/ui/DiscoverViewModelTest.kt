package io.github.tomerar.freetvremote.ui

import io.github.tomerar.freetvremote.data.SavedTv
import io.github.tomerar.freetvremote.discovery.DiscoveredTv
import io.github.tomerar.freetvremote.discovery.TvDiscovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val tv = DiscoveredTv("Living Room", "192.168.1.20", 6466)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        discovery: TvDiscovery,
        durationMs: Long = 1_000,
        saved: List<SavedTv> = emptyList(),
        selected: MutableList<String> = mutableListOf(),
        sameTv: Boolean = true,
        recorded: MutableList<Triple<String, String, String?>> = mutableListOf(),
        confirmed: MutableList<String> = mutableListOf(),
    ) = DiscoverViewModel(
        discovery,
        flowOf(saved),
        scanDurationMs = durationMs,
        selectTv = { selected += it },
        confirmSameTv = { _, host ->
            confirmed += host
            sameTv
        },
        recordAddress = { id, host, name -> recorded += Triple(id, host, name) },
    )

    private val savedTv = SavedTv(id = "tv-1", name = "Living Room TV", host = "192.168.1.20", pin = "AA==")

    /** A discovery that never ends by itself (like the real one) and counts how many are listening. */
    private class OpenDiscovery(
        private val results: List<DiscoveredTv> = emptyList(),
    ) : TvDiscovery {
        val active = AtomicInteger()
        val started = AtomicInteger()

        override fun discover(): Flow<List<DiscoveredTv>> =
            callbackFlow {
                started.incrementAndGet()
                active.incrementAndGet()
                trySend(results)
                awaitClose { active.decrementAndGet() }
            }
    }

    /** [DiscoverViewModel.uiState] only updates while someone collects it, like the screen does. */
    private fun TestScope.watch(vm: DiscoverViewModel) {
        backgroundScope.launch { vm.uiState.collect {} }
    }

    @Test
    fun `nothing is searched until the user asks`() =
        runTest(dispatcher) {
            val discovery = OpenDiscovery()
            val vm = viewModel(discovery)
            watch(vm)
            runCurrent()
            assertEquals(ScanPhase.IDLE, vm.uiState.value.phase)
            assertEquals(0, discovery.started.get())
        }

    @Test
    fun `a search ends by itself and stops listening`() =
        runTest(dispatcher) {
            val discovery = OpenDiscovery(listOf(tv))
            val vm = viewModel(discovery)
            watch(vm)
            vm.startScan()
            runCurrent()
            assertEquals(1, discovery.active.get())
            advanceTimeBy(500)
            assertEquals(ScanPhase.SCANNING, vm.uiState.value.phase)
            advanceTimeBy(600)
            runCurrent()
            assertEquals(0, discovery.active.get())
            assertEquals(ScanPhase.DONE, vm.uiState.value.phase)
            assertEquals(1f, vm.uiState.value.progress)
            assertEquals(listOf(tv), vm.uiState.value.devices)
        }

    @Test
    fun `a discovery error is reported as failed`() =
        runTest(dispatcher) {
            val vm = viewModel({ flow { throw IllegalStateException("no network") } })
            watch(vm)
            vm.startScan()
            runCurrent()
            assertEquals(ScanPhase.FAILED, vm.uiState.value.phase)
        }

    @Test
    fun `starting twice does not run two searches and stop cancels one`() =
        runTest(dispatcher) {
            val discovery = OpenDiscovery()
            val vm = viewModel(discovery)
            watch(vm)
            vm.startScan()
            vm.startScan()
            runCurrent()
            assertEquals(1, discovery.started.get())
            vm.stopScan()
            runCurrent()
            assertEquals(0, discovery.active.get())
            assertEquals(ScanPhase.IDLE, vm.uiState.value.phase)
        }

    @Test
    fun `finished results survive leaving the screen`() =
        runTest(dispatcher) {
            val vm = viewModel(OpenDiscovery(listOf(tv)))
            watch(vm)
            vm.startScan()
            advanceTimeBy(1_100)
            runCurrent()
            vm.stopScan()
            assertEquals(ScanPhase.DONE, vm.uiState.value.phase)
            assertEquals(listOf(tv), vm.uiState.value.devices)
        }

    @Test
    fun `choosing a TV that is already paired selects it and opens the remote without pairing`() =
        runTest(dispatcher) {
            val selected = mutableListOf<String>()
            val vm = viewModel(OpenDiscovery(), saved = listOf(savedTv), selected = selected)
            val events = mutableListOf<String>()
            vm.choose("192.168.1.20", "Living Room TV", null, { h, _, _ -> events += "pair $h" }, { events += "remote" })
            runCurrent()
            assertEquals(listOf("tv-1"), selected)
            assertEquals(listOf("remote"), events)
        }

    @Test
    fun `choosing a TV that is not paired goes on to pairing and selects nothing`() =
        runTest(dispatcher) {
            val selected = mutableListOf<String>()
            val vm = viewModel(OpenDiscovery(), saved = listOf(savedTv), selected = selected)
            val events = mutableListOf<String>()
            vm.choose("192.168.1.31", "Bedroom", "Bedroom", { h, n, svc -> events += "pair $h $n $svc" }, { events += "remote" })
            runCurrent()
            assertEquals(emptyList<String>(), selected)
            assertEquals(listOf("pair 192.168.1.31 Bedroom Bedroom"), events)
        }

    @Test
    fun `choosing a paired TV while searching stops the search`() =
        runTest(dispatcher) {
            val discovery = OpenDiscovery(listOf(tv))
            val vm = viewModel(discovery, saved = listOf(savedTv))
            watch(vm)
            vm.startScan()
            runCurrent()
            assertEquals(1, discovery.active.get())
            vm.choose("192.168.1.20", "Living Room TV", null, { _, _, _ -> }, {})
            runCurrent()
            assertEquals(0, discovery.active.get())
        }

    private val shield = SavedTv(id = "tv-2", name = "Living room", host = "192.168.1.30", pin = "AA==", serviceName = "nvidia")

    private suspend fun kotlinx.coroutines.test.TestScope.scanFindingAndWait(vm: DiscoverViewModel) {
        watch(vm)
        vm.startScan()
        runCurrent()
        advanceTimeBy(200)
        runCurrent()
    }

    @Test
    fun `a saved TV found at a new address is moved once the device is confirmed`() =
        runTest(dispatcher) {
            val recorded = mutableListOf<Triple<String, String, String?>>()
            val vm =
                viewModel(OpenDiscovery(listOf(DiscoveredTv("nvidia", "192.168.1.99", 6466))), saved = listOf(shield), recorded = recorded)
            scanFindingAndWait(vm)
            assertEquals(listOf(Triple("tv-2", "192.168.1.99", "nvidia")), recorded)
        }

    @Test
    fun `a device with the same name that is not the paired TV changes nothing`() =
        runTest(dispatcher) {
            val recorded = mutableListOf<Triple<String, String, String?>>()
            val confirmed = mutableListOf<String>()
            val vm =
                viewModel(
                    OpenDiscovery(listOf(DiscoveredTv("nvidia", "192.168.1.99", 6466))),
                    saved = listOf(shield),
                    sameTv = false,
                    recorded = recorded,
                    confirmed = confirmed,
                )
            scanFindingAndWait(vm)
            assertEquals(listOf("192.168.1.99"), confirmed)
            assertEquals(emptyList<Triple<String, String, String?>>(), recorded)
            // It stays available to pair as a new TV.
            assertEquals(
                listOf("192.168.1.99"),
                vm.uiState.value.available
                    .map { it.host },
            )
        }

    @Test
    fun `a saved TV found at its own address only gets its network name recorded, without a network check`() =
        runTest(dispatcher) {
            val recorded = mutableListOf<Triple<String, String, String?>>()
            val confirmed = mutableListOf<String>()
            val oldEntry = shield.copy(serviceName = null, name = "nvidia")
            val vm =
                viewModel(
                    OpenDiscovery(listOf(DiscoveredTv("nvidia", "192.168.1.30", 6466))),
                    saved = listOf(oldEntry),
                    recorded = recorded,
                    confirmed = confirmed,
                )
            scanFindingAndWait(vm)
            assertEquals(listOf(Triple("tv-2", "192.168.1.30", "nvidia")), recorded)
            assertEquals(emptyList<String>(), confirmed)
        }

    @Test
    fun `the same device is only checked once per search`() =
        runTest(dispatcher) {
            val confirmed = mutableListOf<String>()
            val vm =
                viewModel(
                    OpenDiscovery(listOf(DiscoveredTv("nvidia", "192.168.1.99", 6466))),
                    saved = listOf(shield),
                    sameTv = false,
                    confirmed = confirmed,
                )
            scanFindingAndWait(vm)
            advanceTimeBy(500)
            assertEquals(1, confirmed.size)
        }

    @Test
    fun `saved TVs are listed before any search, and found saved TVs are not offered for pairing`() =
        runTest(dispatcher) {
            val vm = viewModel(OpenDiscovery(listOf(tv, DiscoveredTv("New TV", "192.168.1.55", 6466))), saved = listOf(savedTv))
            watch(vm)
            runCurrent()
            assertEquals(listOf(savedTv), vm.uiState.value.saved)
            assertEquals(emptyList<DiscoveredTv>(), vm.uiState.value.available)
            vm.startScan()
            runCurrent()
            val state = vm.uiState.value
            assertEquals(listOf("192.168.1.55"), state.available.map { it.host })
            assertEquals(true, state.isNearby(savedTv))
        }
}
