package com.motorsiklo.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.motorsiklo.app.ui.screens.*

sealed class Screen(val route: String) {
    object Landing       : Screen("landing")
    object RoleSelect    : Screen("role_select")
    object EnforcerLogin : Screen("enforcer_login")
    object AdminLogin    : Screen("admin_login")
    object AdminResets   : Screen("admin_resets")
    object Register      : Screen("register")
    object Main          : Screen("main/{role}/{username}") {
        fun createRoute(role: String, username: String) = "main/$role/$username"
    }
}

@Composable
fun MotorSikloNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController    = navController,
        startDestination = Screen.Landing.route
    ) {
        composable(Screen.Landing.route) {
            LandingScreen(
                onLoginClick    = { navController.navigate(Screen.RoleSelect.route) },
                onRegisterClick = { navController.navigate(Screen.Register.route) }
            )
        }

        composable(Screen.RoleSelect.route) {
            RoleSelectScreen(
                onEnforcerClick = { navController.navigate(Screen.EnforcerLogin.route) },
                onAdminClick    = { navController.navigate(Screen.AdminLogin.route) },
                onBackClick     = { navController.popBackStack() }
            )
        }

        composable(Screen.EnforcerLogin.route) {
            LoginScreen(
                role            = "Traffic Enforcer",
                onLoginSuccess  = { username ->
                    navController.navigate(Screen.Main.createRoute("Traffic Enforcer", username)) {
                        popUpTo(Screen.Landing.route) { inclusive = true }
                    }
                },
                onBackClick     = { navController.popBackStack() }
            )
        }

        composable(Screen.AdminLogin.route) {
            LoginScreen(
                role            = "Administrator",
                onLoginSuccess  = { username ->
                    navController.navigate(Screen.Main.createRoute("Administrator", username)) {
                        popUpTo(Screen.Landing.route) { inclusive = true }
                    }
                },
                onBackClick     = { navController.popBackStack() }
            )
        }

        composable(Screen.AdminResets.route) {
            AdminRequestsScreen(onBack = { navController.popBackStack() })
        }

        composable(Screen.Register.route) {
            RegisterScreen(
                onSignInClick = { navController.popBackStack(Screen.Landing.route, inclusive = false) },
                onBackClick   = { navController.popBackStack() }
            )
        }

        composable(Screen.Main.route) { backStackEntry ->
            val role = backStackEntry.arguments?.getString("role") ?: ""
            val username = backStackEntry.arguments?.getString("username") ?: ""
            MainScreen(
                role     = role,
                username = username,
                onLogout = {
                    navController.navigate(Screen.Landing.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToResets = {
                    navController.navigate(Screen.AdminResets.route)
                }
            )
        }
    }
}