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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mojdesoh.groupchallenge.data.ChallengeRepository
import com.mojdesoh.groupchallenge.data.ChallengeResultEvaluator
import com.mojdesoh.groupchallenge.data.buildResultMessage
import com.mojdesoh.groupchallenge.data.formatNumber

@Composable
fun ResultScreen(groupId: String, repository: ChallengeRepository, onBack: () -> Unit) {
    val group by repository.observeGroup(groupId).collectAsState(initial = null)
    val members by repository.observeMembers(groupId).collectAsState(initial = emptyList())
    val entries by repository.observeEntries(groupId).collectAsState(initial = emptyList())

    val challenge = group?.challenge

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            if (challenge == null || group == null) {
                Text("Loading…", style = MaterialTheme.typography.headlineSmall)
            }
        }
        if (challenge == null || group == null) {
            return@Column
        }

        val result = ChallengeResultEvaluator.evaluate(challenge, members, entries)
        val (title, _) = buildResultMessage(group!!.name, challenge, result)

        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
            if (result.groupSucceeded) {
                "Congratulations, you did it! You reached \"${challenge.title}\"."
            } else {
                val worst = result.worstStanding
                if (worst != null) {
                    "Shame on group ${group!!.name}, but more on ${worst.member.displayName}, " +
                        "who contributed less."
                } else {
                    "Shame on group ${group!!.name} — the goal wasn't reached."
                }
            },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp)
        )

        Spacer(Modifier.height(24.dp))
        Text("Final standings", style = MaterialTheme.typography.titleMedium)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        val sorted = result.standings.sortedByDescending { it.total }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(sorted) { standing ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(standing.member.displayName)
                    Text(
                        "${formatNumber(standing.total)} ${challenge.unit}" +
                            if (standing.succeeded) " ✓" else ""
                    )
                }
            }
        }
    }
}
