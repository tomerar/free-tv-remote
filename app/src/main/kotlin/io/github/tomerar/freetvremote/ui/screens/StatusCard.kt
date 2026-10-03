package io.github.tomerar.freetvremote.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.tomerar.freetvremote.R
import io.github.tomerar.freetvremote.protocol.remote.ConnectionState
import io.github.tomerar.freetvremote.protocol.remote.FailureReason
import io.github.tomerar.freetvremote.protocol.remote.TvState
import io.github.tomerar.freetvremote.ui.AppName
import io.github.tomerar.freetvremote.ui.LocalNetworkPermission
import io.github.tomerar.freetvremote.ui.RemoteUiState
import io.github.tomerar.freetvremote.ui.appNameOf
import io.github.tomerar.freetvremote.ui.connectedMinutes
import io.github.tomerar.freetvremote.ui.tvModelName
import io.github.tomerar.freetvremote.ui.volumeFraction
import kotlinx.coroutines.delay

private const val CLOCK_TICK_MS = 30_000L
private val ConnectedGreen = Color(0xFF4CD964)
private val WaitingAmber = Color(0xFFFFB020)

/**
 * The connection card. Compact by default (state of the TV, volume and app on one line, so the remote stays the
 * focus of the screen); a tap opens the details. Problems and their actions are never hidden behind the tap.
 */
@Composable
internal fun StatusCard(
    state: RemoteUiState,
    onReconnect: () -> Unit,
    onPairAgain: (String, String) -> Unit,
    onOpenKeyboard: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    now: () -> Long = System::currentTimeMillis,
) {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(LocalNetworkPermission.isGranted(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionGranted = it }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionGranted = LocalNetworkPermission.isGranted(context) }
    var expanded by rememberSaveable { mutableStateOf(false) }

    val connection = state.connection
    val failure = (connection as? ConnectionState.Failed)?.reason
    val connected = connection == ConnectionState.Connected
    val dot =
        when {
            connected -> ConnectedGreen
            failure != null -> MaterialTheme.colorScheme.error
            else -> WaitingAmber
        }
    val canExpand = connected
    val showDetails = expanded && canExpand
    val toggleLabel = stringResource(if (showDetails) R.string.status_collapse else R.string.status_expand)
    val toggleState = stringResource(if (showDetails) R.string.status_state_expanded else R.string.status_state_collapsed)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .then(
                            if (canExpand) {
                                Modifier
                                    .clickable(onClickLabel = toggleLabel, role = Role.Button) { expanded = !expanded }
                                    .semantics { stateDescription = toggleState }
                            } else {
                                Modifier
                            },
                        ).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(10.dp).background(dot, CircleShape))
                Text(
                    text = compactLine(state),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (canExpand) {
                    Icon(
                        imageVector = if (showDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                    )
                }
            }
            if (showDetails) {
                Details(state, now, onOpenKeyboard, onOpenDiagnostics)
            }
            ProblemActions(
                state = state,
                permissionGranted = permissionGranted,
                onGrant = { launcher.launch(LocalNetworkPermission.PERMISSION) },
                onReconnect = onReconnect,
                onPairAgain = onPairAgain,
            )
        }
    }
}

/** Permission, pairing and reconnect actions: shown whenever they apply, whether or not the details are open. */
@Composable
private fun ProblemActions(
    state: RemoteUiState,
    permissionGranted: Boolean,
    onGrant: () -> Unit,
    onReconnect: () -> Unit,
    onPairAgain: (String, String) -> Unit,
) {
    val tv = state.activeTv
    val failed = state.connection is ConnectionState.Failed
    val needsReconnect = !failed && tv != null && state.connection != ConnectionState.Connected
    if (permissionGranted && !failed && !needsReconnect) return
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!permissionGranted) {
            Text(stringResource(R.string.permission_banner), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onGrant) { Text(stringResource(R.string.permission_grant)) }
        }
        if (failed && tv != null) {
            Button(onClick = { onPairAgain(tv.host, tv.name) }) { Text(stringResource(R.string.action_pair_again)) }
        } else if (needsReconnect) {
            TextButton(onClick = onReconnect) { Text(stringResource(R.string.action_reconnect)) }
        }
    }
}

