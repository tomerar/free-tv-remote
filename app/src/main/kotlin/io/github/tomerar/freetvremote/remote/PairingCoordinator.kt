package io.github.tomerar.freetvremote.remote

import io.github.tomerar.freetvremote.data.SavedTv
import io.github.tomerar.freetvremote.data.TvRepository
import io.github.tomerar.freetvremote.diagnostics.maskHost
import io.github.tomerar.freetvremote.protocol.pairing.PairingClient
import io.github.tomerar.freetvremote.protocol.pairing.PairingException
import io.github.tomerar.freetvremote.protocol.pairing.PairingSecret
import io.github.tomerar.freetvremote.protocol.pairing.PairingSession
import io.github.tomerar.freetvremote.protocol.tls.ClientIdentity
import io.github.tomerar.freetvremote.protocol.tls.publicKeyPin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.security.GeneralSecurityException

enum class PairingFailure {
    /** The TV could not be reached (off, wrong address, other network, no permission). */
    UNREACHABLE,

    /** The TV refused the code or ended the pairing. */
    REJECTED,

    /** The TV answered in a way we do not understand. */
    UNEXPECTED,

    /** This phone could not prepare its security key or save the pairing (storage problem). */
    INTERNAL,
}

sealed interface PairingState {
    data object Idle : PairingState

    data object Connecting : PairingState

    /** The TV shows a code. [lastCodeWasInvalid] is set after a mistyped code. */
    data class AwaitingCode(
        val lastCodeWasInvalid: Boolean = false,
    ) : PairingState

    data object Verifying : PairingState

    data class Success(
        val tv: SavedTv,
    ) : PairingState

    data class Failed(
        val reason: PairingFailure,
    ) : PairingState
}

fun interface PairingClientFactory {
    fun create(identity: ClientIdentity, host: String, port: Int): PairingClient
}

/** Drives one pairing attempt. Owned by a ViewModel so it survives rotation. */
class PairingCoordinator(
    private val scope: CoroutineScope,
    private val identity: IdentityProvider,
    private val tvs: TvRepository,
    private val clients: PairingClientFactory = PairingClientFactory { id, host, port -> PairingClient(id, host, port) },
    /** Receives short, non-sensitive progress notes (never the code, keys or certificates). */
    private val log: (String) -> Unit = {},
) {
    private val _state = MutableStateFlow<PairingState>(PairingState.Idle)
    val state: StateFlow<PairingState> = _state

    private var job: Job? = null
    private var session: PairingSession? = null
    private var target: Target? = null

    private data class Target(
        val name: String,
        val host: String,
        val pairingPort: Int,
        val remotePort: Int,
    )

    fun start(
        name: String,
        host: String,
        pairingPort: Int = SavedTv.DEFAULT_PAIRING_PORT,
        remotePort: Int = SavedTv.DEFAULT_REMOTE_PORT,
    ) {
        cancel()
        target = Target(name, host, pairingPort, remotePort)
        log("pairing: connecting to ${maskHost(host)}:$pairingPort")
        _state.value = PairingState.Connecting
        job =
            scope.launch {
                try {
                    session = clients.create(identity.get(), host, pairingPort).begin()
                    log("pairing: the TV shows a code")
                    _state.value = PairingState.AwaitingCode()
                } catch (e: PairingException) {
                    fail(e.toFailure(), e)
                } catch (e: IOException) {
                    fail(PairingFailure.INTERNAL, e) // identity could not be loaded or stored
                } catch (e: GeneralSecurityException) {
                    fail(PairingFailure.INTERNAL, e)
                }
            }
    }

    /** Submits what the user typed. A mistyped code keeps the session open for another try. */
    fun submit(rawCode: String) {
        val current = session ?: return
        val info = target ?: return
        if (_state.value !is PairingState.AwaitingCode) return
        if (!PairingSecret.isWellFormedCode(PairingSecret.normalizeCode(rawCode))) {
            _state.value = PairingState.AwaitingCode(lastCodeWasInvalid = true)
            return
        }
        log("pairing: code entered, verifying with the TV")
        _state.value = PairingState.Verifying
        job =
            scope.launch {
                try {
                    val certificate = current.submitCode(rawCode)
                    val saved = tvs.savePaired(info.name, info.host, certificate.publicKeyPin(), info.remotePort, info.pairingPort)
                    log("pairing: TV accepted the code and the pairing was saved")
                    closeSession()
                    log("pairing: pairing connection closed")
                    _state.value = PairingState.Success(saved)
                } catch (e: PairingException.InvalidCode) {
                    log("pairing: the code was wrong, waiting for another try")
                    _state.value = PairingState.AwaitingCode(lastCodeWasInvalid = true)
                } catch (e: PairingException) {
                    closeSession()
                    fail(e.toFailure(), e)
                } catch (e: IOException) {
                    closeSession() // the TV accepted, but the pairing could not be stored on this phone
                    fail(PairingFailure.INTERNAL, e)
                }
            }
    }

    /** Aborts any attempt in progress and returns to [PairingState.Idle]. */
    fun cancel() {
        job?.cancel()
        job = null
        closeSession()
        _state.value = PairingState.Idle
    }

    private fun fail(reason: PairingFailure, cause: Exception) {
        log("pairing: failed ($reason): ${cause::class.simpleName}")
        _state.value = PairingState.Failed(reason)
    }

    private fun closeSession() {
        session?.close()
        session = null
    }

    private fun PairingException.toFailure(): PairingFailure =
        when (this) {
            is PairingException.ConnectionFailed -> PairingFailure.UNREACHABLE
            is PairingException.Rejected, is PairingException.InvalidCode -> PairingFailure.REJECTED
            is PairingException.ProtocolError -> PairingFailure.UNEXPECTED
        }
}
