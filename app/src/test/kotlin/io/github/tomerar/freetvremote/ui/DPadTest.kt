package io.github.tomerar.freetvremote.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import io.github.tomerar.freetvremote.data.ThemeMode
import io.github.tomerar.freetvremote.protocol.remote.KeyCodes
import io.github.tomerar.freetvremote.remote.GestureTiming
import io.github.tomerar.freetvremote.remote.KeyGestures
import io.github.tomerar.freetvremote.remote.KeySender
import io.github.tomerar.freetvremote.ui.components.DPad
import io.github.tomerar.freetvremote.ui.screens.RemoteControlsPanel
import io.github.tomerar.freetvremote.ui.theme.FreeTvRemoteTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Real touch input against the real D-pad composable (Robolectric, no emulator). The important
 * property: a touch on the physical left of the pad is always LEFT, in LTR and in RTL.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class DPadTest {
    @get:Rule
    val compose = createComposeRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    private class Recorder : KeySender {
        val events = CopyOnWriteArrayList<String>()

        override suspend fun tap(code: Int) {
            events += "tap$code"
        }

        override suspend fun holdStart(code: Int) {
            events += "start$code"
        }

        override suspend fun holdEnd(code: Int) {
            events += "end$code"
        }
    }

    private val recorder = Recorder()
    private val gestures = KeyGestures(scope, recorder, GestureTiming(repeatDelayMs = 60_000, longPressMs = 60_000))

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Composable
    private fun Pad(direction: LayoutDirection) {
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            FreeTvRemoteTheme(ThemeMode.DARK) {
                DPad(gestures = gestures, haptics = rememberHaptics(false), onAccessibilityClick = {})
            }
        }
    }

    private fun pressAt(fractionX: Float, fractionY: Float) {
        compose.onRoot().performTouchInput {
            val position = Offset(width * fractionX, height * fractionY)
            down(position)
            up()
        }
        compose.waitForIdle()
    }

    private fun assertDirections(direction: LayoutDirection) {
        compose.setContent { Pad(direction) }
        pressAt(0.08f, 0.5f)
        pressAt(0.92f, 0.5f)
        pressAt(0.5f, 0.08f)
        pressAt(0.5f, 0.92f)
        pressAt(0.5f, 0.5f)
        assertEquals(
            listOf(
                "tap${KeyCodes.DPAD_LEFT}",
                "tap${KeyCodes.DPAD_RIGHT}",
                "tap${KeyCodes.DPAD_UP}",
                "tap${KeyCodes.DPAD_DOWN}",
                "tap${KeyCodes.DPAD_CENTER}",
            ),
            recorder.events.toList(),
        )
    }

    @Test
    fun `touches map to the right keys in LTR`() = assertDirections(LayoutDirection.Ltr)

    @Test
    fun `touches map to the same physical keys in RTL`() = assertDirections(LayoutDirection.Rtl)

    @Test
    @Config(qualifiers = "he-w411dp-h891dp-xxhdpi")
    fun `Hebrew content descriptions are used`() {
        compose.setContent { Pad(LayoutDirection.Rtl) }
        compose.onNodeWithContentDescription("שמאלה").assertExists()
        compose.onNodeWithContentDescription("אישור").assertExists()
        compose.onNodeWithContentDescription("לוח ניווט").assertExists()
    }

    @Test
    fun `dead zones between the ring and the corners do nothing`() {
        compose.setContent { Pad(LayoutDirection.Ltr) }
        pressAt(0.02f, 0.02f) // outside the circle
        assertEquals(emptyList<String>(), recorder.events.toList())
    }

    private fun screenshot(name: String, direction: LayoutDirection, qualifiersNote: String) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                FreeTvRemoteTheme(ThemeMode.DARK) {
                    androidx.compose.material3.Surface {
                        RemoteControlsPanel(
                            gestures,
                            rememberHaptics(false),
                            io.github.tomerar.freetvremote.protocol.remote
                                .TvState(),
                        ) {}
                    }
                }
            }
        }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("screenshot $name ($qualifiersNote) ${bitmap.width}x${bitmap.height}")
    }

    @Test
    fun `render remote controls LTR`() = screenshot("remote_ltr", LayoutDirection.Ltr, "en")

    @Test
    @Config(qualifiers = "he-w411dp-h891dp-xxhdpi")
    fun `render remote controls RTL`() = screenshot("remote_rtl", LayoutDirection.Rtl, "he")
}
