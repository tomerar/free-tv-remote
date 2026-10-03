package io.github.tomerar.freetvremote.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomerar.freetvremote.R
import io.github.tomerar.freetvremote.discovery.DiscoveredTv
import io.github.tomerar.freetvremote.ui.DiscoverViewModel
import io.github.tomerar.freetvremote.ui.LocalAppContainer
import io.github.tomerar.freetvremote.ui.LocalNetworkPermission
import io.github.tomerar.freetvremote.ui.ScanPhase
import io.github.tomerar.freetvremote.ui.simpleFactory

/** Valid IPv4 literal or a plain host name (letters, digits, dots, dashes). */
internal fun isValidHost(input: String): Boolean {
    val host = input.trim()
    if (host.isEmpty() || host.length > MAX_HOST_LENGTH) return false
    val ipv4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""").matchEntire(host)
    if (ipv4 != null) return ipv4.groupValues.drop(1).all { it.toInt() in 0..MAX_OCTET }
    return Regex("""^[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)*$""").matches(host)
}

private const val MAX_HOST_LENGTH = 253
private const val MAX_OCTET = 255

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(onBack: (() -> Unit)?, onPair: (host: String, name: String) -> Unit, onOpenRemote: () -> Unit) {
    val container = LocalAppContainer.current
    val vm: DiscoverViewModel = viewModel(factory = simpleFactory { DiscoverViewModel(container) })
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val defaultName = stringResource(R.string.discover_device_default_name)
    var granted by remember { mutableStateOf(LocalNetworkPermission.isGranted(context)) }
    var askedOnce by remember { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            granted = it
            askedOnce = true
            if (it) vm.startScan()
        }

    LifecycleStartEffect(granted) {
        // The user may have granted the permission in system settings.
        granted = LocalNetworkPermission.isGranted(context)
        onStopOrDispose { vm.stopScan() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.discover_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(stringResource(R.string.discover_intro), style = MaterialTheme.typography.bodyMedium) }
            if (!granted) {
                item {
                    PermissionCard(
                        askedBefore = askedOnce,
                        onGrant = { launcher.launch(LocalNetworkPermission.PERMISSION) },
                        onOpenSettings = {
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                            )
                        },
                    )
                }
            } else {
                item { ScanStatus(state.phase, state.progress, empty = state.devices.isEmpty(), onSearch = vm::startScan) }
                items(state.devices, key = { it.name + it.host }) { device ->
                    DeviceRow(
                        device,
                        paired = device.host in state.pairedHosts,
                        onClick = { vm.choose(device.host, device.name, onPair, onOpenRemote) },
                    )
                }
                if (state.phase == ScanPhase.DONE || state.phase == ScanPhase.FAILED) {
                    if (state.devices.isNotEmpty()) {
                        item {
                            OutlinedButton(onClick = vm::startScan) { Text(stringResource(R.string.discover_rescan)) }
                        }
                    }
                }
            }
            item {
                val nothingFound = (state.phase == ScanPhase.DONE || state.phase == ScanPhase.FAILED) && state.devices.isEmpty()
                ManualEntry(forceOpen = nothingFound, onPair = { host -> vm.choose(host, defaultName, onPair, onOpenRemote) })
            }
        }
    }
}

@Composable
private fun PermissionCard(askedBefore: Boolean, onGrant: () -> Unit, onOpenSettings: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.permission_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.permission_message), style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onGrant) { Text(stringResource(R.string.permission_grant)) }
            if (askedBefore) {
                OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.permission_open_settings)) }
            }
        }
    }
}

@Composable
private fun ScanStatus(phase: ScanPhase, progress: Float, empty: Boolean, onSearch: () -> Unit) {
    when (phase) {
        ScanPhase.IDLE -> {
            Button(onClick = onSearch, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.discover_search))
            }
        }

        ScanPhase.SCANNING -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.discover_scanning), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
        }

        ScanPhase.DONE, ScanPhase.FAILED -> {
            if (empty) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val title = if (phase == ScanPhase.FAILED) R.string.discover_error_title else R.string.discover_none_title
                        val tips = if (phase == ScanPhase.FAILED) R.string.discover_error else R.string.discover_none_tips
                        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(tips), style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = onSearch) { Text(stringResource(R.string.discover_rescan)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: DiscoveredTv, paired: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Filled.Tv, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleMedium)
                Text(device.host, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (paired) Text(stringResource(R.string.discover_paired_badge), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ManualEntry(forceOpen: Boolean, onPair: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    if (!expanded && !forceOpen) {
        TextButton(onClick = { expanded = true }) { Text(stringResource(R.string.discover_manual_title)) }
        return
    }
    var host by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    val submit = {
        if (isValidHost(host)) onPair(host.trim()) else showError = true
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)) {
        Text(stringResource(R.string.discover_manual_title), style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = host,
            onValueChange = {
                host = it
                showError = false
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.discover_manual_label)) },
            placeholder = { Text(stringResource(R.string.discover_manual_hint)) },
            isError = showError,
            supportingText = { if (showError) Text(stringResource(R.string.discover_manual_invalid)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
        )
        Button(onClick = submit) { Text(stringResource(R.string.discover_manual_connect)) }
    }
}
