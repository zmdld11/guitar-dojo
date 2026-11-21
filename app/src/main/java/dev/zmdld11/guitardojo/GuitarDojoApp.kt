package dev.zmdld11.guitardojo

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.zmdld11.guitardojo.data.cards.CardDeck
import dev.zmdld11.guitardojo.navigation.TopLevelDestination
import dev.zmdld11.guitardojo.ui.screens.CalendarScreen
import dev.zmdld11.guitardojo.ui.screens.PhrasePlayerScreen
import dev.zmdld11.guitardojo.ui.screens.PhrasesScreen
import dev.zmdld11.guitardojo.ui.screens.ProfileScreen
import dev.zmdld11.guitardojo.ui.screens.SessionScreen
import dev.zmdld11.guitardojo.ui.screens.TodayScreen
import dev.zmdld11.guitardojo.ui.screens.ToolsScreen
import dev.zmdld11.guitardojo.ui.screens.cards.CardsScreen
import dev.zmdld11.guitardojo.ui.screens.metronome.MetronomeScreen
import dev.zmdld11.guitardojo.ui.screens.tuner.TunerScreen

private const val PAGE_FADE_MILLIS = 180
private const val PAGE_SLIDE_MILLIS = 240

private const val METRONOME_ROUTE = "metronome"
private const val TUNER_ROUTE = "tuner"
private const val PHRASE_ROUTE = "phrase/{phraseId}?speed={speed}&loopStart={loopStart}&loopEnd={loopEnd}"
private const val CARDS_ROUTE = "cards/{deckId}"
private const val SESSION_ROUTE = "session"
private const val CALENDAR_ROUTE = "calendar"

/** speed/loopStart/loopEnd 为可选预置：训练向导跳进来时按引擎给的速度与循环区间打开播放器。 */
private fun phraseRoute(
    phraseId: String,
    speed: Double? = null,
    loopBars: IntRange? = null,
): String =
    buildString {
        append("phrase/")
        append(android.net.Uri.encode(phraseId))
        val params = mutableListOf<String>()
        speed?.let { params += "speed=$it" }
        loopBars?.let {
            params += "loopStart=${it.first}"
            params += "loopEnd=${it.last}"
        }
        if (params.isNotEmpty()) append("?").append(params.joinToString("&"))
    }

private fun cardsRoute(deck: CardDeck): String = "cards/${deck.id}"

@Composable
fun GuitarDojoApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isTopLevel = TopLevelDestination.entries.any { it.route == currentRoute }

    BoxWithConstraints {
        val useRail = maxWidth >= 840.dp
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!useRail && isTopLevel) {
                    GuitarDojoBottomBar(
                        currentRoute = currentRoute,
                        onNavigate = navController::navigateToTopLevel,
                    )
                }
            },
        ) { innerPadding ->
            Row(modifier = Modifier.padding(innerPadding)) {
                if (useRail && isTopLevel) {
                    GuitarDojoRail(
                        currentRoute = currentRoute,
                        onNavigate = navController::navigateToTopLevel,
                    )
                }
                NavHost(
                    navController = navController,
                    startDestination = TopLevelDestination.TODAY.route,
                    modifier = Modifier.weight(1f),
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
                    composable(TopLevelDestination.TODAY.route) {
                        TodayScreen(
                            onOpenCards = { navController.navigate(cardsRoute(CardDeck.THEORY)) },
                            onStartSession = { navController.navigate(SESSION_ROUTE) },
                            onOpenCalendar = { navController.navigate(CALENDAR_ROUTE) },
                        )
                    }
                    composable(TopLevelDestination.TOOLS.route) {
                        ToolsScreen(
                            onOpenMetronome = { navController.navigate(METRONOME_ROUTE) },
                            onOpenTuner = { navController.navigate(TUNER_ROUTE) },
                            onOpenCards = { deck -> navController.navigate(cardsRoute(deck)) },
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
                    composable(SESSION_ROUTE) {
                        SessionScreen(
                            onBack = { navController.popBackStack() },
                            onStartPhrase = { phraseId, speed, loopBars ->
                                navController.navigate(phraseRoute(phraseId, speed, loopBars))
                            },
                            onOpenCardDeck = { deck -> navController.navigate(cardsRoute(deck)) },
                        )
                    }
                    composable(CALENDAR_ROUTE) {
                        CalendarScreen(onBack = { navController.popBackStack() })
                    }
                    composable(
                        route = CARDS_ROUTE,
                        arguments = listOf(navArgument("deckId") { type = NavType.StringType }),
                    ) { entry ->
                        val deck =
                            CardDeck.fromId(entry.arguments?.getString("deckId").orEmpty())
                                ?: CardDeck.THEORY
                        CardsScreen(
                            deck = deck,
                            onBack = { navController.popBackStack() },
                        )
                    }
                    composable(
                        route = PHRASE_ROUTE,
                        arguments = listOf(
                            navArgument("phraseId") { type = NavType.StringType },
                            navArgument("speed") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument("loopStart") {
                                type = NavType.IntType
                                defaultValue = -1
                            },
                            navArgument("loopEnd") {
                                type = NavType.IntType
                                defaultValue = -1
                            },
                        ),
                    ) { entry ->
                        val phraseId = entry.arguments?.getString("phraseId").orEmpty()
                        val speed = entry.arguments?.getString("speed").orEmpty().toDoubleOrNull()
                        val loopStart = entry.arguments?.getInt("loopStart")?.takeIf { it >= 0 }
                        val loopEnd = entry.arguments?.getInt("loopEnd")?.takeIf { it >= 0 }
                        PhrasePlayerScreen(
                            phraseId = phraseId,
                            onBack = { navController.popBackStack() },
                            initialSpeed = speed,
                            initialLoopBars = if (loopStart != null && loopEnd != null) loopStart..loopEnd else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GuitarDojoRail(
    currentRoute: String?,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
        TopLevelDestination.entries.forEach { destination ->
            val selected = currentRoute == destination.route
            NavigationRailItem(
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
