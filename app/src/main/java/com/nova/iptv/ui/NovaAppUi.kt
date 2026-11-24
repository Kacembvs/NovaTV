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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.nova.iptv.core.isTvLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.nova.iptv.domain.ContentKind
import com.nova.iptv.ui.browse.CatalogScreen
import com.nova.iptv.ui.downloads.DownloadsScreen
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.runtime.LaunchedEffect
import com.nova.iptv.ui.browse.LiveScreen
import com.nova.iptv.ui.components.NovaLogo
import com.nova.iptv.ui.components.TvActionButton
import com.nova.iptv.ui.guide.GuideScreen
import com.nova.iptv.ui.movie.MovieScreen
import com.nova.iptv.ui.search.SearchScreen
import com.nova.iptv.ui.home.HomeScreen
import com.nova.iptv.ui.profiles.ProfileEditScreen
import com.nova.iptv.ui.profiles.ProfilesScreen
import com.nova.iptv.ui.series.SeriesScreen
import com.nova.iptv.ui.theme.NovaColors
import com.nova.iptv.ui.theme.LocalTvInterface
import com.nova.iptv.ui.theme.TvNavigation

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("home", "Accueil", Icons.Rounded.Home),
    Tab("live", "TV", Icons.Rounded.LiveTv),
    Tab("movies", "Films", Icons.Rounded.Movie),
    Tab("series", "Séries", Icons.Rounded.VideoLibrary),
    Tab("profiles", "Réglages", Icons.Rounded.Settings),
)

/** Phones are where people download the most, so downloads get their own tab there. */
private val mobileTabs = tabs.take(4) + Tab("downloads", "Hors ligne", Icons.Rounded.DownloadForOffline) + tabs.last()

@Composable
fun NovaAppUi(openRequest: String? = null, onOpenHandled: () -> Unit = {}) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val tv = isTvLayout()
    val useRail = LocalConfiguration.current.screenWidthDp >= 840

    val goToTab: (String) -> Unit = { target ->
        nav.navigate(target) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }
    LaunchedEffect(openRequest) {
        if (openRequest != null) {
            goToTab(openRequest)
            onOpenHandled()
        }
    }

    if (tv) {
        val sectionRoute = when {
            route == "movie/{ref}" -> "movies"
            route == "seriesDetail/{ref}" -> "series"
            route == "collection/{kind}/{group}" ->
                if (backStack?.arguments?.getString("kind") == ContentKind.MOVIE.name) "movies" else "series"
            route == "guide" -> "live"
            route?.startsWith("profile/") == true -> "profiles"
            else -> route
        }
        LandscapeTvScale {
            TvAppFrame(sectionRoute, goToTab) { modifier ->
                NovaNavHost(nav, goToTab, modifier)
            }
        }
    } else if (useRail) {
        Row(Modifier.fillMaxSize().safeDrawingPadding()) {
            NavigationRail(containerColor = NovaColors.Background2) {
                NovaLogo(Modifier.width(72.dp).padding(vertical = 12.dp))
                Spacer(Modifier.height(8.dp))
                mobileTabs.forEach { tab ->
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
                    mobileTabs.forEach { tab ->
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
 * The TV interface is designed for a 960 × 540 dp screen. Phones in landscape only offer about
 * 360 dp of height, so the whole frame is scaled down to keep the TV composition intact.
 */
@Composable
internal fun LandscapeTvScale(content: @Composable () -> Unit) {
    if (LocalContext.current.isTelevision()) return content()
    BoxWithConstraints(Modifier.fillMaxSize().background(NovaColors.Background).safeDrawingPadding()) {
        val scale = (maxHeight.value / TV_LAYOUT_REFERENCE_HEIGHT).coerceIn(0.6f, 1f)
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density * scale, density.fontScale)) {
            content()
        }
    }
}

internal const val TV_LAYOUT_REFERENCE_HEIGHT = 480f

@Composable
internal fun TvAppFrame(route: String?, onSelect: (String) -> Unit, content: @Composable (Modifier) -> Unit) {
    val style = LocalTvInterface.current
    val contentFocus = remember { FocusRequester() }
    val top = style.navigation == TvNavigation.TOP
    val rail = style.navigation == TvNavigation.RAIL
    Box(Modifier.fillMaxSize()) {
        if (top) TvTopNavigation(route, onSelect, contentFocus)
        else TvSidebar(route, onSelect, contentFocus, rail)
        content(
            Modifier.fillMaxSize()
                .padding(start = if (top) 24.dp else if (rail) 104.dp else 184.dp,
                    top = if (top) 88.dp else 24.dp, end = 32.dp, bottom = 16.dp)
                .focusRequester(contentFocus).focusGroup(),
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvSidebar(route: String?, onSelect: (String) -> Unit, contentFocus: FocusRequester, compact: Boolean) {
    val requesters = remember { tabs.associate { it.route to FocusRequester() } }
    val current = tabs.firstOrNull { it.route == route } ?: tabs.first()

    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(if (compact) 104.dp else 184.dp)
            .background(Brush.horizontalGradient(listOf(NovaColors.Background2, NovaColors.Background)))
            .focusProperties { enter = { requesters.getValue(current.route) } }
            .focusGroup()
            .padding(horizontal = 10.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NovaLogo(Modifier.width(if (compact) 80.dp else 164.dp).padding(bottom = 20.dp))
        tabs.forEach { tab ->
            val selected = tab.route == route
            TvActionButton(
                label = if (compact) "" else tab.label,
                onClick = { onSelect(tab.route) },
                selected = selected,
                icon = tab.icon,
                modifier = Modifier.fillMaxWidth().focusRequester(requesters.getValue(tab.route))
                    .semantics { contentDescription = tab.label }
                    .focusProperties { right = contentFocus },
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvTopNavigation(route: String?, onSelect: (String) -> Unit, contentFocus: FocusRequester) {
    val requesters = remember { tabs.associate { it.route to FocusRequester() } }
    val current = tabs.firstOrNull { it.route == route } ?: tabs.first()
    Row(
        Modifier.fillMaxWidth().height(80.dp).background(NovaColors.Background2)
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .focusProperties { enter = { requesters.getValue(current.route) } }.focusGroup(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NovaLogo(Modifier.width(150.dp))
        tabs.forEach { tab ->
            TvActionButton(
                tab.label, { onSelect(tab.route) },
                Modifier.weight(1f).focusRequester(requesters.getValue(tab.route))
                    .semantics { contentDescription = tab.label }
                    .focusProperties { down = contentFocus },
                selected = current == tab,
            )
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
                onOpenCollection = { kind, group -> nav.navigate("collection/${kind.name}/" + Uri.encode(group)) },
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
        composable("collection/{kind}/{group}") { entry ->
            val kind = ContentKind.valueOf(requireNotNull(entry.arguments?.getString("kind")))
            CatalogScreen(kind, openSeries, openMovie, addProfile, requireNotNull(entry.arguments?.getString("group")))
        }
        composable("downloads") { DownloadsScreen(onBrowse = { goToTab("movies") }) }
        composable("profiles") {
            ProfilesScreen(onAdd = addProfile, onEdit = { id -> nav.navigate("profile/edit?id=$id") }, onOpenDownloads = { goToTab("downloads") })
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
