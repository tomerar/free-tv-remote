package io.github.tomerar.freetvremote.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.tomerar.freetvremote.R
import io.github.tomerar.freetvremote.data.AppShortcut
import io.github.tomerar.freetvremote.protocol.remote.KeyCodes
import io.github.tomerar.freetvremote.protocol.remote.TvState
import io.github.tomerar.freetvremote.remote.KeyBehavior
import io.github.tomerar.freetvremote.remote.KeyGestures
import io.github.tomerar.freetvremote.ui.Haptics
import io.github.tomerar.freetvremote.ui.LocalAppContainer
import io.github.tomerar.freetvremote.ui.RemoteUiState
import io.github.tomerar.freetvremote.ui.RemoteViewModel
import io.github.tomerar.freetvremote.ui.components.DPad
import io.github.tomerar.freetvremote.ui.components.KeyRocker
import io.github.tomerar.freetvremote.ui.components.ROCKER_HEIGHT
import io.github.tomerar.freetvremote.ui.components.ROCKER_WIDTH
import io.github.tomerar.freetvremote.ui.components.RemoteKeyButton
import io.github.tomerar.freetvremote.ui.components.RockerKey
import io.github.tomerar.freetvremote.ui.rememberHaptics
import io.github.tomerar.freetvremote.ui.shortcutStyle
import io.github.tomerar.freetvremote.ui.simpleFactory

@Composable
fun RemoteScreen(
    onOpenSettings: () -> Unit,
    onAddTv: () -> Unit,
    onManageTvs: () -> Unit,
    onEditShortcuts: () -> Unit,
    onPairAgain: (host: String, name: String) -> Unit,
    onOpenDiagnostics: () -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: RemoteViewModel = viewModel(factory = simpleFactory { RemoteViewModel(container) })
    val state by vm.uiState.collectAsStateWithLifecycle()
    val haptics = rememberHaptics(state.settings.hapticsEnabled)
    var showKeyboard by remember { mutableStateOf(false) }
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

    val snackbarHost = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(vm) {
        vm.shortcutNotSent.collect { name ->
            snackbarHost.showSnackbar(resources.getString(R.string.shortcut_not_sent, name))
        }
    }

    val actions =
        remember(vm, onOpenSettings, onAddTv, onManageTvs, onEditShortcuts, onPairAgain, onOpenDiagnostics) {
            RemoteActions(
                onTap = vm::tap,
                onLaunch = vm::launch,
                onSwitchTv = vm::switchTv,
                onReconnect = vm::reconnect,
                onOpenKeyboard = { showKeyboard = true },
                navigation =
                    RemoteNavigation(
                        onOpenSettings = onOpenSettings,
                        onAddTv = onAddTv,
                        onManageTvs = onManageTvs,
                        onEditShortcuts = onEditShortcuts,
                        onOpenDiagnostics = onOpenDiagnostics,
                        onPairAgain = onPairAgain,
                    ),
            )
        }
    RemoteContent(state, vm.gestures, haptics, snackbarHost, actions)

    if (showKeyboard) {
        KeyboardSheet(
            keyboard = vm.keyboard,
            onDismiss = { showKeyboard = false },
            onKey = vm::tap,
        )
    }
}

