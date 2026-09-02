package com.mojdesoh.groupchallenge.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
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
import com.mojdesoh.groupchallenge.data.GoalType
import com.mojdesoh.groupchallenge.data.formatNumber
import com.mojdesoh.groupchallenge.data.periodLabel

@Composable
fun ProgressScreen(
    groupId: String,
    repository: ChallengeRepository,
    onLogProgress: () -> Unit,
    onViewResult: () -> Unit
) {
    val group by repository.observeGroup(groupId).collectAsState(initial = null)
    val members by repository.observeMembers(groupId).collectAsState(initial = emptyList())
    val entries by repository.observeEntries(groupId).collectAsState(initial = emptyList())
    var currentUserId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        currentUserId = repository.currentUserId()
    }

    val challenge = group?.challenge

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        if (challenge == null) {
            Text("Loading…", style = MaterialTheme.typography.headlineSmall)
            return@Column
        }

        val periodEnded = System.currentTimeMillis() >= challenge.endAtMillis
        val myTotal = entries.firstOrNull { it.userId == currentUserId }?.total ?: 0.0
        val progressFraction = if (challenge.goalNumber > 0) {
            (myTotal / challenge.goalNumber).coerceIn(0.0, 1.0)
        } else 0.0
        val goalWord = when (challenge.goalType) {
            GoalType.MIN -> "at least"
            GoalType.MAX -> "at most"
        }

        Text(challenge.title, style = MaterialTheme.typography.headlineSmall)
        Text(
            "Goal: $goalWord ${formatNumber(challenge.goalNumber)} ${challenge.unit}, over ${challenge.periodLabel()}",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(16.dp))
        Text("You: ${formatNumber(myTotal)} ${challenge.unit}", style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(
            progress = { progressFraction.toFloat() },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )

        Spacer(Modifier.height(24.dp))
        Text("Group standing", style = MaterialTheme.typography.titleMedium)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        val totalsByUser = entries.associateBy { it.userId }
        val leaderboard = members.map { member ->
            member to (totalsByUser[member.userId]?.total ?: 0.0)
        }.sortedByDescending { it.second }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(leaderboard) { (member, total) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(member.displayName)
                    Text("${formatNumber(total)} ${challenge.unit}")
                }
            }
        }

        if (periodEnded) {
            Text(
                "The challenge period has ended.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Button(onClick = onViewResult, modifier = Modifier.fillMaxWidth()) {
                Text("View results")
            }
        } else {
            Button(onClick = onLogProgress, modifier = Modifier.fillMaxWidth()) {
                Text("Log progress")
            }
        }
    }
}
