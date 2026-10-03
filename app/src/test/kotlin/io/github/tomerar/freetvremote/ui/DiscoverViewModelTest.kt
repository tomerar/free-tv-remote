package io.github.tomerar.freetvremote.ui

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

    private fun viewModel(discovery: TvDiscovery, durationMs: Long = 1_000) =
        DiscoverViewModel(discovery, flowOf(emptyList()), scanDurationMs = durationMs)

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
}
