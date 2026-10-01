package com.mojdesoh.groupchallenge.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun JoinChoiceScreen(
    onBack: () -> Unit,
    onEnterChallengeName: () -> Unit,
    onDiscoverChallenges: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BackButton(onBack)
            Text("Join a challenge", style = MaterialTheme.typography.headlineSmall)
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = onEnterChallengeName, modifier = Modifier.fillMaxWidth()) {
            Text("Enter the challenge name")
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onDiscoverChallenges, modifier = Modifier.fillMaxWidth()) {
            Text("Discover existing challenges")
        }
    }
}
