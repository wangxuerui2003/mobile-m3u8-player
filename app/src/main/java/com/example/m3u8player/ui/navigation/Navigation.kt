package com.example.m3u8player.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.m3u8player.ui.home.HomeScreen
import com.example.m3u8player.ui.search.SearchScreen
import com.example.m3u8player.ui.watchlater.WatchLaterScreen
import com.example.m3u8player.ui.downloads.DownloadsScreen
import com.example.m3u8player.ui.profile.ProfileScreen
import com.example.m3u8player.ui.detail.DetailScreen
import android.content.Intent
import com.example.m3u8player.ui.player.PlayerActivity

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Home : Screen("home", "首页", Icons.Default.Home)
    object Search : Screen("search", "搜索", Icons.Default.Search)
    object WatchLater : Screen("watch_later", "稍后再看", Icons.Default.FavoriteBorder)
    object Downloads : Screen("downloads", "下载", Icons.Default.Download)
    object Profile : Screen("profile", "我的", Icons.Default.Person)
}

@Composable
fun AppNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController = androidx.navigation.compose.rememberNavController()
) {
    val screens = listOf(
        Screen.Home,
        Screen.Search,
        Screen.WatchLater,
        Screen.Downloads,
        Screen.Profile
    )
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    Scaffold(
        modifier = modifier,
        bottomBar = {
            NavigationBar {
                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.route == screen.route,
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
            
            composable(Screen.WatchLater.route) {
                WatchLaterScreen(
                    onVideoClick = { vodId ->
                        navController.navigate("detail/$vodId")
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
                ProfileScreen()
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
                            }
                            context.startActivity(intent)
                        }
                    }
                )
            }
        }
    }
}
