package dev.zmdld11.guitardojo

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.zmdld11.guitardojo.navigation.TopLevelDestination
import dev.zmdld11.guitardojo.ui.screens.PhrasePlayerScreen
import dev.zmdld11.guitardojo.ui.screens.PhrasesScreen
import dev.zmdld11.guitardojo.ui.screens.ProfileScreen
import dev.zmdld11.guitardojo.ui.screens.TodayScreen
import dev.zmdld11.guitardojo.ui.screens.ToolsScreen
import dev.zmdld11.guitardojo.ui.screens.metronome.MetronomeScreen
import dev.zmdld11.guitardojo.ui.screens.tuner.TunerScreen

private const val PAGE_FADE_MILLIS = 180
private const val PAGE_SLIDE_MILLIS = 240

private const val METRONOME_ROUTE = "metronome"
private const val TUNER_ROUTE = "tuner"
private const val PHRASE_ROUTE = "phrase/{phraseId}"

private fun phraseRoute(phraseId: String): String = "phrase/$phraseId"

@Composable
fun GuitarDojoApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTopLevel = TopLevelDestination.entries.any { it.route == currentRoute }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (isTopLevel) {
                GuitarDojoBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = navController::navigateToTopLevel,
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.TODAY.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(animationSpec = tween(durationMillis = PAGE_FADE_MILLIS, delayMillis = 40)) +
                    slideInVertically(animationSpec = tween(durationMillis = PAGE_SLIDE_MILLIS)) { fullHeight ->
                        fullHeight / 24
                    }
            },
            exitTransition = { fadeOut(animationSpec = tween(durationMillis = PAGE_FADE_MILLIS)) },
            popEnterTransition = { fadeIn(animationSpec = tween(durationMillis = PAGE_FADE_MILLIS)) },
            popExitTransition = {
                fadeOut(animationSpec = tween(durationMillis = PAGE_FADE_MILLIS)) +
                    slideOutVertically(animationSpec = tween(durationMillis = PAGE_SLIDE_MILLIS)) { fullHeight ->
                        fullHeight / 24
                    }
            },
        ) {
            composable(TopLevelDestination.TODAY.route) { TodayScreen() }
            composable(TopLevelDestination.TOOLS.route) {
                ToolsScreen(
                    onOpenMetronome = { navController.navigate(METRONOME_ROUTE) },
                    onOpenTuner = { navController.navigate(TUNER_ROUTE) },
                )
            }
            composable(TopLevelDestination.PHRASES.route) {
                PhrasesScreen(onOpenPhrase = { phraseId -> navController.navigate(phraseRoute(phraseId)) })
            }
            composable(TopLevelDestination.PROFILE.route) { ProfileScreen() }
            composable(METRONOME_ROUTE) {
                MetronomeScreen(onBack = { navController.popBackStack() })
            }
            composable(TUNER_ROUTE) {
                TunerScreen(onBack = { navController.popBackStack() })
            }
            composable(
                route = PHRASE_ROUTE,
                arguments = listOf(navArgument("phraseId") { type = NavType.StringType }),
            ) { entry ->
                val phraseId = entry.arguments?.getString("phraseId").orEmpty()
                PhrasePlayerScreen(
                    phraseId = phraseId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun GuitarDojoBottomBar(
    currentRoute: String?,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentRoute == destination.route
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(destination) },
                icon = {
                    Icon(
                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                        contentDescription = destination.label,
                    )
                },
                label = { Text(destination.label) },
            )
        }
    }
}

private fun NavController.navigateToTopLevel(destination: TopLevelDestination) {
    if (currentDestination?.route == destination.route) return
    navigate(destination.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
