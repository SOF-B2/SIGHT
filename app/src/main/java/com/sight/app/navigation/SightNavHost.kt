package com.sight.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sight.app.ui.AnalysisViewModel
import com.sight.app.ui.home.HomeScreen
import com.sight.app.ui.motion.MotionScreen
import com.sight.app.ui.scene.SceneScreen
import com.sight.app.ui.settings.SettingsScreen
import com.sight.app.ui.simulation.SimulationScreen
import com.sight.app.ui.tracking.TrackingScreen
import com.sight.app.ui.vision.VisionScreen

private data class Dest(val route: String, val label: String, val icon: ImageVector)

private val destinations = listOf(
    Dest("home", "Home", Icons.Filled.Home),
    Dest("vision", "Vision", Icons.Filled.Visibility),
    Dest("tracking", "Tracking", Icons.Filled.Timeline),
    Dest("motion", "Motion", Icons.Filled.Speed),
    Dest("scene", "Scene", Icons.Filled.Description),
    Dest("simulation", "Sim Lab", Icons.Filled.Science),
    Dest("settings", "Settings", Icons.Filled.Settings)
)

@Composable
fun SightNavHost(viewModel: AnalysisViewModel) {
    val navController = rememberNavController()
    val state by viewModel.state.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"
    Scaffold(
        bottomBar = {
            NavigationBar {
                destinations.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(navController = navController, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(state, onNavigate = { navController.navigate(it) }) }
            composable("vision") { VisionScreen(state, viewModel) }
            composable("tracking") { TrackingScreen(state) }
            composable("motion") { MotionScreen(state) }
            composable("scene") { SceneScreen(state) }
            composable("simulation") { SimulationScreen(state, viewModel) }
            composable("settings") { SettingsScreen(state, viewModel) }
        }
    }
}
