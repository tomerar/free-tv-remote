package io.github.tomerar.freetvremote.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.tomerar.freetvremote.data.ThemeMode
import io.github.tomerar.freetvremote.protocol.remote.TvState
import io.github.tomerar.freetvremote.remote.GestureTiming
import io.github.tomerar.freetvremote.remote.KeyGestures
import io.github.tomerar.freetvremote.remote.KeySender
import io.github.tomerar.freetvremote.ui.screens.RemoteControlsPanel
import io.github.tomerar.freetvremote.ui.theme.FreeTvRemoteTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every tappable control of the remote must be at least 48 x 48 dp (Android accessibility guideline), also on small phones. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class TouchTargetTest {
    @get:Rule
    val compose = createComposeRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After
    fun tearDown() = scope.cancel()

    private val noop =
        object : KeySender {
            override suspend fun tap(code: Int) = Unit

            override suspend fun holdStart(code: Int) = Unit

            override suspend fun holdEnd(code: Int) = Unit
        }

    private fun assertTargets() {
        val gestures = KeyGestures(scope, noop, GestureTiming())
        compose.setContent {
            FreeTvRemoteTheme(ThemeMode.DARK, dynamicColor = false) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    RemoteControlsPanel(gestures, rememberHaptics(false), TvState(), onTap = {})
                }
            }
        }
        val clickable = compose.onAllNodes(hasClickAction())
        val count = clickable.fetchSemanticsNodes().size
        assertTrue("expected tappable controls", count > 5)
        for (i in 0 until count) {
            clickable[i].assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun `controls are big enough on a small phone`() = assertTargets()

    @Test
    @Config(qualifiers = "w411dp-h891dp-xxhdpi")
    fun `controls are big enough on a regular phone`() = assertTargets()
}
