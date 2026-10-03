package io.github.tomerar.freetvremote.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** After a successful pairing the app must show the remote, not close. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PairingNavigationTest {
    @get:Rule
    val compose = createComposeRule()

    private fun run(start: String, before: (NavHostController) -> Unit) {
        lateinit var nav: NavHostController
        compose.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = start) {
                composable(Routes.REMOTE) { Text("remote screen") }
                composable(Routes.DISCOVER) { Text("discover screen") }
                composable(Routes.PAIR) { Text("pair screen") }
            }
        }
        compose.runOnIdle { before(nav) }
        compose.runOnIdle { nav.showRemote() }
        compose.waitForIdle()
        compose.onNodeWithText("remote screen").assertExists()
        assertEquals(Routes.REMOTE, nav.currentBackStackEntry?.destination?.route)
        assertNotNull(nav.currentBackStackEntry)
    }

    @Test
    fun `first pairing from onboarding lands on the remote`() =
        run(Routes.DISCOVER) { it.navigate(Routes.pair("192.168.1.5", "TCL")) }

    @Test
    fun `adding another TV from the remote lands on the remote`() =
        run(Routes.REMOTE) {
            it.navigate(Routes.DISCOVER)
            it.navigate(Routes.pair("192.168.1.5", "TCL"))
        }
}
