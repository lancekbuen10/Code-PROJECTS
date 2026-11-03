package com.motorsiklo.app.ui.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.motorsiklo.app.FirebaseManager

@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun MainScreen(
    role: String,
    username: String,
    onLogout: () -> Unit,
    onNavigateToResets: () -> Unit // Passed from NavGraph
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current

    // FIX: reportAppStatus only triggers after successful landing on the main authenticated screen.
    // This prevents "phantom" online users when the session is restored but the user is still on Login.
    LaunchedEffect(Unit) {
        FirebaseManager.reportAppStatus(context)
    }

    Scaffold(
        containerColor = colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = colorScheme.surface,
                tonalElevation = 4.dp // Added elevation for modern look
            ) {
                bottomNavItems.forEach { item ->
                    val selected = currentRoute == item.route
                    NavigationBarItem(
                        selected = selected,
                        onClick  = {
                            if (currentRoute != item.route) {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState    = true
                                }
                            }
                        },
                        icon = {
                            Icon(
                                imageVector        = item.icon,
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor   = colorScheme.primary,
                            selectedTextColor   = colorScheme.primary,
                            unselectedIconColor = colorScheme.onSurfaceVariant, // Better visibility in light mode
                            unselectedTextColor = colorScheme.onSurfaceVariant, // Better visibility in light mode
                            indicatorColor      = colorScheme.primary.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavHost(
                navController    = navController,
                startDestination = BottomNavItem.Dashboard.route,
                enterTransition  = { fadeIn(animationSpec = tween(400)) + scaleIn(initialScale = 0.95f, animationSpec = tween(400)) },
                exitTransition   = { fadeOut(animationSpec = tween(200)) }
            ) {
                composable(BottomNavItem.Dashboard.route) {
                    if (role == "Administrator") {
                        AdminDashboardScreen(
                            username = username,
                            onLogout = onLogout,
                            onNavigateToRequests = onNavigateToResets
                        )
                    } else {
                        DashboardScreen(
                            role = role,
                            username = username,
                            onLogout = onLogout,
                            onNavigateToResets = onNavigateToResets
                        )
                    }
                }
                composable(BottomNavItem.Violations.route) {
                    ViolationsScreen(role = role, username = username)
                }
                composable(BottomNavItem.Statistics.route) {
                    // Pass the role down to StatisticsScreen to handle Enforcer/Admin timeframe rules
                    StatisticsScreen(role = role)
                }
                composable(BottomNavItem.Profile.route) {
                    ProfileScreen(role = role, username = username, onLogout = onLogout)
                }
            }
        }
    }
}