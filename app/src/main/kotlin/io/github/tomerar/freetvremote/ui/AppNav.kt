package io.github.tomerar.freetvremote.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.tomerar.freetvremote.ui.screens.DiagnosticsScreen
import io.github.tomerar.freetvremote.ui.screens.DiscoverScreen
import io.github.tomerar.freetvremote.ui.screens.PairScreen
import io.github.tomerar.freetvremote.ui.screens.RemoteScreen
import io.github.tomerar.freetvremote.ui.screens.SettingsScreen
import io.github.tomerar.freetvremote.ui.screens.ShortcutsScreen
import io.github.tomerar.freetvremote.ui.screens.TvsScreen

/** Replaces the whole back stack (onboarding or "add a TV") with the remote. */
internal fun NavController.showRemoteAfterPairing() {
    navigate(Routes.REMOTE) {
        popUpTo(graph.id) { inclusive = true }
    }
}

internal object Routes {
    const val REMOTE = "remote"
    const val DISCOVER = "discover"
    const val SETTINGS = "settings"
    const val TVS = "tvs"
    const val SHORTCUTS = "shortcuts"
    const val DIAGNOSTICS = "diagnostics"
    const val PAIR = "pair/{host}/{name}"

    fun pair(host: String, name: String) = "pair/${Uri.encode(host)}/${Uri.encode(name)}"
}

/** [startOnboarding] is true when there is no saved TV yet. */
@Composable
fun AppNav(startOnboarding: Boolean, navController: NavHostController = rememberNavController()) {
    val start = remember { if (startOnboarding) Routes.DISCOVER else Routes.REMOTE }
    NavHost(navController = navController, startDestination = start) {
        composable(Routes.REMOTE) {
            RemoteScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onAddTv = { navController.navigate(Routes.DISCOVER) },
                onManageTvs = { navController.navigate(Routes.TVS) },
                onEditShortcuts = { navController.navigate(Routes.SHORTCUTS) },
                onPairAgain = { host, name -> navController.navigate(Routes.pair(host, name)) },
            )
        }
        composable(Routes.DISCOVER) {
            DiscoverScreen(
                onBack = if (navController.previousBackStackEntry != null) ({ navController.popBackStack() }) else null,
                onPair = { host, name -> navController.navigate(Routes.pair(host, name)) },
            )
        }
        composable(
            Routes.PAIR,
            arguments = listOf(navArgument("host") { type = NavType.StringType }, navArgument("name") { type = NavType.StringType }),
        ) { entry ->
            val host = entry.arguments?.getString("host").orEmpty()
            val name = entry.arguments?.getString("name").orEmpty()
            PairScreen(
                host = host,
                name = name,
                onBack = { navController.popBackStack() },
                onPaired = { navController.showRemoteAfterPairing() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onManageTvs = { navController.navigate(Routes.TVS) },
                onEditShortcuts = { navController.navigate(Routes.SHORTCUTS) },
                onOpenDiagnostics = { navController.navigate(Routes.DIAGNOSTICS) },
            )
        }
        composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.TVS) {
            TvsScreen(onBack = { navController.popBackStack() }, onAddTv = { navController.navigate(Routes.DISCOVER) })
        }
        composable(Routes.SHORTCUTS) { ShortcutsScreen(onBack = { navController.popBackStack() }) }
    }
}
