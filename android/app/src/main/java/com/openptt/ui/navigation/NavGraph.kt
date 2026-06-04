package com.openptt.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.openptt.ui.screens.channel.ChannelScreen
import com.openptt.ui.screens.groups.GroupsScreen
import com.openptt.ui.screens.home.HomeScreen
import com.openptt.ui.screens.login.LoginScreen
import com.openptt.ui.screens.settings.SettingsScreen
import com.openptt.ui.screens.setup.ServerSetupScreen

/**
 * Navigation routes for the app.
 */
object Routes {
    const val SERVER_SETUP = "server_setup"
    const val LOGIN = "login"
    const val HOME = "home"
    const val GROUP_DETAIL = "group/{groupId}"
    const val CHANNEL = "channel/{channelId}"
    const val SETTINGS = "settings"

    fun groupDetail(groupId: String) = "group/$groupId"
    fun channel(channelId: String) = "channel/$channelId"
}

@Composable
fun NavGraph(startDestination: String = Routes.SERVER_SETUP) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.SERVER_SETUP) {
            ServerSetupScreen(
                onContinue = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.SERVER_SETUP) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onGroupClick = { groupId ->
                    navController.navigate(Routes.groupDetail(groupId))
                },
                onSettingsClick = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }

        composable(
            Routes.GROUP_DETAIL,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
            GroupsScreen(
                groupId = groupId,
                onChannelClick = { channelId ->
                    navController.navigate(Routes.channel(channelId))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            Routes.CHANNEL,
            arguments = listOf(navArgument("channelId") { type = NavType.StringType })
        ) { backStackEntry ->
            val channelId = backStackEntry.arguments?.getString("channelId") ?: return@composable
            ChannelScreen(
                channelId = channelId,
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Routes.SERVER_SETUP) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
