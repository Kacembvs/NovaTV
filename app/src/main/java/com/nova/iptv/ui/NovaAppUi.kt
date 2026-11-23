package com.nova.iptv.ui

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nova.iptv.core.isTelevision
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.ui.browse.CatalogScreen
import com.nova.iptv.ui.browse.LiveScreen
import com.nova.iptv.ui.components.NovaLogo
import com.nova.iptv.ui.home.HomeScreen
import com.nova.iptv.ui.profiles.ProfileEditScreen
import com.nova.iptv.ui.profiles.ProfilesScreen
import com.nova.iptv.ui.series.SeriesScreen
import com.nova.iptv.ui.theme.NovaColors

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Accueil", Icons.Rounded.Home),
    Tab("live", "TV", Icons.Rounded.LiveTv),
    Tab("movies", "Films", Icons.Rounded.Movie),
    Tab("series", "Séries", Icons.Rounded.VideoLibrary),
    Tab("profiles", "Profils", Icons.Rounded.AccountCircle),
)

@Composable
fun NovaAppUi() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val useRail = LocalContext.current.isTelevision() || LocalConfiguration.current.screenWidthDp >= 840

    val goToTab: (String) -> Unit = { target ->
        nav.navigate(target) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    if (useRail) {
        Row(Modifier.fillMaxSize().safeDrawingPadding()) {
            NavigationRail(containerColor = NovaColors.Background2) {
                NovaLogo(Modifier.width(72.dp).padding(vertical = 12.dp))
                Spacer(Modifier.height(8.dp))
                tabs.forEach { tab ->
                    NavigationRailItem(
                        selected = route == tab.route,
                        onClick = { goToTab(tab.route) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
            NovaNavHost(nav, goToTab, Modifier.weight(1f).fillMaxHeight())
        }
    } else {
        Scaffold(
            containerColor = NovaColors.Background,
            bottomBar = {
                NavigationBar(containerColor = NovaColors.Background2) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = { goToTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            },
        ) { padding ->
            NovaNavHost(nav, goToTab, Modifier.padding(padding).consumeWindowInsets(padding))
        }
    }
}

@Composable
private fun NovaNavHost(nav: NavHostController, goToTab: (String) -> Unit, modifier: Modifier) {
    val addProfile = { nav.navigate("profile/edit") }
    val openSeries = { ref: String -> nav.navigate("seriesDetail/" + Uri.encode(ref)) }

    NavHost(nav, startDestination = "home", modifier = modifier) {
        composable("home") { HomeScreen(onNavigate = goToTab, onAddProfile = addProfile, onOpenSeries = openSeries) }
        composable("live") { LiveScreen(onAddProfile = addProfile) }
        composable("movies") { CatalogScreen(ContentKind.MOVIE, onOpenSeries = openSeries, onAddProfile = addProfile) }
        composable("series") { CatalogScreen(ContentKind.SERIES, onOpenSeries = openSeries, onAddProfile = addProfile) }
        composable("profiles") {
            ProfilesScreen(onAdd = addProfile, onEdit = { id -> nav.navigate("profile/edit?id=$id") })
        }
        composable(
            "profile/edit?id={id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType; defaultValue = -1L }),
        ) { entry ->
            val id = entry.arguments?.getLong("id")?.takeIf { it > 0 }
            ProfileEditScreen(profileId = id, onDone = { nav.popBackStack() })
        }
        composable("seriesDetail/{ref}", arguments = listOf(navArgument("ref") { type = NavType.StringType })) { entry ->
            SeriesScreen(ref = entry.arguments?.getString("ref").orEmpty(), onBack = { nav.popBackStack() })
        }
    }
}
