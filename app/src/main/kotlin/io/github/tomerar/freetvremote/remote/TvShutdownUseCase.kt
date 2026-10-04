package io.github.tomerar.freetvremote.remote

import io.github.tomerar.freetvremote.data.SavedTv
import io.github.tomerar.freetvremote.data.TvRepository
import io.github.tomerar.freetvremote.protocol.remote.ConnectionState
import io.github.tomerar.freetvremote.protocol.remote.KeyCodes
import io.github.tomerar.freetvremote.protocol.remote.RemoteSession
import io.github.tomerar.freetvremote.protocol.remote.TvState
import io.github.tomerar.freetvremote.timer.SleepOutcome
import io.github.tomerar.freetvremote.timer.TvShutdown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Switches one specific saved TV off, safely. Android TV has one power key that toggles, so a key sent to a TV that is
 * already off would turn it on. Therefore it sends the key only when the TV itself reports, on this very connection,
 * that it is on; with no such report (or no connection) it sends nothing and says so.
 *
 * It never retries after the key was written: a second toggle could undo the first.
 */
class TvShutdownUseCase(
    private val scope: CoroutineScope,
    private val tvs: TvRepository,
    private val controller: RemoteController,
    private val factory: SessionFactory,
    private val connectTimeoutMs: Long = CONNECT_TIMEOUT_MS,
    private val reportTimeoutMs: Long = REPORT_TIMEOUT_MS,
    private val offReportTimeoutMs: Long = OFF_REPORT_TIMEOUT_MS,
    private val totalTimeoutMs: Long = TOTAL_TIMEOUT_MS,
    private val log: (String) -> Unit = {},
) : TvShutdown {
    override suspend fun run(tvId: String): SleepOutcome {
        val tv = tvs.tvs.first().firstOrNull { it.id == tvId } ?: return SleepOutcome.TV_REMOVED
        log("shutting down: open remote on this TV=${controller.activeTv.value?.id == tvId}, connection=${controller.connection.value}")
        var keySent = false
        val outcome = withTimeoutOrNull(totalTimeoutMs) { shutDown(tv) { keySent = true } }
        // Running out of time after the key went out means "sent", never "could not be reached".
        return outcome ?: if (keySent) SleepOutcome.SENT else SleepOutcome.UNREACHABLE
    }

    private suspend fun shutDown(tv: SavedTv, onKeySent: () -> Unit): SleepOutcome {
        if (controller.activeTv.value?.id == tv.id && controller.connection.value == ConnectionState.Connected) {
            // The open remote is already talking to this TV (a second connection could knock it off).
            val outcome = decide(controller.tvState, onKeySent) { controller.pressKey(KeyCodes.POWER) }
            if (outcome != SleepOutcome.UNKNOWN_STATE) return outcome
        }
        repeat(ATTEMPTS) { attempt ->
            val session = createOrNull(tv) ?: return SleepOutcome.FAILED
            try {
                session.start()
                val connected =
                    withTimeoutOrNull(connectTimeoutMs) {
                        session.connectionState.first { it == ConnectionState.Connected || it is ConnectionState.Failed }
                    }
                log("attempt ${attempt + 1}: connection ${connected ?: "timed out"}")
                if (connected == ConnectionState.Connected) {
                    return decide(session.tvState, onKeySent) { session.pressKey(KeyCodes.POWER) }
                }
                log("could not connect (attempt ${attempt + 1})")
            } finally {
                session.stop()
            }
        }
        return SleepOutcome.UNREACHABLE
    }

    private suspend fun decide(tvState: StateFlow<TvState>, onKeySent: () -> Unit, press: suspend () -> Boolean): SleepOutcome {
        val report = withTimeoutOrNull(reportTimeoutMs) { tvState.first { it.isOnFresh } }
        if (report == null) {
            log("the TV sent no power report, nothing was sent")
            return SleepOutcome.UNKNOWN_STATE
        }
        log("the TV reports it is ${if (report.isOn == true) "on" else "off"}")
        if (report.isOn != true) return SleepOutcome.ALREADY_OFF
        if (!press()) return SleepOutcome.FAILED
        onKeySent()
        log("power key sent")
        val off = withTimeoutOrNull(offReportTimeoutMs) { tvState.first { it.isOnFresh && it.isOn == false } }
        log("standby report after the key: ${off != null}")
        return if (off != null) SleepOutcome.TURNED_OFF else SleepOutcome.SENT
    }

    private suspend fun createOrNull(tv: SavedTv): RemoteSession? =
        try {
            factory.create(scope, tv)
        } catch (e: IOException) {
            log("could not build the session: ${e::class.simpleName}")
            null
        } catch (e: GeneralSecurityException) {
            log("could not build the session: ${e::class.simpleName}")
            null
        }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000L
        const val REPORT_TIMEOUT_MS = 4_000L
        const val OFF_REPORT_TIMEOUT_MS = 8_000L
        const val TOTAL_TIMEOUT_MS = 45_000L
        const val ATTEMPTS = 2
    }
}
