package br.com.ryuukossei.gymprogress.ui.navigation

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import br.com.ryuukossei.gymprogress.R
import br.com.ryuukossei.gymprogress.ui.exercises.ExercisesScreen
import br.com.ryuukossei.gymprogress.ui.history.HistoryScreen
import br.com.ryuukossei.gymprogress.ui.home.HomeScreen
import br.com.ryuukossei.gymprogress.ui.history.SessionDetailScreen
import br.com.ryuukossei.gymprogress.ui.session.RunSessionScreen
import br.com.ryuukossei.gymprogress.ui.settings.SettingsScreen
import br.com.ryuukossei.gymprogress.ui.workouts.WorkoutDetailScreen
import br.com.ryuukossei.gymprogress.ui.workouts.WorkoutsScreen

private object Routes {
    const val HOME = "home"
    const val WORKOUTS = "workouts"
    const val WORKOUT = "workout/{id}"
    const val HISTORY = "history"
    const val SESSION = "session/{id}"
    const val EXERCISES = "exercises"
    const val SETTINGS = "settings"
    const val RUN = "run/{id}"

    fun workout(id: String) = "workout/$id"
    fun session(id: String) = "session/$id"
    fun run(id: String) = "run/$id"
}

private data class Tab(val route: String, val label: String, @param:DrawableRes val icon: Int, val owns: List<String>)

private val tabs = listOf(
    Tab(Routes.HOME, "Início", R.drawable.ic_nav_home, listOf(Routes.HOME)),
    Tab(Routes.WORKOUTS, "Treinos", R.drawable.ic_nav_workouts, listOf(Routes.WORKOUTS, Routes.WORKOUT)),
    Tab(Routes.EXERCISES, "Exercícios", R.drawable.ic_nav_exercises, listOf(Routes.EXERCISES)),
    Tab(Routes.HISTORY, "Histórico", R.drawable.ic_nav_history, listOf(Routes.HISTORY, Routes.SESSION)),
    Tab(Routes.SETTINGS, "Ajustes", R.drawable.ic_nav_settings, listOf(Routes.SETTINGS)),
)

/**
 * Telas principais com a barra de navegação inferior.
 * [sessionToOpen]: sessão a abrir direto (ex.: ao tocar na notificação do cronômetro).
 */
@Composable
fun MainNavigation(sessionToOpen: String? = null, onSessionOpened: () -> Unit = {}) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    LaunchedEffect(sessionToOpen) {
        if (sessionToOpen != null) {
            val alreadyOpen = currentRoute == Routes.RUN &&
                backStack?.arguments?.getString("id") == sessionToOpen
            if (!alreadyOpen) navController.navigate(Routes.run(sessionToOpen)) { launchSingleTop = true }
            onSessionOpened()
        }
    }

    Scaffold(
        bottomBar = {
            // A tela de execução do treino ocupa a tela inteira.
            if (currentRoute != Routes.RUN) NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute in tab.owns,
                        onClick = { navController.navigateToTab(tab.route) },
                        icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
        // As telas internas cuidam da barra de status com a própria TopAppBar.
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeScreen(onOpenWorkouts = { navController.navigateToTab(Routes.WORKOUTS) })
            }
            composable(Routes.WORKOUTS) {
                WorkoutsScreen(
                    onOpenWorkout = { navController.navigate(Routes.workout(it)) },
                    onRunSession = { navController.navigate(Routes.run(it)) },
                )
            }
            composable(Routes.WORKOUT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                WorkoutDetailScreen(
                    workoutId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onRunSession = { navController.navigate(Routes.run(it)) },
                )
            }
            composable(Routes.RUN, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                RunSessionScreen(
                    sessionId = entry.arguments?.getString("id").orEmpty(),
                    onExit = {
                        // Volta para a aba Treinos, onde o treino em andamento aparece.
                        navController.navigate(Routes.WORKOUTS) {
                            popUpTo(navController.graph.findStartDestination().id)
                            launchSingleTop = true
                        }
                    },
                    onFinished = { id ->
                        navController.navigate(Routes.session(id)) {
                            popUpTo(navController.graph.findStartDestination().id)
                        }
                    },
                )
            }
            composable(Routes.EXERCISES) { ExercisesScreen() }
            composable(Routes.HISTORY) {
                HistoryScreen(onOpenSession = { navController.navigate(Routes.session(it)) })
            }
            composable(Routes.SESSION, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                SessionDetailScreen(
                    sessionId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) { SettingsScreen() }
        }
    }
}

private fun NavHostController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
