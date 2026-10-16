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
import br.com.ryuukossei.gymprogress.ui.history.HistoryScreen
import br.com.ryuukossei.gymprogress.ui.history.SessionDetailScreen
import br.com.ryuukossei.gymprogress.ui.settings.SettingsScreen
import br.com.ryuukossei.gymprogress.ui.workouts.WorkoutDetailScreen
import br.com.ryuukossei.gymprogress.ui.workouts.WorkoutsScreen

private object Routes {
    const val WORKOUTS = "workouts"
    const val WORKOUT = "workout/{id}"
    const val HISTORY = "history"
    const val SESSION = "session/{id}"
    const val SETTINGS = "settings"

    fun workout(id: String) = "workout/$id"
    fun session(id: String) = "session/$id"
}

private data class Tab(val route: String, val label: String, @DrawableRes val icon: Int, val owns: List<String>)

private val tabs = listOf(
    Tab(Routes.WORKOUTS, "Treinos", R.drawable.ic_nav_workouts, listOf(Routes.WORKOUTS, Routes.WORKOUT)),
    Tab(Routes.HISTORY, "Histórico", R.drawable.ic_nav_history, listOf(Routes.HISTORY, Routes.SESSION)),
    Tab(Routes.SETTINGS, "Ajustes", R.drawable.ic_nav_settings, listOf(Routes.SETTINGS)),
)

/** Telas principais com a barra de navegação inferior. */
@Composable
fun MainNavigation() {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
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
            startDestination = Routes.WORKOUTS,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.WORKOUTS) {
                WorkoutsScreen(
                    onOpenWorkout = { navController.navigate(Routes.workout(it)) },
                    onOpenSession = { navController.navigate(Routes.session(it)) },
                )
            }
            composable(Routes.WORKOUT, arguments = listOf(navArgument("id") { type = NavType.StringType })) { entry ->
                WorkoutDetailScreen(
                    workoutId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
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
