package com.nova.iptv.ui

import android.net.Uri
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
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
import com.nova.iptv.ui.guide.GuideScreen
import com.nova.iptv.ui.movie.MovieScreen
import com.nova.iptv.ui.search.SearchScreen
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
    Tab("profiles", "Réglages", Icons.Rounded.Settings),
)

@Composable
fun NovaAppUi() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val tv = LocalContext.current.isTelevision()
    val useRail = LocalConfiguration.current.screenWidthDp >= 840

    val goToTab: (String) -> Unit = { target ->
        nav.navigate(target) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    if (tv) {
        // TV safe area (overscan) on the content side; the sidebar runs along the left edge.
        Row(Modifier.fillMaxSize()) {
            TvSidebar(route, goToTab)
            NovaNavHost(nav, goToTab, Modifier.weight(1f).fillMaxHeight().padding(top = 24.dp, end = 40.dp, bottom = 16.dp))
        }
    } else if (useRail) {
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

/**
 * Android TV side bar: collapsed to icons while browsing, expanded with labels when it has the focus.
 * Entering it with the remote lands on the current tab.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvSidebar(route: String?, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (expanded) 230.dp else 88.dp, tween(180), label = "sidebar")
    val requesters = remember { tabs.associate { it.route to FocusRequester() } }
    val current = tabs.firstOrNull { it.route == route } ?: tabs.first()

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(width)
            .background(Brush.horizontalGradient(listOf(NovaColors.Background2, NovaColors.Background)))
            .onFocusChanged { expanded = it.hasFocus }
            .focusProperties { enter = { requesters.getValue(current.route) } }
            .focusGroup()
            .padding(horizontal = 14.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NovaLogo(Modifier.width(if (expanded) 140.dp else 60.dp).padding(bottom = 20.dp))
        tabs.forEach { tab ->
            val selected = tab.route == route
            var focused by remember { mutableStateOf(false) }
            Surface(
                onClick = { onSelect(tab.route) },
                shape = RoundedCornerShape(14.dp),
                color = when {
                    focused -> NovaColors.Cyan.copy(alpha = 0.22f)
                    selected -> NovaColors.Card
                    else -> Color.Transparent
                },
                border = if (focused) BorderStroke(2.dp, NovaColors.Cyan) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(requesters.getValue(tab.route))
                    .onFocusChanged { focused = it.isFocused },
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(tab.icon, contentDescription = tab.label, tint = if (selected || focused) NovaColors.Cyan else NovaColors.TextSecondary)
                    if (expanded) {
                        Spacer(Modifier.width(14.dp))
                        Text(tab.label, fontSize = 16.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun NovaNavHost(nav: NavHostController, goToTab: (String) -> Unit, modifier: Modifier) {
    val addProfile = { nav.navigate("profile/edit") }
    val openSeries = { ref: String -> nav.navigate("seriesDetail/" + Uri.encode(ref)) }
    val openMovie = { ref: String -> nav.navigate("movie/" + Uri.encode(ref)) }

    NavHost(nav, startDestination = "home", modifier = modifier) {
        composable("home") {
            HomeScreen(
                onNavigate = { route -> if (route == "search") nav.navigate("search") else goToTab(route) },
                onAddProfile = addProfile,
                onOpenSeries = openSeries,
                onOpenMovie = openMovie,
            )
        }
        composable("search") {
            SearchScreen(onBack = { nav.popBackStack() }, onOpenMovie = openMovie, onOpenSeries = openSeries)
        }
        composable("movie/{ref}", arguments = listOf(navArgument("ref") { type = NavType.StringType })) { entry ->
            MovieScreen(ref = entry.arguments?.getString("ref").orEmpty(), onBack = { nav.popBackStack() }, onOpenMovie = openMovie)
        }
        composable("live") { LiveScreen(onAddProfile = addProfile, onOpenGuide = { nav.navigate("guide") }) }
        composable("guide") { GuideScreen(onBack = { nav.popBackStack() }) }
        composable("movies") { CatalogScreen(ContentKind.MOVIE, onOpenSeries = openSeries, onOpenMovie = openMovie, onAddProfile = addProfile) }
        composable("series") { CatalogScreen(ContentKind.SERIES, onOpenSeries = openSeries, onOpenMovie = openMovie, onAddProfile = addProfile) }
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
