package io.github.tomerar.freetvremote.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomerar.freetvremote.R
import io.github.tomerar.freetvremote.data.AppShortcut
import io.github.tomerar.freetvremote.protocol.remote.ConnectionState
import io.github.tomerar.freetvremote.protocol.remote.FailureReason
import io.github.tomerar.freetvremote.protocol.remote.KeyCodes
import io.github.tomerar.freetvremote.protocol.remote.TvState
import io.github.tomerar.freetvremote.remote.KeyBehavior
import io.github.tomerar.freetvremote.remote.KeyGestures
import io.github.tomerar.freetvremote.ui.Haptics
import io.github.tomerar.freetvremote.ui.LocalAppContainer
import io.github.tomerar.freetvremote.ui.LocalNetworkPermission
import io.github.tomerar.freetvremote.ui.RemoteUiState
import io.github.tomerar.freetvremote.ui.RemoteViewModel
import io.github.tomerar.freetvremote.ui.components.DPad
import io.github.tomerar.freetvremote.ui.components.RemoteKeyButton
import io.github.tomerar.freetvremote.ui.rememberHaptics
import io.github.tomerar.freetvremote.ui.simpleFactory
import androidx.lifecycle.viewmodel.compose.viewModel as composeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
    onOpenSettings: () -> Unit,
    onAddTv: () -> Unit,
    onManageTvs: () -> Unit,
    onEditShortcuts: () -> Unit,
    onPairAgain: (host: String, name: String) -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: RemoteViewModel = viewModel(factory = simpleFactory { RemoteViewModel(container) })
    val state by vm.uiState.collectAsStateWithLifecycle()
    val haptics = rememberHaptics(state.settings.hapticsEnabled)
    var showKeyboard by remember { mutableStateOf(false) }
    var showTvMenu by remember { mutableStateOf(false) }
    val view = LocalView.current

    // Hardware volume buttons drive the TV only while this screen is visible and the setting is on.
    DisposableEffect(state.settings.useVolumeKeys) {
        vm.volumeKeys.active = state.settings.useVolumeKeys
        onDispose { vm.volumeKeys.active = false }
    }
    DisposableEffect(state.settings.keepScreenOn) {
        view.keepScreenOn = state.settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }
    // A finger can be lost while the app is paused; never leave a key held down on the TV.
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { vm.gestures.releaseAll() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        Row(
                            modifier =
                                Modifier
                                    .clickable { showTvMenu = true }
                                    .padding(vertical = 8.dp)
                                    .semantics { contentDescription = view.context.getString(R.string.remote_switch_tv) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = state.activeTv?.name ?: stringResource(R.string.status_no_tv),
                                maxLines = 1,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Icon(Icons.Filled.MoreVert, contentDescription = null)
                        }
                        TvMenu(
                            expanded = showTvMenu,
                            state = state,
                            onDismiss = { showTvMenu = false },
                            onSwitch = {
                                vm.switchTv(it)
                                showTvMenu = false
                            },
                            onAdd = {
                                showTvMenu = false
                                onAddTv()
                            },
                            onManage = {
                                showTvMenu = false
                                onManageTvs()
                            },
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showKeyboard = true }) {
                        Icon(Icons.Filled.Keyboard, contentDescription = stringResource(R.string.key_keyboard))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCard(state, vm::reconnect, onPairAgain)
            RemoteControlsPanel(vm.gestures, haptics, vm::tap)
            ShortcutRow(state.shortcuts, onLaunch = vm::launch, onEdit = onEditShortcuts)
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showKeyboard) {
        KeyboardSheet(
            keyboard = vm.keyboard,
            onDismiss = { showKeyboard = false },
            onKey = vm::tap,
        )
    }
}

@Composable
private fun TvMenu(
    expanded: Boolean,
    state: RemoteUiState,
    onDismiss: () -> Unit,
    onSwitch: (String) -> Unit,
    onAdd: () -> Unit,
    onManage: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        state.tvs.forEach { tv ->
            DropdownMenuItem(
                text = { Text(tv.name) },
                onClick = { onSwitch(tv.id) },
                trailingIcon = { if (tv.id == state.activeTv?.id) Icon(Icons.Filled.Check, contentDescription = null) },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.tvs_add)) },
            onClick = onAdd,
            leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.tvs_title)) },
            onClick = onManage,
            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
        )
    }
}

