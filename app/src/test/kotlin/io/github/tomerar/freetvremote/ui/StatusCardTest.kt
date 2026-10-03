package io.github.tomerar.freetvremote.ui

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.tomerar.freetvremote.data.SavedTv
import io.github.tomerar.freetvremote.data.ThemeMode
import io.github.tomerar.freetvremote.protocol.remote.ConnectionState
import io.github.tomerar.freetvremote.protocol.remote.FailureReason
import io.github.tomerar.freetvremote.protocol.remote.TvState
import io.github.tomerar.freetvremote.ui.screens.StatusCard
import io.github.tomerar.freetvremote.ui.theme.FreeTvRemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class StatusCardTest {
    @get:Rule
    val compose = createComposeRule()

    private val tv = SavedTv(id = "1", name = "Living Room", host = "192.168.1.20", pin = "00", remotePort = 6466, pairingPort = 6467)

    private val connected =
        RemoteUiState(
            activeTv = tv,
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
                ),
            connectedSince = 0L,
        )

    private var keyboardOpened = 0
    private var diagnosticsOpened = 0
    private var reconnects = 0

    private fun show(state: RemoteUiState) {
        compose.setContent {
            FreeTvRemoteTheme(ThemeMode.DARK, dynamicColor = false) {
                StatusCard(
                    state = state,
                    onReconnect = { reconnects++ },
                    onPairAgain = { _, _ -> },
                    onOpenKeyboard = { keyboardOpened++ },
                    onOpenDiagnostics = { diagnosticsOpened++ },
                    now = { 12 * 60_000L + 5_000L },
                )
            }
        }
    }

    @Test
    fun `closed by default it shows only the important facts in one line`() {
        show(connected)
        compose.onNodeWithText("Connected · TV on · Volume 13 · Netflix").assertIsDisplayed()
        compose.onNodeWithText("192.168.1.20").assertDoesNotExist()
        compose.onNodeWithText("TCL 65C735").assertDoesNotExist()
    }

    @Test
    fun `a tap opens the details and a second tap closes them`() {
        show(connected)
        compose.onNodeWithText("Connected · TV on · Volume 13 · Netflix").performClick()
        compose.onNodeWithText("TCL 65C735").assertIsDisplayed()
        compose.onNodeWithText("13 / 100").assertIsDisplayed()
        compose.onNodeWithText("192.168.1.20").assertIsDisplayed()
        compose.onNodeWithText("12 minutes").assertIsDisplayed()
        compose.onNodeWithText("Connected · TV on · Volume 13 · Netflix").performClick()
        compose.onNodeWithText("192.168.1.20").assertDoesNotExist()
    }

    @Test
    fun `the closed card is at least 48 dp tall so it is easy to tap`() {
        show(connected)
        compose.onNodeWithText("Connected · TV on · Volume 13 · Netflix").assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `an unknown app is hidden when closed and shown by its id when open`() {
        show(connected.copy(tvState = connected.tvState.copy(currentApp = "com.vendor.player", currentAppLabel = null)))
        compose.onNodeWithText("Connected · TV on · Volume 13").performClick()
        compose.onNodeWithText("com.vendor.player").assertIsDisplayed()
    }

    @Test
    fun `muted replaces the volume in the closed line`() {
        show(connected.copy(tvState = connected.tvState.copy(isMuted = true)))
        compose.onNodeWithText("Connected · TV on · Muted · Netflix").assertIsDisplayed()
    }

    @Test
    fun `facts the TV did not report are simply left out`() {
        show(connected.copy(tvState = TvState()))
        compose.onNodeWithText("Connected").assertIsDisplayed()
    }

    @Test
    fun `no keyboard shortcut while the TV is not asking for text`() {
        show(connected)
        compose.onNodeWithText("Connected · TV on · Volume 13 · Netflix").performClick()
        compose.onNodeWithText("Type").assertDoesNotExist()
    }

    @Test
    fun `the keyboard shortcut appears when the TV asks for text, and works`() {
        show(connected.copy(tvState = connected.tvState.copy(textFieldActive = true)))
        compose.onNodeWithText("Connected · TV on · Volume 13 · Netflix").performClick()
        compose.onNodeWithText("The TV is asking for text.").assertIsDisplayed()
        compose.onNodeWithText("Type").performClick()
        assertEquals(1, keyboardOpened)
    }

    @Test
    fun `the details link to diagnostics`() {
        show(connected)
        compose.onNodeWithText("Connected · TV on · Volume 13 · Netflix").performClick()
        compose.onNodeWithText("Diagnostics").performClick()
        assertEquals(1, diagnosticsOpened)
    }

    @Test
    fun `a pairing problem offers Pair again without opening anything`() {
        show(RemoteUiState(activeTv = tv, connection = ConnectionState.Failed(FailureReason.NOT_PAIRED)))
        compose.onNodeWithText("Pair again").assertIsDisplayed()
    }

    @Test
    fun `a dropped connection offers Reconnect without opening anything`() {
        show(RemoteUiState(activeTv = tv, connection = ConnectionState.Idle))
        compose.onNodeWithText("Reconnect").performClick()
        assertEquals(1, reconnects)
    }
}
