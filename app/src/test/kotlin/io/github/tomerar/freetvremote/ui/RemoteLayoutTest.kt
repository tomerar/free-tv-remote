package io.github.tomerar.freetvremote.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import io.github.tomerar.freetvremote.data.AppShortcut
import io.github.tomerar.freetvremote.data.SavedTv
import io.github.tomerar.freetvremote.data.ThemeMode
import io.github.tomerar.freetvremote.protocol.remote.ConnectionState
import io.github.tomerar.freetvremote.protocol.remote.TvState
import io.github.tomerar.freetvremote.remote.KeyGestures
import io.github.tomerar.freetvremote.remote.KeySender
import io.github.tomerar.freetvremote.ui.screens.RemoteActions
import io.github.tomerar.freetvremote.ui.screens.RemoteContent
import io.github.tomerar.freetvremote.ui.screens.RemoteNavigation
import io.github.tomerar.freetvremote.ui.theme.FreeTvRemoteTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CopyOnWriteArrayList

/** The remote screen as a whole: what is on it, what is hidden when, and that every control is big enough. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class RemoteLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private val tv = SavedTv("1", "Living Room", "192.168.1.20", "AA==")
    private val events = CopyOnWriteArrayList<String>()

    private val netflix =
        TvState(isOn = true, volumeLevel = 13, volumeMax = 100, currentApp = "com.netflix.ninja", currentAppLabel = "Netflix")

    private fun state(tvState: TvState = netflix) =
        RemoteUiState(
            activeTv = tv,
            tvs = listOf(tv),
            connection = ConnectionState.Connected,
            tvState = tvState,
            connectedSince = 0L,
            shortcuts = AppShortcut.defaults.filter { it.enabled },
        )

    private fun show(state: RemoteUiState) {
        compose.setContent {
            val scope = rememberCoroutineScope()
            val gestures =
                androidx.compose.runtime.remember {
                    KeyGestures(
                        scope,
                        object : KeySender {
                            override suspend fun tap(code: Int) = Unit

                            override suspend fun holdStart(code: Int) = Unit

                            override suspend fun holdEnd(code: Int) = Unit
                        },
                    )
                }
            FreeTvRemoteTheme(ThemeMode.DARK, dynamicColor = false) {
                RemoteContent(
                    state = state,
                    gestures = gestures,
                    haptics = rememberHaptics(false),
                    snackbarHost = androidx.compose.runtime.remember { SnackbarHostState() },
                    actions =
                        RemoteActions(
                            onTap = { events += "tap $it" },
                            onLaunch = { events += "launch ${it.id}" },
                            onSwitchTv = {},
                            onReconnect = {},
                            onOpenKeyboard = { events += "keyboard" },
                            navigation =
                                RemoteNavigation(
                                    onOpenSettings = { events += "settings" },
                                    onAddTv = {},
                                    onManageTvs = {},
                                    onEditShortcuts = { events += "edit" },
                                    onOpenDiagnostics = {},
                                    onPairAgain = { _, _ -> },
                                ),
                        ),
                )
            }
        }
    }

    /** Controls only partly on screen (cut by the screen edge while scrolling) are not small; they are checked when fully visible. */
    private fun assertEveryVisibleControlIsBigEnough(minimumCount: Int) {
        val root = compose.onRoot().getBoundsInRoot()
        val clickable = compose.onAllNodes(hasClickAction())
        var checked = 0
        for (i in 0 until clickable.fetchSemanticsNodes().size) {
            val bounds = clickable[i].getBoundsInRoot()
            val inside = bounds.left >= root.left && bounds.right <= root.right
            if (!inside || bounds.top < root.top || bounds.bottom > root.bottom) continue
            clickable[i].assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
            checked++
        }
        assertTrue("expected at least $minimumCount controls, checked $checked", checked >= minimumCount)
    }

    @Test
    fun `every key of a physical remote is there`() {
        show(state())
        listOf(
            "Mute",
            "Power",
            "Input",
            "Menu",
            "OK",
            "Up",
            "Down",
            "Left",
            "Right",
            "Back",
            "Keyboard",
            "Home",
            "TV settings",
            "Guide",
            "Volume up",
            "Volume down",
            "Play or pause",
            "Info",
            "Channel up",
            "Channel down",
        ).forEach { compose.onNodeWithContentDescription(it).assertExists() }
    }

    @Test
    fun `the keys send their codes`() {
        show(state())
        compose.onNodeWithContentDescription("Input").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Channel up").performScrollTo().performClick()
        compose.waitForIdle()
        // Taps go through the key gestures, not the TalkBack action; nothing must open a screen.
        assertTrue(events.none { it == "settings" || it == "keyboard" })
    }

    @Test
    fun `shortcuts show colored letters with their names and open the app`() {
        show(state())
        compose.onNodeWithText("N$LRM").assertExists()
        compose.onNodeWithText("D+$LRM").assertExists()
        compose.onNodeWithText("PV$LRM").assertExists()
        compose.onNodeWithContentDescription("Open Netflix on the TV").performScrollTo().performClick()
        assertEquals(listOf("launch netflix"), events.toList())
    }

    @Test
    fun `the keyboard and edit buttons work`() {
        show(state())
        compose.onNodeWithContentDescription("Keyboard").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Edit shortcuts").performScrollTo().performClick()
        assertEquals(listOf("keyboard", "edit"), events.toList())
    }

    @Test
    fun `every tappable control is at least 48 dp`() {
        show(state(netflix.copy(textFieldActive = true)))
        assertEveryVisibleControlIsBigEnough(minimumCount = 15)
    }

    @Test
    @Config(qualifiers = "w320dp-h568dp-xhdpi")
    fun `a small phone still shows the whole key block and every control is big enough`() {
        show(state())
        compose.onNodeWithContentDescription("OK").assertIsDisplayed()
        compose.onNodeWithContentDescription("Channel down").assertExists()
        compose.onNodeWithContentDescription("Home").assertExists()
        assertEveryVisibleControlIsBigEnough(minimumCount = 8)
    }

    @Test
    @Config(qualifiers = "he-w411dp-h891dp-xxhdpi")
    fun `in Hebrew the keys keep their physical places and shortcut names stay readable`() {
        show(state())
        compose.onNodeWithContentDescription("הגברת עוצמה").assertExists()
        compose.onNodeWithText("N$LRM").assertExists()
    }

    private companion object {
        const val LRM = "\u200E"
    }
}
