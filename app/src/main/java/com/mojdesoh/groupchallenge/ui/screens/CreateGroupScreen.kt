package com.mojdesoh.groupchallenge.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mojdesoh.groupchallenge.data.ChallengeRepository
import com.mojdesoh.groupchallenge.data.LocalPrefs
import kotlinx.coroutines.launch

@Composable
fun CreateGroupScreen(
    repository: ChallengeRepository,
    prefs: LocalPrefs,
    onCreated: (groupId: String) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    var yourName by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Text("Create a group", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = groupName,
            onValueChange = { groupName = it },
            label = { Text("Group name") },
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
        )
        OutlinedTextField(
            value = yourName,
            onValueChange = { yourName = it },
            label = { Text("Your name") },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        Button(
            onClick = {
                error = null
                isSubmitting = true
                scope.launch {
                    try {
                        val group = repository.createGroup(groupName.trim(), yourName.trim())
                        prefs.groupId = group.id
                        prefs.displayName = yourName.trim()
                        onCreated(group.id)
                    } catch (t: Throwable) {
                        error = "Couldn't create the group. Check your connection and try again."
                    } finally {
                        isSubmitting = false
                    }
                }
            },
            enabled = !isSubmitting && groupName.isNotBlank() && yourName.isNotBlank(),
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp)
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
            }
            Text("Create")
        }
    }
}
