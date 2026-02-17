package com.steli.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.steli.app.data.AuthManager
import com.steli.app.ui.screens.*
import com.steli.app.ui.theme.SteliTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthManager.init(this)
        enableEdgeToEdge()
        setContent {
            SteliTheme {
                SteliApp()
            }
        }
    }
}

// ── Navigation routes ────────────────────────────────────────────

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val HOME = "home"
    const val RANK = "rank"
    const val DISCOVER = "discover"
    const val PROFILE = "profile"
    const val USER = "user/{username}"
    fun user(username: String) = "user/$username"
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val bottomNavItems = listOf(
    BottomNavItem(Routes.HOME, "Home", Icons.Filled.Home),
    BottomNavItem(Routes.RANK, "Rank", Icons.Filled.Balance),
    BottomNavItem(Routes.DISCOVER, "Search", Icons.Filled.Search),
    BottomNavItem(Routes.PROFILE, "Profile", Icons.Filled.Person),
)

// ── Root composable ──────────────────────────────────────────────

@Composable
fun SteliApp() {
    val navController = rememberNavController()
    // TODO: Change to start on login page
    val startDestination = Routes.HOME

    // Check current route for showing bottom bar
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
        ) {
            // ── Auth screens ─────────────────────────────────────

            composable(Routes.LOGIN) {
                // TODO: Login screen
                Text("Login Screen Placeholder")
            }

            composable(Routes.REGISTER) {
                // TODO: Register screen
                Text("Register Screen Placeholder")
            }

            // ── Main screens ─────────────────────────────────────

            composable(Routes.HOME) {
                HomeScreen()
            }

            composable(Routes.RANK) {
                RankScreen()
            }

            composable(Routes.DISCOVER) {
                // TODO: Search screen
                Text("Search Screen Placeholder")
            }

            composable(Routes.PROFILE) {
                // TODO: Profile screen
                Text("Profile Screen Placeholder")
            }

            composable(Routes.USER) {
                // TODO: User screen
                Text("User Screen Placeholder")
            }
        }
    }
}
