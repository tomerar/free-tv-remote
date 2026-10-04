package io.github.tomerar.freetvremote.ui

import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import io.github.tomerar.freetvremote.data.ThemeMode
import io.github.tomerar.freetvremote.timer.SleepOutcome
import io.github.tomerar.freetvremote.timer.SleepTimer
import io.github.tomerar.freetvremote.timer.SleepTimerResult
import io.github.tomerar.freetvremote.ui.screens.SleepTimerActions
import io.github.tomerar.freetvremote.ui.screens.SleepTimerSheet
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
class SleepTimerSheetTest {
    @get:Rule
    val compose = createComposeRule()

    private val started = mutableListOf<Int>()
    private val extended = mutableListOf<Int>()
    private var cancelled = 0
    private var dismissedResult = 0
    private var exactSettings = 0

    private val actions =
        SleepTimerActions(
            onStart = { started += it },
            onExtend = { extended += it },
            onCancel = { cancelled++ },
            onDismissResult = { dismissedResult++ },
            onOpenExactSettings = { exactSettings++ },
        )

    private fun show(state: SleepTimerUiState, tvName: String? = "Living Room") {
        compose.setContent {
            FreeTvRemoteTheme(ThemeMode.DARK, dynamicColor = false) {
                SleepTimerSheet(state, tvName, actions, onDismiss = {})
            }
        }
    }

    private fun running(remainingMs: Long, phase: SleepTimer.Phase = SleepTimer.Phase.ARMED) =
        SleepTimer(
            revision = 1,
            tvId = "tv",
            tvName = "Living Room",
            durationMs = 3_600_000,
            dueElapsedMs = SystemClock.elapsedRealtime() + remainingMs,
            dueWallMs = System.currentTimeMillis() + remainingMs,
            bootCount = 1,
            exact = true,
            phase = phase,
        )

    @Test
    fun `starts with the default of 30 minutes and starts that`() {
        show(SleepTimerUiState())
        compose.onNode(hasSetTextAction()).assertTextContains("30")
        compose.onNodeWithText("Start timer").assertIsEnabled().performClick()
        assertEquals(listOf(30), started)
    }

    @Test
    fun `the form starts from the minutes asked for last time, not from the default`() {
        show(SleepTimerUiState(initialMinutes = 1))
        compose.onNode(hasSetTextAction()).assertTextContains("1")
        compose.onNodeWithText("Start timer").performClick()
        assertEquals(listOf(1), started)
    }

    @Test
    fun `a preset sets the duration`() {
        show(SleepTimerUiState())
        compose.onNodeWithText("1 h 30 min").performClick()
        compose.onNodeWithText("Start timer").performClick()
        assertEquals(listOf(90), started)
    }

    @Test
    fun `any number of minutes can be typed`() {
        show(SleepTimerUiState())
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("37")
        compose.onNodeWithText("Start timer").performClick()
        assertEquals(listOf(37), started)
    }

    @Test
    fun `a number outside the range cannot be started`() {
        show(SleepTimerUiState())
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("999")
        compose.onNodeWithText("Start timer").assertIsNotEnabled()
    }

    @Test
    fun `plus and minus move in steps of five`() {
        show(SleepTimerUiState())
        compose.onNodeWithContentDescription("Longer by 5 minutes").performClick()
        compose.onNodeWithText("Start timer").performClick()
        assertEquals(listOf(35), started)
    }

    @Test
    fun `without a TV the timer cannot be started`() {
        show(SleepTimerUiState(), tvName = null)
        compose.onNodeWithText("Start timer").assertIsNotEnabled()
        compose.onNodeWithText("Pick a TV first.").assertIsDisplayed()
    }

    @Test
    fun `a running timer shows the time left and can be extended or cancelled`() {
        show(SleepTimerUiState(active = running(42 * 60_000L)))
        compose.onNodeWithText("Start timer").assertDoesNotExist()
        compose.onNodeWithContentDescription("Time left 42:", substring = true).assertExists()
        compose.onNodeWithText("+15 min").performClick()
        compose.onNodeWithText("Cancel timer").performClick()
        assertEquals(listOf(15), extended)
        assertEquals(1, cancelled)
    }

    @Test
    fun `once the TV is being switched off nothing can be cancelled`() {
        show(SleepTimerUiState(active = running(0, SleepTimer.Phase.RUNNING)))
        compose.onNodeWithText("Cancel timer").assertIsNotEnabled()
        compose.onNodeWithText("+15 min").assertIsNotEnabled()
    }

    @Test
    fun `the last result is explained and can be dismissed`() {
        show(SleepTimerUiState(last = SleepTimerResult(SleepOutcome.ALREADY_OFF, "Living Room", System.currentTimeMillis())))
        compose.onNodeWithText("Living Room was already off, so nothing was sent.").assertIsDisplayed()
        compose.onNodeWithText("Dismiss").performClick()
        assertEquals(1, dismissedResult)
    }

    @Test
    fun `missing exact alarm access is explained with a way to grant it`() {
        show(SleepTimerUiState(exactAllowed = false))
        compose.onNodeWithText("Allow").performClick()
        assertEquals(1, exactSettings)
    }
}
