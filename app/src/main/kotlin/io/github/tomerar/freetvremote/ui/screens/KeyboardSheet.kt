package io.github.tomerar.freetvremote.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.tomerar.freetvremote.R
import io.github.tomerar.freetvremote.protocol.remote.KeyCodes
import io.github.tomerar.freetvremote.remote.KeyboardDraftController
import io.github.tomerar.freetvremote.remote.KeyboardStatus

/** Type on the phone, send to whatever text field is focused on the TV. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardSheet(keyboard: KeyboardDraftController, onDismiss: () -> Unit, onKey: (Int) -> Unit) {
    val draft by keyboard.draft.collectAsStateWithLifecycle()
    val status by keyboard.status.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { keyboard.onSheetOpened() }

    ModalBottomSheet(onDismissRequest = onDismiss, contentWindowInsets = { WindowInsets.ime }) {
        KeyboardPanel(
            draft = draft,
            status = status,
            onDraftChange = keyboard::onDraftChange,
            onSend = keyboard::send,
            onKey = onKey,
        )
    }
}

/** The stateless content of the keyboard sheet. */
@Composable
internal fun KeyboardPanel(
    draft: String,
    status: KeyboardStatus,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onKey: (Int) -> Unit,
) {
    val focus = remember { FocusRequester() }
    val sending = status == KeyboardStatus.Sending
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(
        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.keyboard_title), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
            label = { Text(stringResource(R.string.keyboard_hint)) },
            enabled = !sending,
            isError = status is KeyboardStatus.Failed,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
        )
        StatusLine(status)
        Text(
            stringResource(R.string.keyboard_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = { onKey(KeyCodes.DEL) }) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = null)
                Text(stringResource(R.string.keyboard_backspace), modifier = Modifier.padding(start = 8.dp))
            }
            FilledTonalButton(onClick = { onKey(KeyCodes.ENTER) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardReturn, contentDescription = null)
                Text(stringResource(R.string.keyboard_enter), modifier = Modifier.padding(start = 8.dp))
            }
            Button(onClick = onSend, enabled = draft.isNotEmpty() && !sending) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                Text(stringResource(R.string.keyboard_send), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Announced politely to TalkBack when it changes. */
@Composable
private fun StatusLine(status: KeyboardStatus) {
    val text =
        when (status) {
            KeyboardStatus.Idle -> {
                null
            }

            KeyboardStatus.Sending -> {
                stringResource(R.string.keyboard_status_sending)
            }

            KeyboardStatus.Sent -> {
                stringResource(R.string.keyboard_status_sent)
            }

            is KeyboardStatus.Failed -> {
                stringResource(
                    when (status.reason) {
                        KeyboardStatus.Failed.Reason.NOT_CONNECTED -> R.string.keyboard_error_not_connected
                        KeyboardStatus.Failed.Reason.SEND_FAILED -> R.string.keyboard_error_send_failed
                    },
                )
            }
        }
    if (text != null) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (status is KeyboardStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}
