package com.mojdesoh.groupchallenge.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mojdesoh.groupchallenge.data.ChallengeRepository
import com.mojdesoh.groupchallenge.data.Group
import com.mojdesoh.groupchallenge.data.GroupStatus
import com.mojdesoh.groupchallenge.data.RemovalNotice
import com.mojdesoh.groupchallenge.data.status
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    repository: ChallengeRepository,
    onCreateChallenge: () -> Unit,
    onJoinGroup: () -> Unit,
    onOpenGroup: (Group) -> Unit,
    onEditGroup: (Group) -> Unit,
    onGroupDeleted: (groupId: String) -> Unit,
    onGroupArchived: (groupId: String) -> Unit
) {
    var currentUserId by remember { mutableStateOf<String?>(null) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var removalNotices by remember { mutableStateOf<List<RemovalNotice>>(emptyList()) }
    LaunchedEffect(Unit) {
        val uid = try {
            repository.currentUserId()
        } catch (t: Throwable) {
            connectionError = "Couldn't connect to Firebase. Check your connection and app/google-services.json setup, then reopen the app."
            return@LaunchedEffect
        }
        currentUserId = uid
        // A failure here shouldn't block the rest of Home — removal notices are a nice-to-have,
        // not required to see or manage your challenges.
        removalNotices = try {
            repository.getRemovalNotices(uid)
        } catch (t: Throwable) {
            emptyList()
        }
    }

    val userId = currentUserId
    val groups by (if (userId != null) repository.observeMyGroups(userId) else emptyFlow())
        .collectAsState(initial = emptyList())
    val visibleGroups = groups.filter { !it.archived }

    var pendingDelete by remember { mutableStateOf<Group?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    var pendingArchive by remember { mutableStateOf<Group?>(null) }
    var isArchiving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Your challenges", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onJoinGroup) { Text("Join a group") }
            }

            if (connectionError != null || (userId != null && visibleGroups.isEmpty())) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    connectionError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    } ?: run {
                        Text("No challenges yet", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Start a group, invite people, set a goal, and hold each other to it.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    items(visibleGroups, key = { it.id }) { group ->
                        ChallengeCard(
                            group = group,
                            isAdmin = group.adminId == userId,
                            onOpen = { onOpenGroup(group) },
                            onEdit = { onEditGroup(group) },
                            onDelete = { pendingDelete = group },
                            onArchive = { pendingArchive = group }
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onCreateChallenge,
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
        ) {
            Text("+", style = MaterialTheme.typography.headlineSmall)
        }
    }

    pendingDelete?.let { group ->
        AlertDialog(
            onDismissRequest = { if (!isDeleting) pendingDelete = null },
            title = { Text("Delete \"${group.name}\"?") },
            text = { Text("This removes the challenge for everyone in the group. This can't be undone.") },
            confirmButton = {
                TextButton(
                    enabled = !isDeleting,
                    onClick = {
                        isDeleting = true
                        scope.launch {
                            try {
                                repository.deleteGroup(group.id)
                                onGroupDeleted(group.id)
                            } finally {
                                isDeleting = false
                                pendingDelete = null
                            }
                        }
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }, enabled = !isDeleting) { Text("Cancel") }
            }
        )
    }

    pendingArchive?.let { group ->
        AlertDialog(
            onDismissRequest = { if (!isArchiving) pendingArchive = null },
            title = { Text("Are you sure to archive this challenge?") },
            confirmButton = {
                TextButton(
                    enabled = !isArchiving,
                    onClick = {
                        isArchiving = true
                        scope.launch {
                            try {
                                repository.archiveGroup(group.id, true)
                                onGroupArchived(group.id)
                            } finally {
                                isArchiving = false
                                pendingArchive = null
                            }
                        }
                    }
                ) { Text("I am sure") }
            },
            dismissButton = {
                TextButton(onClick = { pendingArchive = null }, enabled = !isArchiving) { Text("No, discard") }
            }
        )
    }

    removalNotices.firstOrNull()?.let { notice ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Removed from \"${notice.groupName}\"") },
            text = {
                Text("You have been removed from the challenge \"${notice.groupName}\" by ${notice.removedByName}.")
            },
            confirmButton = {
                TextButton(onClick = {
                    val uid = userId ?: return@TextButton
                    removalNotices = removalNotices.drop(1)
                    scope.launch { repository.dismissRemovalNotice(uid, notice.groupId) }
                }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun ChallengeCard(
    group: Group,
    isAdmin: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onArchive: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(group.name, style = MaterialTheme.typography.titleMedium)
                Text(statusLabel(group), style = MaterialTheme.typography.bodyMedium)
            }
            if (isAdmin) {
                TextButton(onClick = onEdit, enabled = group.status() != GroupStatus.ENDED) {
                    Text("Edit")
                }
                if (group.status() != GroupStatus.NOT_LOCKED) {
                    TextButton(onClick = onArchive) { Text("Archive") }
                }
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

private fun statusLabel(group: Group): String = when (group.status()) {
    GroupStatus.NOT_LOCKED -> "Waiting to start"
    GroupStatus.ACTIVE -> "In progress"
    GroupStatus.ENDED -> "Ended"
}
