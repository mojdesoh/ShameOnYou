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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.mojdesoh.groupchallenge.data.JoinRequest
import com.mojdesoh.groupchallenge.data.RemovalNotice
import com.mojdesoh.groupchallenge.data.status
import com.mojdesoh.groupchallenge.data.toDateTimeLabel
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
    var myJoinRequests by remember { mutableStateOf<List<JoinRequest>>(emptyList()) }
    LaunchedEffect(Unit) {
        val uid = try {
            repository.currentUserId()
        } catch (t: Throwable) {
            connectionError = "Couldn't connect to Firebase. Check your connection and app/google-services.json setup, then reopen the app."
            return@LaunchedEffect
        }
        currentUserId = uid
        // A failure here shouldn't block the rest of Home — removal notices and pending requests
        // are a nice-to-have, not required to see or manage your challenges.
        removalNotices = try {
            repository.getRemovalNotices(uid)
        } catch (t: Throwable) {
            emptyList()
        }
        myJoinRequests = try {
            repository.getMyJoinRequests(uid)
        } catch (t: Throwable) {
            emptyList()
        }
    }

    val userId = currentUserId
    val groups by (if (userId != null) repository.observeMyGroups(userId) else emptyFlow())
        .collectAsState(initial = emptyList())
    val visibleGroups = groups.filter { !it.archived }

    // One-time fetch of pending join requests for every challenge the current user admins,
    // refreshed whenever the admin's group list changes. Not live — a new request only shows up
    // the next time Home loads or a group's membership changes — which is enough for this scope.
    var pendingJoinRequests by remember { mutableStateOf<List<JoinRequest>>(emptyList()) }
    LaunchedEffect(groups, userId) {
        val uid = userId ?: return@LaunchedEffect
        pendingJoinRequests = try {
            groups.filter { it.adminId == uid }.flatMap { repository.getPendingJoinRequests(it.id) }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    var pendingDelete by remember { mutableStateOf<Group?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    var pendingArchive by remember { mutableStateOf<Group?>(null) }
    var isArchiving by remember { mutableStateOf(false) }
    var pendingLeave by remember { mutableStateOf<Group?>(null) }
    var isLeaving by remember { mutableStateOf(false) }
    var pendingCancelRequest by remember { mutableStateOf<JoinRequest?>(null) }
    var isCancellingRequest by remember { mutableStateOf(false) }
    var isHandlingJoinRequest by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Your challenges", style = MaterialTheme.typography.headlineSmall)
                TextButton(onClick = onJoinGroup) { Text("Join a challenge") }
            }

            if (connectionError != null ||
                (userId != null && visibleGroups.isEmpty() && myJoinRequests.isEmpty())
            ) {
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
                        val isAdmin = group.adminId == userId
                        ChallengeCard(
                            group = group,
                            isAdmin = isAdmin,
                            onOpen = { onOpenGroup(group) },
                            onEdit = { onEditGroup(group) },
                            onArchive = { pendingArchive = group },
                            onDeleteOrLeave = {
                                if (isAdmin) pendingDelete = group else pendingLeave = group
                            }
                        )
                    }
                    items(myJoinRequests, key = { "request-${it.groupId}" }) { request ->
                        RequestedChallengeCard(
                            request = request,
                            onCancel = { pendingCancelRequest = request }
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
            title = { Text("Are you sure you want to delete ${group.name} challenge?") },
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

    pendingLeave?.let { group ->
        AlertDialog(
            onDismissRequest = { if (!isLeaving) pendingLeave = null },
            title = { Text("Are you sure to leave the challenge?") },
            confirmButton = {
                TextButton(
                    enabled = !isLeaving,
                    onClick = {
                        isLeaving = true
                        scope.launch {
                            try {
                                repository.leaveGroup(group.id)
                            } finally {
                                isLeaving = false
                                pendingLeave = null
                            }
                        }
                    }
                ) { Text("Yes, leave") }
            },
            dismissButton = {
                TextButton(onClick = { pendingLeave = null }, enabled = !isLeaving) { Text("No") }
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

    pendingCancelRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { if (!isCancellingRequest) pendingCancelRequest = null },
            title = { Text("Cancel your request to join \"${request.groupName}\"?") },
            confirmButton = {
                TextButton(
                    enabled = !isCancellingRequest,
                    onClick = {
                        isCancellingRequest = true
                        scope.launch {
                            try {
                                repository.removeJoinRequest(request.groupId, request.userId)
                                myJoinRequests = myJoinRequests.filter { it.groupId != request.groupId }
                            } finally {
                                isCancellingRequest = false
                                pendingCancelRequest = null
                            }
                        }
                    }
                ) { Text("Yes, cancel") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCancelRequest = null }, enabled = !isCancellingRequest) { Text("No") }
            }
        )
    }

    pendingJoinRequests.firstOrNull()?.let { request ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Join request") },
            text = {
                Column {
                    Text("${request.displayName} wants to join \"${request.groupName}\".")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Requested ${request.requestedAtMillis.toDateTimeLabel()}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isHandlingJoinRequest,
                    onClick = {
                        isHandlingJoinRequest = true
                        scope.launch {
                            try {
                                repository.confirmJoinRequest(request)
                            } finally {
                                pendingJoinRequests = pendingJoinRequests.drop(1)
                                isHandlingJoinRequest = false
                            }
                        }
                    }
                ) { Text("Confirm") }
            },
            dismissButton = {
                TextButton(
                    enabled = !isHandlingJoinRequest,
                    onClick = {
                        isHandlingJoinRequest = true
                        scope.launch {
                            try {
                                repository.removeJoinRequest(request.groupId, request.userId)
                            } finally {
                                pendingJoinRequests = pendingJoinRequests.drop(1)
                                isHandlingJoinRequest = false
                            }
                        }
                    }
                ) { Text("Reject") }
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
    onArchive: () -> Unit,
    onDeleteOrLeave: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(group.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${statusLabel(group)} · ${if (isAdmin) "Admin" else "Member"}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            IconButton(onClick = onEdit, enabled = isAdmin && group.status() != GroupStatus.ENDED) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = onArchive, enabled = isAdmin) {
                Icon(Icons.Default.Archive, contentDescription = "Archive")
            }
            IconButton(onClick = onDeleteOrLeave) {
                Icon(Icons.Default.Delete, contentDescription = if (isAdmin) "Delete" else "Leave")
            }
        }
    }
}

@Composable
private fun RequestedChallengeCard(request: JoinRequest, onCancel: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(request.groupName, style = MaterialTheme.typography.titleMedium)
                Text("Requested", style = MaterialTheme.typography.bodyMedium)
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Delete, contentDescription = "Cancel request")
            }
        }
    }
}

private fun statusLabel(group: Group): String = when (group.status()) {
    GroupStatus.NOT_LOCKED -> "Waiting to start"
    GroupStatus.ACTIVE -> "In progress"
    GroupStatus.ENDED -> "Ended"
}
