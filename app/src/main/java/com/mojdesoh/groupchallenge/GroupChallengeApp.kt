package com.mojdesoh.groupchallenge

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mojdesoh.groupchallenge.data.ChallengeRepository
import com.mojdesoh.groupchallenge.data.GroupStatus
import com.mojdesoh.groupchallenge.data.LocalPrefs
import com.mojdesoh.groupchallenge.data.status
import com.mojdesoh.groupchallenge.ui.screens.ChallengeDetailsScreen
import com.mojdesoh.groupchallenge.ui.screens.CreateChallengeScreen
import com.mojdesoh.groupchallenge.ui.screens.CreateGroupScreen
import com.mojdesoh.groupchallenge.ui.screens.DiscoverChallengesScreen
import com.mojdesoh.groupchallenge.ui.screens.EditChallengeScreen
import com.mojdesoh.groupchallenge.ui.screens.EntryScreen
import com.mojdesoh.groupchallenge.ui.screens.HomeScreen
import com.mojdesoh.groupchallenge.ui.screens.JoinChoiceScreen
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
            is PendingNav.JoinWithCode -> navController.navigate("joinGroup?code=${nav.code}")
            is PendingNav.OpenEntry -> navController.navigate("entry/${nav.groupId}")
            is PendingNav.OpenResult -> navController.navigate("result/${nav.groupId}")
            null -> Unit
        }
        if (pendingNav != null) onPendingNavConsumed()
    }

    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val isHome = currentRoute == "home"
    // Same button throughout the app: big and centered on Home, a small + FAB everywhere else,
    // except on the create form itself where it would just reopen the screen you're already on.
    // Stays bottom-anchored in both states (screen content already reserves room for it there)
    // and only slides horizontally, so it never overlaps Home's own centered empty-state text.
    val showCreateFab = currentRoute != "createGroup"
    val fabHorizontalBias by animateFloatAsState(if (isHome) 0f else 1f, label = "createFabHorizontalBias")

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = startDestination) {
            composable("home") {
                HomeScreen(
                    repository = repository,
                    onJoinGroup = { navController.navigate("joinChoice") },
                    onOpenGroup = { group ->
                        val destination = when (group.status()) {
                            GroupStatus.NOT_LOCKED -> "lobby/${group.id}"
                            GroupStatus.ACTIVE, GroupStatus.ENDED -> "details/${group.id}"
                        }
                        navController.navigate(destination)
                    },
                    onEditGroup = { group ->
                        when (group.status()) {
                            GroupStatus.NOT_LOCKED -> navController.navigate("lobby/${group.id}")
                            GroupStatus.ACTIVE -> navController.navigate("editChallenge/${group.id}")
                            GroupStatus.ENDED -> Unit
                        }
                    },
                    onGroupDeleted = { groupId -> ReminderScheduler.cancelAll(context, groupId) },
                    onGroupArchived = { groupId -> ReminderScheduler.cancelAll(context, groupId) }
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
            composable("joinChoice") {
                JoinChoiceScreen(
                    onBack = { navController.popBackStack() },
                    onEnterChallengeName = { navController.navigate("joinGroup") },
                    onDiscoverChallenges = { navController.navigate("discoverChallenges") }
                )
            }
            composable("discoverChallenges") {
                DiscoverChallengesScreen(
                    repository = repository,
                    onBack = { navController.popBackStack() },
                    onSelectChallenge = { code -> navController.navigate("joinGroup?code=$code") }
                )
            }
            composable(
                "joinGroup?code={code}",
                arguments = listOf(navArgument("code") { type = NavType.StringType; nullable = true; defaultValue = null })
            ) { backStackEntry ->
                JoinGroupScreen(
                    repository = repository,
                    prefs = prefs,
                    prefilledCode = backStackEntry.arguments?.getString("code"),
                    onBack = { navController.popBackStack() },
                    onRequested = {
                        navController.navigate("home") { popUpTo("home") { inclusive = true } }
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
                        navController.navigate("details/$groupId") { popUpTo("home") }
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
                        navController.navigate("details/$groupId") { popUpTo("home") }
                    }
                )
            }
            composable(
                "details/{groupId}",
                arguments = listOf(navArgument("groupId") { type = NavType.StringType })
            ) { backStackEntry ->
                val groupId = backStackEntry.arguments!!.getString("groupId")!!
                ChallengeDetailsScreen(
                    groupId = groupId,
                    repository = repository,
                    onBack = { navController.popBackStack() },
                    onViewProgress = { navController.navigate("progress/$groupId") },
                    onViewResult = { navController.navigate("result/$groupId") }
                )
            }
            composable(
                "editChallenge/{groupId}",
                arguments = listOf(navArgument("groupId") { type = NavType.StringType })
            ) { backStackEntry ->
                val groupId = backStackEntry.arguments!!.getString("groupId")!!
                EditChallengeScreen(
                    groupId = groupId,
                    repository = repository,
                    onBack = { navController.popBackStack() }
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

        if (showCreateFab) {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate("createGroup") },
                expanded = isHome,
                icon = { Icon(Icons.Default.Add, contentDescription = "Create a challenge") },
                text = { Text("Create a challenge") },
                modifier = Modifier.align(BiasAlignment(fabHorizontalBias, 1f)).padding(24.dp)
            )
        }
    }
}
