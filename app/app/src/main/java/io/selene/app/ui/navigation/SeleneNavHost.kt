package io.selene.app.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.selene.app.ui.browser.BrowserScreen
import io.selene.app.ui.downloads.DownloadsScreen
import io.selene.app.ui.feeds.FeedsScreen
import io.selene.app.ui.feeds.FeedsViewModel
import io.selene.app.ui.downloads.DownloadsViewModel
import io.selene.app.ui.library.LibraryScreen
import io.selene.app.ui.library.LibraryViewModel
import io.selene.app.ui.player.PlayerScreen
import io.selene.app.ui.resolve.ResolveScreen
import io.selene.app.ui.resolve.ResolveViewModel
import io.selene.app.ui.settings.SettingsScreen
import io.selene.app.ui.settings.SettingsViewModel

private object Routes {
    const val RESOLVE = "resolve?url={url}"
    const val DOWNLOADS = "downloads"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val PLAYER = "player/{uri}"
    const val BROWSER = "browser"
    const val FEEDS = "feeds"

    fun resolve(url: String) = "resolve?url=${Uri.encode(url)}"
    fun player(uri: String) = "player/${Uri.encode(uri)}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("resolve?url=", "Nouveau", Icons.Default.AddLink),
    Tab(Routes.DOWNLOADS, "Téléchargements", Icons.Default.Download),
    Tab(Routes.LIBRARY, "Bibliothèque", Icons.Default.VideoLibrary),
    Tab(Routes.FEEDS, "Flux", Icons.Default.Podcasts),
    Tab(Routes.SETTINGS, "Réglages", Icons.Default.Settings),
)

@Composable
fun SeleneNavHost(initialUrl: String?) {
    val navController = rememberNavController()
    val startDestination = if (initialUrl.isNullOrBlank()) Routes.LIBRARY else Routes.resolve(initialUrl)

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute != Routes.PLAYER

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = when (tab.route) {
                                Routes.RESOLVE -> currentRoute == Routes.RESOLVE
                                else -> currentRoute == tab.route
                            },
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(
                route = Routes.RESOLVE,
                arguments = listOf(
                    navArgument("url") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) {
                val viewModel: ResolveViewModel = hiltViewModel()
                ResolveScreen(
                    viewModel = viewModel,
                    onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) },
                    onOpenBrowser = { navController.navigate(Routes.BROWSER) },
                )
            }

            composable(Routes.DOWNLOADS) {
                val viewModel: DownloadsViewModel = hiltViewModel()
                DownloadsScreen(
                    viewModel = viewModel,
                    onPlay = { uri -> navController.navigate(Routes.player(uri)) },
                )
            }

            composable(Routes.LIBRARY) {
                val viewModel: LibraryViewModel = hiltViewModel()
                LibraryScreen(viewModel = viewModel)
            }

            composable(Routes.FEEDS) {
                val viewModel: FeedsViewModel = hiltViewModel()
                FeedsScreen(viewModel = viewModel)
            }

            composable(Routes.BROWSER) {
                BrowserScreen(
                    onPickMedia = { url -> navController.navigate(Routes.resolve(url)) },
                )
            }

            composable(Routes.SETTINGS) {
                val viewModel: SettingsViewModel = hiltViewModel()
                SettingsScreen(viewModel = viewModel)
            }

            composable(
                route = Routes.PLAYER,
                arguments = listOf(navArgument("uri") { type = NavType.StringType }),
            ) { entry ->
                val uri = entry.arguments?.getString("uri").orEmpty()
                PlayerScreen(uri = Uri.decode(uri))
            }
        }
    }
}
