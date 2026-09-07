package com.mojdesoh.groupchallenge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mojdesoh.groupchallenge.data.ChallengeRepository
import com.mojdesoh.groupchallenge.data.GroupStatus
import com.mojdesoh.groupchallenge.data.LocalPrefs
import com.mojdesoh.groupchallenge.data.status
import com.mojdesoh.groupchallenge.ui.screens.CreateChallengeScreen
import com.mojdesoh.groupchallenge.ui.screens.CreateGroupScreen
import com.mojdesoh.groupchallenge.ui.screens.EntryScreen
import com.mojdesoh.groupchallenge.ui.screens.HomeScreen
import com.mojdesoh.groupchallenge.ui.screens.JoinGroupScreen
import com.mojdesoh.groupchallenge.ui.screens.LobbyScreen
import com.mojdesoh.groupchallenge.ui.screens.ProgressScreen
import com.mojdesoh.groupchallenge.ui.screens.ResultScreen
import com.mojdesoh.groupchallenge.work.ReminderScheduler

/** Where the app should jump to on top of Home, e.g. from a deep link or a notification tap. */
sealed class PendingNav {
    data class JoinWithCode(val code: String) : PendingNav()
    data class OpenEntry(val groupId: String) : PendingNav()
    data class OpenResult(val groupId: String) : PendingNav()
}

@Composable
fun GroupChallengeApp(prefs: LocalPrefs, pendingNav: PendingNav?, onPendingNavConsumed: () -> Unit) {
    val repository = remember { ChallengeRepository() }
    val navController = rememberNavController()
    val context = LocalContext.current

    val startDestination = remember { "home" }

    LaunchedEffect(pendingNav) {
        when (val nav = pendingNav) {
            is PendingNav.JoinWithCode -> navController.navigate("joinGroup")
            is PendingNav.OpenEntry -> navController.navigate("entry/${nav.groupId}")
            is PendingNav.OpenResult -> navController.navigate("result/${nav.groupId}")
            null -> Unit
        }
        if (pendingNav != null) onPendingNavConsumed()
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable("home") {
            HomeScreen(
                repository = repository,
                onCreateChallenge = { navController.navigate("createGroup") },
                onJoinGroup = { navController.navigate("joinGroup") },
                onOpenGroup = { group ->
                    val destination = when (group.status()) {
                        GroupStatus.NOT_LOCKED -> "lobby/${group.id}"
                        GroupStatus.ACTIVE -> "progress/${group.id}"
                        GroupStatus.ENDED -> "result/${group.id}"
                    }
                    navController.navigate(destination)
                },
                onEditGroup = { group -> navController.navigate("lobby/${group.id}") },
                onGroupDeleted = { groupId -> ReminderScheduler.cancelAll(context, groupId) }
            )
        }
        composable("createGroup") {
            CreateGroupScreen(
                repository = repository,
                prefs = prefs,
                onBack = { navController.popBackStack() },
                onCreated = { groupId ->
                    navController.navigate("lobby/$groupId") { popUpTo("home") }
                }
            )
        }
        composable("joinGroup") {
            JoinGroupScreen(
                repository = repository,
                prefs = prefs,
                prefilledCode = (pendingNav as? PendingNav.JoinWithCode)?.code,
                onBack = { navController.popBackStack() },
                onJoined = { groupId ->
                    navController.navigate("lobby/$groupId") { popUpTo("home") }
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
                onBack = { navController.popBackStack() },
                onLockAndSetChallenge = { navController.navigate("createChallenge/$groupId") },
                onChallengeActive = {
                    navController.navigate("progress/$groupId") { popUpTo("home") }
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
                onBack = { navController.popBackStack() },
                onChallengeStarted = {
                    navController.navigate("progress/$groupId") { popUpTo("home") }
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
                onBack = { navController.popBackStack() },
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
                onBack = { navController.popBackStack() },
                onSubmitted = { navController.popBackStack() }
            )
        }
        composable(
            "result/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments!!.getString("groupId")!!
            ResultScreen(
                groupId = groupId,
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