/**
 * The remote screen without any view model: top bar with the TV name (menu) and the app settings, the connection
 * card, and below it the remote itself, laid out like a physical TV remote.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RemoteContent(
    state: RemoteUiState,
    gestures: KeyGestures,
    haptics: Haptics,
    snackbarHost: SnackbarHostState,
    actions: RemoteActions,
) {
    var showTvMenu by remember { mutableStateOf(false) }
    val switchTvDescription = stringResource(R.string.remote_switch_tv)
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = {
                    Box {
                        Row(
                            modifier =
                                Modifier
                                    .heightIn(min = 48.dp)
                                    .clickable { showTvMenu = true }
                                    .padding(vertical = 8.dp)
                                    .semantics { contentDescription = switchTvDescription },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = state.activeTv?.name ?: stringResource(R.string.status_no_tv),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                        TvMenu(
                            expanded = showTvMenu,
                            state = state,
                            onDismiss = { showTvMenu = false },
                            onSwitch = {
                                actions.onSwitchTv(it)
                                showTvMenu = false
                            },
                            onAdd = {
                                showTvMenu = false
                                actions.navigation.onAddTv()
                            },
                            onManage = {
                                showTvMenu = false
                                actions.navigation.onManageTvs()
                            },
                        )
                    }
                },
                actions = {
                    IconButton(onClick = actions.navigation.onOpenSettings, modifier = Modifier.size(48.dp)) {
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            StatusCard(
                state = state,
                onReconnect = actions.onReconnect,
                onPairAgain = actions.navigation.onPairAgain,
                onOpenKeyboard = actions.onOpenKeyboard,
                onOpenDiagnostics = actions.navigation.onOpenDiagnostics,
            )
            RemoteBody {
                RemoteControlsPanel(gestures, haptics, state.tvState, actions.onOpenKeyboard, actions.onTap)
                AppButtons(state.shortcuts, actions.onLaunch, actions.navigation.onEditShortcuts)
            }
            Spacer(Modifier.height(8.dp))
        }
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

private val BODY_MAX_WIDTH = 340.dp
private val DPAD_MAX_SIZE = 250.dp
private val KEY_SIZE = 56.dp

/** The remote's body: a rounded slab, centered, like the plastic of a physical remote. */
@Composable
private fun RemoteBody(content: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Surface(
            modifier = Modifier.width(maxWidth.coerceAtMost(BODY_MAX_WIDTH)),
            shape = RoundedCornerShape(40.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) { content() }
        }
    }
}

/**
 * The keys, laid out like a physical TV remote and identical in every language (left is always left):
 * mute and power, input and menu, the D-pad, back / keyboard / home, TV settings and guide, then the VOL and CH
 * rockers with play/pause and info between them. [onTap] is the single-tap action used by TalkBack's "activate".
 */
@Composable
internal fun RemoteControlsPanel(
    gestures: KeyGestures,
    haptics: Haptics,
    tvState: TvState,
    onKeyboard: () -> Unit = {},
    onTap: (Int) -> Unit,
) {
    @Composable
    fun key(icon: ImageVector, label: Int, code: Int, tint: Color = MaterialTheme.colorScheme.onSurface) =
        RemoteKeyButton(
            icon = icon,
            description = stringResource(label),
            code = code,
            gestures = gestures,
            haptics = haptics,
            onAccessibilityClick = onTap,
            size = KEY_SIZE,
            contentColor = tint,
        )
    val muted = tvState.isMuted == true
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ThreeColumns(
                start = {
                    key(
                        if (muted) Icons.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeOff,
                        R.string.key_mute,
                        KeyCodes.VOLUME_MUTE,
                        if (muted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                },
                end = { key(Icons.Filled.PowerSettingsNew, R.string.key_power, KeyCodes.POWER, PowerRed) },
            )
            ThreeColumns(
                start = { key(Icons.AutoMirrored.Filled.Input, R.string.key_input, KeyCodes.TV_INPUT) },
                end = { key(Icons.Filled.Menu, R.string.key_menu, KeyCodes.MENU) },
            )
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DPad(gestures = gestures, haptics = haptics, onAccessibilityClick = onTap, size = maxWidth.coerceAtMost(DPAD_MAX_SIZE))
            }
            ThreeColumns(
                start = { key(Icons.AutoMirrored.Filled.ArrowBack, R.string.key_back, KeyCodes.BACK) },
                center = { KeyboardKey(highlighted = tvState.textFieldActive, onClick = onKeyboard) },
                end = { key(Icons.Filled.Home, R.string.key_home, KeyCodes.HOME) },
            )
            ThreeColumns(
                start = { key(Icons.Filled.Tune, R.string.key_tv_settings, KeyCodes.SETTINGS) },
                end = { key(Icons.Filled.LiveTv, R.string.key_guide, KeyCodes.GUIDE) },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                KeyRocker(
                    label = stringResource(R.string.remote_label_volume),
                    groupDescription = stringResource(R.string.remote_volume_group),
                    up = RockerKey(KeyCodes.VOLUME_UP, Icons.Filled.Add, stringResource(R.string.key_volume_up)),
                    down = RockerKey(KeyCodes.VOLUME_DOWN, Icons.Filled.Remove, stringResource(R.string.key_volume_down)),
                    gestures = gestures,
                    haptics = haptics,
                    onAccessibilityClick = onTap,
                )
                Column(
                    Modifier.height(ROCKER_HEIGHT),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    key(Icons.Filled.PlayArrow, R.string.key_play_pause, KeyCodes.MEDIA_PLAY_PAUSE)
                    key(Icons.Outlined.Info, R.string.key_info, KeyCodes.INFO)
                }
                KeyRocker(
                    label = stringResource(R.string.remote_label_channel),
                    groupDescription = stringResource(R.string.remote_channel_group),
                    up = RockerKey(KeyCodes.CHANNEL_UP, Icons.Filled.KeyboardArrowUp, stringResource(R.string.key_channel_up)),
                    down = RockerKey(KeyCodes.CHANNEL_DOWN, Icons.Filled.KeyboardArrowDown, stringResource(R.string.key_channel_down)),
                    gestures = gestures,
                    haptics = haptics,
                    onAccessibilityClick = onTap,
                )
            }
        }
    }
}

