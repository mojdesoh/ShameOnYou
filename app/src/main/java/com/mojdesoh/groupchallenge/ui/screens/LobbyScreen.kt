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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import android.content.Intent
import com.mojdesoh.groupchallenge.data.ChallengeRepository
import com.mojdesoh.groupchallenge.data.LocalPrefs

@Composable
fun LobbyScreen(
    groupId: String,
    repository: ChallengeRepository,
    prefs: LocalPrefs,
    onBack: () -> Unit,
    onLockAndSetChallenge: () -> Unit,
    onChallengeActive: () -> Unit
) {
    val context = LocalContext.current
    val group by repository.observeGroup(groupId).collectAsState(initial = null)
    val members by repository.observeMembers(groupId).collectAsState(initial = emptyList())
    var currentUserId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        currentUserId = repository.currentUserId()
    }

    LaunchedEffect(group) {
        val g = group
        if (g != null && g.locked && g.challenge != null) {
            onChallengeActive()
        }
    }

    val isAdmin = group?.adminId != null && group?.adminId == currentUserId

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Text(group?.name ?: "Loading…", style = MaterialTheme.typography.headlineSmall)
        }

        group?.let { g ->
            Spacer(Modifier.height(16.dp))
            Text("Group code: ${g.inviteCode}", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = {
                    val shareText = "Join my group \"${g.name}\" on Group Challenge.\n" +
                        "Group code: ${g.inviteCode}\n" +
                        "Or tap this link if you already have the app: groupchallenge://join?code=${g.inviteCode}"
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(intent, "Share invite"))
                },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Share invite")
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Members (${members.size})", style = MaterialTheme.typography.titleMedium)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(members) { member ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(member.displayName)
                    if (member.userId == group?.adminId) {
                        Text("admin", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        if (isAdmin) {
            Button(onClick = onLockAndSetChallenge, modifier = Modifier.fillMaxWidth()) {
                Text("Lock group & set challenge")
            }
        } else {
            Text(
                "Waiting for the admin to lock the group and set a challenge…",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
