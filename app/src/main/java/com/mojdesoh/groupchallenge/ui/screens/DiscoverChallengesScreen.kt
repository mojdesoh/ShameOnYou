package com.mojdesoh.groupchallenge.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mojdesoh.groupchallenge.data.ChallengeRepository
import com.mojdesoh.groupchallenge.data.Group
import com.mojdesoh.groupchallenge.data.GroupStatus
import com.mojdesoh.groupchallenge.data.status
import com.mojdesoh.groupchallenge.data.toDateLabel

@Composable
fun DiscoverChallengesScreen(
    repository: ChallengeRepository,
    onBack: () -> Unit,
    onSelectChallenge: (code: String) -> Unit
) {
    var challenges by remember { mutableStateOf<List<Group>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val uid = repository.currentUserId()
            challenges = repository.discoverJoinableGroups(uid)
        } catch (t: Throwable) {
            error = "Couldn't load challenges. Check your connection and try again."
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Text("Discover existing challenges", style = MaterialTheme.typography.headlineSmall)
        }

        Spacer(Modifier.height(16.dp))

        val current = challenges
        when {
            error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
            current == null -> CircularProgressIndicator()
            current.isEmpty() -> Text(
                "No open challenges right now. Check back later, or create your own.",
                style = MaterialTheme.typography.bodyMedium
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(current, key = { it.id }) { group ->
                    ChallengeDiscoveryCard(group = group, onClick = { onSelectChallenge(group.inviteCode) })
                }
            }
        }
    }
}

@Composable
private fun ChallengeDiscoveryCard(group: Group, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(group.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Created ${group.createdAtMillis.toDateLabel()}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                if (group.status() == GroupStatus.NOT_LOCKED) "Not started" else "In progress",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
