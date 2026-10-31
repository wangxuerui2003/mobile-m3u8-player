package com.example.m3u8player.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.example.m3u8player.ui.home.HomeScreen
import com.example.m3u8player.ui.search.SearchScreen
import com.example.m3u8player.ui.watchlater.WatchLaterScreen
import com.example.m3u8player.ui.recent.RecentPlaybackRequest
import com.example.m3u8player.ui.recent.RecentWatchScreen
import com.example.m3u8player.ui.downloads.DownloadsScreen
import com.example.m3u8player.ui.profile.ProfileScreen
import com.example.m3u8player.ui.detail.DetailScreen
import android.content.Intent
import android.net.Uri
import com.example.m3u8player.ui.player.PlayerActivity

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Screen("home", "首页", Icons.Default.Home)
    object Search : Screen("search", "搜索", Icons.Default.Search)
    object Recent : Screen("recent", "最近观看", Icons.Default.History)
    object WatchLater : Screen("watch_later", "稍后再看", Icons.Default.FavoriteBorder)
    object Downloads : Screen("downloads", "下载", Icons.Default.Download)
    object Profile : Screen("profile", "我的", Icons.Default.Person)
}

private const val SEARCH_ROUTE_WITH_QUERY = "search?query={query}"

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = androidx.navigation.compose.rememberNavController()
) {
    val screens = listOf(
        Screen.Home,
        Screen.Search,
        Screen.Recent,
        Screen.Downloads,
        Screen.Profile
    )
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentTopRoute = currentDestination?.route?.substringBefore("?")
    
    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentTopRoute == screen.route,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.startDestinationId)
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onVideoClick = { vodId ->
                        navController.navigate("detail/$vodId")
                    },
                    onSearchRequest = { query ->
                        navController.navigate("search?query=${Uri.encode(query)}")
                    }
                )
            }
            
            composable(Screen.Search.route) {
                SearchScreen(
                    onVideoClick = { vodId ->
                        navController.navigate("detail/$vodId")
                    }
                )
            }

            composable(
                route = SEARCH_ROUTE_WITH_QUERY,
                arguments = listOf(
                    navArgument("query") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                SearchScreen(
                    initialQuery = backStackEntry.arguments?.getString("query"),
                    onVideoClick = { vodId ->
                        navController.navigate("detail/$vodId")
                    }
                )
            }
            
            composable(Screen.WatchLater.route) {
                WatchLaterScreen(
                    onVideoClick = { vodId ->
                        navController.navigate("detail/$vodId")
                    }
                )
            }

            composable(Screen.Recent.route) {
                val context = androidx.compose.ui.platform.LocalContext.current
                RecentWatchScreen(
                    onPlayRequest = { request ->
                        context.startActivity(request.toPlayerIntent(context))
                    }
                )
            }
            
            composable(Screen.Downloads.route) {
                DownloadsScreen(
                    onVideoClick = { vodId ->
                        navController.navigate("detail/$vodId")
                    }
                )
            }
            
            composable(Screen.Profile.route) {
                ProfileScreen(
                    onWatchLaterClick = {
                        navController.navigate("watch_later")
                    }
                )
            }
            
            composable("detail/{vodId}") { backStackEntry ->
                val vodId = backStackEntry.arguments?.getString("vodId")?.toIntOrNull() ?: return@composable
                val context = androidx.compose.ui.platform.LocalContext.current
                
                DetailScreen(
                    vodId = vodId,
                    onBack = {
                        navController.popBackStack()
                    },
                    onPlay = { episode, detail ->
                        val episodeUrl = episode.url.takeIf { it.isNotBlank() }
                            ?: detail.vodPlayUrl.takeIf { it.isNotBlank() }

                        if (episodeUrl != null) {
                            val intent = Intent(context, PlayerActivity::class.java).apply {
                                putExtra("vodId", detail.vodId)
                                putExtra("vodName", detail.vodName)
                                putExtra("episode", episode.index)
                                putExtra("episodeName", episode.name)
                                putExtra("coverUrl", detail.vodPic)
                                putExtra("videoUrl", episodeUrl)
                                putExtra("startPositionMs", 0L)
                                putExtra("totalDurationMs", 0L)
                            }
                            context.startActivity(intent)
                        }
                    }
                )
            }
        }
    }
}

private fun RecentPlaybackRequest.toPlayerIntent(context: android.content.Context): Intent {
    return Intent(context, PlayerActivity::class.java).apply {
        putExtra("vodId", vodId)
        putExtra("vodName", vodName)
        putExtra("episode", episode)
        putExtra("episodeName", episodeName)
        putExtra("coverUrl", coverUrl)
        putExtra("startPositionMs", startPositionMs)
        putExtra("videoUrl", videoUrl)
        putExtra("localPlaylistPath", localPlaylistPath)
        putExtra("isPartialDownload", isPartialDownload)
        putExtra("downloadedDurationMs", downloadedDurationMs)
        putExtra("totalDurationMs", totalDurationMs)
    }
}
