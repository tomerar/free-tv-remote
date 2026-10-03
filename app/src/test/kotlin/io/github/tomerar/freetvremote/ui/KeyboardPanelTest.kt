package io.github.tomerar.freetvremote.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import io.github.tomerar.freetvremote.data.ThemeMode
import io.github.tomerar.freetvremote.remote.KeyboardStatus
import io.github.tomerar.freetvremote.ui.screens.KeyboardPanel
import io.github.tomerar.freetvremote.ui.theme.FreeTvRemoteTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class KeyboardPanelTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(draft: String, status: KeyboardStatus) {
        compose.setContent {
            FreeTvRemoteTheme(ThemeMode.DARK) {
                KeyboardPanel(draft = draft, status = status, onDraftChange = {}, onSend = {}, onKey = {})
            }
        }
    }

    @Test
    fun `a failed send shows the error and the kept draft`() {
        show("my search", KeyboardStatus.Failed(KeyboardStatus.Failed.Reason.SEND_FAILED))
        compose.onNodeWithText("my search").assertExists()
        compose.onNodeWithText("Could not send. Your text is kept; try again.").assertExists()
        compose.onNodeWithText("Send").assertIsEnabled()
    }

    @Test
    fun `not connected is explained`() {
        show("x", KeyboardStatus.Failed(KeyboardStatus.Failed.Reason.NOT_CONNECTED))
        compose.onNodeWithText("Not connected to the TV, so nothing was sent.", substring = true).assertExists()
    }

    @Test
    fun `while sending the field and the send button are disabled`() {
        show("busy", KeyboardStatus.Sending)
        compose.onNodeWithText("Sending…").assertExists()
        compose.onNodeWithText("Send").assertIsNotEnabled()
    }

    @Test
    @Config(qualifiers = "he-w411dp-h891dp-xxhdpi")
    fun `the failure message is localized to Hebrew`() {
        show("שלום", KeyboardStatus.Failed(KeyboardStatus.Failed.Reason.SEND_FAILED))
        compose.onNodeWithText("שלום").assertExists()
        compose.onNodeWithText("לא ניתן לשלוח. הטקסט שלכם נשמר; נסו שוב.").assertExists()
    }
}
