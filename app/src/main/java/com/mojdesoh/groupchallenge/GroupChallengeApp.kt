package com.mojdesoh.groupchallenge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mojdesoh.groupchallenge.data.ChallengeRepository
import com.mojdesoh.groupchallenge.data.LocalPrefs
import com.mojdesoh.groupchallenge.ui.screens.CreateChallengeScreen
import com.mojdesoh.groupchallenge.ui.screens.CreateGroupScreen
import com.mojdesoh.groupchallenge.ui.screens.EntryScreen
import com.mojdesoh.groupchallenge.ui.screens.JoinGroupScreen
import com.mojdesoh.groupchallenge.ui.screens.LobbyScreen
import com.mojdesoh.groupchallenge.ui.screens.ProgressScreen
import com.mojdesoh.groupchallenge.ui.screens.ResultScreen
import com.mojdesoh.groupchallenge.ui.screens.WelcomeScreen

/** Where the app should jump to on top of its normal saved-group start destination. */
sealed class PendingNav {
    data class JoinWithCode(val code: String) : PendingNav()
    data class OpenEntry(val groupId: String) : PendingNav()
    data class OpenResult(val groupId: String) : PendingNav()
}

@Composable
fun GroupChallengeApp(prefs: LocalPrefs, pendingNav: PendingNav?, onPendingNavConsumed: () -> Unit) {
    val repository = remember { ChallengeRepository() }
    val navController = rememberNavController()

    val startDestination = remember {
        when (pendingNav) {
            is PendingNav.JoinWithCode -> "joinGroup"
            else -> prefs.groupId?.let { "lobby/$it" } ?: "welcome"
        }
    }

    LaunchedEffect(pendingNav) {
        when (val nav = pendingNav) {
            is PendingNav.OpenEntry -> navController.navigate("entry/${nav.groupId}")
            is PendingNav.OpenResult -> navController.navigate("result/${nav.groupId}")
            is PendingNav.JoinWithCode, null -> Unit
        }
        if (pendingNav != null) onPendingNavConsumed()
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable("welcome") {
            WelcomeScreen(
                onCreateGroup = { navController.navigate("createGroup") },
                onJoinGroup = { navController.navigate("joinGroup") }
            )
        }
        composable("createGroup") {
            CreateGroupScreen(
                repository = repository,
                prefs = prefs,
                onCreated = { groupId ->
                    navController.navigate("lobby/$groupId") {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                    }
                }
            )
        }
        composable("joinGroup") {
            JoinGroupScreen(
                repository = repository,
                prefs = prefs,
                prefilledCode = (pendingNav as? PendingNav.JoinWithCode)?.code,
                onJoined = { groupId ->
                    navController.navigate("lobby/$groupId") {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                    }
                }
            )
        }
        composable(
            "lobby/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments!!.getString("groupId")!!
            LobbyScreen(
                groupId = groupId,
                repository = repository,
                prefs = prefs,
                onLockAndSetChallenge = { navController.navigate("createChallenge/$groupId") },
                onChallengeActive = {
                    navController.navigate("progress/$groupId") {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                    }
                }
            )
        }
        composable(
            "createChallenge/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments!!.getString("groupId")!!
            CreateChallengeScreen(
                groupId = groupId,
                repository = repository,
                onChallengeStarted = {
                    navController.navigate("progress/$groupId") {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                    }
                }
            )
        }
        composable(
            "progress/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments!!.getString("groupId")!!
            ProgressScreen(
                groupId = groupId,
                repository = repository,
                onLogProgress = { navController.navigate("entry/$groupId") },
                onViewResult = { navController.navigate("result/$groupId") }
            )
        }
        composable(
            "entry/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments!!.getString("groupId")!!
            EntryScreen(
                groupId = groupId,
                repository = repository,
                prefs = prefs,
                onSubmitted = { navController.popBackStack() }
            )
        }
        composable(
            "result/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments!!.getString("groupId")!!
            ResultScreen(groupId = groupId, repository = repository)
        }
    }
}
