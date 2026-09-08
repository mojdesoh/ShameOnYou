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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mojdesoh.groupchallenge.data.ChallengeRepository
import com.mojdesoh.groupchallenge.data.ChallengeResultEvaluator
import com.mojdesoh.groupchallenge.data.toDateLabel

@Composable
fun ChallengeDetailsScreen(
    groupId: String,
    repository: ChallengeRepository,
    onBack: () -> Unit,
    onViewProgress: () -> Unit,
    onViewResult: () -> Unit
) {
    val group by repository.observeGroup(groupId).collectAsState(initial = null)
    val members by repository.observeMembers(groupId).collectAsState(initial = emptyList())
    val entries by repository.observeEntries(groupId).collectAsState(initial = emptyList())
    var currentUserId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        currentUserId = repository.currentUserId()
    }

    val g = group
    val challenge = g?.challenge

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Text(g?.name ?: "Loading…", style = MaterialTheme.typography.headlineSmall)
        }

        if (g == null || challenge == null) {
            return@Column
        }

        val myName = members.firstOrNull { it.userId == currentUserId }?.displayName
        val finished = System.currentTimeMillis() >= challenge.endAtMillis
        val statusLabel = if (!finished) {
            "Ongoing"
        } else {
            val result = ChallengeResultEvaluator.evaluate(challenge, members, entries)
            if (result.groupSucceeded) "Success" else "Failure"
        }

        Spacer(Modifier.height(20.dp))
        DetailRow("Unique code", g.inviteCode)
        DetailRow("My name", myName ?: "—")
        DetailRow("Created", challenge.startAtMillis.toDateLabel())
        DetailRow("End date", challenge.endAtMillis.toDateLabel())
        DetailRow("Status", statusLabel)

        Spacer(Modifier.height(24.dp))
        Text("Members (${members.size})", style = MaterialTheme.typography.titleMedium)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(members) { member ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(member.displayName)
                    if (member.userId == g.adminId) {
                        Text("admin", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        Button(
            onClick = if (finished) onViewResult else onViewProgress,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Text(if (finished) "View full results" else "View progress")
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