/** Power keeps its red, as on a real remote. */
private val PowerRed = Color(0xFFE5484D)

/** A row with a key on each side and an optional one in the middle; the columns line up from row to row. */
@Composable
private fun ThreeColumns(
    start: @Composable () -> Unit,
    end: @Composable () -> Unit,
    center: (@Composable () -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(ROCKER_WIDTH), contentAlignment = Alignment.Center) { start() }
        Box(Modifier.width(ROCKER_WIDTH), contentAlignment = Alignment.Center) { center?.invoke() }
        Box(Modifier.width(ROCKER_WIDTH), contentAlignment = Alignment.Center) { end() }
    }
}

/** The keyboard sits where a physical remote has its microphone; it lights up when the TV is asking for text. */
@Composable
private fun KeyboardKey(highlighted: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.key_keyboard)
    val container = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
    val content = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier =
            Modifier
                .size(KEY_SIZE)
                .clip(CircleShape)
                .background(container)
                .clickable(onClickLabel = label, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Keyboard, contentDescription = null, tint = content)
    }
}

private val BADGE_SIZE = 56.dp
private val BADGE_SLOT = 76.dp
private const val APP_COLUMNS = 3

/**
 * App buttons in a grid of three, like the app keys at the bottom of a physical remote: colored circles with letters
 * (no brand logos) and the name below. The last cell edits the list.
 */
@Composable
private fun AppButtons(shortcuts: List<AppShortcut>, onLaunch: (AppShortcut) -> Unit, onEdit: () -> Unit) {
    val groupLabel = stringResource(R.string.remote_shortcuts_group)
    val cells: List<AppShortcut?> = shortcuts + listOf(null)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            Modifier.fillMaxWidth().semantics { contentDescription = groupLabel },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            cells.chunked(APP_COLUMNS).forEach { row ->
                if (row == listOf(null)) {
                    // Only the edit button left: center it under the grid.
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { EditButton(onEdit) }
                    return@forEach
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    for (i in 0 until APP_COLUMNS) {
                        Box(Modifier.width(BADGE_SLOT), contentAlignment = Alignment.TopCenter) {
                            when {
                                i >= row.size -> Unit
                                row[i] == null -> EditButton(onEdit)
                                else -> ShortcutBadge(row[i]!!, onLaunch)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutBadge(shortcut: AppShortcut, onLaunch: (AppShortcut) -> Unit) {
    val style = shortcutStyle(shortcut)
    val description = stringResource(R.string.remote_launch_shortcut, shortcut.name)
    Column(
        modifier =
            Modifier
                .width(BADGE_SLOT)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClickLabel = description, role = Role.Button) { onLaunch(shortcut) }
                .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(BADGE_SIZE).clip(CircleShape).background(style.color), contentAlignment = Alignment.Center) {
            Text(style.letters + LRM, color = style.onColor, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 1)
        }
        Text(
            text = shortcut.name + LRM,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EditButton(onEdit: () -> Unit) {
    val label = stringResource(R.string.remote_edit_shortcuts)
    Box(
        modifier =
            Modifier
                .size(BADGE_SIZE)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable(onClickLabel = label, role = Role.Button, onClick = onEdit)
                .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Keeps brand names such as "Disney+" in left-to-right order inside right-to-left text. */
private const val LRM = "‎"
