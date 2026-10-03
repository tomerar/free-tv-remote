package io.github.tomerar.freetvremote.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import io.github.tomerar.freetvremote.AppContainer
import io.github.tomerar.freetvremote.data.AppShortcut
import io.github.tomerar.freetvremote.data.SavedTv
import io.github.tomerar.freetvremote.data.ThemeMode
import io.github.tomerar.freetvremote.protocol.remote.ConnectionState
import io.github.tomerar.freetvremote.protocol.remote.TvState
import io.github.tomerar.freetvremote.remote.KeyGestures
import io.github.tomerar.freetvremote.remote.KeySender
import io.github.tomerar.freetvremote.ui.screens.DiagnosticsScreen
import io.github.tomerar.freetvremote.ui.screens.DiscoverScreen
import io.github.tomerar.freetvremote.ui.screens.PairScreen
import io.github.tomerar.freetvremote.ui.screens.RemoteActions
import io.github.tomerar.freetvremote.ui.screens.RemoteContent
import io.github.tomerar.freetvremote.ui.screens.RemoteControlsPanel
import io.github.tomerar.freetvremote.ui.screens.RemoteNavigation
import io.github.tomerar.freetvremote.ui.screens.RemoteScreen
import io.github.tomerar.freetvremote.ui.screens.SettingsScreen
import io.github.tomerar.freetvremote.ui.screens.ShortcutsScreen
import io.github.tomerar.freetvremote.ui.screens.StatusCard
import io.github.tomerar.freetvremote.ui.screens.TvsScreen
import io.github.tomerar.freetvremote.ui.theme.FreeTvRemoteTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders every screen to PNG files under `build/screenshots` for visual review, and doubles as a
 * smoke test: no screen may crash on first composition, in English or Hebrew.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreensScreenshotTest {
    @get:Rule
    val compose = createComposeRule()

    private val container: AppContainer by lazy {
        val app = ApplicationProvider.getApplicationContext<Application>()
        AppContainer(app).also { c ->
            runBlocking {
                c.tvRepository.savePaired("Living Room Shield", "192.168.1.20", byteArrayOf(1, 2, 3), 6466, 6467)
                c.tvRepository.savePaired("Bedroom TCL", "192.168.1.31", byteArrayOf(4, 5, 6), 6466, 6467)
            }
        }
    }

    private fun shoot(name: String, content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                FreeTvRemoteTheme(ThemeMode.DARK, dynamicColor = false) {
                    Surface(Modifier, color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        }
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun remote() =
        shoot("screen_remote") {
            RemoteScreen(onOpenSettings = {}, onAddTv = {}, onManageTvs = {}, onEditShortcuts = {}, onPairAgain = {
                _,
                _,
                ->
            }, onOpenDiagnostics = {})
        }

    @Test
    fun remoteScreen() = remote()

    @Test
    @Config(qualifiers = "he-w411dp-h891dp-xxhdpi")
    fun remoteScreenHebrew() =
        shoot("screen_remote_he") {
            RemoteScreen(onOpenSettings = {}, onAddTv = {}, onManageTvs = {}, onEditShortcuts = {}, onPairAgain = {
                _,
                _,
                ->
            }, onOpenDiagnostics = {})
        }

    @Test
    fun discoverScreen() = shoot("screen_discover") { DiscoverScreen(onBack = {}, onPair = { _, _, _ -> }, onOpenRemote = {}) }

    @Test
    fun pairScreen() = shoot("screen_pair") { PairScreen("192.168.1.20", "Living Room Shield", onBack = {}, onPaired = {}) }

    @Test
    fun settingsScreen() =
        shoot("screen_settings") {
            SettingsScreen(onBack = {}, onManageTvs = {}, onEditShortcuts = {}, onOpenDiagnostics = {})
        }

    @Test
    @Config(qualifiers = "he-w411dp-h891dp-xxhdpi")
    fun settingsScreenHebrew() =
        shoot("screen_settings_he") {
            SettingsScreen(onBack = {}, onManageTvs = {}, onEditShortcuts = {}, onOpenDiagnostics = {})
        }

    @Test
    fun diagnosticsScreen() =
        shoot("screen_diagnostics") {
            container.eventLog.logBlocking("App", "started: Free TV Remote 0.1.6, Android 15", 'I')
            container.eventLog.logBlocking("Discovery", "search started", 'I')
            container.eventLog.logBlocking("Discovery", "search finished: 1 TV(s) found", 'I')
            container.eventLog.logBlocking("Pairing", "pairing: connecting to 192.168.1.x:6467", 'I')
            container.eventLog.logBlocking("Pairing", "pairing: the TV shows a code", 'I')
            container.eventLog.logBlocking("Pairing", "pairing: TV accepted the code and the pairing was saved", 'I')
            container.eventLog.logBlocking("Connection", "state: Connected", 'I')
            DiagnosticsScreen(onBack = {})
        }

    @Test
    @Config(qualifiers = "he-w411dp-h891dp-xxhdpi")
    fun diagnosticsScreenHebrew() = shoot("screen_diagnostics_he") { DiagnosticsScreen(onBack = {}) }

    private val sampleTv = SavedTv("1", "Living Room", "192.168.1.20", "AA==")
    private val sampleState =
        RemoteUiState(
            activeTv = sampleTv,
            connection = ConnectionState.Connected,
            tvState =
                TvState(
                    isOn = true,
                    volumeLevel = 13,
                    volumeMax = 100,
                    currentApp = "com.netflix.ninja",
                    currentAppLabel = "Netflix",
                    deviceVendor = "TCL",
                    deviceModel = "65C735",
                    textFieldActive = true,
                ),
            connectedSince = 0L,
        )

    @Composable
    private fun PreviewRemote(tvState: TvState) {
        val scope = rememberCoroutineScope()
        val gestures =
            remember {
                KeyGestures(
                    scope,
                    object : KeySender {
                        override suspend fun tap(code: Int) = Unit

                        override suspend fun holdStart(code: Int) = Unit

                        override suspend fun holdEnd(code: Int) = Unit
                    },
                )
            }
        RemoteContent(
            state =
                sampleState.copy(
                    tvState = tvState,
                    tvs = listOf(sampleTv),
                    shortcuts = AppShortcut.defaults.filter { it.enabled },
                ),
            gestures = gestures,
            haptics = rememberHaptics(false),
            snackbarHost = remember { SnackbarHostState() },
            actions = RemoteActions({}, {}, {}, {}, {}, RemoteNavigation({}, {}, {}, {}, {}, { _, _ -> })),
        )
    }

    private val playing = sampleState.tvState.copy(textFieldActive = false)

    @Test
    @Config(qualifiers = "w411dp-h1450dp-xxhdpi")
    fun remoteWholeScreen() = shoot("v3_remote_full") { PreviewRemote(playing) }

    @Test
    @Config(qualifiers = "he-w411dp-h1450dp-xxhdpi")
    fun remoteWholeScreenHebrew() = shoot("v3_remote_full_he") { PreviewRemote(playing.copy(textFieldActive = true, isMuted = true)) }

    @Test
    fun remotePlaying() = shoot("v2_remote_playing") { PreviewRemote(playing) }

    @Test
    fun remoteHomeScreen() =
        shoot("v2_remote_home") {
            PreviewRemote(playing.copy(currentApp = "com.google.android.apps.tv.launcherx", currentAppLabel = null))
        }

    @Test
    fun remoteTextRequested() = shoot("v2_remote_text") { PreviewRemote(playing.copy(textFieldActive = true, isMuted = true)) }

    @Test
    @Config(qualifiers = "he-w411dp-h891dp-xxhdpi")
    fun remotePlayingHebrew() = shoot("v2_remote_he") { PreviewRemote(playing) }

    @Test
    @Config(qualifiers = "w320dp-h568dp-xhdpi")
    fun remoteSmallPhone() = shoot("v2_remote_small") { PreviewRemote(playing) }

    @Test
    fun statusCardClosed() =
        shoot("status_card_closed") {
            StatusCard(sampleState, {}, { _, _ -> }, {}, {}, now = { 12 * 60_000L })
        }

    @Test
    fun statusCardOpen() {
        compose.setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                FreeTvRemoteTheme(ThemeMode.DARK, dynamicColor = false) {
                    Surface(Modifier, color = MaterialTheme.colorScheme.background) {
                        StatusCard(sampleState, {}, { _, _ -> }, {}, {}, now = { 12 * 60_000L })
                    }
                }
            }
        }
        compose.onNodeWithText("Connected · TV on · Volume 13 · Netflix").performClick()
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "status_card_open.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun shortcutsScreen() = shoot("screen_shortcuts") { ShortcutsScreen(onBack = {}) }

    @Test
    fun tvsScreen() = shoot("screen_tvs") { TvsScreen(onBack = {}, onAddTv = {}) }
}
