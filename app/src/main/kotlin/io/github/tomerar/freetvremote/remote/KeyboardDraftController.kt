package io.github.tomerar.freetvremote.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** What the keyboard sheet knows about the last send. "Sent" means the write succeeded, not that the TV applied it. */
sealed interface KeyboardStatus {
    data object Idle : KeyboardStatus

    data object Sending : KeyboardStatus

    /** The text was written to the connection (the TV gives no acknowledgement we can rely on). */
    data object Sent : KeyboardStatus

    data class Failed(
        val reason: Reason,
    ) : KeyboardStatus {
        enum class Reason { NOT_CONNECTED, SEND_FAILED }
    }
}

/**
 * Owns the text the user is typing for the TV. The draft is only cleared after a successful send; a
 * failure keeps it so nothing is lost. It lives in a ViewModel, so rotation and dismissing the sheet
 * cannot corrupt it, and results are applied only for the submission that is still current.
 */
class KeyboardDraftController(
    private val scope: CoroutineScope,
    private val isConnected: () -> Boolean,
    private val sendText: suspend (String) -> Boolean,
    private val sendTimeoutMs: Long = SEND_TIMEOUT_MS,
) {
    private val _draft = MutableStateFlow("")
    private val _status = MutableStateFlow<KeyboardStatus>(KeyboardStatus.Idle)
    private var submission = 0L

    val draft: StateFlow<String> = _draft
    val status: StateFlow<KeyboardStatus> = _status

    /** Editing is blocked while a send is in flight, so a finishing send can never clear newer text. */
    fun onDraftChange(text: String) {
        if (_status.value == KeyboardStatus.Sending) return
        _draft.value = text
        if (_status.value != KeyboardStatus.Idle) _status.value = KeyboardStatus.Idle
    }

    /** A stale result from an earlier visit must not greet the user when the sheet is reopened. */
    fun onSheetOpened() {
        if (_status.value != KeyboardStatus.Sending) _status.value = KeyboardStatus.Idle
    }

    fun send() {
        val text = _draft.value
        if (text.isEmpty() || _status.value == KeyboardStatus.Sending) return
        if (!isConnected()) {
            _status.value = KeyboardStatus.Failed(KeyboardStatus.Failed.Reason.NOT_CONNECTED)
            return
        }
        val id = ++submission
        _status.value = KeyboardStatus.Sending
        scope.launch {
            val ok = withTimeoutOrNull(sendTimeoutMs) { sendText(text) } == true
            if (id != submission) return@launch // superseded; never touch the newer submission's state
            if (ok) {
                _draft.value = ""
                _status.value = KeyboardStatus.Sent
            } else {
                _status.value = KeyboardStatus.Failed(KeyboardStatus.Failed.Reason.SEND_FAILED)
            }
        }
    }

    companion object {
        const val SEND_TIMEOUT_MS = 5_000L
    }
}