/** The one-line summary: connection, then what the TV reported (power, volume, app). Missing facts are left out. */
@Composable
private fun compactLine(state: RemoteUiState): String {
    val parts = mutableListOf(connectionLabel(state))
    if (state.connection == ConnectionState.Connected) {
        val tv = state.tvState
        tv.isOn?.let { parts += stringResource(if (it) R.string.tv_power_short_on else R.string.tv_power_short_off) }
        if (tv.isMuted == true) {
            parts += stringResource(R.string.tv_volume_muted)
        } else if (tv.volumeLevel != null) {
            parts += stringResource(R.string.tv_volume_short, tv.volumeLevel!!)
        }
        when (val app = appNameOf(tv)) {
            is AppName.Label -> parts += app.text
            is AppName.Known -> parts += stringResource(app.resId)
            is AppName.Package, null -> Unit
        }
    }
    return parts.joinToString(" · ")
}

@Composable
private fun Details(state: RemoteUiState, now: () -> Long, onOpenKeyboard: () -> Unit, onOpenDiagnostics: () -> Unit) {
    val tv = state.tvState
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tvModelName(tv)?.let { DetailRow(R.string.status_detail_tv, it) }
        volumeFraction(tv)?.let { fraction ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DetailRow(R.string.status_detail_volume, "${tv.volumeLevel} / ${tv.volumeMax}")
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            }
        }
        when (val app = appNameOf(tv)) {
            is AppName.Label -> DetailRow(R.string.status_detail_app, app.text)
            is AppName.Known -> DetailRow(R.string.status_detail_app, stringResource(app.resId))
            is AppName.Package -> DetailRow(R.string.status_detail_app, app.id)
            null -> Unit
        }
        state.activeTv?.let { DetailRow(R.string.status_detail_address, it.host) }
        state.connectedSince?.let { since -> ConnectedFor(since, now) }
        if (tv.textFieldActive) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.status_text_field_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onOpenKeyboard) { Text(stringResource(R.string.status_text_field_action)) }
            }
        }
        TextButton(onClick = onOpenDiagnostics) { Text(stringResource(R.string.diagnostics_title)) }
    }
}

@Composable
private fun ConnectedFor(since: Long, now: () -> Long) {
    var current by remember { mutableLongStateOf(now()) }
    LaunchedEffect(since) {
        while (true) {
            current = now()
            delay(CLOCK_TICK_MS)
        }
    }
    val minutes = connectedMinutes(since, current)
    val text =
        if (minutes < 1) {
            stringResource(R.string.status_connected_just_now)
        } else {
            pluralStringResource(R.plurals.status_connected_minutes, minutes.toInt(), minutes.toInt())
        }
    DetailRow(R.string.status_detail_connected, text)
}

@Composable
private fun DetailRow(label: Int, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun connectionLabel(state: RemoteUiState): String =
    when (val c = state.connection) {
        ConnectionState.Connected -> {
            stringResource(R.string.status_connected)
        }

        ConnectionState.Connecting -> {
            stringResource(R.string.status_connecting)
        }

        is ConnectionState.Reconnecting -> {
            stringResource(R.string.status_reconnecting, c.attempt)
        }

        is ConnectionState.Failed -> {
            when (c.reason) {
                FailureReason.NOT_PAIRED -> stringResource(R.string.status_not_paired)
                FailureReason.CERTIFICATE_MISMATCH -> stringResource(R.string.status_cert_mismatch)
            }
        }

        ConnectionState.Idle -> {
            stringResource(if (state.activeTv == null) R.string.status_no_tv else R.string.status_idle)
        }
    }
