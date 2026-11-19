package com.bandeja.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bandeja.app.R
import com.bandeja.app.ui.appViewModel
import com.bandeja.app.ui.category.CategoryScreen
import com.bandeja.app.ui.detail.DetailScreen
import com.bandeja.app.ui.editor.CategoryEditorScreen
import com.bandeja.app.ui.favorites.FavoritesScreen
import com.bandeja.app.ui.home.HomeScreen
import com.bandeja.app.ui.onboarding.OnboardingScreen
import com.bandeja.app.ui.save.SaveContentSheet
import com.bandeja.app.ui.save.SaveViewModel
import com.bandeja.app.ui.search.SearchScreen
import com.bandeja.app.ui.settings.SettingsScreen

private enum class Tab(val route: String, val label: Int, val selected: ImageVector, val unselected: ImageVector) {
    Home(Routes.HOME, R.string.nav_home, Icons.Rounded.Home, Icons.Outlined.Home),
    Search(Routes.SEARCH, R.string.nav_search, Icons.Rounded.Search, Icons.Outlined.Search),
    Favorites(Routes.FAVORITES, R.string.nav_favorites, Icons.Rounded.Star, Icons.Outlined.StarOutline),
    Settings(Routes.SETTINGS, R.string.nav_settings, Icons.Rounded.Settings, Icons.Outlined.Settings),
}

/** Shell of the app: bottom bar on the four tabs, a floating "save link" button on Home, and the nav graph. */
@Composable
fun BandejaNavHost(
    startDestination: String,
    pendingContentId: Long?,
    onPendingContentHandled: () -> Unit,
    onOnboardingFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    var showSaveSheet by rememberSaveable { mutableStateOf(false) }
    // One ViewModel for the manual sheet, created with the shell so the FAB can reset it before opening.
    val manualSaveViewModel: SaveViewModel = appViewModel(key = "manual-save") {
        SaveViewModel(it.saveContent, it.categoryRepository, it.settingsRepository, it.metadataFetcher, it.appScope)
    }

    // "Abrir" in the share sheet's duplicate warning arrives here as a pending id.
    LaunchedEffect(pendingContentId) {
        if (pendingContentId != null) {
            navController.navigate(Routes.content(pendingContentId))
            onPendingContentHandled()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = route in Routes.tabs,
                enter = slideInVertically(tween(200)) { it } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(150)) { it } + fadeOut(tween(150)),
            ) {
                BandejaNavigationBar(current = route, onSelect = { navController.navigateToTab(it) })
            }
        },
        floatingActionButton = {
            AnimatedVisibility(visible = route == Routes.HOME, enter = fadeIn(), exit = fadeOut()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        // Reset here, on the tap: a rotation re-composes the sheet and must not clear what was typed.
                        manualSaveViewModel.startManual()
                        showSaveSheet = true
                    },
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.home_save_link)) },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = { pushEnter() },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(160)) },
            popExitTransition = { popExit() },
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onFinished = {
                    onOnboardingFinished()
                    // Opened from Settings: just go back. First run: continue to Home.
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    } else {
                        navController.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                    }
                })
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onOpenCategory = { navController.navigate(Routes.category(it)) },
                    onOpenContent = { navController.navigate(Routes.content(it)) },
                    onNewCategory = { navController.navigate(Routes.categoryEditor()) },
                    onEditCategory = { navController.navigate(Routes.categoryEditor(it)) },
                    onSeeFavorites = { navController.navigateToTab(Routes.FAVORITES) },
                    contentPadding = padding,
                )
            }
            composable(Routes.SEARCH) {
                SearchScreen(onOpenContent = { navController.navigate(Routes.content(it)) }, contentPadding = padding)
            }
            composable(Routes.FAVORITES) {
                FavoritesScreen(onOpenContent = { navController.navigate(Routes.content(it)) }, contentPadding = padding)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onShowIntro = { navController.navigate(Routes.ONBOARDING) }, contentPadding = padding)
            }
            composable(Routes.CATEGORY, arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType })) { entry ->
                CategoryScreen(
                    categoryId = entry.arguments?.getLong(Routes.ARG_ID) ?: 0L,
                    onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.categoryEditor(it)) },
                    onOpenContent = { navController.navigate(Routes.content(it)) },
                )
            }
            composable(Routes.CONTENT, arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType })) { entry ->
                DetailScreen(
                    contentId = entry.arguments?.getLong(Routes.ARG_ID) ?: 0L,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Routes.CATEGORY_EDITOR,
                arguments = listOf(navArgument(Routes.ARG_ID) { type = NavType.LongType; defaultValue = -1L }),
            ) { entry ->
                CategoryEditorScreen(
                    categoryId = entry.arguments?.getLong(Routes.ARG_ID)?.takeIf { it > 0 },
                    onBack = { navController.popBackStack() },
                    // The category (and its screen) no longer exists: go back to the list that contains it.
                    onDeleted = { navController.popBackStack(Routes.HOME, inclusive = false) },
                )
            }
        }
    }

    if (showSaveSheet) {
        SaveContentSheet(
            viewModel = manualSaveViewModel,
            onDismiss = { showSaveSheet = false },
            onOpenExisting = { id ->
                showSaveSheet = false
                navController.navigate(Routes.content(id))
            },
        )
    }
}

@Composable
private fun BandejaNavigationBar(current: String?, onSelect: (String) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Tab.entries.forEach { tab ->
            val selected = current == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(tab.route) },
                icon = { Icon(if (selected) tab.selected else tab.unselected, contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
            )
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushEnter(): EnterTransition {
    // Tabs cross-fade; deeper screens slide in slightly from the right.
    return if (targetState.destination.route in Routes.tabs) {
        fadeIn(tween(180))
    } else {
        fadeIn(tween(200)) + slideInHorizontally(tween(240)) { it / 12 }
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition {
    return if (initialState.destination.route in Routes.tabs) {
        fadeOut(tween(120))
    } else {
        fadeOut(tween(150)) + slideOutHorizontally(tween(220)) { it / 12 }
    }
}