@Composable
private fun StatusCard(state: RemoteUiState, onReconnect: () -> Unit, onPairAgain: (String, String) -> Unit) {
    val context = LocalContext.current
    var permissionGranted by remember { mutableStateOf(LocalNetworkPermission.isGranted(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionGranted = it }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionGranted = LocalNetworkPermission.isGranted(context) }

    val connection = state.connection
    val failure = (connection as? ConnectionState.Failed)?.reason
    val connected = connection == ConnectionState.Connected
    val dot =
        when {
            connected -> Color(0xFF4CD964)
            failure != null -> MaterialTheme.colorScheme.error
            else -> Color(0xFFFFB020)
        }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(10.dp).background(dot, CircleShape))
                Text(connectionLabel(state), style = MaterialTheme.typography.titleSmall)
            }
            if (connected) TvDetails(state.tvState)
            if (!permissionGranted) {
                Text(stringResource(R.string.permission_banner), style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { launcher.launch(LocalNetworkPermission.PERMISSION) }) {
                    Text(stringResource(R.string.permission_grant))
                }
            }
            when {
                failure != null && state.activeTv != null -> {
                    Button(onClick = { onPairAgain(state.activeTv.host, state.activeTv.name) }) {
                        Text(stringResource(R.string.action_pair_again))
                    }
                }

                !connected && state.activeTv != null -> {
                    TextButton(onClick = onReconnect) {
                        Text(stringResource(R.string.action_reconnect))
                    }
                }
            }
        }
    }
}

@Composable
private fun TvDetails(tv: TvState) {
    val parts =
        buildList {
            tv.isOn?.let { add(stringResource(if (it) R.string.tv_power_on else R.string.tv_power_off)) }
            if (tv.isMuted == true) {
                add(stringResource(R.string.tv_volume_muted))
            } else if (tv.volumeLevel != null && tv.volumeMax != null) {
                add(stringResource(R.string.tv_volume, tv.volumeLevel!!, tv.volumeMax!!))
            }
            tv.currentApp?.let { add(stringResource(R.string.tv_current_app, it)) }
        }
    parts.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
            if (state.activeTv == null) stringResource(R.string.status_no_tv) else stringResource(R.string.status_idle)
        }
    }

/** All the keys of the remote. [onTap] is the single-tap action used by TalkBack's "activate". */
@Composable
internal fun RemoteControlsPanel(gestures: KeyGestures, haptics: Haptics, onTap: (Int) -> Unit) {
    val tap = onTap
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        @Composable
        fun key(
            icon: ImageVector,
            label: Int,
            code: Int,
            behavior: KeyBehavior = KeyBehavior.TAP_OR_LONG,
            container: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor: Color = MaterialTheme.colorScheme.onSurface,
        ) = RemoteKeyButton(
            icon = icon,
            description = stringResource(label),
            code = code,
            gestures = gestures,
            haptics = haptics,
            onAccessibilityClick = tap,
            behavior = behavior,
            container = container,
            contentColor = contentColor,
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            key(
                Icons.Filled.PowerSettingsNew,
                R.string.key_power,
                KeyCodes.POWER,
                container = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
            key(Icons.Filled.VolumeOff, R.string.key_mute, KeyCodes.VOLUME_MUTE)
        }
        DPad(gestures = gestures, haptics = haptics, onAccessibilityClick = tap)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            key(Icons.AutoMirrored.Filled.ArrowBack, R.string.key_back, KeyCodes.BACK)
            key(Icons.Filled.Home, R.string.key_home, KeyCodes.HOME)
            key(Icons.Filled.Menu, R.string.key_menu, KeyCodes.MENU)
        }
        // Volume (− left, + right) and transport controls keep their physical direction in right-to-left layouts.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                key(Icons.Filled.Remove, R.string.key_volume_down, KeyCodes.VOLUME_DOWN, KeyBehavior.REPEAT)
                Icon(
                    Icons.Filled.VolumeUp,
                    contentDescription = stringResource(R.string.remote_volume_group),
                    tint = MaterialTheme.colorScheme.primary,
                )
                key(Icons.Filled.Add, R.string.key_volume_up, KeyCodes.VOLUME_UP, KeyBehavior.REPEAT)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                key(Icons.Filled.FastRewind, R.string.key_rewind, KeyCodes.MEDIA_REWIND, KeyBehavior.REPEAT)
                key(Icons.Filled.PlayArrow, R.string.key_play_pause, KeyCodes.MEDIA_PLAY_PAUSE)
                key(Icons.Filled.FastForward, R.string.key_forward, KeyCodes.MEDIA_FAST_FORWARD, KeyBehavior.REPEAT)
            }
        }
    }
}

@Composable
private fun ShortcutRow(shortcuts: List<AppShortcut>, onLaunch: (AppShortcut) -> Unit, onEdit: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.remote_shortcuts_group), style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.remote_edit_shortcuts))
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shortcuts, key = { it.id }) { shortcut ->
                val description = stringResource(R.string.remote_launch_shortcut, shortcut.name)
                AssistChip(
                    onClick = { onLaunch(shortcut) },
                    label = { Text(shortcut.name + LRM) },
                    modifier =
                        Modifier
                            .height(48.dp)
                            .semantics { contentDescription = description },
                    shape = RoundedCornerShape(24.dp),
                )
            }
        }
    }
}

/** Keeps brand names such as "Disney+" in left-to-right order inside right-to-left text. */
private const val LRM = "‎"
