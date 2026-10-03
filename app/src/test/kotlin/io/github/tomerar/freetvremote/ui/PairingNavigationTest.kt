package io.github.tomerar.freetvremote.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
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

    @Test
    fun `the pairing route carries the host, the name and the optional network name`() {
        assertEquals("pair/10.0.0.1/Living%20Room", Routes.pair("10.0.0.1", "Living Room"))
        assertEquals("pair/10.0.0.1/Living%20Room?service=nvidia%20%2F%20room%3F", Routes.pair("10.0.0.1", "Living Room", "nvidia / room?"))
    }

    @Test
    fun `the route arguments arrive intact, with and without a network name`() {
        lateinit var nav: NavHostController
        var received: Triple<String?, String?, String?>? = null
        compose.setContent {
            nav = rememberNavController()
            NavHost(nav, startDestination = Routes.DISCOVER) {
                composable(Routes.DISCOVER) { Text("discover screen") }
                composable(
                    Routes.PAIR,
                    arguments =
                        listOf(
                            navArgument("host") { type = NavType.StringType },
                            navArgument("name") { type = NavType.StringType },
                            navArgument("service") {
                                type = NavType.StringType
                                nullable = true
                                defaultValue = null
                            },
                        ),
                ) { entry ->
                    received =
                        Triple(
                            entry.arguments?.getString("host"),
                            entry.arguments?.getString("name"),
                            entry.arguments?.getString("service"),
                        )
                    Text("pair screen")
                }
            }
        }
        compose.runOnIdle { nav.navigate(Routes.pair("10.0.0.1", "Living Room", "nvidia / room?")) }
        compose.waitForIdle()
        assertEquals(Triple("10.0.0.1", "Living Room", "nvidia / room?"), received)
        compose.runOnIdle { nav.navigate(Routes.pair("10.0.0.2", "Android TV")) }
        compose.waitForIdle()
        assertEquals(Triple("10.0.0.2", "Android TV", null), received)
    }
}
