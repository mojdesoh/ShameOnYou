package com.mojdesoh.groupchallenge.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.mojdesoh.groupchallenge.data.Member
import kotlinx.coroutines.launch

@Composable
fun EditChallengeScreen(
    groupId: String,
    repository: ChallengeRepository,
    onBack: () -> Unit
) {
    val group by repository.observeGroup(groupId).collectAsState(initial = null)
    val members by repository.observeMembers(groupId).collectAsState(initial = emptyList())

    var name by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingRemoval by remember { mutableStateOf<Member?>(null) }
    var isRemoving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Seed the field once, from the first load — don't clobber what the admin is typing.
    LaunchedEffect(group?.id) {
        if (name == null) name = group?.name
    }

    val g = group

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Text("Edit challenge", style = MaterialTheme.typography.headlineSmall)
        }

        if (g == null) {
            return@Column
        }

        OutlinedTextField(
            value = name ?: "",
            onValueChange = { name = it },
            label = { Text("Challenge name") },
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
        )
        Text(
            "Unique code: ${g.inviteCode} (can't be changed)",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp)
        )

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        Button(
            onClick = {
                val trimmed = name.orEmpty().trim()
                if (trimmed.isEmpty()) {
                    error = "Enter a challenge name."
                    return@Button
                }
                error = null
                isSaving = true
                scope.launch {
                    try {
                        repository.updateGroupName(groupId, trimmed)
                    } catch (t: Throwable) {
                        error = "Couldn't save. Check your connection and try again."
                    } finally {
                        isSaving = false
                    }
                }
            },
            enabled = !isSaving && name.orEmpty().trim().isNotEmpty() && name?.trim() != g.name,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
        ) {
            Text("Save name")
        }

        Spacer(Modifier.height(24.dp))
        Text("Members (${members.size})", style = MaterialTheme.typography.titleMedium)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(members, key = { it.userId }) { member ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(member.displayName)
                    if (member.userId == g.adminId) {
                        Text("admin", style = MaterialTheme.typography.labelMedium)
                    } else {
                        TextButton(onClick = { pendingRemoval = member }) { Text("Remove") }
                    }
                }
            }
        }
    }

    pendingRemoval?.let { member ->
        AlertDialog(
            onDismissRequest = { if (!isRemoving) pendingRemoval = null },
            title = { Text("Remove ${member.displayName}?") },
            text = {
                Text(
                    "They'll be removed from \"${g?.name.orEmpty()}\" and see a notice next time " +
                        "they open the app. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isRemoving,
                    onClick = {
                        val currentGroup = g ?: return@TextButton
                        isRemoving = true
                        scope.launch {
                            try {
                                repository.removeMember(
                                    groupId, member.userId, currentGroup.name, currentGroup.adminDisplayName
                                )
                            } finally {
                                isRemoving = false
                                pendingRemoval = null
                            }
                        }
                    }
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }, enabled = !isRemoving) { Text("Cancel") }
            }
        )
    }
}
