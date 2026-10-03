package io.github.tomerar.freetvremote.remote

import io.github.tomerar.freetvremote.data.AppShortcut
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

/**
 * Opens an app shortcut on the TV and reports when the command could not be sent. A tap on a shortcut is a
 * single discrete action, so (unlike navigation keys) it is worth telling the user when nothing happened.
 * "Not sent" is all this knows: a successful send does not prove the TV opened the app.
 */
class ShortcutLauncher(
    private val scope: CoroutineScope,
    private val launchApp: suspend (String) -> Boolean,
) {
    private val _notSent = MutableSharedFlow<String>(extraBufferCapacity = BUFFER)

    /** Names of the shortcuts whose command could not be sent. */
    val notSent: SharedFlow<String> = _notSent

    fun launch(shortcut: AppShortcut) {
        scope.launch {
            if (!launchApp(shortcut.link)) _notSent.tryEmit(shortcut.name)
        }
    }

    private companion object {
        const val BUFFER = 4
    }
}
