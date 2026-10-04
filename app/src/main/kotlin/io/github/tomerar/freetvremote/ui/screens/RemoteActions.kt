package io.github.tomerar.freetvremote.ui.screens

import io.github.tomerar.freetvremote.data.AppShortcut

/** Where the remote screen can send the user. */
internal class RemoteNavigation(
    val onOpenSettings: () -> Unit,
    val onAddTv: () -> Unit,
    val onManageTvs: () -> Unit,
    val onEditShortcuts: () -> Unit,
    val onOpenDiagnostics: () -> Unit,
    val onPairAgain: (host: String, name: String) -> Unit,
)

/** What the sleep timer sheet can ask for. */
internal class SleepTimerActions(
    val onStart: (minutes: Int) -> Unit,
    val onExtend: (minutes: Int) -> Unit,
    val onCancel: () -> Unit,
    val onDismissResult: () -> Unit,
    val onOpenExactSettings: () -> Unit,
)

/** Everything the remote screen can ask for; bundled so the stateless content stays easy to call and to test. */
internal class RemoteActions(
    val onTap: (Int) -> Unit,
    val onLaunch: (AppShortcut) -> Unit,
    val onSwitchTv: (String) -> Unit,
    val onReconnect: () -> Unit,
    val onOpenKeyboard: () -> Unit,
    val timer: SleepTimerActions,
    val navigation: RemoteNavigation,
)
