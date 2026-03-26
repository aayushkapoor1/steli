package com.steli.app

import android.os.Bundle
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
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
    const val RANK_WITH_SPOT = "rank?spotName={spotName}"
    const val DISCOVER = "discover"
    const val PROFILE = "profile"
    const val USER = "user/{username}"
    fun user(username: String) = "user/$username"
    fun rankWithSpot(spotName: String) = "rank?spotName=${Uri.encode(spotName)}"
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
    // Always start on login; if already logged in, LoginScreen will navigate to HOME
    val startDestination = Routes.LOGIN

    // Check current route for showing bottom bar
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentBaseRoute = currentRoute?.substringBefore("?")
    val showBottomBar = currentBaseRoute in bottomNavItems.map { it.route }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    bottomNavItems.forEach { item ->
                        val selected = currentBaseRoute == item.route
                        val selectedColor = MaterialTheme.colorScheme.onSurface
                        val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                        val contentColor = if (selected) selectedColor else unselectedColor
                        val bgColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(16.dp))
                                .background(bgColor)
                                .clickable {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    item.icon,
                                    contentDescription = item.label,
                                    tint = contentColor,
                                    modifier = Modifier.size(22.dp),
                                )
                                Text(
                                    text = item.label,
                                    color = contentColor,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
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

            // ── Auth screens ─────────────────────────────────────

            composable(Routes.LOGIN) {
                LoginScreen(
                    onNavigateToRegister = {
                        navController.navigate(Routes.REGISTER)
                    },
                    onLoginSuccess = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                )
            }

            composable(Routes.REGISTER) {
                RegisterScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onRegisterSuccess = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                )
            }

            // ── Main screens ─────────────────────────────────────

            composable(Routes.HOME) {
                HomeScreen()
            }

            composable(Routes.RANK) {
                RankScreen()
            }

            composable(Routes.RANK_WITH_SPOT) { backStackEntry ->
                val spotName = backStackEntry.arguments?.getString("spotName")
                RankScreen(prefillSpotName = spotName)
            }

            composable(Routes.DISCOVER) {
                DiscoverScreen(
                    onAddSpot = {
                        navController.navigate(Routes.rankWithSpot(it)) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToUser = { navController.navigate(Routes.user(it)) },
                )
            }

            composable(Routes.PROFILE) {
                ProfileScreen(
                    username = null,
                    onNavigateToUser = { navController.navigate(Routes.user(it)) },
                    onLogout = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }

            composable(Routes.USER) { backStackEntry ->
                val username = backStackEntry.arguments?.getString("username")
                if (username == null) {
                    Text("User not found")
                    return@composable
                }

                ProfileScreen(
                    username = username,
                    onNavigateToUser = { navController.navigate(Routes.user(it)) },
                    onNavigateBack = { navController.popBackStack() },
                    onLogout = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}
